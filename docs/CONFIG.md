# Einstellungen und Hintergrundbilder

Alle dauerhaften Optionen aus den beiden Einstellungsseiten sind über die Datei
`keytab_config.txt` im externen App-Dateiverzeichnis steuerbar. Der Config-Button
zeigt den absoluten Pfad an und ergänzt fehlende Schlüssel mit den aktuellen
Einstellungen. Vorhandene Zeilen, Kommentare und Werte bleiben erhalten.
Android-Berechtigungen und die Auswahl der Systemtastatur sind Systemaktionen,
keine per Textdatei erteilbaren Berechtigungen.

## Standardwerte (2026-09-28)

**Die Vorgabewerte sind die aus `keytab_config.txt` des Nutzergeräts** (Stand
2026-09-25, übernommen 2026-09-28). Eine Neuinstallation sieht damit genau so
aus wie das Gerät, aus dem die Datei stammt — und der Config-Button schreibt
dieselben Werte in eine neu angelegte Datei:

| Schlüssel | Standard | Schlüssel | Standard |
|---|---|---|---|
| `num_row` | `true` | `dark_mode` | `true` (dunkel) |
| `autocorrect` | `false` | `gradient_mode` | `top_down` |
| `suggestions` | `true` | `theme_dark_gradient_mode` | `invert` |
| `clip_tab` | `true` | `theme_light_gradient_mode` | `top_down` |
| `snippet_tab` | `true` | `gaming_mode` | `true` |
| `dynamic_keys` | `true` | `gaming_effect` | `true` |
| `swipe`, `swipe_preview` | `false` | `trail`, `trail_trace` | `false` |
| `trail_steps` | `5` | `language` | `de` |
| `bg_image_uri` | leer | `bg_image_fill` | `fit` |
| `gradient_color1/2` | `default` | `theme_*` (Farben) | `default` |
| `gradient_off_dark/light` | `false` | `max_scale` | `1.21` |

`dark_mode = system` bleibt wählbar (es entfernt den Override und folgt dann dem
Gerät); die Skalierwerte stehen in `KeyTabConfig` und waren bereits diese.

## Anwendung und Vorrang

Die Datei wird beim Öffnen der Einstellungen oder Tastatur geprüft. Nur ein
geänderter Dateiinhalt wird importiert. Danach können UI-Schalter die Werte wieder
ändern; eine unveränderte Datei überschreibt diese Änderungen nicht. Beim nächsten
externen Bearbeiten werden alle gültigen enthaltenen Schlüssel erneut übernommen.
Fehlende oder ungültige Werte lassen die bestehenden Einstellungen unverändert.

## Schlüssel

Alle unten genannten Schlüssel werden aus `keytab_config.txt` importiert. Es gibt
zwei Importer, die nacheinander laufen: `SettingsConfig` (Schalter, Farben, Pfade)
und `KeyTabConfig.entries()` (Skalierungswerte); die Abschnitte unten markieren,
welcher Schlüssel wohin gehört.

**Schalter** (`true` / `false`), Importer `SettingsConfig`:

- `num_row`, `term_tab`, `clip_tab`, `snippet_tab`, `suggestions`, `autocorrect`,
  `dynamic_keys`. `num_row` hat Default `true`, `autocorrect` Default `false` und
  steuert die automatische Korrektur beim Leerzeichen (auch als Schalter im
  Einstellungs-Screen).

  Entfernt 2026-09-27: `emoji_suggestions` (Default `false` — hing bis zu 2
  thematische Emojis hinten an die Wortvorschläge an). Der ☺-Emoji-Katalog in der
  Vorschlagsleiste bleibt davon unberührt; alte Config-Dateien dürfen den Key
  weiterhin enthalten, er wird ignoriert. Die
  Reinigung des gelernten Feldes erfolgt separat über **🧹 Gelerntes Wörterbuch
  prüfen**: Vorschau und Bestätigung sind erforderlich, der Basiswortschatz bleibt
  unverändert.
