#!/usr/bin/env python3
"""KeyTab — Icon-Generator (vier Varianten: dunkler Grund, Grün/Blau/Weiß).

Die Farben kommen aus dem Repo, nicht aus einer zweiten Wahrheit:
  * Grün  = `primary` aus `app/src/main/res/values/colors.xml` (#4caf50)
  * Schwarz = `background`/`surface` aus `values-night/colors.xml` (#1a1a1a/#2e2e2e)
  * Blau  = der Farbwert des bisherigen Launcher-Icons
            (`app/src/main/res/drawable/ic_launcher.xml`, #1B6EF3)

Je Variante entstehen:
  * Store-Export für Fastlane: 512x512 PNG (`images/icon.png`, IzzyOnDroid-Pflicht)
  * Adaptive-Icon-Ebenen in 108dp je Density
    (background/foreground/monochrome: mdpi 108 … xxxhdpi 432 px)
  * Legacy-Raster für API 24–25 (minSdk 24): 48/72/96/144/192 px
  * XML-Wrapper `mipmap-anydpi-v26/ic_launcher.xml`
  * eine Vergleichstafel (512/192/96/48 px auf hell und dunkel)

Adaptive-Regeln (Android-Doku, „Adaptive icons“): alle Ebenen 108x108 dp,
Logo mindestens 48x48 dp und höchstens 66x66 dp — die inneren 66x66 dp sind
die Safe Zone, die keine OEM-Maske beschneidet. Deshalb wird das Motiv mit
66/108 der Kantenlänge zentriert gezeichnet.

Aufruf:
    python3 scripts/make_icon.py                      # alle Varianten
    python3 scripts/make_icon.py --variant b          # nur eine
    python3 scripts/make_icon.py --variant b --install  # B ins App-/Fastlane-Tree

Ausgabe (Default): build/icon-drafts/  (build/ ist gitignored, die Entwürfe
verschmutzen das Repo also nicht). Pillow nötig: python3-pil.
"""

from __future__ import annotations

import argparse
import math
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
OUT_DEFAULT = ROOT / "build" / "icon-drafts"
FONT_DIR = Path("/usr/share/fonts/truetype/dejavu")

SS = 4                      # Supersampling-Faktor gegen Treppchen
# Adaptive-Safe-Zone: die Android-Doku erlaubt höchstens 66/108 dp. Gezeichnet
# wird mit 62/108 — Pillow legt Umrisse halb über die Pfadkante (bei Variante A
# ~3 % Überstand), damit bleibt auch ein Outline-Motiv unter der 66-dp-Grenze.
SAFE_FRACTION = 62 / 108
LEGACY_FRACTION = 0.70      # Motivgroesse im Legacy-Icon (keine Maske auf API 24/25)
STORE_SIZE = 512            # Fastlane: images/icon.png
DENSITIES = {               # Android-Density -> Legacy-Kantenlänge in px
    "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192,
}
BLACK_TOP = (0x0D, 0x0D, 0x0D)
BLACK_BOTTOM = (0x1A, 0x1A, 0x1A)


def _hex_to_rgb(value: str) -> tuple[int, int, int]:
    return tuple(int(value[i:i + 2], 16) for i in (1, 3, 5))  # type: ignore[return-value]


def color_from_xml(name: str, path: Path, default: tuple[int, int, int]) -> tuple[int, int, int]:
    """Liest `<color name="…">#rrggbb</color>` aus einer Ressourcendatei."""
    if not path.exists():
        return default
    m = re.search(rf'<color name="{re.escape(name)}">(#[0-9a-fA-F]{{6}})</color>', path.read_text())
    return _hex_to_rgb(m.group(1)) if m else default


def blue_from_vector(path: Path, default: tuple[int, int, int]) -> tuple[int, int, int]:
    """Erster fillColor-Wert des bisherigen Launcher-Vektors = Bestandsblau."""
    if not path.exists():
        return default
    m = re.search(r'android:fillColor="(#[0-9a-fA-F]{6})"', path.read_text())
    return _hex_to_rgb(m.group(1)) if m else default


