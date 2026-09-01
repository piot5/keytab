# KeyTab

**Tastatur-App (IME) mit integriertem Tab-Dateimanager + Wortvorhersage** – 100 % Kotlin, Ready-to-build Android-Projekt.

## Features

### 📂 Tab-Dateimanager (Hauptfeature)
- **Mehrere Browser-Tabs direkt in der Tastatur** – jeder Tab merkt sich sein Verzeichnis
- Navigation, Ordner wechseln, BackStack + Parent-Navigation, Dateigrößen-Anzeige
- Verzeichnis-Laden **asynchron im Hintergrund** (kein UI-Freeze bei großen Ordnern)
- Tipp auf Datei = Pfad einfügen; in der App zusätzlich Öffnen per VIEW-Intent
- Material 3 UI (TabLayout + RecyclerView mit DiffUtil)
- **Einzigartig**: Dateimanager als IME-Overlay – ohne App-Wechsel Dateien browsen

### ⌨️ KeyTab Keyboard (IME)
- Vollwertige Bildschirmtastatur als `InputMethodService`
- TAB-Taste (sendet `KEYCODE_TAB` – ideal für Termux/SSH-Shells)
- Shift-Umschaltung, Backspace (Long-Press = Wort löschen), Enter, Space
- Long-Press auf Buchstaben zeigt Umlaute/Sonderzeichen-Popup in Theme-Farben

### 📝 Notes (Editor + Ablage in einem Tab)
- **Editor**: Text direkt in der Tastatur notieren, asynchron speichern/laden
- **📂 Load öffnet einen Ordner-Browser** (IME-Dialog mit Fenster-Token): Ordner antippen = hinein, „▲ …" = hoch, Datei antippen = laden; die gewählte Datei wird Save-Ziel (Default: `keytab_editor.txt`)
- **Flache Load/Save-Zeile** (26dp) mit Datei-Namen-Anzeige
- **Ablage**: bis zu 50 Einträge, persistent in `clipboard_history.txt`
- Auto-Capture beim Tab-Wechsel (Android-10+-Beschränkung respektiert)
- Tipp auf Ablage-Eintrag = **direkt ins Zielfeld einfügen** (der Editor fängt die Eingabe nicht ab – nur Load befüllt den Editor)

### 🔒 Datenschutz
- Keine Netzwerkberechtigung, keine Datensammlung

## Architektur

`KeyTabImeService` ist ein schlanker Keyboard-Core (Tasten, Shift, Symbole, Popups).
Die Sub-Features leben in eigenen Klassen:

| Klasse | Verantwortung |
|---|---|
| `FileManagerPanel` | IME-Dateimanager (Navigation, async Listing) |
| `EditorPanel` | Interner Editor mit Speichern/Laden |
| `ClipboardPanel` | Ablage: Capture, Persistenz, Anzeige |
| `TextEditLogic` | **Reine, Android-freie Logik** (Wortgrenzen, Größenformat, Encoding) |

Allen Panels gemeinsam: Datei-I/O auf einem Hintergrund-Executor, UI-Updates über den
Main-Handler, veraltete Ergebnisse werden bei Navigation verworfen.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

20 Unit-Tests für `TextEditLogic` (Wortgrenzen-Scan inkl. Grenzfälle, `formatSize`
inkl. GB/TB, Clip-History-Encoding/Display) plus 11 Robolectric-Tests für die Panels
(`EditorPanel`, `ClipboardPanel`). Die Logik-Klasse ist bewusst Android-frei, um ohne
Emulator/Gerät lauffähig zu sein.

## Build

### Auf dem PC (empfohlen)

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Voraussetzungen: JDK 17, Android SDK (compileSdk 34). SDK-Pfad in `local.properties`
(`sdk.dir=...`) oder Umgebungsvariable `ANDROID_HOME`.

### On-Device (ARM64 / Proot / Termux)

Googles `aapt2` ist x86_64-only → ARM-Build-Tools von
<https://github.com/lzhiyong/android-sdk-tools> nutzen und in
`~/.gradle/gradle.properties` setzen: `android.aapt2FromMavenOverride=/pfad/zu/aapt2`

**Wichtig:** Ein Build direkt auf der SD-Karte (`/storage/emulated/0`/`/mnt/sdcard`)
ist unzuverlässig (Gradle-Daemon/Worker werden getötet, Datei-Locks auf dem FUSE-Mount).
Deshalb: Projekt nach `$HOME` (interner Speicher) kopieren und dort bauen:

