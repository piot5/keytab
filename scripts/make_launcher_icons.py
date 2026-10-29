#!/usr/bin/env python3
"""KeyTab — Launcher-Icons aus dem Master-Artwork erzeugen.

Warum dieses Skript existiert
-----------------------------
v0.20 hat das 2600x2600-Artwork (1,24 MB) direkt als ``ic_launcher.png`` in
alle fünf Dichte-Ordner kopiert und dabei das Adaptive Icon (anydpi-v26) sowie
Background/Foreground/Monochrome gelöscht. Ergebnis: 5 x 982 KB im APK, also
+4,9 MB — die APK wuchs von 2,28 MB (v0.19) auf 6,92 MB (v0.20), ohne dass
irgendwo stand, warum. Kein Gate hat es gesehen, weil kein Gate Asset-Größen
kennt.

Erzeugt wird deshalb der vollständige Satz in korrekter Größe:

    mipmap-<dichte>/ic_launcher.png               Legacy (API 24/25): Artwork komplett
    mipmap-<dichte>/ic_launcher_foreground.png    Adaptive-Vordergrund, Motiv freigestellt
    mipmap-<dichte>/ic_launcher_monochrome.png    Themed-Icon (API 33+): Silhouette
    mipmap-anydpi-v26/ic_launcher.xml             Adaptive Icon (API 26+)
    values/ic_launcher_background.xml             Grundfarbe des Artworks

Geometrie (Google-Vorgabe für Adaptive Icons): Leinwand 108x108 dp, sichere
Zone 66x66 dp. Das Motiv (Signal >= 60) liegt bei 60,2 % der Breite = 65 dp und
damit innerhalb der sicheren Zone — es wird also NICHT beschnitten, sondern 1:1
auf die Leinwand abgebildet ("Icon behalten"). Die sehr schwache Grundtextur
des Artworks (Signal 1-47) reicht bis an den Rand; sie wird von der
Launcher-Maske beschnitten, was auf dem schwarzen Grund unsichtbar ist.

Die Ebenen entstehen ohne geratene Schwelle:
  Vordergrund  Alpha = Signal (max(r,g,b)), RGB unpremultipliziert. Über den
               schwarzen Hintergrund komponiert ergibt das wieder das Artwork
               (Rundungsfehler <= 1) — inklusive weicher Kanten.
  Monochrom    bewusst NICHT das Signal, sondern nur das Motiv ab Signal 60.
               Sonst würde die Grundtextur den ganzen Themed-Icon-Bereich
               leicht eintönen, statt nur das Motiv zu zeigen.

Aufruf:
    python3 scripts/make_launcher_icons.py            # Icons neu erzeugen
    python3 scripts/make_launcher_icons.py --check     # nur prüfen (ohne Pillow)

--check prüft Dateien, Pixelmaße und ein Größenbudget und ist bewusst ohne
Pillow lauffähig, damit es im Gate ``scripts/check_docs_drift.sh`` laufen kann
(dort steckt auch die Ursachenbeschreibung). Exit 1 = Abweichung.
"""

from __future__ import annotations

import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MASTER = ROOT / "docs" / "images" / "keytab-icon-master-2600.png"
RES = ROOT / "app" / "src" / "main" / "res"

# (Ordnername, Faktor) — Faktor 1.0 = mdpi
DENSITIES = (
    ("mdpi", 1.0),
    ("hdpi", 1.5),
    ("xhdpi", 2.0),
    ("xxhdpi", 3.0),
    ("xxxhdpi", 4.0),
)
LEGACY_DP = 48   # klassische Launcher-Icon-Kante
ADAPTIVE_DP = 108  # Adaptive-Icon-Leinwand; Motiv liegt in den inneren 66 dp

# Budget pro PNG. Ein versehentlich hineinkopiertes 2600-px-Artwork sprengt es
# um Größenordnungen (982 KB gemessen) und fällt damit sofort auf.
MAX_PNG_BYTES = 60 * 1024

BACKGROUND_COLOR = "#FF000000"  # Grundfläche des Artworks (gemessen: (0,0,0))

ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Adaptives Icon: Leinwand 108x108 dp, Motiv innerhalb der 66x66-dp-Safe-Zone
     (Artwork-Motiv = 60,2 % der Breite = 65 dp). Erzeugt von
     scripts/make_launcher_icons.py — nicht von Hand bearbeiten. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""

BACKGROUND_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Grundfarbe des Launcher-Icons (Hintergrundebene des Adaptive Icons).
     Erzeugt von scripts/make_launcher_icons.py — nicht von Hand bearbeiten. -->
<resources>
    <color name="ic_launcher_background">%s</color>
