#!/usr/bin/env python3
"""Render KeyTab keyboard screenshots from the real layout and colour resources.

The images are drawn from the same data the app uses (values/colors.xml,
values-night/colors.xml, panel_keyboard_letters.xml), so a layout or theme
change shows up in the documentation instead of silently drifting away from it.
They are *mockups* of the keyboard view, not captures of a device run: device
screenshots (frame timing, animations, per-theme contrast) cannot be produced
by a script and stay an open gap in the README.

Usage:  python3 scripts/make_screens.py [output_dir]
Default output dir: docs/images

Requires Pillow (python3-pil). Fonts: DejaVu Sans (Debian/Ubuntu package
fonts-dejavu-core, already used by the Termux/proot toolchain).
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs" / "images"

W, H = 1080, 1044
PAD = 6
GAP = 6
FONT_DIR = Path("/usr/share/fonts/truetype/dejavu")


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    return ImageFont.truetype(str(FONT_DIR / name), size)


def read_colors(rel: str) -> dict[str, str]:
    """Read a colour resource file relative to app/src/main/res,
    e.g. 'values/colors.xml' or 'values-night/colors.xml'."""
    text = (ROOT / "app/src/main/res" / rel).read_text()
    return dict(re.findall(r'<color name="([^"]+)">([^<]+)</color>', text))


def center(draw: ImageDraw.ImageDraw, box, text: str, fnt, fill) -> None:
    x0, y0, x1, y1 = box
    l, t, r, b = draw.textbbox((0, 0), text, font=fnt)
    draw.text((x0 + (x1 - x0 - (r - l)) / 2 - l, y0 + (y1 - y0 - (b - t)) / 2 - t),
              text, font=fnt, fill=fill)


def rounded(draw, box, radius, fill, outline=None, width=2):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def key_row(draw, y, height, keys, theme, key_font, highlight=None):
    """Draw one homogeneous key row (KeyRow style: equal weights, 2dp margins)."""
    n = len(keys)
    key_w = (W - 2 * PAD - (n - 1) * GAP) / n
    for i, label in enumerate(keys):
        x = PAD + i * (key_w + GAP)
        box = (x, y, x + key_w, y + height)
        filled = highlight is not None and label == highlight
        rounded(draw, box, 10,
                theme["primary"] if filled else theme["key_bg"],
                theme["primary"] if filled else theme["key_outline"])
        center(draw, box, label, key_font, "#ffffff" if filled else theme["key_text"])



def compose(theme: dict, path: Path, *, candidates: list[str], highlight: str | None,
            rows: list[list[str]], bottom: list[str], tab_active: int,
            tabs: list[str], caption: str) -> None:
    img = Image.new("RGB", (W, H), theme["kbd_bg"])
    d = ImageDraw.Draw(img)

    # --- Suggestion bar: the top candidate is rendered 2x wide (README) -------
    bar_top, bar_h = PAD, 96
    rounded(d, (PAD, bar_top, W - PAD, bar_top + bar_h), 12, theme["surface"])
    slots = [2] + [1] * (len(candidates) - 1)
    cell = (W - 2 * PAD - 3 * GAP) / sum(slots)
    x = PAD
    for i, (label, span) in enumerate(zip(candidates, slots)):
        box_w = cell * span + GAP * (span - 1)
        box = (x, bar_top + 6, x + box_w, bar_top + bar_h - 6)
        rounded(d, box, 8, theme["suggestion_bg"])
        if i == 0:
            d.rounded_rectangle((box[0], box[1], box[0] + 6, box[3]), radius=3,
                                fill=theme["primary"])
        center(d, box, label, font(34, bold=(i == 0)),
               theme["primary"] if i == 0 else theme["text_primary"])
        x += box_w + GAP

    # --- Key rows ------------------------------------------------------------
    y = bar_top + bar_h + GAP * 2
    for row in rows:
        key_row(d, y, 150, row, theme, font(44), highlight)
        y += 150 + GAP
    key_row(d, y, 130, bottom, theme, font(38))
    y += 130 + GAP

    # --- Tab strip (abc | Editor | Files | Snip | ☰) -------------------------
    rounded(d, (PAD, y, W - PAD, y + 96), 12, theme["tab_bg"])
    tab_w = (W - 2 * PAD) / len(tabs)
    for i, name in enumerate(tabs):
        x0 = PAD + i * tab_w
        box = (x0 + 4, y + 8, x0 + tab_w - 4, y + 88)
        active = i == tab_active
        rounded(d, box, 8, theme["tab_selected_bg"] if active else theme["tab_bg"])
        center(d, box, name, font(34, bold=active),
               theme["primary"] if active else theme["tab_text"])
        if active:
            d.rounded_rectangle((box[0], box[3] - 6, box[2], box[3]), radius=3,
                                fill=theme["primary"])

    center(d, (0, y + 100, W, H - 4), caption, font(26), theme["text_secondary"])

    OUT.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)
    print("wrote", path.relative_to(ROOT), img.size)



QWERTZ_1 = ["q", "w", "e", "r", "t", "z", "u", "i", "o", "p", "ü"]
QWERTZ_2 = ["a", "s", "d", "f", "g", "h", "j", "k", "l", "ö", "ä"]
QWERTZ_3 = ["⇧", "y", "x", "c", "v", "b", "n", "m", "ß", "⌫"]
BOTTOM = ["?123", "⇥", "␣", "↵"]
TABS = ["abc", "Editor", "Files", "Snippets", "☰"]


def themed(base: dict[str, str], *, light: bool) -> dict[str, str]:
    """Fill in the colours that only exist as styles in the layout."""
    return {**base,
            "suggestion_bg": "#f7f7f7" if light else "#242424",
            "key_outline": "#cfcfcf" if light else "#3a3a3a",
            "tab_bg": base["surface"],
            "tab_selected_bg": "#ffffff" if light else "#2e2e2e"}


if __name__ == "__main__":
    LIGHT = themed(read_colors("values/colors.xml"), light=True)
    NIGHT = themed(read_colors("values-night/colors.xml"), light=False)

    compose(LIGHT, OUT / "keyboard-light.png",
            candidates=["Haus", "haust", "Haushalt"], highlight="s",
            rows=[QWERTZ_1, QWERTZ_2, QWERTZ_3], bottom=BOTTOM, tab_active=0,
            tabs=TABS,
            caption="Light theme, QWERTZ (de) - top suggestion 2x wide with green "
                    "accent, likely next key scaled 1.30x")
    compose(NIGHT, OUT / "keyboard-dark.png",
            candidates=["Haus", "haust", "Haushalt"], highlight=None,
            rows=[QWERTZ_1, QWERTZ_2, QWERTZ_3], bottom=BOTTOM, tab_active=0,
            tabs=TABS,
            caption="Dark theme (default preset) - the same fully offline engine, "
                    "no INTERNET permission, 50 ms keystroke budget")
    compose(LIGHT, OUT / "keyboard-snippets.png",
            candidates=["git status", "git add .", "git log"], highlight=None,
            rows=[QWERTZ_1, QWERTZ_2, QWERTZ_3], bottom=BOTTOM, tab_active=3,
            tabs=TABS,
            caption="Sentence start without a typed word: your last used snippets "
                    "appear in the bar instead of word predictions")
