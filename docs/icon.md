# Icon — Farben

Farbwerte des KeyTab-Icons. Motiv-Quelle: [`icon-source.svg`](icon-source.svg) ·
Generator: [`../scripts/make_icon.py`](../scripts/make_icon.py).

## Variante D (aktuelles Icon)

| Element | Hex | RGB | Herkunft |
|---|---|---|---|
| Caret (Chevron) | `#2196F3` | 33, 150, 243 | `icon-source.svg`; im Generator `SVG_BLUE` |
| Balken (Cursor) | `#4CAF50` | 76, 175, 80 | App-Akzent `primary` (`app/src/main/res/values/colors.xml`) |
| Grund (SVG-Canvas, abgerundet `rx=112`) | `#121212` | 18, 18, 18 | `icon-source.svg` |
| Grund (App/Store, full-bleed) | `#0d0d0d → #1a1a1a` | 13, 13, 13 → 26, 26, 26 | Generator-Verlauf `BLACK_TOP → BLACK_BOTTOM`; `#1a1a1a` = `background` in `values-night/colors.xml` |

## App-Palette (Bezug)

| Element | Hex | Quelle |
|---|---|---|
| Akzent (primär) | `#4CAF50` | `values/colors.xml` `primary` |
| Tasten/Fläche (dunkel) | `#2E2E2E` | `values-night/colors.xml` `surface` |
| Grund (dunkel) | `#1A1A1A` | `values-night/colors.xml` `background` |
| Alt-Icon-Blau (abgelöst) | `#1B6EF3` | ehemals `drawable/ic_launcher.xml` (nicht mehr im Tree) |

## Generator

Alle vier Varianten (A/B/C/D) liegen in `scripts/make_icon.py`; die Palette wird
aus `values*/colors.xml` gelesen. Nur `SVG_BLUE = #2196F3` ist für Variante D
fest kodiert (stammt aus `icon-source.svg`).

```bash
python3 scripts/make_icon.py --variant d --install
```