```bash
# 1. Projekt in internen Speicher kopieren (einmalig + nach Änderungen)
cp -r /mnt/sdcard/Ubuntu-proot-termux/projects/keytab ~/build/
cd ~/build/keytab
rm -rf app/build .gradle   # nur bei seltsamen Fehlern nötig

# 2. Bauen (gradlew hat kein Exec-Bit auf FAT → über sh aufrufen!)
sh ./gradlew :app:assembleDebug --offline
# APK: app/build/outputs/apk/debug/app-debug.apk

# 3. APK zurück auf die SD-Karte für die Installation
cp app/build/outputs/apk/debug/app-debug.apk \
   /mnt/sdcard/Ubuntu-proot-termux/projects/keytab/app-debug-new.apk
```

> Langläufer-Builds mit `setsid … &` starten, sonst tötet das Terminal-Timeout
> den Gradle-Prozess. `--offline` spart Zeit, wenn keine neuen Abhängigkeiten nötig sind.

## Installation (per Shizuku/rish, ohne PC)

Voraussetzung: Shizuku-Server läuft (nach Reboot in der Shizuku-App neu starten),
Wrapper `~/bin/rsh` eingerichtet (siehe `projects/rish/README.md`).

```bash
# 1. APK nach /data/local/tmp kopieren (die Shell sieht /mnt/...-Pfade nicht!)
sh ~/bin/rsh 'cp /sdcard/Ubuntu-proot-termux/projects/keytab/app-debug-new.apk \
  /data/local/tmp/keytab.apk'

# 2. Installieren (Update mit -r)
sh ~/bin/rsh 'pm install -r /data/local/tmp/keytab.apk'
# → "Success"

# 3. Aufräumen + verifizieren
sh ~/bin/rsh 'rm -f /data/local/tmp/keytab.apk; \
  dumpsys package com.piotv.keytab | grep lastUpdateTime'
```

Alternativ manuell: APK kopieren → Dateimanager → installieren
(Paketinstaller-Dialog bestätigen), dann:

1. *Systemeinstellungen → Sprachen & Eingabe → Bildschirmtastatur verwalten* → **KeyTab** aktivieren
2. In einem Textfeld Tastatur wechseln → **KeyTab**

## Struktur

```
app/src/main/java/com/piotv/keytab/
├── MainActivity.kt              # Einstellungen: Tastatur aktivieren, Theme
├── file/FileManagerFragment.kt  # Tab-Dateimanager (in der App, mit DiffUtil)
└── ime/
    ├── KeyTabImeService.kt      # Keyboard-Core: Tasten, Shift, Symbole, Popups, Tabs
    ├── FileManagerPanel.kt      # IME-Dateimanager
    ├── EditorPanel.kt           # Notes-Editor: Speichern/Laden mit Ordner-Browser
    ├── ClipboardPanel.kt        # Ablage (Clipboard-Historie)
    ├── TerminalPanel.kt         # Interaktive Shell im Terminal-Tab
    ├── LetterPopup.kt           # Long-Press-Sonderzeichen + Drag-Auswahl
    └── TextEditLogic.kt         # Reine, testbare Textlogik
app/src/test/java/com/piotv/keytab/ime/
├── PanelsTest.kt                # 11 Robolectric-Tests (Editor/Clipboard)
└── TextEditLogicTest.kt         # 20 Unit-Tests
```

## Changelog (0.5.0 → 0.6.0)

- **Feature:** **Wortvorhersage/Autovervollständigung** (`SuggestionEngine`, offline, Android-frei): Unigram-Frequenzmodell (FrequencyWords de_50k, Top 6000), Bigramme, User-Dictionary mit Decay, Prefix-Autocomplete + Damerau-Levenshtein-Fuzzy-Korrektur, Case-Matching, Persistenz. 19 Unit-Tests
- **Feature:** **Dynamische Tastengröße** (optional): wahrscheinlichere Buchstaben werden größer (1,15×), unwahrscheinlichere kleiner (0,85×) – basierend auf den Vorschlagewerten
- **Feature:** **Optionale Darstellung** – Schalter in der Einstellungs-App: Wortvorhersage und dynamische Tastengröße separat ein-/ausblenden
- **UI:** Einstellungs-App um zwei Material-Switches erweitert (Suggestions / Dynamic Keys)

