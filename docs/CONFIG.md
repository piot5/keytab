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

- `num_row`, `term_tab`, `clip_tab`, `snippet_tab`, `suggestions`, `autocorrect`,
  `dynamic_keys`: `true` / `false`.
- `language`: `de`, `en`, `es`, `fr`, `it`, `pt`, `nl`.
- `dark_mode`: `true`, `false` oder `system`.
- `gaming_mode`, `gaming_effect`: Hervorhebung der nächsten Taste und Puls-Effekt;
  die alten Namen bleiben kompatibel.
- `gradient_color1`, `gradient_color2`: `#RRGGBB`, `#AARRGGBB` oder `default`.
  Die zwei Verlaufsfarben sind wie bisher gemeinsam für Hell/Dunkel gespeichert.
- `gradient_mode`: `top_down`, `invert`, `radial`.
- `gradient_off_dark`, `gradient_off_light`: `true` / `false`.
- `theme_dark_bg`, `theme_dark_key`, `theme_dark_hl`, `theme_dark_text`,
  `theme_dark_gaming` sowie dieselben Schlüssel mit `theme_light_`:
  Farbe inkl. Alpha oder `default` zum Entfernen des Overrides.
- `bg_image_uri`: leer zum Abschalten, zugänglicher absoluter Dateipfad oder
  `content://`-URI. Die Bildauswahl in Themes erteilt einen dauerhaften Lesezugriff.
  Eine URI allein in der Config erteilt keine Android-Berechtigung.
- `bg_image_fill`: `fit`, `cover`, `stretch`.
- Skalierung wie bisher: `max_scale`, `mid_scale`, `hot_threshold`,
  `mid_threshold`, `min_neighbor_scale`, `mid_neighbor_scale`.

Beispiel:

```ini
term_tab = false
snippet_tab = true
gradient_color1 = #FF2196F3
gradient_color2 = #800D47A1 # Alpha 128
gradient_mode = top_down
gradient_off_dark = false
theme_dark_bg = default
bg_image_fill = cover
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