GREEN = color_from_xml("primary", RES / "values" / "colors.xml", (0x4C, 0xAF, 0x50))
DARK_BG = color_from_xml("background", RES / "values-night" / "colors.xml", (0x1A, 0x1A, 0x1A))
BLUE = blue_from_vector(RES / "drawable" / "ic_launcher.xml", (0x1B, 0x6E, 0xF3))
WHITE = (0xFF, 0xFF, 0xFF)
SVG_BLUE = (0x21, 0x96, 0xF3)   # #2196F3 — Blau aus docs/icon-source.svg


def gradient_square(size: int) -> Image.Image:
    """Fast-schwarzer Verlauf #0d0d0d -> App-Night-Hintergrund #1a1a1a."""
    column = Image.new("RGB", (1, size))
    for y in range(size):
        t = y / max(1, size - 1)
        column.putpixel((0, y), tuple(
            round(BLACK_TOP[i] + (BLACK_BOTTOM[i] - BLACK_TOP[i]) * t) for i in range(3)
        ))
    return column.resize((size, size), Image.Resampling.BICUBIC)


def render_motif(size: int, motif, fraction: float = SAFE_FRACTION) -> Image.Image:
    """Motiv transparent rendern, zentriert auf `fraction` der Kantenlänge."""
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    side = big * fraction
    x0 = (big - side) / 2
    box = (x0, x0, x0 + side, x0 + side)
    motif(draw, box, max(2 * SS, round(big * 0.052)))
    return img.resize((size, size), Image.Resampling.LANCZOS)


def silhouette(motif_img: Image.Image) -> Image.Image:
    """Monochrom-Ebene: weiße Silhouette (das System färbt sie selbst ein)."""
    out = Image.new("RGBA", motif_img.size, (255, 255, 255, 0))
    out.putalpha(motif_img.getchannel("A"))
    return out


def compose(size: int, motif, fraction: float) -> Image.Image:
    """Legacy-Icon: Hintergrund + farbiges Motiv, deckend, quadratisch."""
    base = gradient_square(size).convert("RGBA")
    base.alpha_composite(render_motif(size, motif, fraction))
    return base.convert("RGB")

# --------------------------------------------------------------- Motive
# Alle Motive zeichnen in die übergebene Box (Safe Zone). `stroke` ist die
# Strichstärke für die aktuelle Renderauflösung (bereits supersampled).


def motif_keyboard(draw: ImageDraw.ImageDraw, box, stroke: int) -> None:
    """Variante A — Tastatur mit grüner Vorschlagsleiste über blauen Keys.

    Bildmarke ist die App-eigene UI: die grüne Leiste ist die Top-Vorschau der
    Vorschlagsleiste, der Rest ist das Tastenfeld.
    """
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    small = min(w, h)
    # grüne Vorschlagsleiste
    draw.rounded_rectangle(
        [x0 + 0.08 * w, y0, x1 - 0.08 * w, y0 + 0.20 * h],
        radius=0.06 * small, fill=GREEN,
    )
    # Tastaturkörper: um die halbe Strichbreite eingerückt, weil Pillow den
    # Umriss mittig auf die Pfadkante legt — sonst ragt er aus der Safe Zone.
    body_stroke = max(2 * SS, round(stroke * 0.62))
    inset = body_stroke / 2
    draw.rounded_rectangle(
        [x0 + inset, y0 + 0.26 * h, x1 - inset, y1 - inset], radius=0.12 * small,
        outline=BLUE, width=body_stroke,
    )
    # 2 Reihen Tasten — größere Abstände halten das Motiv bis 32 px lesbar
    key_w, key_h, key_r = 0.18 * w, 0.16 * h, 0.04 * small
    for row in range(2):
        for col in range(3):
            kx = x0 + 0.10 * w + col * 0.31 * w
            ky = y0 + 0.40 * h + row * 0.28 * h
            draw.rounded_rectangle(
                [kx, ky, kx + key_w, ky + key_h], radius=key_r, fill=BLUE,
            )


