# KeyTab

**Tastatur-App (IME) mit integriertem Tab-Dateimanager** – 100 % Kotlin, Ready-to-build Android-Projekt.

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

### 📝 Editor
- Text direkt in der Tastatur notieren; Speichern/Laden in `keytab_editor.txt` (asynchron)

### 📋 Ablage (Clipboard-Historie)
- Bis zu 50 Einträge, persistent in `clipboard_history.txt`
- Auto-Capture beim Tab-Wechsel (wenn die Tastatur fokussiert ist – Android-10+-Beschränkung respektiert)
- Tipp = einfügen

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

17 Unit-Tests für `TextEditLogic` (Wortgrenzen-Scan inkl. Grenzfälle, `formatSize`,
Clip-History-Encoding/Display). Die Logik-Klasse ist bewusst Android-frei, um ohne
Emulator/Gerät lauffähig zu sein.

## Build

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Voraussetzungen: JDK 17, Android SDK (compileSdk 34). SDK-Pfad in `local.properties`
(`sdk.dir=...`) oder Umgebungsvariable `ANDROID_HOME`.

> On-Device-Build (ARM64 / Proot / Termux): Googles `aapt2` ist x86_64-only.
> ARM-Build-Tools von <https://github.com/lzhiyong/android-sdk-tools> nutzen und in
> `~/.gradle/gradle.properties` setzen:
> `android.aapt2FromMavenOverride=/pfad/zu/aapt2`

## Installation

1. APK installieren
2. *Systemeinstellungen → Sprachen & Eingabe → Bildschirmtastatur verwalten* → **KeyTab** aktivieren
3. In einem Textfeld Tastatur wechseln → **KeyTab**

## Struktur

```
app/src/main/java/com/piotv/keytab/
├── MainActivity.kt              # Einstellungen: Tastatur aktivieren, Theme
├── file/FileManagerFragment.kt  # Tab-Dateimanager (in der App, mit DiffUtil)
└── ime/
    ├── KeyTabImeService.kt      # Keyboard-Core: Tasten, Shift, Symbole, Popups
    ├── FileManagerPanel.kt      # IME-Dateimanager
    ├── EditorPanel.kt           # Interner Editor
    ├── ClipboardPanel.kt        # Ablage (Clipboard-Historie)
    └── TextEditLogic.kt         # Reine, testbare Textlogik
app/src/test/java/com/piotv/keytab/ime/
└── TextEditLogicTest.kt         # 17 Unit-Tests
```

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

## Lizenz

MIT – siehe [LICENSE](LICENSE)
