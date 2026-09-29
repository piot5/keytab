#!/usr/bin/env python3
"""KeyTab — schneller 3-Lauf-Zyklus: langer Text tippen, dann alles bis auf
~1 Wort loeschen (KEYCODE_DEL, deterministische Anzahl), Frame-Timing je
Phase. Kein Preflight/Screenshot pro Phase — Setup und Layout-Erkennung
einmalig. Das Geraet muss eingeschaltet bleiben und darf waehrend der
Messung nicht beruehrt werden.

Aufruf: python3 scripts/perf_cycle.py [--runs 3] [--word-chars 24]
Exit:   0 = Budgets gehalten (Jank@60 <= 10 %), 1 = verletzt, 2 = Setup.
"""
from __future__ import annotations

import argparse
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from device_perf import (  # noqa: E402
    PKG, detect_layout, fetch_framestats, local_screenshot,
    parse_framestats, parse_gfxinfo_summary, rsh,
)

BUDGET_JANK60 = 10.0
DEL_KEYCODE = 67          # KEYCODE_DEL
KEEP_CHARS = 5            # so viele Zeichen am Ende stehen bleiben (~1 Wort)


def ime_shown() -> bool:
    return "mIsInputViewShown=true" in rsh(
        "dumpsys input_method | grep -m1 mIsInputViewShown", timeout=60)


def normal_field_focused() -> bool:
    return "inputType=0x1 " in rsh("dumpsys input_method", timeout=60)


def setup() -> None:
    rsh("am start -n com.piotv.keytab.debug/com.piotv.keytab.ImeTargetActivity",
        timeout=60)
    time.sleep(2)
    rsh("input tap 610 190", timeout=60)
    time.sleep(1)
    for _ in range(4):
        if ime_shown() and normal_field_focused():
            return
        rsh("input tap 610 190", timeout=60)
        time.sleep(1)
    print("ABBRUCH: Normalfeld/IME nicht verifizierbar", file=sys.stderr)
    sys.exit(2)


def snapshot(pkg: str) -> dict:
    summary = parse_gfxinfo_summary(rsh(f"dumpsys gfxinfo {pkg}", timeout=90))
    frames = parse_framestats(fetch_framestats(pkg))
    jank = frames.get("janky60_pct", 0.0) if frames.get("available") \
        else summary.get("janky_pct", 0.0)
    return {
        "events": 0, "frames": summary.get("frames", 0),
        "jank60": round(jank, 1),
        "p50": summary.get("p50", 0.0), "p95": summary.get("p95", 0.0),
        "ui95": frames.get("ui_thread_ms", {}).get("p95", 0.0)
        if frames.get("available") else None,
    }


def batch(events: list[str]) -> str:
    return "; ".join(events)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--word-chars", type=int, default=24,
                    help="getippte Zeichen je Lauf")
    ap.add_argument("--pkg", default=os.environ.get("KEYTAB_PKG", PKG))
    args = ap.parse_args()

    setup()
    layout = detect_layout(__import__("PIL.Image", fromlist=["Image"])
                           .open(local_screenshot()))
    if not layout.letters:
        print("ABBRUCH: keine Tasten erkannt", file=sys.stderr)
        sys.exit(2)
    # Backspace/Space aus dem kalibrierten Override, falls vorhanden —
    # die Auto-Erkennung verwechselt Backspace gelegentlich mit Enter.
    import json
    override = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                            "device_perf_layout.json")
    if os.path.exists(override):
        raw = json.load(open(override, encoding="utf-8"))
        if raw.get("backspace"):
            layout.backspace = tuple(raw["backspace"])
        if raw.get("space"):
            layout.space = tuple(raw["space"])
    names = [n for n in layout.letter_names
             if n in "qwertzuiopasdfghjklzxcvbnm"]
    # Echter Text: Woerter + Leerzeichen-Taps (echte Wortvorhersage).
    # Nur Buchstaben, die die Erkennung sicher findet (dynamische
    # Key-Skalierung laesst unlikely Keys schrumpfen und sie werden
    # dann nicht erkannt).
    sentence = "haus auf zeit gut"
    if not layout.space or not layout.backspace:
        print("ABBRUCH: Space/Backspace im Layout fehlt", file=sys.stderr)
        sys.exit(2)
    typed = sentence[:args.word_chars]
    typed = typed[:typed.rfind(" ")] if " " in typed else typed  # ganzes Wort
    del_count = max(len(typed) - KEEP_CHARS, 1)
    if any(c not in layout.letter_names for c in typed.replace(" ", "")):
        print("ABBRUCH: Buchstabe fehlt im erkannten Layout", file=sys.stderr)
        sys.exit(2)

    print(f"Setup OK — {args.runs} Laeufe: '{typed}' tippen ({len(typed)} "
          f"Zeichen), dann {del_count}x Backspace-Tap (es bleibt ~1 Wort). "
          f"NICHT BERUEHREN.")
    results: list[dict] = []
    t0 = time.time()
    for run in range(1, args.runs + 1):
        rsh(f"dumpsys gfxinfo {args.pkg} reset", timeout=60)
        seq: list[str] = []
        for c in typed:
            p = layout.space if c == " " else layout.letter(c)
            if p:
                seq.append(f"input tap {p[0]} {p[1]}")
        rsh(batch(seq), timeout=240)
        typing = snapshot(args.pkg)
        typing["events"] = len(seq)

        rsh(f"dumpsys gfxinfo {args.pkg} reset", timeout=60)
        bx, by = layout.backspace
        rsh(batch(f"input tap {bx} {by}" for _ in range(del_count)),
            timeout=240)
        back = snapshot(args.pkg)
        back["events"] = del_count

        results.append({"run": run, "typing": typing, "backspace": back})
        print(f"  run {run} fertig ({time.time() - t0:.0f}s gesamt)")

    total = time.time() - t0
    print()
    print(f"{'Phase':<10}{'Events':>7}{'Frames':>8}{'Jank60':>8}"
          f"{'p50':>6}{'p95':>6}{'UI p95':>8}  Ergebnis")
    print("-" * 62)
    worst = 0.0
    for r in results:
        for phase in ("typing", "backspace"):
            s = r[phase]
            ok = s["jank60"] <= BUDGET_JANK60 and s["frames"] > 0
            worst = max(worst, s["jank60"])
            ui = f"{s['ui95']:.1f}" if s["ui95"] is not None else "-"
            print(f"{phase:<10}{s['events']:>7}{s['frames']:>8}"
                  f"{s['jank60']:>7.1f}%{s['p50']:>6.1f}{s['p95']:>6.1f}"
                  f"{ui:>8}  {'PASS' if ok else 'FAIL'}")
    print(f"\nGesamt: {total:.0f} s, schlechtester Jank@60: {worst:.1f} % "
          f"(Budget {BUDGET_JANK60:g} %)")
    return 0 if worst <= BUDGET_JANK60 else 1


if __name__ == "__main__":
    sys.exit(main())