def motif_terminal(draw: ImageDraw.ImageDraw, box, stroke: int) -> None:
    """Variante B — Shell-Prompt `>_`: Chevron grün, Cursor-Unterstrich blau.

    Das ist die Marke der Nische (Termux/SSH/Tab-Completion) und bleibt auch bei
    48 px eindeutig lesbar.
    """
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    width = round(stroke * 1.35)
    apex = (x0 + 0.42 * w, y0 + 0.50 * h)
    top = (x0 + 0.06 * w, y0 + 0.16 * h)
    bottom = (x0 + 0.06 * w, y1 - 0.16 * h)
    draw.line([top, apex], fill=GREEN, width=width, joint="curve")
    draw.line([apex, bottom], fill=GREEN, width=width, joint="curve")
    draw.line(
        [(x0 + 0.50 * w, y1 - 0.16 * h), (x1 - 0.02 * w, y1 - 0.16 * h)],
        fill=BLUE, width=width,
    )


# Variante C: True = gespiegeltes K (Grundstrich rechts, Schrägen öffnen nach
# links), False = erste Fassung. Die Schrägen sind EIN verbundenes Winkel-Paar
# mit spitzer Miter-Spitze und laufen außen genau so hoch wie der Grundstrich.
MONOGRAM_MIRRORED = True
MONOGRAM_GAP = 0.07         # Abstand zwischen Spitze und Grundstrich (Anteil Motivbreite)
MONOGRAM_ARM = 0.15         # Armstärke der Schrägen
MONOGRAM_STEM = 0.17        # Stärke des Grundstrichs
MONOGRAM_TOP = 0.10         # obere Kante von Grundstrich und Schrägen (Anteil Höhe)


def _unit(dx: float, dy: float) -> tuple[float, float]:
    length = math.hypot(dx, dy) or 1.0
    return dx / length, dy / length


def _line_intersect(p, d, q, e):
    """Schnittpunkt der Geraden p+s·d und q+t·e (für die Miter-Spitze)."""
    den = d[0] * e[1] - d[1] * e[0]
    if abs(den) < 1e-9:
        return p
    t = ((q[0] - p[0]) * e[1] - (q[1] - p[1]) * e[0]) / den
    return (p[0] + t * d[0], p[1] + t * d[1])


def _chevron_polygon(d_left: float, gap: float, half_height: float, mid_y: float,
                     arm: float) -> list[tuple[float, float]]:
    """Sechseck für die verbundenen Schrägen (spitze Spitze, kein runder Knick).

    Lokale Koordinaten: u=0 ist die Kante des Grundstrichs, u wächst von ihm weg,
    y wie im Motiv. Die Spitze wird als Miter konstruiert; `gap` wird deshalb an
    der *fertigen* Spitze gemessen, nicht am Arm-Mittelpunkt — sonst frisst der
    Miter-Überstand den Abstand wieder auf.
    """
    half_arm = arm / 2
    ux, u_tip = 0.707, gap + half_arm            # Startwert: 45-Grad-Arme
    for _ in range(40):
        a_y = mid_y - half_height + half_arm * ux
        rise, run = mid_y - a_y, d_left - u_tip
        length = math.hypot(run, rise)
        if length < 1e-9 or rise <= 0:
            break
        ux, u_tip = run / length, gap + half_arm * length / rise
    a_y = mid_y - half_height + half_arm * ux    # äußere Armkappe genau auf Kantenhöhe
    b_y = mid_y + half_height - half_arm * ux
    a, b, t = (d_left, a_y), (d_left, b_y), (u_tip, mid_y)
    u1, u2 = _unit(t[0] - a[0], t[1] - a[1]), _unit(t[0] - b[0], t[1] - b[1])
    n1 = (-u1[1], u1[0])                         # außen (oben) am oberen Arm
    n2 = (u2[1], -u2[0])                         # außen (unten) am unteren Arm
    a_out = (a[0] + n1[0] * half_arm, a[1] + n1[1] * half_arm)
    a_in = (a[0] - n1[0] * half_arm, a[1] - n1[1] * half_arm)
    b_out = (b[0] + n2[0] * half_arm, b[1] + n2[1] * half_arm)
    b_in = (b[0] - n2[0] * half_arm, b[1] - n2[1] * half_arm)
    t_out = _line_intersect(a_out, u1, b_out, u2)   # spitze Außenspitze
    t_in = _line_intersect(a_in, u1, b_in, u2)
    return [a_out, t_out, b_out, b_in, t_in, a_in]


