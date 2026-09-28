#!/usr/bin/env python3
"""KeyTab — echte Geraete-Perfmessung der IME ueber Shizuku/rish (kein Build).

Die JVM-Benchmarks messen den Algorithmus in Mikrosekunden, nicht das echte
Frame-Budget eines Panels. Dieses Script schliesst genau diese Luecke: es tippt
echte Touch-Events auf die echten Tasten eines angeschlossenen Geraets und
liest die Frame-Zeiten aus `dumpsys gfxinfo`.

Ablauf: Preflight (rish/KeyTab/IME sichtbar) -> Layout-Erkennung per
Pixelanalyse des Screenshots -> Szenarien (tippen, swipen, Backspace-Autorepeat,
Tab-Wechsel, kalt einblenden) -> Budget-Pruefung (PASS/FAIL je Szenario).

Methodik, Grenzen und Interpretation: docs/DEVICE_PERF.md
Aufruf:  python3 scripts/device_perf.py [--scenarios typing,swipe] [--iterations N]
Exit:    0 = Budgets gehalten, 1 = Budget verletzt, 2 = Setup/Umgebung.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import shlex
import statistics
import subprocess
import sys
import time
from dataclasses import dataclass, field, asdict
from typing import Any, Dict, List, Optional, Sequence, Tuple

PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILD_DIR = os.path.join(PROJECT_DIR, "build", "device-perf")
LAYOUT_OVERRIDE = os.path.join(PROJECT_DIR, "scripts", "device_perf_layout.json")

PKG = "com.piotv.keytab"
IME_SERVICE = "com.piotv.keytab/.ime.KeyTabImeService"
REMOTE_DIR = "/sdcard/keytab_perf"          # shared storage -> lokal lesbar
RSH = os.environ.get("KEYTAB_RSH", "sh ~/bin/rsh")

# Dark-Theme-Farben (values/colors.xml, values-night/colors.xml)
KEY_RGB = (46, 46, 46)
BG_RGB = (26, 26, 26)
KEY_TOLERANCE = 14

# Framedeadline: 60 Hz = 16.6 ms, 120 Hz = 8.3 ms. Schwellen so gewaehlt, dass
# ein 60-Hz-Panel ohne Jank durchkommt; 120 Hz wird an p95 sichtbar knapp.
DEFAULT_BUDGETS: Dict[str, float] = {
    "janky_pct": 10.0,
    "p50_ms": 16.6,
    "p95_ms": 16.6,
    "p99_ms": 33.3,
    "min_frames_per_event": 0.5,   # Plausibilitaet: Frames vs. Events
    "max_ui_thread_ms": 16.6,      # UI-Thread je Frame (Process + Draw)
}

QWERTZ_LETTERS = "qwertzuiopüasdfghjklöäyxcvbnmß"
TAB_ORDER = ["theme", "abc", "editor", "files", "clip", "snip", "menu"]

SCENARIOS: Dict[str, Dict[str, Any]] = {
    "typing": {
        "title": "Tippen (Buchstaben, Auto-Shift, Vorschlaege)",
        "why": "Alltags-Hot-Path: Trail-Klassifikation + Vorschlagsleiste "
               "+ dynamische Key-Skalierung pro Tastendruck.",
    },
    "swipe": {
        "title": "Wischen (SwipeManager, Pfadvorschau)",
        "why": "Teuerster Move-Pfad: charAt/dedup pro Motion-Event, "
               "Overlay-Invalidate, Rot-Trace waehrend des Wischens.",
    },
    "backspace": {
        "title": "Backspace-Autorepeat (Long-Press, 30-250 ms)",
        "why": "RepeatScheduler im Dauerbetrieb — der Pfad, der am "
               "wahrscheinlichsten sichtbar rueckelt (Editier-Rhythmus).",
    },
    "tabswitch": {
        "title": "Panel-Wechsel (abc/Files/Clip/Snippets)",
        "why": "Panel-Aufbau mit RecyclerView, Datei-Listing, Layoutwechsel — "
               "die teuerste Nicht-Tippen-Interaktion.",
    },
    "coldshow": {
        "title": "IME kalt einblenden (onCreateInputView)",
        "why": "Teuerster Einzel-Frame: View-Baum, Binding, Theme-Anwendung — "
               "bestimmt die perceived latency beim IME-Wechsel.",
    },
}


class RshError(RuntimeError):
    """rish/Shizuku nicht erreichbar oder Kommando fehlgeschlagen."""


def rsh(command: str, timeout: int = 180, check: bool = True) -> str:
    """Ein Kommando ueber rish (Shizuku, uid=2000) ausfuehren."""
    proc = subprocess.run(
        ["sh", "-c", f"{RSH} {shlex.quote(command)}"],
        capture_output=True, text=True, timeout=timeout,
    )
    out = (proc.stdout or "") + (proc.stderr or "")
    out = "\n".join(
        line for line in out.splitlines()
        if "Waiting for Shizuku authorization" not in line
    )
    if check and proc.returncode != 0:
        raise RshError(f"rish fehlgeschlagen (rc={proc.returncode}): "
                       f"{command[:120]}\n{out.strip()[:400]}")
    return out


# --------------------------------------------------------------------------- Metrik

def _percentile(values: Sequence[float], pct: float) -> float:
    """Nearest-Rank-Perzentil — dieselbe Definition, die gfxinfo benutzt."""
    if not values:
        return 0.0
    ordered = sorted(values)
    idx = int(round(pct / 100.0 * len(ordered) + 0.5)) - 1
    return ordered[max(0, min(idx, len(ordered) - 1))]


def parse_gfxinfo_summary(text: str) -> Dict[str, Any]:
    """Kopfblock von `dumpsys gfxinfo <pkg>` lesen."""
    def grab(pattern: str) -> Optional[float]:
        m = re.search(pattern, text)
        return float(m.group(1)) if m else None

    return {
        "frames": int(grab(r"Total frames rendered:\s*(\d+)") or 0),
        "janky": int(grab(r"Janky frames:\s*(\d+)") or 0),
        "janky_pct": grab(r"Janky frames:\s*\d+\s*\(([0-9.]+)%\)") or 0.0,
        "p50": grab(r"50th percentile:\s*(\d+)ms") or 0.0,
        "p90": grab(r"90th percentile:\s*(\d+)ms") or 0.0,
        "p95": grab(r"95th percentile:\s*(\d+)ms") or 0.0,
        "p99": grab(r"99th percentile:\s*(\d+)ms") or 0.0,
        "missed_vsync": int(grab(r"Number Missed Vsync:\s*(\d+)") or 0),
        "high_input_latency": int(grab(r"Number High input latency:\s*(\d+)") or 0),
        "slow_ui_thread": int(grab(r"Number Slow UI thread:\s*(\d+)") or 0),
        "slow_draw": int(grab(r"Number Slow issue draw commands:\s*(\d+)") or 0),
        "deadline_missed": int(grab(r"Number Frame deadline missed:\s*(\d+)") or 0),
        "gpu_p50": grab(r"50th gpu percentile:\s*(\d+)ms") or 0.0,
    }


# Spaltenindizes im framestats-CSV (1-basiert, aus dem Header):
#   1 Flags, 3 IntendedVsync, 6 HandleInputStart, 9 DrawStart, 12 FrameInterval,
#   14 SyncQueued, 17 SwapBuffers, 18 FrameCompleted
#
# Warum die Ausduennung auf dem Geraet passiert: rish schneidet sehr lange
# Zeilen mitten drin ab (aus 25 Feldern werden 7), wodurch die CSV lokal nicht
# mehr parsebar war. Acht kurze Felder je Frame kommen unbeschnitten an.
FRAMESTATS_AWK = (
    "dumpsys gfxinfo {pkg} framestats | awk -F, "
    "'/^Flags,/ {{next}} NF>=18 "
    "{{print $3\",\"$6\",\"$9\",\"$14\",\"$17\",\"$18\",\"$12\",\"$1}}'"
)


def fetch_framestats(pkg: str = PKG) -> str:
    """Kompakte framestats-Zeilen holen: intended,input,draw,sync,swap,done,int,flags."""
    return rsh(FRAMESTATS_AWK.format(pkg=pkg), timeout=120)


def parse_framestats(text: str) -> Dict[str, Any]:
    """Kompakte framestats auswerten.

    * deadline = FrameCompleted - IntendedVsync: der Abstand zur Framedeadline.
      Weil gfxinfo "janky" gegen die *tatsaechliche* Panel-Rate zaehlt (hier
      120 Hz = 8.3 ms), wird zusaetzlich der Anteil Frames ueber der
      60-Hz-Deadline berechnet ("janky60") — der ist geraeteunabhaengig und
      damit das Gate.
    * ui      = SyncQueued - HandleInputStart: Input-Verarbeitung plus
      Measure/Layout/Record, also die eigentliche Arbeit am UI-Thread.
    * draw    = FrameCompleted - DrawStart: Zeichnen und Rauschen.
    """
    rows: List[List[float]] = []
    for line in text.splitlines():
        parts = line.strip().split(",")
        if len(parts) != 8:
            continue
        try:
            rows.append([float(p) for p in parts])
        except ValueError:
            continue
    if not rows:
        return {"available": False}
    if len(rows) < 10:
        return {"available": False, "sampled_frames": len(rows),
                "note": "zu wenige vollstaendige Frame-Zeilen (rish-Abschneiden?)"}

    intended, input_start, draw_start, sync_queued, swap, completed, interval, flags = \
        zip(*rows)

    def deltas(a: Sequence[float], b: Sequence[float]) -> List[float]:
        # Zeitstempel sind auf neueren Geraeten absolute Nanosekunden (positiv);
        # auf aelteren relativ und negativ. Beide Faelle: Ende > Start > 0.
        return [(y - x) / 1e6 for x, y in zip(a, b) if x > 0 and y > x]

    deadline = deltas(intended, completed)
    ui = deltas(input_start, sync_queued)
    draw = deltas(draw_start, completed)
    frame_ms = [v / 1e6 for v in interval if v > 0]

    def over_budget(values: Sequence[float], budget: float) -> float:
        return (100.0 * sum(1 for v in values if v > budget) / len(values)) \
            if values else 0.0

    return {
        "available": True,
        "sampled_frames": len(deadline),
        "janky60_pct": round(over_budget(deadline, 16.67), 2),
        "janky120_pct": round(over_budget(deadline, 8.34), 2),
        "deadline_distance": {
            "p50": round(_percentile(deadline, 50), 2),
            "p95": round(_percentile(deadline, 95), 2),
            "p99": round(_percentile(deadline, 99), 2),
            "max": round(max(deadline), 2) if deadline else 0.0,
            "mean": round(statistics.fmean(deadline), 2) if deadline else 0.0,
        },
        "ui_thread_ms": {
            "p50": round(_percentile(ui, 50), 2),
            "p95": round(_percentile(ui, 95), 2),
            "max": round(max(ui), 2) if ui else 0.0,
        },
        "draw_ms": {
            "p50": round(_percentile(draw, 50), 2),
            "p95": round(_percentile(draw, 95), 2),
        },
        "reported_frame_interval_ms": round(statistics.fmean(frame_ms), 2)
        if frame_ms else 0.0,
        "gpu_frames": sum(1 for f in flags if int(f) & 8),
    }


# --------------------------------------------------------------------------- Geraet

@dataclass
class DeviceInfo:
    model: str = "?"
    android: str = "?"
    sdk: str = "?"
    refresh_hz: float = 60.0
    width: int = 0
    height: int = 0
    density: int = 0
    ime_shown: bool = False
    target_window: str = "?"

    @property
    def frame_budget_ms(self) -> float:
        """Deadline eines Frames bei der tatsaechlichen Refresh-Rate."""
        return round(1000.0 / self.refresh_hz, 2) if self.refresh_hz else 16.6


def device_info() -> DeviceInfo:
    info = DeviceInfo()
    for prop, attr in (("ro.product.model", "model"),
                       ("ro.build.version.release", "android"),
                       ("ro.build.version.sdk", "sdk")):
        out = rsh(f"getprop {prop}", timeout=60).strip().splitlines()
        if out and out[-1].strip():
            setattr(info, attr, out[-1].strip())

    size = rsh("wm size; wm density", timeout=60)
    m = re.search(r"Physical size:\s*(\d+)x(\d+)", size)
    if m:
        info.width, info.height = int(m.group(1)), int(m.group(2))
    m = re.search(r"Physical density:\s*(\d+)", size)
    if m:
        info.density = int(m.group(1))

    disp = rsh("dumpsys display | grep -m1 DisplayDeviceInfo", timeout=90)
    m = re.search(r"renderFrameRate\s+([0-9.]+)", disp)
    if m:
        info.refresh_hz = float(m.group(1))

    ime = rsh("dumpsys input_method | grep -m1 mIsInputViewShown", timeout=90)
    info.ime_shown = "mIsInputViewShown=true" in ime
    win = rsh("dumpsys window | grep -m1 mCurrentFocus", timeout=90)
    m = re.search(r"Window\{[^}]*\}\s*(?:u\d+\s+)?(\S+)", win)
    info.target_window = m.group(1) if m else win.strip()[:80]
    return info


def preflight(info: DeviceInfo) -> List[Tuple[bool, str]]:
    """Menschenlesbare Vorpruefungen; ein False hier heisst: Setup korrigieren."""
    checks: List[Tuple[bool, str]] = []
    try:
        who = rsh("id -u", timeout=60).strip()
        checks.append((who == "2000", f"rish/Shizuku erreichbar (uid={who or '?'})"))
    except Exception as exc:  # noqa: BLE001
        checks.append((False, f"rish/Shizuku nicht erreichbar: {exc}"))
        return checks

    checks.append((f"package:{PKG}" in rsh(f"pm list packages {PKG}", timeout=60),
                   f"KeyTab installiert ({PKG})"))

    cur = rsh("settings get secure default_input_method", timeout=60).strip()
    checks.append((PKG in cur, f"KeyTab ist aktive IME ({cur or 'keine'})"))

    power = rsh("dumpsys power | grep -m1 mWakefulness", timeout=60)
    checks.append(("Awake" in power, f"Bildschirm an ({power.strip()[:40]})"))
    checks.append((info.ime_shown, "IME-Fenster sichtbar (Zielfeld hat Fokus)"))
    checks.append((bool(info.width and info.height),
                   f"Geometrie {info.width}x{info.height} @ {info.density}dpi, "
                   f"{info.refresh_hz:g} Hz, Budget {info.frame_budget_ms} ms"))
    return checks


# --------------------------------------------------------------------------- Layout
#
# Die Tastatur ist verlaufsfarbig (Tasten 38-64, Hintergrund 26, alles mit einem
# senkrechten Verlauf). Eine feste Farbpruefung auf die Tastenfarbe taugt daher
# nicht. Stattdessen wird die Geometrie ueber die *Struktur* bestimmt:
#
#   Zeilen:  Der Median jeder Bildzeile ist auf einer Taste konstant und in der
#            Luecke zwischen zwei Tastenzeilen niedriger. Vergleich mit dem
#            Mittel der 30 Zeilen darueber/darunter (das kompensiert den
#            Verlauf) trennt Zeilen von Luecken.
#   Spalten: Dasselbe im Kleinen: Der Median je Spalte ueber die Zeilenhoehe
#            einer Zeile ist zwischen zwei Tasten niedriger.
#   Tabs:    Beschriftungen (☾ abc EDITOR ...) sind Text auf Hintergrund, keine
#            gefuellten Rechtecke. Sie werden als helle Pixel-Runs erkannt.

KEY_ROW_MIN_H = 70        # px Mindesthoehe einer Tastenzeile
GAP_DEPTH_Y = 4            # Luecke liegt >= 4 Stufen unter dem Nachbarwert
GAP_DEPTH_X = 3
GAP_NEIGH_Y = 30
GAP_NEIGH_X = 12           # in Einheiten von 2 px
MIN_KEY_W = 16             # px Mindestbreite einer Taste
PATTERN_TOL = 12           # px Toleranz beim Zusammenfassen gleicher Zeilen


@dataclass
class KeyLayout:
    """Trefferpunkte in Screen-Pixeln, aus dem Screenshot erkannt."""
    letters: List[Tuple[int, int]] = field(default_factory=list)
    letter_names: List[str] = field(default_factory=list)
    backspace: Optional[Tuple[int, int]] = None
    space: Optional[Tuple[int, int]] = None
    tabs: Dict[str, Tuple[int, int]] = field(default_factory=dict)
    rows: List[Tuple[str, List[Tuple[int, int]]]] = field(default_factory=list)
    source: str = "?"

    def letter(self, name: str) -> Optional[Tuple[int, int]]:
        return self.letters[self.letter_names.index(name)] \
            if name in self.letter_names else None


def local_screenshot() -> str:
    """Screenshot holen und lokal spiegeln; gibt den lokalen Pfad zurueck.

    Geraete-Pfad und lokaler Pfad sind derselbe Shared Storage (/sdcard), der
    unter proot/Termux durchgemountet ist. Deshalb kein Pull ueber rish noetig.
    """
    os.makedirs(BUILD_DIR, exist_ok=True)
    local = os.path.join(BUILD_DIR, "shot.png")
    device_path = f"{REMOTE_DIR}/shot.png"
    rsh(f"mkdir -p {REMOTE_DIR} && screencap -p {device_path}", timeout=90)
    for candidate in (device_path, os.path.join("/mnt", device_path)):
        if os.path.exists(candidate):
            with open(candidate, "rb") as src, open(local, "wb") as dst:
                dst.write(src.read())
            return local
    raise RshError(f"Screenshot nicht lesbar: {device_path} fehlt auch lokal")


def _runs(flags: Sequence[int]) -> List[Tuple[int, int]]:
    """Index-Baender, an denen flag == 0 ist (die 'Inseln' dazwischen)."""
    out: List[Tuple[int, int]] = []
    start: Optional[int] = None
    for i, v in enumerate(flags):
        if not v and start is None:
            start = i
        elif v and start is not None:
            out.append((start, i - 1))
            start = None
    if start is not None:
        out.append((start, len(flags) - 1))
    return out


def _row_medians(data: bytes, width: int, y0: int, y1: int, step: int = 2) -> List[float]:
    return [statistics.median(data[y * width:(y + 1) * width:step])
            for y in range(y0, y1)]


def _key_rows(data: bytes, width: int, y0: int, y1: int) -> List[Tuple[int, int]]:
    """Kandidaten-Baender fuer Tastenzeilen (absolute y-Koordinaten)."""
    med = _row_medians(data, width, y0, y1)
    n = len(med)
    flags = [1 if med[i] < (med[i - GAP_NEIGH_Y] + med[i + GAP_NEIGH_Y]) / 2
             - GAP_DEPTH_Y else 0
             for i in range(GAP_NEIGH_Y, n - GAP_NEIGH_Y)]
    out = []
    for a, b in _runs(flags):
        if b - a >= 10:
            out.append((a + y0 + GAP_NEIGH_Y, b + y0 + GAP_NEIGH_Y))
    return out


def _column_keys(data: bytes, width: int, top: int, bottom: int,
                 step: int = 2) -> List[Tuple[int, int]]:
    """Tasten-Mittelpunkte (x) einer Zeile."""
    ys = range(top + 5, bottom - 5, 3)
    col = [statistics.median([data[y * width + x] for y in ys])
           for x in range(0, width, step)]
    m = len(col)
    if m <= 2 * GAP_NEIGH_X:
        return []
    flags = [1 if col[i] < (col[i - GAP_NEIGH_X] + col[i + GAP_NEIGH_X]) / 2
             - GAP_DEPTH_X else 0
             for i in range(GAP_NEIGH_X, m - GAP_NEIGH_X)]
    centers = []
    for a, b in _runs(flags):
        if (b - a) * step < MIN_KEY_W:
            continue
        centers.append((int((a + b) / 2) + GAP_NEIGH_X) * step)
    return centers


def _label_runs(data: bytes, width: int, top: int, bottom: int,
                min_bright: int = 3, merge_gap: int = 45) -> List[int]:
    """Mittelpunkte heller Beschriftungs-Runs (fuer die Tab-Leiste)."""
    bright = [sum(1 for y in range(top, bottom) if data[y * width + x] > 90)
              for x in range(width)]
    runs: List[List[int]] = []
    start: Optional[int] = None
    for x, v in enumerate(bright):
        if v >= min_bright and start is None:
            start = x
        elif v < min_bright and start is not None:
            runs.append([start, x - 1])
            start = None
    if start is not None:
        runs.append([start, width - 1])
    merged: List[List[int]] = []
    for r in runs:
        if merged and r[0] - merged[-1][1] < merge_gap:
            merged[-1][1] = r[1]
        else:
            merged.append(r)
    return [int((a + b) / 2) for a, b in merged]


def detect_layout(image) -> KeyLayout:
    """Tastenrechtecke und Tab-Leiste aus dem Screenshot bestimmen."""
    from PIL import Image

    gray = image.convert("L")
    width, height = gray.size
    data = gray.tobytes()

    # Tastaturregion: untere Haelfte, aber ohne Navigationsleiste ganz unten.
    y0 = int(height * 0.45)
    y1 = int(height * 0.94)
    bands = _key_rows(data, width, y0, y1)

    # Baender mit gleichem Spaltenmuster sind Teile derselben Zeile (glyphen
    # druecken den Median einzelner Zeilen und zerlegen die Zeile sonst).
    groups: List[Dict[str, Any]] = []
    for top, bottom in bands:
        keys = _column_keys(data, width, top, bottom)
        if len(keys) < 4:
            continue
        for g in groups:
            if len(g["keys"]) == len(keys) and all(
                    abs(a - b) <= PATTERN_TOL for a, b in zip(g["keys"], keys)):
                g["top"] = min(g["top"], top)
                g["bottom"] = max(g["bottom"], bottom)
                break
        else:
            groups.append({"top": top, "bottom": bottom, "keys": keys})

    rows = [g for g in groups if g["bottom"] - g["top"] >= KEY_ROW_MIN_H]
    rows.sort(key=lambda g: g["top"])
    if len(rows) < 3:
        raise RshError("Layout: weniger als 3 Tastenzeilen erkannt — "
                       "Override in scripts/device_perf_layout.json setzen")

    layout = KeyLayout(source="screenshot (Strukturanalyse)")

    # Tab-Leiste: die Beschriftungszeile mit den meisten hellen Runs oberhalb der
    # ersten Tastenzeile. Ein festes Offset greift daneben, wenn der Terminal-
    # Inhalt darueber ebenfalls hellen Text hat (Termux-Extra-Keys) — deshalb
    # wird das Fenster mit den meisten Runs gesucht statt ein Abstand zu raten.
    tab_top = rows[0]["top"]
    best: List[int] = []
    best_y = max(y0, tab_top - 45)
    win = 45
    for top in range(max(y0, tab_top - 260), max(y0 + 1, tab_top - 25), 10):
        found = _label_runs(data, width, top, min(tab_top - 10, top + win))
        if len(found) > len(best):
            best, best_y = found, top + win // 2
    for i, x in enumerate(best[:len(TAB_ORDER)]):
        layout.tabs[TAB_ORDER[i]] = (x, best_y)

    # Zeilen-Kette: Zahlendreihe -> q-Reihe -> Home -> Shift -> unterste.
    def row_with_keys(count: int, start: int) -> Optional[Dict[str, Any]]:
        for g in rows[start:]:
            if len(g["keys"]) >= count:
                return g
        return None

    numbers = row_with_keys(9, 0)
    idx = rows.index(numbers) if numbers else 0
    letters_rows = [g for g in rows[idx + 1:] if len(g["keys"]) >= 9][:2]
    if not letters_rows:
        raise RshError("Layout: keine Buchstabenzeilen erkannt — "
                       "Override in scripts/device_perf_layout.json setzen")

    names = ["qwertzuiopü", "asdfghjklöä"]
    for name_row, name_row_names in zip(letters_rows, names):
        y = (name_row["top"] + name_row["bottom"]) // 2
        for x, ch in zip(name_row["keys"], name_row_names):
            layout.letters.append((x, y))
            layout.letter_names.append(ch)

    # Backspace = letzte Taste der naechsten Zeile, Space = mittlere der untersten.
    after = [g for g in rows if g["top"] > letters_rows[-1]["bottom"] - 10]
    if after:
        nxt = after[0]
        layout.backspace = (nxt["keys"][-1], (nxt["top"] + nxt["bottom"]) // 2)
    bottom_row = rows[-1]
    if len(bottom_row["keys"]) >= 3:
        # Die unterste Zeile ist [?123][TAB][SPACE][.][ENTER]; die breiteste
        # Taste liegt in der Mitte. Ohne Breitenmessung ist die mittlere Taste
        # die beste Naeherung.
        layout.space = (bottom_row["keys"][len(bottom_row["keys"]) // 2],
                        (bottom_row["top"] + bottom_row["bottom"]) // 2)
    layout.rows = [(f"y{g['top']}-{g['bottom']}", g["keys"]) for g in rows]
    return layout


def load_layout_override() -> Optional[KeyLayout]:
    """Handkalibriertes Layout (JSON) — noetig bei Light-Theme oder Sonderlayouts."""
    if not os.path.exists(LAYOUT_OVERRIDE):
        return None
    with open(LAYOUT_OVERRIDE, encoding="utf-8") as fh:
        raw = json.load(fh)
    layout = KeyLayout(source=f"override {os.path.basename(LAYOUT_OVERRIDE)}")
    layout.letters = [tuple(p) for p in raw.get("letters", [])]     # type: ignore[misc]
    layout.letter_names = list(raw.get("letter_names", ""))
    if raw.get("backspace"):
        layout.backspace = tuple(raw["backspace"])                  # type: ignore[assignment]
    if raw.get("space"):
        layout.space = tuple(raw["space"])                          # type: ignore[assignment]
    layout.tabs = {k: tuple(v) for k, v in raw.get("tabs", {}).items()}  # type: ignore[misc]
    return layout


def save_layout_override(layout: KeyLayout) -> str:
    """Erkanntes Layout als Override speichern (fuer andere Themes/Geraete)."""
    payload = {
        "letters": [list(p) for p in layout.letters],
        "letter_names": "".join(layout.letter_names),
        "backspace": list(layout.backspace) if layout.backspace else None,
        "space": list(layout.space) if layout.space else None,
        "tabs": {k: list(v) for k, v in layout.tabs.items()},
    }
    with open(LAYOUT_OVERRIDE, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, indent=2, ensure_ascii=False)
    return LAYOUT_OVERRIDE


def resolve_layout(use_override: bool = False, save: bool = False,
                   verbose: bool = True) -> KeyLayout:
    from PIL import Image

    if use_override:
        layout = load_layout_override()
        if layout is None:
            raise RshError(f"--layout verlangt, aber {LAYOUT_OVERRIDE} fehlt")
    else:
        shot = local_screenshot()
        layout = detect_layout(Image.open(shot))
        if save:
            save_layout_override(layout)
    if verbose:
        print(f"  Layout: {layout.source}")
        for label, xs in layout.rows:
            print(f"    {label}: {len(xs)} Tasten")
        print(f"    Buchstaben ({len(layout.letters)}): "
              f"{(str(layout.letters[0]) + ' … ' + str(layout.letters[-1])) if layout.letters else '-'}")
        print(f"    Backspace: {layout.backspace}  Space: {layout.space}")
        print(f"    Tabs: { {k: v for k, v in sorted(layout.tabs.items())} }")
    return layout


# --------------------------------------------------------------------------- Szenarien

def _batch(events: Sequence[str]) -> str:
    """Mehrere input-Kommandos in EINEN rish-Aufruf packen.

    Spart pro Szenario die Socket-Roundtrips; die `input`-Prozesse selbst
    bleiben einzeln (das ist die dokumentierte Einschraenkung).
    """
    return " ; ".join(events)


def scenario_typing(layout: KeyLayout, iterations: int) -> Tuple[str, int]:
    words = ["verhandlung", "synchronisation", "haus", "terminal", "test"]
    events: List[str] = []
    for i in range(iterations):
        for word in words:
            for ch in word:
                pos = layout.letter(ch)
                if pos:
                    events.append(f"input tap {pos[0]} {pos[1]}")
    return _batch(events), len(events)


def scenario_swipe(layout: KeyLayout, iterations: int) -> Tuple[str, int]:
    if not layout.letters:
        return "", 0
    y = layout.letters[0][1]
    x0 = layout.letters[0][0]
    x1 = layout.letters[-1][0]
    mid = layout.letters[len(layout.letters) // 2][0]
    events: List[str] = []
    for i in range(iterations):
        # Hin- und Rueckwisch ueber die Buchstabenreihe (Swipe-Pfad inkl. Preview)
        events.append(f"input swipe {x0} {y} {x1} {y} 220")
        events.append(f"input swipe {x1} {y} {x0} {y} 220")
        # kurzer vertikaler Wisch (Kandidaten-Refresh / Overlay-Invalidate)
        events.append(f"input swipe {mid} {y} {mid} {y - 120} 160")
    return _batch(events), len(events)


def scenario_backspace(layout: KeyLayout, iterations: int) -> Tuple[str, int]:
    if not layout.backspace:
        return "", 0
    x, y = layout.backspace
    events = [f"input swipe {x} {y} {x} {y} 1500" for _ in range(iterations)]
    return _batch(events), len(events)


def scenario_tabswitch(layout: KeyLayout, iterations: int) -> Tuple[str, int]:
    order = ["files", "clip", "snip", "editor", "abc"]
    events: List[str] = []
    for _ in range(iterations):
        for name in order:
            pos = layout.tabs.get(name)
            if pos:
                events.append(f"input tap {pos[0]} {pos[1]}")
    return _batch(events), len(events)


def ime_shown() -> bool:
    """Ist das IME-Fenster gerade sichtbar?"""
    return "mIsInputViewShown=true" in rsh(
        "dumpsys input_method | grep -m1 mIsInputViewShown", timeout=90)


def show_ime(info: DeviceInfo, layout: KeyLayout) -> bool:
    """IME wieder einblenden und die Sichtbarkeit verifizieren.

    Es gibt keinen Shell-Befehl, der die IME zuverlaessig einblendet
    (`ime set` wird von HyperOS/MIUI ignoriert). Der Weg ist, den Fokus des
    Zielfelds zu erneuern. Kandidaten in Reihenfolge: die rechte Beschriftung
    ueber der Tastatur (Termux-Schalter "Tastatur ein/aus"), dann die obere
    Bildhaelfte als generischer Tap. Rueckgabe: war die IME danach sichtbar?
    """
    candidates: List[Tuple[int, int]] = []
    if layout.tabs:
        candidates.append(max(layout.tabs.values(), key=lambda v: v[0]))
    candidates.append((info.width // 2, int(info.height * 0.25)))
    for x, y in candidates:
        rsh(f"input tap {x} {y}", timeout=60)
        time.sleep(1.2)
        if ime_shown():
            return True
    return False


def scenario_coldshow(info: DeviceInfo, layout: KeyLayout, iterations: int) \
        -> Tuple[str, int]:
    """IME aus- und wieder einblenden: der teuerste Einzel-Frame.

    Der Wechsel erzwingt onCreateInputView: View-Baum aufbauen, binden, Theme
    anwenden. Jede Iteration wird nur dann gezaehlt, wenn die IME davor
    wirklich ausgeblendet und danach wieder sichtbar war — sonst wuerde der
    Report Frames des Wegs davor als "kalt" ausgeben.
    """
    counted = 0
    for _ in range(iterations):
        rsh("input keyevent 4", timeout=60)          # BACK: IME aus
        time.sleep(0.8)
        if ime_shown():
            continue                                 # ausgeblendet wurde nicht
        if show_ime(info, layout):
            counted += 1
            time.sleep(0.8)
    return "", counted


SCENARIO_FUNCS: Dict[str, Callable[..., Tuple[str, int]]] = {
    "typing": scenario_typing,
    "swipe": scenario_swipe,
    "backspace": scenario_backspace,
    "tabswitch": scenario_tabswitch,
    "coldshow": scenario_coldshow,
}




# --------------------------------------------------------------------------- Auswertung

@dataclass
class Result:
    scenario: str
    title: str
    passed: bool
    skipped: bool = False
    reason: str = ""
    events: int = 0
    summary: Dict[str, Any] = field(default_factory=dict)
    frames: Dict[str, Any] = field(default_factory=dict)
    budgets: Dict[str, float] = field(default_factory=dict)
    checks: List[Dict[str, Any]] = field(default_factory=list)
    wall_s: float = 0.0


def _check(name: str, value: float, limit: float, unit: str = "ms",
           lower_is_better: bool = True) -> Dict[str, Any]:
    ok = value <= limit if lower_is_better else value >= limit
    return {"name": name, "value": value, "limit": limit,
            "unit": unit, "ok": ok}


def evaluate(result: Result, budgets: Dict[str, float]) -> Result:
    """Gates bewerten. Jank wird gegen die 60-Hz-Deadline gewertet, damit das
    Ergebnis zwischen 60-Hz- und 120-Hz-Panels vergleichbar bleibt; die
    Panel-Rate selbst wird im Report als Information ausgegeben."""
    s = result.summary
    f = result.frames
    if not s.get("frames"):
        result.checks = [{"name": "frames", "value": 0, "limit": 1,
                          "unit": " Frames", "ok": False}]
        result.passed = False
        result.reason = ("keine Frames gezeichnet — hat die IME auf das Event "
                         "ueberhaupt reagiert?")
        return result
    jank = f.get("janky60_pct", 0.0) if f.get("available") else s.get("janky_pct", 0.0)
    checks: List[Dict[str, Any]] = [
        _check("janky60_pct", jank, budgets["janky_pct"], "%"),
        _check("p50", s.get("p50", 0.0), budgets["p50_ms"]),
        _check("p95", s.get("p95", 0.0), budgets["p95_ms"]),
        _check("p99", s.get("p99", 0.0), budgets["p99_ms"]),
    ]
    if f.get("available"):
        checks.append(_check("ui_thread_p95", f["ui_thread_ms"]["p95"],
                             budgets["max_ui_thread_ms"]))
    if result.events:
        ratio = s.get("frames", 0) / result.events
        checks.append(_check("frames_per_event", ratio,
                             budgets["min_frames_per_event"], "", False))
    result.checks = checks
    result.passed = all(c["ok"] for c in checks)
    return result


def run_scenario(name: str, info: DeviceInfo, layout: KeyLayout,
                 iterations: int, budgets: Dict[str, float],
                 verbose: bool = True) -> Result:
    meta = SCENARIOS[name]
    if name == "coldshow":
        command, events = scenario_coldshow(info, layout, iterations)
    else:
        command, events = SCENARIO_FUNCS[name](layout, iterations)

    result = Result(scenario=name, title=meta["title"], passed=False,
                    events=events, budgets=budgets)
    if not command or events == 0:
        result.skipped = True
        result.reason = "keine passenden Tasten im erkannten Layout gefunden"
        return result
    if verbose:
        print(f"  → {meta['title']}: {events} Events …")
    rsh(f"dumpsys gfxinfo {PKG} reset", timeout=60)
    start = time.time()
    try:
        rsh(command, timeout=max(180, events * 12))
    except RshError as exc:
        result.skipped = True
        result.reason = f"Events fehlgeschlagen: {exc}"[:200]
        return result
    time.sleep(0.8)                      # Nachlauf der letzten Frames
    result.wall_s = round(time.time() - start, 1)

    result.summary = parse_gfxinfo_summary(rsh(f"dumpsys gfxinfo {PKG}", timeout=90))
    result.frames = parse_framestats(fetch_framestats(PKG))
    return evaluate(result, budgets)

# --------------------------------------------------------------------------- Report

COLORS = {"red": "\033[31m", "green": "\033[32m", "yellow": "\033[33m",
          "bold": "\033[1m", "reset": "\033[0m"}


def _c(text: str, color: str, enabled: bool) -> str:
    return f"{COLORS[color]}{text}{COLORS['reset']}" if enabled else text


def print_report(results: List[Result], info: DeviceInfo, budgets: Dict[str, float],
                 color: bool = True) -> None:
    print()
    print(_c("KeyTab - echte Geraete-Perfmessung (Shizuku/rish)", "bold", color))
    print("=" * 68)
    print(f"Geraet      : {info.model}, Android {info.android} (API {info.sdk})")
    print(f"Display     : {info.width}x{info.height} @ {info.density}dpi, "
          f"{info.refresh_hz:g} Hz -> Framedeadline {info.frame_budget_ms} ms")
    print(f"Zielfeld    : {info.target_window}")
    print(f"Gate        : Jank gegen 60-Hz-Deadline (16.7 ms); "
          f"Jank@Hz = gfxinfo-Wert gegen {info.refresh_hz:g} Hz")
    print(f"Budgets     : janky <= {budgets['janky_pct']:g} %, p50/p95 <= "
          f"{budgets['p50_ms']:g} ms, p99 <= {budgets['p99_ms']:g} ms, "
          f"UI-Thread p95 <= {budgets['max_ui_thread_ms']:g} ms")
    print()
    print(_c(f"{'Szenario':<11}{'Events':>7}{'Frames':>8}{'Jank60':>8}{'Jank@Hz':>8}"
             f"{'p50':>6}{'p95':>6}{'p99':>6}{'UI p95':>8}  Ergebnis", "bold", color))
    print("-" * 74)
    for r in results:
        s, f = r.summary, r.frames
        ui = f"{f['ui_thread_ms']['p95']:.1f}" if f.get("available") else "-"
        if r.skipped:
            print(f"{r.scenario:<11}{r.events:>7}{'-':>8}{'-':>8}{'-':>8}"
                  f"{'-':>6}{'-':>6}{'-':>6}{ui:>8}  {_c('SKIP', 'yellow', color)}  "
                  f"{r.reason[:38]}")
            continue
        mark = _c("PASS", "green", color) if r.passed else _c("FAIL", "red", color)
        j60 = f"{f['janky60_pct']:.1f}%" if f.get("available") else "-"
        print(f"{r.scenario:<11}{r.events:>7}{s.get('frames', 0):>8}{j60:>8}"
              f"{s.get('janky_pct', 0):>7.1f}%{s.get('p50', 0):>5.0f}m"
              f"{s.get('p95', 0):>5.0f}m{s.get('p99', 0):>5.0f}m{ui:>8}  {mark}")
    print()
    for r in results:
        if r.skipped or r.passed:
            continue
        print(_c(f"FEHLGESCHLAGEN: {r.scenario}", "red", color))
        for c in r.checks:
            if not c["ok"]:
                print(f"    {c['name']}: {c['value']}{c['unit']} > "
                      f"{c['limit']}{c['unit']}")
    agg = [r for r in results if not r.skipped and r.summary.get("frames")]
    if agg:
        frames = sum(r.summary["frames"] for r in agg)
        janky = sum(r.summary["janky"] for r in agg)
        print()
        print(f"Gesamt      : {frames} Frames, {janky} janky "
              f"({100.0 * janky / max(1, frames):.1f} %), "
              f"{sum(r.wall_s for r in agg):.0f} s Laufzeit")
        print(f"             : slow UI thread "
              f"{sum(r.summary.get('slow_ui_thread', 0) for r in agg)}, "
              f"Deadline verfehlt "
              f"{sum(r.summary.get('deadline_missed', 0) for r in agg)}, "
              f"hohe Input-Latenz "
              f"{sum(r.summary.get('high_input_latency', 0) for r in agg)}")


def write_reports(results: List[Result], info: DeviceInfo,
                  budgets: Dict[str, float], json_path: Optional[str]) -> None:
    os.makedirs(BUILD_DIR, exist_ok=True)
    payload = {
        "stamp": time.strftime("%Y-%m-%d %H:%M"),
        "device": asdict(info),
        "budgets": budgets,
        "all_passed": all(r.passed or r.skipped for r in results),
        "results": [asdict(r) for r in results],
    }
    target = json_path or os.path.join(
        BUILD_DIR, f"device-perf-{time.strftime('%Y%m%d-%H%M%S')}.json")
    os.makedirs(os.path.dirname(os.path.abspath(target)), exist_ok=True)
    latest = os.path.join(BUILD_DIR, "latest.json")
    for path in (target, latest):
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, indent=2, ensure_ascii=False)
    print(f"\nJSON        : {os.path.relpath(target, PROJECT_DIR)}")
    print(f"             : {os.path.relpath(latest, PROJECT_DIR)}")


# --------------------------------------------------------------------------- CLI

def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="KeyTab Geraete-Perfmessung der IME (Shizuku/rish)")
    parser.add_argument("--scenarios", default="all",
                        help="Komma-Liste: " + ",".join(SCENARIOS) + " (default: all)")
    parser.add_argument("--iterations", type=int, default=3,
                        help="Wiederholungen je Szenario (default: 3)")
    parser.add_argument("--layout", action="store_true",
                        help="Handkalibriertes Layout aus device_perf_layout.json")
    parser.add_argument("--budget-janky", type=float,
                        default=DEFAULT_BUDGETS["janky_pct"], metavar="PCT")
    parser.add_argument("--budget-p95", type=float, default=DEFAULT_BUDGETS["p95_ms"],
                        metavar="MS")
    parser.add_argument("--budget-p99", type=float, default=DEFAULT_BUDGETS["p99_ms"],
                        metavar="MS")
    parser.add_argument("--budget-ui", type=float,
                        default=DEFAULT_BUDGETS["max_ui_thread_ms"], metavar="MS")
    parser.add_argument("--json", help="Zusaetzlicher JSON-Zielpfad")
    parser.add_argument("--save-layout", action="store_true",
                        help="Erkanntes Layout als device_perf_layout.json sichern")
    parser.add_argument("--list-scenarios", action="store_true")
    parser.add_argument("--no-color", action="store_true")
    args = parser.parse_args(argv)

    if args.list_scenarios:
        for name, meta in SCENARIOS.items():
            print(f"{name:<11} {meta['title']}\n{'':<11} {meta['why']}")
        return 0

    budgets = dict(DEFAULT_BUDGETS)
    budgets["janky_pct"] = args.budget_janky
    budgets["p95_ms"] = args.budget_p95
    budgets["p99_ms"] = args.budget_p99
    budgets["max_ui_thread_ms"] = args.budget_ui

    names = list(SCENARIOS) if args.scenarios == "all" else \
        [n.strip() for n in args.scenarios.split(",") if n.strip()]
    unknown = [n for n in names if n not in SCENARIOS]
    if unknown:
        print(f"Unbekannte Szenarien: {', '.join(unknown)}", file=sys.stderr)
        return 2

    color = not args.no_color and sys.stdout.isatty()
    try:
        info = device_info()
    except Exception as exc:  # noqa: BLE001
        print(f"Setup-Fehler: Geraet nicht erreichbar ({exc})", file=sys.stderr)
        return 2

    print("\nPreflight")
    checks = preflight(info)
    for ok, text in checks:
        print(f"  {_c('OK', 'green', color) if ok else _c('XX', 'red', color)} {text}")
    if not all(ok for ok, _ in checks):
        print("\nAbbruch: Setup unvollstaendig (siehe Meldungen oben).")
        return 2

    try:
        layout = resolve_layout(use_override=args.layout, save=args.save_layout,
                                verbose=True)
    except Exception as exc:  # noqa: BLE001
        print(f"\nLayout-Erkennung fehlgeschlagen: {exc}", file=sys.stderr)
        return 2

    print(f"\nMessung (je Szenario {args.iterations} Iterationen)")
    results: List[Result] = []
    for name in names:
        try:
            results.append(run_scenario(name, info, layout, args.iterations,
                                         budgets, verbose=True))
        except Exception as exc:  # noqa: BLE001
            results.append(Result(scenario=name, title=SCENARIOS[name]["title"],
                                  passed=False, skipped=True,
                                  reason=f"interner Fehler: {exc}"[:200]))
        print(f"    {time.strftime('%H:%M:%S')} {name} fertig")

    print_report(results, info, budgets, color=color)
    write_reports(results, info, budgets, args.json)
    return 0 if all(r.passed or r.skipped for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