- **Feature:** Editor- und Clipboard-Tab zusammengelegt → **„Notes"**; Tab-Leiste jetzt: abc · Notes · Files · Terminal
- **Feature:** **📂 Load öffnet einen Ordner-Browser** (Dialog mit IME-Fenster-Token): Ordner navigieren, Datei wählen → wird geladen und als Save-Ziel gemerkt
- **Feature:** **Löschtaste beschleunigt mit der Zeit**: Wort-Repeat startet bei 250 ms und verkürzt sich pro Wiederholung ×0,85 bis auf 30 ms
- **Fix:** Absturz (BadTokenException) beim Laden im Notes-Editor – Dialog hängt jetzt am IME-Fenster (TYPE_APPLICATION_ATTACHED_DIALOG + windowToken)
- **Fix:** Sonnensymbol (☀) im hellen Theme unsichtbar – Textfarbe wird über den themen-übersteuerten Kontext aufgelöst statt über den System-Kontext (weiß auf weiß bei System-Dark + Light-Override)
- **Fix:** Ablage-Einträge fügten sich in den Editor ein, statt ins Zielfeld – Clipboard-Commit geht jetzt immer direkt über die InputConnection
- **UI:** Tab-Schrift 11sp → 9sp; „Term" → „Terminal" ausgeschrieben; Load/Save-Zeile 42dp → 26dp (Style `KeyEditorBar`); kein Platz mehr über der Tab-Leiste; Tab-Indikator-Linie entfernt; 6dp Abstand zwischen Tab-Leiste und Tasten

## Changelog (1.3.3 → 1.4.0)

- **Feature:** Terminal-Tab ist jetzt ein **echtes interaktives Terminal**: eine eigene Shell (`/system/bin/sh`) läuft im Hintergrund, Befehle werden dort ausgeführt und die **Ausgabe erscheint live im Verlauf** über der Eingabezeile (monospace, scrollbar, Verlauf begrenzt)
- **Feature:** Der Terminal-Tab ist **optional** – neuer Schalter „Terminal tab in keyboard" in der Einstellungs-App blendet den Tab in der Tastatur ein/aus (wirkt beim nächsten Öffnen der Tastatur)
- **Hinweis:** Die Shell läuft in der App-Sandbox (kein Root, kein Termux-Umfeld): System-Befehle wie `ls`, `pwd`, `cat` funktionieren, `pm`/`am` sind eingeschränkt
- **Cleanup:** verwaisten `sendCommand` entfernt, Shell wird in `onDestroy` sauber beendet

## Changelog (1.3.2 → 1.3.3)

- **Feature:** Neuer IME-Tab **„Term"** (Terminal) **neben dem Clipboard-Tab**: zeigt eine einzelne Befehlszeile **über der Tastatur** (wie der Editor). Eingaben aus der Tastatur landen dort; die **Enter-Taste** bzw. **„Send"** schickt die Zeile (Befehl + Enter) direkt an die fokussierte App (z. B. Termux)

## Changelog (1.3.1 → 1.3.2)

- **Feature:** Long-Press-Popup löst beim Loslassen (**ohne dass eine Zelle angesteuert wurde**) automatisch das **erste** Zeichen der Popup-Reihe aus
- **Feature:** Enter-Taste behält in allen Tabs **konstante Größe und Position** – die übrigen Tastatur-Tasten werden in Files/Ablage auf `INVISIBLE` gesetzt (Platz bleibt), statt `GONE`
- **Feature:** Einstellungs-App ist jetzt **tab-basiert**: neuer Tab **„Terminal"** öffnet **Termux** per Launch-Intent (Hinweis, falls nicht installiert)
- **Cleanup:** Storage-Berechtigungslogik beibehalten, überflüssige `onRequestPermissionsResult`-Override entfernt

## Changelog (1.3.0 → 1.3.1)

- **Feature:** Theme-Umschalter-Icon jetzt **monochrom (schwarz/weiß)** – statt der bunten Emojis werden die Textsymbole ☾ (Dark) / ☀ (Light) in `key_text`-Farbe gerendert; kleiner (14sp)
- **Feature:** `?123`-Taste kleiner (16sp statt 22sp)
- **Feature:** Long-Press-Popups zeigen **nur noch die Zusatz-Sonderzeichen** (ohne großen Hauptbuchstaben-Header) – kompakter und direkter
- **Feature:** **Nur die Enter-Taste** bleibt in allen Tabs erreichbar; in Files/Ablage sind `?123`/⇥/Space/`.` ausgeblendet statt der ganzen Funktionsleiste
- **Cleanup:** ungenutzten `LinearLayout`-Import entfernt, Doku aktualisiert

## Changelog (1.2.0 → 1.3.0)