def motif_monogram(draw: ImageDraw.ImageDraw, box, stroke: int) -> None:
    """Variante C — Monogramm „K“ (Grundstrich grün, Schrägen blau).

    Gespiegelt sitzt der grüne Grundstrich rechts und das blaue Winkel-Paar läuft
    als eine Form mit spitzer Spitze nach links; die Schrägen laufen außen genauso
    hoch wie der Grundstrich, dazwischen bleibt der Abstand `MONOGRAM_GAP`.
    """
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    stem_w, arm = MONOGRAM_STEM * w, MONOGRAM_ARM * w
    top, bottom = y0 + MONOGRAM_TOP * h, y1 - MONOGRAM_TOP * h
    mid_y = y0 + 0.5 * h
    if MONOGRAM_MIRRORED:
        stem_x, direction = x0 + 0.80 * w, -1.0
        stem_edge = stem_x - stem_w / 2
    else:
        stem_x, direction = x0 + 0.20 * w, 1.0
        stem_edge = stem_x + stem_w / 2
    d_left = 0.575 * w                       # Abstand Grundstrichkante -> Armende
    polygon = _chevron_polygon(d_left, MONOGRAM_GAP * w, (bottom - top) / 2, mid_y, arm)
    draw.polygon([(stem_edge + direction * u, y) for u, y in polygon], fill=BLUE)
    draw.line([(stem_x, top), (stem_x, bottom)], fill=GREEN, width=round(stem_w))


def motif_user_svg(draw: ImageDraw.ImageDraw, box, stroke: int) -> None:
    """Variante D — dein SVG: blaues Caret (#2196F3) + grüner Balken (#4CAF50).

    Übernimmt Form und Farben 1:1 aus `docs/icon-source.svg` (Caret oben
    angespitzt, Balken oben darüber). Der Grund kommt vom Generator
    (schwarzer Verlauf, full-bleed — die abgerundete Canvas des SVG liefert
    Launcher/Store selbst, deshalb ist der Icon-Grund hier volle Fläche).
    """
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    left, right = x0 + 0.18 * w, x0 + 0.82 * w
    # Balken oben (grün, wie der SVG-Balken)
    draw.rounded_rectangle([left, y0 + 0.12 * h, right, y0 + 0.26 * h],
                           radius=0.05 * min(w, h), fill=GREEN)
    # Caret (blau #2196F3): flache Spitze oben, Band nach unten geöffnet
    draw.polygon([
        (left, y0 + 0.92 * h),
        (x0 + 0.50 * w, y0 + 0.56 * h),
        (right, y0 + 0.92 * h),
        (right, y0 + 0.80 * h),
        (x0 + 0.50 * w, y0 + 0.44 * h),
        (left, y0 + 0.80 * h),
    ], fill=SVG_BLUE)


VARIANTS = {
    "a": ("A — Tastatur + grüne Vorschlagsleiste", motif_keyboard),
    "b": ("B — Terminal-Prompt >_ (Chevron grün, Cursor blau)", motif_terminal),
    "c": ("C — Monogramm K, gespiegelt (Grundstrich grün rechts, Arme blau mit Abstand)", motif_monogram),
    "d": ("D — dein SVG (Caret blau #2196F3, Balken grün #4CAF50)", motif_user_svg),
}