</resources>
""" % BACKGROUND_COLOR


def expected_files():
    """(Pfad, Breite, Hoehe) aller PNGs, die existieren muessen."""
    out = []
    for name, factor in DENSITIES:
        legacy = round(LEGACY_DP * factor)
        canvas = round(ADAPTIVE_DP * factor)
        out.append((RES / f"mipmap-{name}" / "ic_launcher.png", legacy, legacy))
        out.append((RES / f"mipmap-{name}" / "ic_launcher_foreground.png", canvas, canvas))
        out.append((RES / f"mipmap-{name}" / "ic_launcher_monochrome.png", canvas, canvas))
    return out


def png_size(path):
    """Pixelmasse direkt aus dem IHDR lesen — ohne Pillow."""
    with path.open("rb") as handle:
        head = handle.read(26)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("keine PNG-Datei")
    return struct.unpack(">II", head[16:24])


def check():
    problems = 0
    for path, width, height in expected_files():
        rel = path.relative_to(ROOT)
        if not path.exists():
            print(f"  x fehlt: {rel}")
            problems += 1
            continue
        size = path.stat().st_size
        got = png_size(path)
        if got != (width, height):
            print(f"  x {rel}: {got[0]}x{got[1]} px, erwartet {width}x{height}")
            problems += 1
        if size > MAX_PNG_BYTES:
            print(f"  x {rel}: {size:,} B > Budget {MAX_PNG_BYTES:,} B")
            problems += 1
    for extra in (RES / "mipmap-anydpi-v26" / "ic_launcher.xml",
                  RES / "values" / "ic_launcher_background.xml"):
        if not extra.exists():
            print(f"  x fehlt: {extra.relative_to(ROOT)}")
            problems += 1
    stale = RES / "KeyTabIcon.png"
    if stale.exists():
        print(f"  x {stale.relative_to(ROOT)}: Master gehoert nicht in res/ "
              "(aapt2 ignoriert ihn, er blaeht nur das Repo auf)")
        problems += 1
    if problems == 0:
        print(f"  OK {len(expected_files())} Launcher-PNGs in korrekter Dichte/Groesse")
    return 1 if problems else 0


def _pillow():
    """Pillow erst zur Laufzeit laden — der Prüfmodus kommt ohne aus."""
    from PIL import Image
    return Image


def extract_foreground(img):
    """Motiv freistellen: Alpha = Signal (max(r,g,b)), RGB zurueckgerechnet.

    Ohne die Rueckrechnung wuerde "ueber Schwarz komponiert" das Bild abdunkeln
    (gemessene Abweichung bis 64/255 bei dunklen, gesaettigten Pixeln), weil
    Alpha und Farbe beide aus demselben Signal stammen. Mit der Rueckrechnung
    gilt rgb*alpha/255 == original.
    """
    width, height = img.size
    out = img.copy()
    source = img.load()
    target = out.load()
    for y in range(height):
        for x in range(width):
            red, green, blue, _ = source[x, y]
            alpha = max(red, green, blue)
            if alpha == 0:
                target[x, y] = (0, 0, 0, 0)
                continue
            factor = 255.0 / alpha
            target[x, y] = (min(255, round(red * factor)),
                            min(255, round(green * factor)),
                            min(255, round(blue * factor)),
                            alpha)
    return out


def extract_monochrome(img, threshold=60):
    """Silhouette fuer das Themed-Icon: nur das Motiv, kein Grundschleier.

    Signalwerte unter ``threshold`` gehoeren zur Grundtextur des Artworks; sie
    wuerden im Themed-Icon als Flaechenschleier erscheinen. Darueber wird weich
    auf 255 hochgezogen, damit die Motivkanten sauber bleiben.
    """
    width, height = img.size
    source = img.load()
    Image = _pillow()
    out = Image.new("RGBA", (width, height), (255, 255, 255, 0))
    target = out.load()
    span = 255 - threshold
    for y in range(height):
        for x in range(width):
            red, green, blue, _ = source[x, y]
            signal = max(red, green, blue)
            if signal <= threshold:
                continue
            alpha = min(255, round((signal - threshold) * 255 / span))
            target[x, y] = (255, 255, 255, alpha)
    return out


def generate():
    try:
        Image = _pillow()
    except ImportError:
        print("Pillow fehlt: apt-get install python3-pil  (oder --check benutzen)",
              file=sys.stderr)
        return 2

    if not MASTER.exists():
        print(f"Master-Artwork fehlt: {MASTER.relative_to(ROOT)}", file=sys.stderr)
        return 2

    master = Image.open(MASTER).convert("RGBA")
    resample = Image.LANCZOS
    written = 0
    for name, factor in DENSITIES:
        directory = RES / f"mipmap-{name}"
        directory.mkdir(parents=True, exist_ok=True)
        legacy = round(LEGACY_DP * factor)
        canvas = round(ADAPTIVE_DP * factor)
        # Erst auf Zielgroesse skalieren, dann die Ebenen daraus ableiten: das
        # Skalieren des fertigen Bildes ist genauer als das Skalieren getrennter
        # Ebenen (keine Halo-Kanten), und die Freistellung laeuft nur ueber die
        # kleinen Bilder.
        artwork = master.resize((legacy, legacy), resample)
        layer_base = master.resize((canvas, canvas), resample)
        jobs = (
            ("ic_launcher.png", artwork),
            ("ic_launcher_foreground.png", extract_foreground(layer_base)),
            ("ic_launcher_monochrome.png", extract_monochrome(layer_base)),
        )
        for filename, image in jobs:
            target = directory / filename
            image.save(target, "PNG", optimize=True)
            written += 1
            print(f"  {str(target.relative_to(ROOT)):58} {target.stat().st_size:>7,} B")

    (RES / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
    (RES / "mipmap-anydpi-v26" / "ic_launcher.xml").write_text(ADAPTIVE_XML, encoding="utf-8")
    (RES / "values").mkdir(parents=True, exist_ok=True)
    (RES / "values" / "ic_launcher_background.xml").write_text(BACKGROUND_XML, encoding="utf-8")
    print(f"  {written} PNGs + 2 XML geschrieben")
    return 0


def main(argv):
    if "--check" in argv[1:]:
        return check()
    return generate()


if __name__ == "__main__":
    sys.exit(main(sys.argv))