- `swipe` (Default `false` — v0.11): Gleit-Eingabe; der Finger gleitet über die
  Tastatur, die Route wird gegen die Engine bewertet (Auto-Commit bei klarem
  Ergebnis, sonst Kandidaten-Leiste). `swipe_preview` (Default `false`,
  experimentell) zeigt die wahrscheinlichen Folge-Tasten des aktuell getippten
  Worts als verbundenen Pfad (passiv) — **seit 2026-09-21 nicht mehr im
  Einstellungs-Screen**, nur noch hier schaltbar. Beide sind in Passwort-Feldern
  hart deaktiviert (gleiche Regel wie `trail`). Siehe `docs/SWIPE_PLAN.md`.
- `trail`, `trail_trace`: Tippspur an/aus und Treffer-Markierung an/aus
  (Default `false`). Die Treffer-Markierung färbt ein Wort **grün**, wenn es
  exakt dem obersten Vorschlag entspricht (reine Bestätigung, es wird nichts
  geändert). Die frühere rote Warnfärbung („würde korrigiert") ist entfernt.
- `gaming_mode`, `gaming_effect`: Hervorhebung der nächsten Taste und Puls-Effekt
  (Defaults beide `true`). Die
  Namen sind historisch (aus der Zeit vor „Likely Highlighting") und bleiben
  als Schlüssel stabil — im Code sind sie `ThemePrefs.KEY_LIKELY` /
  `KEY_LIKELY_EFFECT`. `gaming` ist außerdem der interne `KIND_LIKELY`-Name für
  die Highlight-Farbe in `theme_<dark|light>_gaming`.
- `gradient_off_dark`, `gradient_off_light`.

**Zahlen**, Importer `SettingsConfig`:

- `trail_steps`: Anzahl der Verblass-Stufen der Tippspur (Ganzzahl, Default `5`).
- `normal_height_editor` / `normal_height_files` / `normal_height_clip` /
  `normal_height_snippet`: Höhe des Panels im **Normalzustand** in **dp**
  (Default `0` = automatisch aus Fenster und Messung berechnen, also gleich hoch
  wie der Editor-Tab).
- `max_height_editor` / `max_height_files` / `max_height_clip` /
  `max_height_snippet`: dasselbe im **Vollbild** (Default `0` = das IME-Fenster
  füllen, soweit Android es freigibt).

Regeln für die Höhen:

- **dp, nicht px** — die Datei bleibt dadurch auf Geräten mit anderer Dichte
  gültig; umgerechnet wird erst beim Setzen des Panels.
- **Das Fenster gewinnt.** Passt die Vorgabe nicht ins IME-Fenster (Android gibt
  der IME rund 70 % der Bildschirmhöhe), wird sie gekürzt. Ein zu großer Wert
  schiebt also nie Inhalt aus dem Fenster.
- **Werte ≤ 0 oder unbrauchbar große Werte** gelten als „keine Vorgabe" — eine
  kaputte Datei kann die Tastatur nicht unbenutzbar machen.
- **Der abc-Tab hat keine Vorgabe**: seine Höhe kommt aus den Tasten. Im
  Editor-Tab bleibt im Vollbild die untere Key-Leiste (Leerzeichen/Tab/Enter)
  sichtbar, die Vorgabe gilt nur für das Editor-Panel.

**Aufzählungen**, Importer `SettingsConfig`:

- `language`: `de`, `en`, `es`, `fr`, `it`, `pt`, `nl`.
- `dark_mode`: `true`, `false` oder `system` (Default `true`; `system` entfernt
  den Override und folgt dem Gerät).
- `gradient_mode`: `top_down` (Default), `invert`, `radial`.
- `theme_dark_gradient_mode` / `theme_light_gradient_mode`: wie `gradient_mode`,
  Defaults `invert` (dunkel) und `top_down` (hell) — der Verlauf ist je Modus
  getrennt wählbar.
- `bg_image_fill`: `fit`, `cover`, `stretch`.

**Farben** (`#RRGGBB`, `#AARRGGBB` oder `default`), Importer `SettingsConfig`:

- `gradient_color1`, `gradient_color2`. Die zwei Verlaufsfarben sind wie bisher
  gemeinsam für Hell/Dunkel gespeichert.
- `theme_dark_bg`, `theme_dark_key`, `theme_dark_hl`, `theme_dark_text`,
  `theme_dark_gaming` sowie dieselben Schlüssel mit `theme_light_`.
  `default` entfernt den Override.
- `theme_dark_swipe`, `theme_dark_swipe_edge` sowie dieselben Schlüssel mit
  `theme_light_` (v0.11): Farbe der Swipe-Pfad-Knoten bzw. -Kanten
  (Schaltplan-Preview + Swipe-Eingabe). `default` entfernt den Override.

**Pfade / Zeichenketten**, Importer `SettingsConfig`:

- `bg_image_uri`: leer zum Abschalten, zugänglicher absoluter Dateipfad oder
  `content://`-URI. Die Bildauswahl in Themes erteilt einen dauerhaften Lesezugriff.
  Eine URI allein in der Config erteilt keine Android-Berechtigung.

**Skalierung**, Importer `KeyTabConfig.entries()` (getrennt von `SettingsConfig`):

- `max_scale`, `mid_scale`, `hot_threshold`, `mid_threshold`,
  `min_neighbor_scale`, `mid_neighbor_scale`.
  Alle Werte müssen endlich und > 0 sein; `hot_threshold`/`mid_threshold`
  zusätzlich in `0.0..1.0`. Ungültige Werte lassen den jeweiligen Wert unverändert.

> **Nicht** über `keytab_config.txt` steuerbar: Android-Berechtigungen, die
> Auswahl der Systemtastatur, die Zeichenketten der Oberfläche (`strings.xml`)
> und alles, was nur in der Theme-Einstellungsseite als UI-Zustand existiert.
> Das sind Systemaktionen bzw. Ressourcen, keine per Textdatei erteilbaren
> Berechtigungen.

Beispiel:

```ini
term_tab = false
snippet_tab = true
swipe = true
swipe_preview = true
trail = true
trail_trace = true
trail_steps = 7
gradient_color1 = #FF2196F3
gradient_color2 = #800D47A1 # Alpha 128
gradient_mode = top_down
gradient_off_dark = false
theme_dark_bg = default
theme_dark_swipe = #FF4CAF50
theme_dark_swipe_edge = #8038B04A
bg_image_fill = cover
max_scale = 1.30
# Höhen je Tab (dp, 0 = automatisch) — Beispiel: Datei-Panel höher,
# Clipboard im Vollbild nur halb Fenster:
normal_height_files = 240
max_height_clip = 640
```

Ein expliziter flacher `theme_*_bg`-Override oder `gradient_off_* = true`
deaktiviert den Verlauf. Die Auswahl einer Verlaufsfarbe oder eines Presets in
Themes aktiviert ihn wieder. Das Bild liegt über dem flachen Hintergrund/Verlauf.
Bei `fit` bleiben freie Flächen im darunterliegenden Theme sichtbar. Nicht lesbare
Bilder fallen auf dieses Theme zurück; es werden keine Bilder aus dem Netz geladen.

## Bildmodus und Seitenverhältnis

- **Einpassen (`fit`)**: proportional, vollständig sichtbar, ggf. Ränder.
- **Ausfüllen (`cover`)**: proportional, keine Ränder, ggf. beschnitten.
- **Strecken (`stretch`)**: füllt die Fläche, kann das Bild verzerren.

Als Ausgangspunkt eignet sich Querformat **2:1**, z. B. **1600 × 800 px**.
Entscheidend ist die tatsächliche Tastaturbreite geteilt durch die Tastaturhöhe;
Tab, Zahlenreihe, Ausrichtung und Gerät verändern dieses Verhältnis. Es wird keine
zusätzliche Tastaturhöhen-Einstellung eingeführt. Große Bilder werden vor dem Laden
herunterskaliert (maximal 2048 Pixel je Seite).

Hinweis: Der Terminal-Tab wurde in v0.14 entfernt; `term_tab` wird in alten
`keytab_config.txt`-Dateien als unbekannter Schlüssel ignoriert. Snippets bleiben
unverändert.