- **Feature:** Enter-Taste breiter (eigene Gewichtung statt Fix-Breite) und **in allen Tabs** erreichbar – die Funktionsleiste `?123 / Space / Enter` bleibt in Files/Editor/Ablage sichtbar
- **Feature:** Dateimanager-Zustand (aktuelles Verzeichnis + Zurück-Stack) wird persistiert → nach Tastatur-/App-Neustart wird der letzte Ordner wiederhergestellt
- **Feature:** `formatSize` unterstützt jetzt auch GB und TB (statt bei MB stehenzubleiben)
- **Refactoring:** Long-Press-Popup (Header, Grid, Drag-Auswahl, Highlight) in eigene Klasse `LetterPopup` extrahiert – `KeyTabImeService` deutlich schlanker
- **Cleanup:** toten `MainActivity`-Dead-Code entfernt (`requestStoragePermissions`), ungenutzte Imports bereinigt
- **Tests:** +3 `formatSize`-Tests (GB/TB)
- **Hinweis:** Die Robolectric-Panel-Tests benötigen eine x86_64-Umgebung (native conscrypt-Bibliothek); auf ARM64/Proot fehlt diese, daher dort "no conscrypt_openjdk_jni-linux-aarch_64"

## Changelog (1.1.0 → 1.2.0)

- **Feature:** Long-Press-Popups mit Interpunktion auf Buchstaben-Tasten (`.` `,` `?` `!` `/` `@` `(` `)` `:` `;` …) – Hinweis-Zeichen rechts unten, Shift-sicher (Umlaute unverändert)
- **Feature:** Drag-Auswahl im Popup – Finger nach Loslassen-Taste über die Zeichen ziehen, loslassen = einfügen (Gboard-Stil); Header-Buchstabe tippbar
- **Feature:** Dark/Light-Umschalter (🌙/☀) oben links in der Tab-Zeile, persistiert; ⚙ öffnet die App-Einstellungen
- **Feature:** Umschaltbare Zahlenreihe über den Buchstaben (Einstellungen-Switch in der App)
- **Feature:** TAB-Taste (⇥) in der unteren Reihe + direkter `.`-Button
- **Feature:** Files-Tab: Long-Press-Kontextmenü (Eigenschaften, Pfad/Inhalt/Datei kopieren, Einfügen in anderen Ordner)
- **Fix:** Popup-Position (fensterrelative Koordinaten + Clipping deaktiviert) – Popup erscheint über der Taste statt am unteren Rand
- **Fix:** Popup-Grid kollabierte nicht mehr (Trennlinie war MATCH_PARENT breit)
- **Fix:** ListViews in Files/Ablage respektieren das Theme (text_primary statt Schwarz-auf-Dunkel)
- **Fix:** Baseline der Tasten-Labels (Hauptbuchstabe oben, Hinweis-Zeichen unten, LiftSpan), KeyRow baselineAligned=false
- **Refactoring:** Panels nehmen `Executor` statt `ExecutorService` (testbar), `themedAdapter`-Helper, `versionCode 3`

## Changelog (1.0.0 → 1.1.0)

- **Fix:** Long-Press-Backspace löschte ein Zeichen zu viel (Leerzeichen vor dem Wort)
- **Fix:** Datei-I/O (Dateimanager, Editor, Ablage) läuft asynchron statt auf dem UI-Thread
- **Fix:** Long-Press-Popup nutzt Theme-Farben statt hardcodierter Colors (Day/Night korrekt)
- Refactoring: Gott-Klasse in Panel-Klassen + testbare `TextEditLogic` aufgeteilt
- Alle UI-Strings nach `strings.xml` (i18n-fähig), DiffUtil im App-Dateimanager
- 17 Unit-Tests + JUnit-Setup

## Changelog (1.4.1 → 1.5.0)

- **Feature:** On-Device Wortvorhersage (n-gram + User-Dictionary + Suggestion-Bar)
  - Unigram-Frequenzmodell (FrequencyWords de_50k, CC-BY-SA-4.0) + Bigramme für Next-Word-Prediction
  - User-Dictionary mit Decay, persistiert in SharedPreferences
  - Prefix-Autovervollständigung + Damerau-Levenshtein-Fuzzy-Korrektur
  - **Top-Vorschlag (höchste Wahrscheinlichkeit) = 2× breiter, bold, grüner Akzent-Balken** → besserer Treffbereich
  - 125 Unit-Tests (SuggestionEngine: Scoring, Bigramme, Edit-Distance, Persistenz)

## Lizenz

MIT – siehe [LICENSE](LICENSE)