# Kurzform nur fuer Bildunterschriften (Tafelbreite ist begrenzt)
SHORT = {"a": "Tastatur", "b": "Prompt >_", "c": "K gespiegelt", "d": "dein SVG"}

# --------------------------------------------------- Ausgabe / Installation
ADAPTIVE_DENSITIES = {      # 108dp je Density
    "mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432,
}

ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Erzeugt von scripts/make_icon.py (Variante {variant}). Alle Ebenen sind
     108x108 dp, das Motiv liegt innerhalb der 66x66-dp-Safe-Zone. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""


def _font(size: int, bold: bool = False):
    try:
        name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
        return ImageFont.truetype(str(FONT_DIR / name), size)
    except OSError:
        return None


def make_preview(path: Path, motif, label: str) -> None:
    """Vergleichstafel: 128/96/64/48/32 px auf hellem und dunklem Grund."""
    sizes = [128, 96, 64, 48, 32]
    pad, gap = 18, 16
    row_h = 128 + 2 * pad
    width = 2 * pad + sum(sizes) + gap * (len(sizes) - 1)
    height = 2 * (row_h + gap) + 34
    sheet = Image.new("RGB", (width, height), (0x22, 0x22, 0x22))
    draw = ImageDraw.Draw(sheet)
    for row, bg in enumerate(((0xF0, 0xF0, 0xF0), (0x08, 0x08, 0x08))):
        top = pad + row * (row_h + gap)
        draw.rectangle([0, top, width, top + row_h], fill=bg)
        x = pad
        for size in sizes:
            sheet.paste(compose(size, motif, LEGACY_FRACTION), (x, top + pad))
            x += size + gap
    draw.text((pad, height - 26), label, font=_font(15, True), fill=(0xFF, 0xFF, 0xFF))
    sheet.save(path)


