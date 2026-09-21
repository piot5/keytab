# Einstellungen und Hintergrundbilder

Alle dauerhaften Optionen aus den beiden Einstellungsseiten sind über die Datei
`keytab_config.txt` im externen App-Dateiverzeichnis steuerbar. Der Config-Button
zeigt den absoluten Pfad an und ergänzt fehlende Schlüssel mit den aktuellen
Einstellungen. Vorhandene Zeilen, Kommentare und Werte bleiben erhalten.
Android-Berechtigungen und die Auswahl der Systemtastatur sind Systemaktionen,
keine per Textdatei erteilbaren Berechtigungen.

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
  `dynamic_keys`, `emoji_suggestions` (Default `false` — hängt bis zu 2
  thematische Emojis hinten an die Wortvorschläge an; immer offline).
- `swipe`, `swipe_preview` (Default `false` — v0.11). `swipe` aktiviert die
  Gleit-Eingabe: der Finger gleitet über die Tastatur, die Route wird gegen die
  Engine bewertet (Auto-Commit bei klarem Ergebnis, sonst Kandidaten-Leiste).
  `swipe_preview` zeigt die wahrscheinlichen Folge-Tasten des aktuell getippten
  Worts als verbundenen Pfad (passiv). Beide sind in Passwort-Feldern hart
  deaktiviert (gleiche Regel wie `trail`). Siehe `docs/SWIPE_PLAN.md`.
- `trail`, `trail_trace`: Tippspur an/aus und Korrektur-Trace an/aus
  (Default `false`). Der Korrektur-Trace färbt getippte Wörter
  rot, wenn sie automatisch ersetzt würden (Hinweis, es wird nichts geändert).
- `gaming_mode`, `gaming_effect`: Hervorhebung der nächsten Taste und Puls-Effekt.
  Die Namen sind historisch (aus der Zeit vor „Likely Highlighting") und bleiben
  als Schlüssel stabil — im Code sind sie `ThemePrefs.KEY_LIKELY` /
  `KEY_LIKELY_EFFECT`. `gaming` ist außerdem der interne `KIND_LIKELY`-Name für
  die Highlight-Farbe in `theme_<dark|light>_gaming`.
- `gradient_off_dark`, `gradient_off_light`.

**Zahlen**, Importer `SettingsConfig`:

- `trail_steps`: Anzahl der Verblass-Stufen der Tippspur (Ganzzahl, Default `5`).

**Aufzählungen**, Importer `SettingsConfig`:

- `language`: `de`, `en`, `es`, `fr`, `it`, `pt`, `nl`.
- `dark_mode`: `true`, `false` oder `system` (`system` entfernt den Override).
- `gradient_mode`: `top_down`, `invert`, `radial`.
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

Der Terminal-Tab ist experimentell: Android-Shell in der App-Sandbox, kein PTY,
keine Termux-/proot-Umgebung. Snippets sind davon unabhängig.