def make_compare(path: Path, size: int = 256) -> None:
    """Alle drei Varianten nebeneinander, plus Kleingrößen auf hellem Grund."""
    keys = sorted(VARIANTS)
    pad, strip_h = 24, 110
    width = pad + len(keys) * (size + pad)
    height = 2 * pad + size + strip_h + 62
    sheet = Image.new("RGB", (width, height), (0x22, 0x22, 0x22))
    draw = ImageDraw.Draw(sheet)
    for i, key in enumerate(keys):
        x = pad + i * (size + pad)
        sheet.paste(compose(size, VARIANTS[key][1], LEGACY_FRACTION), (x, pad))
        top = pad + size + pad
        draw.rectangle([x, top, x + size, top + strip_h], fill=(0xF0, 0xF0, 0xF0))
        sizes = (72, 56, 44, 32)
        span = sum(sizes) + 8 * (len(sizes) - 1)
        cx = x + (size - span) // 2
        for small in sizes:
            sheet.paste(compose(small, VARIANTS[key][1], LEGACY_FRACTION),
                        (cx, top + (strip_h - small) // 2))
            cx += small + 8
        draw.text((x, top + strip_h + 14), f"{key.upper()} — {SHORT[key]}",
                  font=_font(15, True), fill=(0xFF, 0xFF, 0xFF))
    sheet.save(path)

def build_variant(key: str, outdir: Path) -> Path:
    _label, motif = VARIANTS[key]
    vdir = outdir / f"variant-{key}"
    vdir.mkdir(parents=True, exist_ok=True)

    # Store-Export (Fastlane: images/icon.png)
    compose(STORE_SIZE, motif, LEGACY_FRACTION).save(vdir / "icon-512.png")

    # Adaptive-Ebenen je Density + XML-Wrapper
    for density, px in ADAPTIVE_DENSITIES.items():
        target = vdir / "adaptive" / f"mipmap-{density}"
        target.mkdir(parents=True, exist_ok=True)
        gradient_square(px).save(target / "ic_launcher_background.png")
        render_motif(px, motif, SAFE_FRACTION).save(target / "ic_launcher_foreground.png")
        silhouette(render_motif(px, motif, SAFE_FRACTION)).save(target / "ic_launcher_monochrome.png")
    anydpi = vdir / "adaptive" / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    (anydpi / "ic_launcher.xml").write_text(ADAPTIVE_XML.format(variant=key.upper()))

    # Legacy-Raster für API 24/25 (minSdk 24)
    for density, px in DENSITIES.items():
        target = vdir / "legacy" / f"mipmap-{density}"
        target.mkdir(parents=True, exist_ok=True)
        compose(px, motif, LEGACY_FRACTION).save(target / "ic_launcher.png")

    make_preview(vdir / "preview-sizes.png", motif, f"{key.upper()} — {SHORT[key]}")
    return vdir


def install(key: str) -> None:
    """Gewählte Variante in den App-Ressourcen und im Fastlane-Baum ablegen."""
    _label, motif = VARIANTS[key]
    for density, px in DENSITIES.items():
        target = RES / f"mipmap-{density}"
        target.mkdir(parents=True, exist_ok=True)
        compose(px, motif, LEGACY_FRACTION).save(target / "ic_launcher.png")
    for density, px in ADAPTIVE_DENSITIES.items():
        target = RES / f"mipmap-{density}"
        target.mkdir(parents=True, exist_ok=True)
        gradient_square(px).save(target / "ic_launcher_background.png")
        render_motif(px, motif, SAFE_FRACTION).save(target / "ic_launcher_foreground.png")
        silhouette(render_motif(px, motif, SAFE_FRACTION)).save(target / "ic_launcher_monochrome.png")
    anydpi = RES / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    (anydpi / "ic_launcher.xml").write_text(ADAPTIVE_XML.format(variant=key.upper()))
    store = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "icon.png"
    store.parent.mkdir(parents=True, exist_ok=True)
    compose(STORE_SIZE, motif, LEGACY_FRACTION).save(store)
    print(f"  installiert: Variante {key.upper()} -> app/src/main/res/mipmap-*/ + "
          f"{store.relative_to(ROOT)}")
    print("  offen (bewusst manuell): AndroidManifest auf @mipmap/ic_launcher umstellen "
          "und das alte drawable/ic_launcher.xml entfernen.")


def main() -> int:
    parser = argparse.ArgumentParser(description="KeyTab-Icon-Generator (3 Varianten)")
    parser.add_argument("--variant", choices=sorted(VARIANTS), help="nur diese Variante rendern")
    parser.add_argument("--out", type=Path, default=OUT_DEFAULT,
                        help=f"Ausgabeverzeichnis (Default: {OUT_DEFAULT.relative_to(ROOT)})")
    parser.add_argument("--install", action="store_true",
                        help="gewählte Variante in res/ und fastlane/ installieren")
    args = parser.parse_args()
    if args.install and not args.variant:
        parser.error("--install braucht --variant (installiert wird genau eine Variante)")

    print(f"Palette: Gruen #{GREEN[0]:02x}{GREEN[1]:02x}{GREEN[2]:02x} . "
          f"Blau #{BLUE[0]:02x}{BLUE[1]:02x}{BLUE[2]:02x} . "
          f"Grund #{BLACK_TOP[0]:02x}{BLACK_TOP[1]:02x}{BLACK_TOP[2]:02x}"
          f"->#{DARK_BG[0]:02x}{DARK_BG[1]:02x}{DARK_BG[2]:02x}")
    keys = [args.variant] if args.variant else sorted(VARIANTS)
    for key in keys:
        vdir = build_variant(key, args.out)
        print(f"  {VARIANTS[key][0]}\n    -> {vdir.relative_to(ROOT)}/ "
              f"(icon-512.png, adaptive/, legacy/, preview-sizes.png)")
    if len(keys) > 1:
        make_compare(args.out / "compare.png")
        print(f"  Vergleichstafel: {(args.out / 'compare.png').relative_to(ROOT)}")
    if args.install:
        install(args.variant)
    return 0


if __name__ == "__main__":
    sys.exit(main())



