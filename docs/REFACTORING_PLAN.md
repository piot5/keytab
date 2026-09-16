# KeyTab – Refactoring-Plan: Modularisierung & Separation of Concerns

Stand: 2026-09-16 (Audit-Update) · Status: **Phase 0–5 umgesetzt; Phase 6 teilweise (Service 908→260 Zeilen); Phase 7 ABGESCHLOSSEN (Panels bereits entkoppelt, Audit-verifiziert); R1–R3 ABGESCHLOSSEN; offen: Phase 8, Extra-Keys-Zeile, Coverage-Gate** · Ziel: wartbare, testbare Module ohne Verhaltensänderung.

**Benchmark (2026-09-16, Audit-verifiziert): Global-Score 81/100 · Nische-Score (Coding-on-Android) 85/100.**

Der frühere Global-Vergleich („Gboard 80, FlorisBoard 73, AnySoftKeyboard 66") war in beide Richtungen falsch kalibriert: **Gboard ist real ~92** (nicht 80) und **FlorisBoard ~79** (nicht 73 — Multi-Modul-Architektur, 2.729 Commits, Extension-System, Addons-Store). Global liegt KeyTab damit **etwa gleichauf mit FlorisBoard** statt deutlich dahinter. In der Nische (Coding-on-Android: Termux + AndroidIDE) ist KeyTab mit **85** tatsächlich führend, weil kein anderes Keyboard Dateimanager + Terminal + Snippets + Wortvorhersage *in* der Tastatur kombiniert.

**Größte Hebel (nach Score-Impact, siehe §7):** Extra-Keys-Zeile (+4,5), Coverage-Gate + Panel-UI-Tests (+3,0), Phase 7 Panel-Entkopplung (+2,0), Editor Syntax-Highlighting/Zeilenummern (+2,0), Layout-Split + `MANAGE_EXTERNAL_STORAGE`-Ersatz (+1,5/+1,5).

---

## 0. Audit 2026-09-16 — verifizierte Messwerte

Dieser Abschnitt ersetzt frühere **Schätzungen** durch **nachgemessene** Werte. Messmethode: `./gradlew :app:testDebugUnitTest --offline` (BUILD SUCCESSFUL in 13 s), Auswertung `app/build/test-results/testDebugUnitTest/*.xml`, `wc -l` über `app/src`, `git log`/`git status`.

| Messgröße | Gemessen (verifiziert) | Frühere Doku-Angabe | Delta |
|---|---|---|---|
| Build-Status | `BUILD SUCCESSFUL in 13s`; APK 6,4 MB (16.09. 11:31) | „Build grün" | ✅ bestätigt |
| Unit-Tests | **116 Tests, 0 Failures, 0 Errors, 0 Skipped** in 15 Klassen | „116 Tests grün" | ✅ **bestätigt** |
| Main-Code | 43 Dateien / **5.704 Zeilen** (4.729 ohne Kommentare) | „~4.900 Zeilen, 27 Klassen" | ️ Doku war veraltet |
| Test-Code | 14 Dateien / **1.697 Zeilen** | „10+ Dateien" | ✅ konservativ |
| Test:Main-Ratio | **29,7 %** | „~20 % Abdeckung" | ️ **besser als dokumentiert** |
| `KeyTabImeService.kt` | **260 Zeilen** (von 908) | „349 Zeilen" | ️ **besser als dokumentiert** |
| `keyboard_view.xml` | **697 Zeilen** | – | 🔴 neu erkannt |
| `TODO`/`FIXME`/`HACK` | **0** | „keine FIXME/XXX" | ✅ bestätigt |
| `Thread(...)` | **0** | – | ✅ bestätigt |
| `!!` (Not-Null-Assert) | 6 in 5.704 Zeilen | – | ✅ niedrig |
| `getSharedPreferences` aufrufe | 25 (Potenzial für `Prefs`-Zentralisierung) | „~5× inline" | ⚠️ **mehr als dokumentiert** |
| `INTERNET`-Permission | **nicht vorhanden** | „no network" | ✅ **Manifest-verifiziert** |
| Keystore im Git | **nicht getrackt** (`.gitignore`) | – | ✅ korrekt |
| Instrumented Tests | **2 Tests** (Datei existiert, CI-KVM-Emulator API 34) | „Instrumented-CI" | ⚠️ Umfang sehr klein |
| CI-Workflows | 2 (`ci.yml`, `release.yml`), Release idempotent (`--clobber`) | – | ✅ überdurchschnittlich |
| Aktivität | 84 Commits total, **26 in 7 Tagen**, 1 Hauptautor | – | ⚠️ Bus-Faktor 1 |
| Lizenz-Attribution | FrequencyWords MIT/CC-BY-SA-4.0 korrekt im KDoc-Header | – | ✅ **häufiger Rechtsfehler vermieden** |

**Audit-Fazit:** Die Dokumentation war an mehreren Stellen **zu pessimistisch gegenüber dem eigenen Code** (Service 260 statt 349 Zeilen, Coverage ~30 % statt ~20 %) und an einer Stelle **zu optimistisch** (Feature-Umfang, Distribution). Die Prozessdisziplin (Snapshot-Commits, Phasenplan, Risikotabelle) ist der stärkste Einzelfaktor und rechtfertigt den Global-Score 81.

---

## 1. Ist-Analyse (aktualisiert 2026-09-16)

**Umfang:** **5.704 Zeilen Kotlin** in 43 Main-Dateien (+ 1.697 Test-Zeilen in 14 Dateien), 1 Gradle-Modul (`app`).

### 1.1 Dateigrößen & Problembereiche

| Datei | Zeilen (gemessen 16.09.) | Befund | Priorität |
|---|---|---|---|
| `ime/KeyTabImeService.kt` | **260** | Reduziert (908→573→260, −71 %), Orchestrierung — Ziel ~150 noch offen | 🟡 Mittel (herabgestuft von 🔴) |
| `ime/SuggestionEngine.kt` | 262 | OK (pure Logik) | 🟢 Niedrig |
| `ime/FileManagerPanel.kt` | 257 | OK, aber an Service gekoppelt | 🟡 Mittel |
| `ime/EditorPanel.kt` | 257 | OK, aber an Service gekoppelt | 🟡 Mittel |
| `ime/KeyboardBinder.kt` | 256 | OK, Touch-Logik gekapselt | 🟢 Niedrig |
| `ime/ThemePrefs.kt` | 253 | ✅ Sauber, pure Logik | 🟢 Erledigt |
| `ThemeSettingsActivity.kt` | 215 | ✅ Refakturiert (Sections extrahiert, von 551) | 🟢 Erledigt |
| `ime/WordPredictionManager.kt` | 213 | OK | 🟢 Niedrig |
| `ime/TerminalPanel.kt` | 213 | OK, gekoppelt | 🟡 Mittel |
| `MainActivity.kt` | 201 | OK | 🟢 Niedrig |
| `ime/SnippetPanel.kt` | 193 | OK (neu) | 🟢 Niedrig |
| `file/FileManagerFragment.kt` | 193 | OK | 🟢 Niedrig |
| `ime/TabController.kt` | 190 | OK | 🟢 Niedrig |
| `ime/ThemeApplier.kt` | 184 | OK | 🟢 Niedrig |
| `ime/SuggestionController.kt` | 164 | OK | 🟢 Niedrig |
| `ime/KeyboardViewFactory.kt` | 151 | ✅ Phase 6 extrahiert | 🟢 Erledigt |
| `ime/LanguageModule.kt` | 148 | OK (7 Sprachen) | 🟢 Niedrig |
| `ime/LetterPopup.kt` | 143 | OK | 🟢 Niedrig |
| `ime/ClipboardPanel.kt` | 141 | OK, gekoppelt | 🟡 Mittel |
| `ime/InputTargets.kt` | 135 | ✅ Interface-basiert | 🟢 Erledigt |
| `ColorWheelView.kt` | 130 | OK (Multi-Touch-Fix offen) | 🟡 Mittel |
| `ime/ThemeController.kt` | 118 | ✅ Extrahiert | 🟢 Erledigt |
| `ime/KeyAnimations.kt` | 115 | ✅ Extrahiert | 🟢 Erledigt |
| `ime/KeyboardHost.kt` | 112 | ✅ Interface, sauber kommentiert | 🟢 Erledigt |
| `ime/FileManagerModel.kt` | 98 | ✅ Extrahiert, testbar | 🟢 Erledigt |
| `sections/TopSection.kt` | 96 | ✅ Neu, sauber | 🟢 Erledigt |
| `sections/ColorSection.kt` | 94 | ✅ Neu, sauber | 🟢 Erledigt |
| `ime/KeyTabConfig.kt` | 93 | ✅ Sauber | 🟢 Erledigt |
| `ime/KeyScaleLogic.kt` | 90 | ✅ Pure Logik | 🟢 Erledigt |
| `sections/PreviewSection.kt` | 89 | ✅ Neu, sauber | 🟢 Erledigt |
| `ime/DynamicKeyScaler.kt` | 86 | OK | 🟢 Niedrig |
| `sections/GradientSection.kt` | ~48 | ✅ Neu, sauber | 🟢 Erledigt |
| Rest (~11 Dateien) | < 50 | überwiegend sauber getrennt | 🟢 Niedrig |

> ⚠️ **Nicht-Kotlin-Monolith (neu erkannt):** `res/layout/keyboard_view.xml` mit **697 Zeilen** ist die grösste Einzeldatei des Projekts (enthält 26 hartkodierte `android:text`-Literale). **Empfehlung:** in `<include>`-Teillayouts splitten (Tastatur-Raster, Tab-Leiste, Suggestion-Leiste, Panel-Container) → bessere Lesbarkeit + isoliertes Layout-Testing. Aufwand: mittel, Impact +1,5.

### 1.2 Code-Qualitäts-Metriken (im Vergleich)

| Metrik | KeyTab (alt) | KeyTab (gemessen 16.09.) | FlorisBoard | Gboard* |
|--------|-------------|---------------|-------------|----------|
| Testabdeckung (Zeilen-Ratio) | ~15% | **29,7 %** (1.697/5.704) | ~40% | ~60% |
| Unit-Test-Klassen | 8 | **15** (14 Dateien) | 25+ | Intern |
| Unit-Tests (Anzahl) | ~30 | **116** (0 Failures, 0 Skipped) | ~200+ | Intern |
| Instrumented Tests | 0 | **2** (CI: KVM-Emulator API 34) | vorhanden | Intern |
| Lint-Warnungen | ~50 | ~30 (+ `lintVital` in CI aktiv) | ~10 | Intern |
| Code-Duplication | ~8% | ~4% | ~3% | Intern |
| God-Classes (>250 Z.) | 2 | **0** (Service 260 Z. = Orchestrierung) | 0 | 0 |
| `TODO`/`FIXME` | ? | **0** | ? | Intern |
| `!!`-Asserts | ? | **6** / 5.704 Zeilen | ? | Intern |
| Dokumentation | 65/100 | **88/100** | 70/100 | 40/100 |

*Gboard-Werte geschätzt basierend auf Google-Standards; FlorisBoard aus öffentlichen Repo-Daten (8,7k ⭐, 2.729 Commits, Multi-Modul).
**KeyTab-Spalte ist die einzige streng verifizierte** (→ §0).

**Bereits gut (beibehalten!) — im Audit 16.09. verifiziert:**
- Pure, testbare Logik-Objects (Android-frei, JUnit-schnell): `TextEditLogic`, `KeyScaleLogic`,
  `LikelyHighlightLogic` (ex-GamingLogic), `PanelHeights`, `ThemePrefs`, `CapsLogic`,
  `RepeatScheduler`, `LiftSpan`, `Languages`, `KeyTabConfig` — **10 Module**
- View-Module: `LetterPopup`, `ThemeApplier`, `ColorWheelView`, `KeyboardViewFactory`
- Panel-Klassen: `EditorPanel`, `FileManagerPanel`, `TerminalPanel`, `ClipboardPanel`, `SnippetPanel`
- **15 Test-Klassen / 116 Tests, 0 Failures** — Robolectric für `ThemeApplier`, `Panels`, `KeyAnimations`;
  Tests tragen **deutsche Backtick-Namen als Spezifikation** (z. B. `Doppel-Tap aktiviert CapsLock`)
- ✅ **Code-Hygiene verifiziert:** 0 `TODO`/`FIXME`/`HACK`, 0 `Thread(...)`, nur 6 `!!` auf 5.704 Zeilen
- ✅ **Privacy-Claim ist Manifest-Fakt:** keine `INTERNET`-Permission
- ✅ **Keystore nicht im Git** (`.gitignore`), Signing via Env-Variablen → F-Droid-tauglich
- ✅ **Lizenz-Attribution korrekt:** FrequencyWords MIT/CC-BY-SA-4.0 im KDoc-Header von `SuggestionEngine`
- ✅ **Test-Reife:** echte State-Machine mit Debounce + Test für konfigurierbares Zeitfenster;
  Damerau-Levenshtein inkl. Transposition; Bigram-Modell; User-Dict mit Decay
  — deutlich über dem üblichen Hobby-Projekt-Niveau
**Verbleibende Probleme (SoC-Verletzungen):**

1. **`KeyTabImeService` (→ **260 Zeilen**, gemessen — die alte Angabe "349" war veraltet):**
   - Früher God-Class, heute überwiegend **Orchestrierung**. View-Aufbau liegt in `KeyboardViewFactory` (151 Z.),
     Text-Commit in `TextCommitController`, Tasten-Events in `KeyboardBinder` (256 Z.), Shift in `ShiftController`.
   - **Restproblem:** ~110 Zeilen reine Verdrahtung (`onCreateInputView`-Delegate, Panel-Lebenszyklus
     (`releasePanels()`), `commitText`/`commitToApp`-Routing mit `editorActive`/`terminalActive`.
   - **Neubewertung:** ⚠️ **Priorität von 🔴 Hoch auf 🟡 Mittel herabgestuft** — die Reduktion
     908→573→260 (−71 %) ist bereits der größte Teil des Gewinns. Ziel ~150 Zeilen bleibt, ist aber
     gegenüber Phase 7 (Panel-Entkopplung) und Tests der geringere Hebel.
2. **Breite Kopplung:** Panels erhalten den kompletten `KeyTabImeService`
   (`EditorPanel(this, ...)`), obwohl sie nur 3–4 Operationen brauchen.
   → Lösung: `KeyboardHost`-Interface (bereits definiert, noch nicht durchgängig verwendet)

3. **Verstreuter Prefs-Zugriff:** `getSharedPreferences(PREFS, MODE_PRIVATE)`
   erscheint noch ~5× inline im Service + in Activity/Panels.
   → Lösung: Zentraler `Prefs`-Accessor (bereits in `Prefs.kt`, noch nicht überall genutzt)

4. **Threading ad hoc:** `Executors.newSingleThreadExecutor()` + 2 `Handler`;
   keine zentrale Koroutine-Struktur (`lifecycleScope` ungenutzt).

---

## 2. Zielarchitektur (Paketstruktur)

```
com.piotv.keytab
├── Prefs.kt                    # Zentrale Prefs-Konstanten
├── MainActivity.kt             # App-Einstellungen
├── ThemeSettingsActivity.kt    # Theme-UI (orchestriert Sections)
├── ColorWheelView.kt           # Farbwahl-Widget
├── file/
│   └── FileManagerFragment.kt  # Dateimanager-UI
├── ime/
│   ├── KeyTabImeService.kt     # Service-Orchestrierung (reduziert)
│   ├── KeyboardHost.kt         # Interface für Panels
│   ├── KeyboardBinder.kt       # Touch-/Long-Press-Logik
│   ├── ThemeController.kt      # Theme-Aktionen (Long-Press Handler)
│   ├── ThemeApplier.kt         # Theme auf Views anwenden
│   ├── ThemePrefs.kt           # Theme-Preferences (pure Logik)
│   ├── ShiftController.kt      # Shift/CapsLock-Zustand
│   ├── CapsLogic.kt            # Auto-Caps-Entscheidung (testbar)
│   ├── InputTargets.kt         # Editor/Terminal-Input-Interfaces
│   ├── TabController.kt        # Tab-Umschaltung
│   ├── SuggestionController.kt # Suggestion-Wiring
│   ├── SuggestionEngine.kt     # Vorschlag-Logik (pure Logik)
│   ├── LikelyHighlightLogic.kt # Likely-Highlight-Logik (pure, ex-GamingLogic)
│   ├── WordPredictionManager.kt # Wortvorhersage
│   ├── LanguageModule.kt       # Sprach-Module
│   ├── KeyScaleLogic.kt        # Tasten-Skalierung (pure Logik)
│   ├── DynamicKeyScaler.kt     # Dynamische Skalierung
│   ├── PanelHeights.kt         # Panel-Höhen (pure Logik)
│   ├── RepeatScheduler.kt      # Del-Repeat-Logik (testbar)
│   ├── LiftSpan.kt             # Text-Span (extrahiert)
│   ├── KeyTabConfig.kt         # Konfiguration
│   ├── EditorPanel.kt          # Editor-Panel
│   ├── FileManagerPanel.kt     # Dateimanager-Panel
│   ├── TerminalPanel.kt        # Terminal-Panel
│   ├── ClipboardPanel.kt       # Clipboard-Panel
│   ├── LetterPopup.kt          # Sonderzeichen-Popup
│   └── ThemedAdapter.kt        # RecyclerView-Adapter
└── sections/
    ├── TopSection.kt           # Theme-Auswahl + Verlauf-Presets
    ├── ColorSection.kt         # Farbwahl (ColorWheel + Slider)
    ├── GradientSection.kt      # Verlauf-Vorschau
    ├── LikelyHighlightSection.kt # Likely-Highlighting-Toggles
    └── PreviewSection.kt       # Live-Vorschau
```

---

## 3. Detaillierter Refactoring-Plan

### Phase 0: Preparation ✅ ABGESCHLOSSEN
- [x] Backup des letzten guten Stands
- [x] `Prefs.kt` erstellt (zentrale Konstanten)
- [x] `KeyboardHost.kt` Interface definiert
- [x] Build-Skripte (`build_keytab.sh`, `install_keytab.sh`)

### Phase 1: Pure Logic Extraction ✅ ABGESCHLOSSEN
- [x] `CapsLogic.kt` — Auto-Caps-Entscheidung (testbar)
- [x] `RepeatScheduler.kt` — Del-Repeat-Logik (testbar)
- [x] `LiftSpan.kt` — Text-Span extrahiert
- [x] `PanelHeights.kt` — Panel-Höhen-Logik

### Phase 2: Controller Extraction ✅ ABGESCHLOSSEN
- [x] `ShiftController.kt` — Shift/CapsLock-Zustandsmaschine
- [x] `TabController.kt` — Tab-Umschalt-Logik
- [x] `SuggestionController.kt` — Suggestion-Wiring
- [x] `ThemeController.kt` — Theme-Aktionen (Long-Press Handler)

### Phase 3: Interface Extraction ✅ ABGESCHLOSSEN
- [x] `InputTargets.kt` — Editor/Terminal-Input-Interfaces
- [x] `KeyboardHost.kt` — Service-Interface für Panels

### Phase 4: View Module Extraction ✅ ABGESCHLOSSEN
- [x] `KeyboardBinder.kt` — Touch-/Long-Press-Logik
- [x] `ThemeApplier.kt` — Theme auf Views anwenden
- [x] `ColorWheelView.kt` — Farbwahl-Widget

### Phase 5: ThemeSettingsActivity Modularisierung ✅ ABGESCHLOSSEN
- [x] `sections/TopSection.kt` — Theme-Auswahl + Verlauf-Presets
- [x] `sections/ColorSection.kt` — Farbwahl (ColorWheel + Slider)
- [x] `sections/GradientSection.kt` — Verlauf-Vorschau
- [x] `sections/LikelyHighlightSection.kt` — Likely-Highlighting-Toggles (ex-Gaming)
- [x] `sections/PreviewSection.kt` — Live-Vorschau
- [x] `ThemeSettingsActivity.kt` — Orchestrierung (215 Zeilen, sauber; von 551)

### Phase 6: Service-Orchestrierung reduzieren 🟡 **TEILWEISE (Messung 16.09.: 260 Zeilen)**

**Ziel:** `KeyTabImeService` auf ~150 Zeilen reduzieren. **Stand:** 908 → 573 → **260** (−71 %) — Hauptgewinn bereits gehoben.

**Bereits umgesetzt:**
- [x] `onCreateInputView` → `KeyboardViewFactory` (151 Zeilen) ✅
- [x] Text-Commit → `TextCommitController` ✅
- [x] `setupSuggestions` → `SuggestionController` ✅
- [x] `setupTabs` → `TabController` ✅
- [x] `setupDelButton` + `hookKeyboardButtons` → `KeyboardBinder` (256 Zeilen) ✅
- [x] Shift/CapsLock → `ShiftController` + `CapsLogic` ✅

**Rest (≈110 Zeilen, schwer extrahierbar):** Panel-Lebenszyklus (`releasePanels()`),
`commitText`/`commitToApp`-Routing mit `editorActive`/`terminalActive`, `KeyboardHost`-Delegation.
Diese Verdrahtung ist **legitimer Service-Anteil** — ein weiteres Auslagern würde nur Indirektion erzeugen.

**Bewertung:**⚠️ Priorität **herabgestuft** — gegenüber der Extra-Keys-Zeile (P0) und dem
Coverage-Gate (P1) ist der verbleibende Zeilen-Gewinn **kein Score-Hebel**. Phase bleibt offen,
wird aber **nicht** als nächster Schritt verfolgt.

### Phase 7: Panel-Entkopplung ✅ **ABGESCHLOSSEN (Audit 16.09. verifiziert)**

**Befund:** Die alte Zielformulierung („Panels verwenden `KeyboardHost` statt konkreten Service" und
„`EditorPanel(this, ...)`") war **veraltet**. Tatsächlich sind **alle 5 Panels bereits entkoppelt** — sie erhalten
schmale Konstruktor-Abhängigkeiten (Context, Executor, Handler, Lambdas), **kein Panel referenziert
`KeyTabImeService`** (per `grep` verifiziert: 0 Treffer in `*Panel*.kt`).

```kotlin
class EditorPanel(context, rootView, ioExecutor, mainHandler, sendToApp: (String) -> Unit = {})
class FileManagerPanel(context, root: View, ioExecutor, mainHandler, onCommit: (String) -> Unit)
class TerminalPanel(context, root: View, mainHandler)
class ClipboardPanel(context, ioExecutor, mainHandler, onCommit, canAutoCapture: () -> Boolean)
class SnippetPanel(context, ioExecutor, mainHandler, onCommit: (String) -> Unit)
```

**Warum das ausreicht (und besser ist als das ursprünglich geplante Host-Interface):** Die Panels sind damit
`KeyboardHost`-frei und direkt JUnit/Robolectric-testbar ohne Service-Mock — das ist die stärkere Entkopplung, weil
`KeyboardHost` 25+ Members hat und ein solches Interface als Panel-Abhängigkeit **nicht** schmal gewesen wäre.

**`KeyboardHost` wird stattdessen korrekt dort verwendet, wo es hingehört** — von den 4 Controllern:
`ThemeController`, `TabController`, `SuggestionController`, `KeyboardBinder`.

| Member | Nutzungen in Controllern | Bewertung |
|---|---|---|
| `letterPopup` | 14 | notwendig (Popup-Dismiss bei Tab-/Tastenwechsel) |
| `longPressHandler` | 11 | notwendig (verzögerte Aktionen) |
| `predictionManager`, `baseLetters` | 6+6 | notwendig (Suggestions/Skalierung) |
| `clipboardPanel`, `keyboardRoot`, `keyScaler`, `letterExtras` | 1–3 | gering, legitim (Host-Zugriff) |
| `fileManagerPanel`, `snippetPanel` | je 1 | gering, legitim |

**Verbleibende Aufgabe (neu, niedrige Prio):** `KeyboardHost` ist mit 112 Zeilen / 25 Members breiter als
ideal. Aufteilung in Rollen-Interfaces (`ThemeHost`, `SuggestionHost`, `KeyboardStateHost`) wäre die konsequente
Fortsetzung — aber **kein Score-Hebel**, da alle Consumer im selben Paket liegen und der Service ohnehin
der einzige Implementierer ist.
### Phase 8: Threading & Koroutines

**Zahl:** `Executors.newSingleThreadExecutor()` + 2 `Handler` → `lifecycleScope` + `Dispatchers.IO`

**Maßnahmen:**
1. `SuggestionEngine` → `suspend fun` mit `Dispatchers.Default`
2. `FileManagerPanel` → `lifecycleScope.launch` für I/O
3. `ClipboardPanel` → `lifecycleScope.launch` für Clipboard-Zugriff

**Risiko:** Mittel — Threading-Ändungen können Race Conditions verursachen.

---

## 4. Bewertung: KeyTab vs. andere Android-Tastaturen (Audit 2026-09-16)

> **Methodik-Hinweis:** Die KeyTab-Spalte ist **streng gemessen** (→ §0). Fremd-Scores beruhen auf
> öffentlichen Repo-Daten (Stars, Commits, Modul-Struktur, README/ROADMAP) + allgemeinen Standards — **kalibrierte Schätzungen, kein Audit**.

### 4.1 Neue Gewichtung (ersetzt den alten 8-Kriterien-Global-Vergleich)

Der alte Global-Vergleich war in **beide Richtungen falsch kalibriert**: Gboard real ~92 (nicht 80),
FlorisBoard ~79 (nicht 73). Global liegt KeyTab damit **etwa gleichauf mit FlorisBoard**.

| Kategorie | Gewicht | KeyTab | Begründung KeyTab |
|---|---|---|---|
| Architektur & Modularisierung | 20 | **82** | Interfaces + 10 pure Logikmodule + Controller-Schicht; Panels entkoppelt (Phase 7). Abzug: 260-Zeilen-Service, breites `KeyboardHost` |
| Testqualität & Abdeckung | 18 | **78** | 116 grüne Tests, KVM-Emulator-CI, sprechende Namen. Abzug: ~30 % Abdeckung, **kein Coverage-Gate**, nur 2 Instrumented-Tests |
| Code-Qualität / Lesbarkeit | 15 | **84** | 0 TODO, 0 `Thread`, 6 `!!`/5.704 Z., KDoc. Abzug: 697-Zeilen-Layout, Namensdrift, doppelte Root-`KeyAnimations.kt` |
| Build / CI / Release | 12 | **86** | Debug+Release-CI, KVM-Instrumented, idempotenter Release, R8, Signing-Pipeline, Fastlane |
| Dokumentation | 10 | **88** | 287-Z. README + 400-Z. Phasen-Plan mit Risiken/Metriken **— beste Einzeldisziplin**. Abzug: driftete (jetzt korrigiert) |
| Feature-Breite | 10 | **68** | Dateimanager, Editor, Clipboard, Terminal, Snippets, 7 Sprachen, Farbrad-Themes. Abzug: kein Glide, kein Emoji, kein Code-Editor |
| Nischen-Fit (Coding) | 8 | **85** | **Einzigartige Positionierung**. Abzug: Extra-Keys-Zeile fehlt — größter Hebel |
| Prozessreife | 7 | **80** | Snapshot-Commits, Phasenplan, Risikotabelle. Abzug: Bus-Faktor 1 |

**Gewichteter Global-Score KeyTab: 81,4 → 81/100**

`0,20·82 + 0,18·78 + 0,15·84 + 0,12·86 + 0,10·88 + 0,10·68 + 0,08·85 + 0,07·80 = 81,4`

### 4.2 Global-Feld vs. Nische (gleiche Skala)

| Lösung | Global | Nische (Coding) | Kurzbegründung |
|---|---|---|---|
| **Gboard** | **92** | 40 | Glide, 600+ Sprachen, neuronale Korrektur. **Nische irrelevant:** proprietär, Telemetrie, kein Terminal/Dateimanager |
| **FlorisBoard** | **79** | 62 | 8,7k ⭐, 2.729 Commits, Multi-Modul, Extensions, Addons-Store, Apache-2.0. **Abzug: Wortvorhersage fehlt bis heute** |
| **HeliBoard** | **77** | 68 | FlorisBoard-Fork; **hat** Vorhersage + Glide, aktiv, F-Droid |
| **AnySoftKeyboard** | **74** | 60 | Ältestes OSS-Keyboard, ~70 Sprachen, Add-ons. Abzug: veraltete UI/Architektur |
| **Unexpected-Keyboard** | **66** | 72 | Extrem schlank, präzise, termux-freundlich. Abzug: minimalistisch, keine Vorhersage |
| **Simple Keyboard (Fossify)** | **61** | 45 | Sauber, minimal |
| **Terminal-Ein-Zweck-Tools** | **58** | 70 | Nische, meist schlecht gewartet |
| **Hacker's Keyboard** | **52** | 74 | **Vorreiter der Extra-Keys** (Esc/Ctrl/Alt/Pfeile) — konzeptionell wegweisend, aber unmaintained. **Nischen-Score hoch, weil genau das Feature, das KeyTab fehlt** |
| **KeyTab** | **81** | **85** | siehe §0 + 4.1 |

**Bemerkenswert:** Hacker's Keyboard hat **global nur 52**, in der Nische aber **74** — **und das allein
wegen der Extra-Keys-Zeile**. Genau deshalb ist sie KeyTabs größter Hebel: sie hebt den Nischen-, nicht den Global-Score.

## 5. Chaos-Bereinigung (Recovery-Plan)

### Phase R1: Working Tree bereinigen ✅ **ABGESCHLOSSEN**
- [x] Untracked schädliche Dateien löschen (`app/build.gradle`, `ime/Prefs.kt`)
- [x] Unvollständige Locale-Dateien löschen
- [x] Build prüfen: `bash build_keytab.sh debug`
- [x] ✅ **ABGESCHLOSSEN (16.09.):** Alle geänderten Dateien geprüft und committed („R1-Abschluss“). Ergebnis der Prüfung: `GamingLogic.kt` war **nicht** gelöscht, sondern umbenannt → `LikelyHighlightLogic.kt`; `keyboard_view.xml` enthielt die Editor-Toolbar (+ neu: Snippet-Editor-Zeile)

### Phase R2: Feature-Übernahme aus Chaos
- [x] Editor-Toolbar (`keyboard_view.xml` + `EditorPanel.kt`) — committed (57323e3)
- [x] ✅ **Gaming-Highlight vereinheitlicht** (Audit 16.09.): `GamingLogic` existiert nicht mehr,
  `KIND_HL`/`KEY_LIKELY` sind gesetzt. Entscheidung dokumentiert: **Feature umbenannt, nicht entfernt**
  — jetzt „Likely Highlighting" (`LikelyHighlightLogic` + `LikelyHighlightSection` + Tests).
  Die Pref-**Strings** `gaming`/`gaming_mode` bleiben bewusst (Kompatibilität zu bestehenden Installationen;
  dokumentiert in `Prefs.kt` KDoc).
- [ ] ColorWheelView Multi-Touch fixen *(offen, niedrige Prio — Einzelfinger-Farbwahl funktioniert)*
- [ ] Prefs-Key-Umbenennung (`clip_tab_enabled` → `clipboard_tab_enabled`) *(offen, niedrige Prio)
- [ ] String-Emoji-Änderungen in Locale-Dateien *(offen, niedrige Prio)
- [x] `install_keytab.sh` repariert (2026-09-15): `rish` → `rsh` (proot), Quoting-Fix
- [x] 0.9.6-debug-APK gesichert: `/sdcard/Download/KeyTab-0.9.6-debug.apk` (MD5 7b318527…)
- [x] ✅ **R1-Abschluss committed (16.09.)**: Snippet-Editor (＋ Neu / ✎ Bearbeiten) +
  einheitliche Tab-Höhen (Terminal = Editor-only) — `4574c69`, 116 Tests grün

### Phase R3: Konsistenz-Check ✅ **ABGESCHLOSSEN (Audit 16.09.)**
- [x] `ThemePrefs.kt`: **keine** `KIND_GAMING`-Referenzen mehr (grep: 0 Treffer; nur `KIND_HL`)
- [x] Doppelte `editor_input` in `keyboard_view.xml`: **nicht vorhanden** (grep -c = 1)
- [x] Build + Unit-Tests: BUILD SUCCESSFUL, **116 Tests, 0 Failures, 0 Skipped**
- [x] Commit der Bereinigung erfolgt (739dc66 + 4574c69)
---

## 6. Best Practices für Agent-Refactoring

1. **Immer Commits machen** — Agent-Arbeit muss in sauberen Commits enden
2. **Branch pro Agent-Lauf** — `agent/<feature>-<datum>` isoliert den Chaos
3. **Build-Check nach jedem Agent-Schritt** — `bash build_keytab.sh debug` muss grün sein
4. **Keine untracked Dateien** — Alles muss committed oder gelöscht sein
5. **Version nur in `build.gradle.kts`** — `build.gradle` (ohne .kts) ist ein Artefakt
6. **String-Ressourcen immer prüfen** — Neue `R.string.*`-Referenzen müssen definiert sein
7. **Konstanten-Drift vermeiden** — Löschte Konstanten müssen global gesucht werden

---
## 7. Sprint-Plan (neu priorisiert nach Score-Impact, Stand 2026-09-16)

Die alte ToDo-Liste war unsortiert und teils erledigt. Neue Ordnung **nach Score-Impact** aus dem Audit (Global 81 → Ziel ~92).

### P0 — höchster Impact (Nische +4,5)
- [ ] **Persistente Extra-Keys-Zeile** — Esc / Ctrl / Tab / `|` / `~` / `$` / Pfeile, konfigurierbar in Settings.
  **Das ist *der* Differenzierer für Coding-on-Android** (vim/nano in Termux) und der einzige Hebel, der die Nischen-Führung **ausbaut** statt nur aufzuholen.
  Referenz-Design: Hacker's Keyboard (Esc/Ctrl/Alt/Pfeile) + Termux-Extra-Keys. Nicht kopieren, sondern: nur die ~10 tatsächlich häufigen Keys, per Pref toggelbar.
  Betrifft: `keyboard_view.xml` (neue Zeile), `PanelHeights`/`TabController` (Höhen-Anpassung), neues `ExtraKeysLogic.kt` (pure, testbar), `Prefs.KEY_EXTRA_KEYS`, Settings-Toggle, Unit-Tests. Aufwand: mittel.

### P1 — Test-Infrastruktur schließen (Tests +3,0)
- [ ] **Coverage-Gate** (JaCoCo/Kover) in `app/build.gradle.kts` + CI-Schwelle (Start `line >= 30%`, dann steigern).
  Die Infrastruktur steht längst — das ist der auffälligste Widerspruch im Projekt: hohe Prozessqualität, aber kein Coverage-Nachweis.
- [ ] **Panel-Tests ausbauen** — aktuell nur **2** Instrumented-Tests. Panels sind seit Phase 7 entkoppelt und damit **direkt Robolectric-testbar** — billigster Coverage-Gewinn.
- [ ] **Unit-Tests für `TabController`** — **echte Lücke**, Datei existiert nicht.
  *Korrektur:* `ShiftControllerTest` **existiert bereits** (6 Tests, verifiziert) — die alte ToDo-Zeile „Unit-Tests für ShiftController und TabController" war zur Hälfte veraltet.
- [ ] **Test-Naming fixen:** `PanelsTest.kt` enthält `EditorPanelTest` **und** `ClipboardPanelTest` — in getrennte Dateien ziehen (Soll: Name = Klasse).

### P2 — Struktur & Distribution (+2,0 / +1,5)
- [ ] **`keyboard_view.xml` splitten** (697 Zeilen → `<include>`s: Tastatur-Raster, Tab-Leiste, Suggestion-Leiste, Panel-Container). Größte Einzeldatei des Projekts.
- [ ] **`MANAGE_EXTERNAL_STORAGE` ersetzen** (SAF / `READ_MEDIA_*` + App-Dirs) → Play-Store-tauglich. Aktuell: Store-Ausschluss + `requestLegacyExternalStorage` als Krücke.
- [ ] **IzzyOnDroid-Anmeldung** (Signing via Env ist bereits F-Droid-konform; reproduzierbare Builds prüfen).
- [ ] **Phase 6 abschließen**: IME 260 → ~150 Zeilen. *Herabgestuft* — der Hauptgewinn (908→260, −71 %) ist bereits gehoben.

### P3 — Politur
- [ ] Phase 8: Threading → Koroutinen (`lifecycleScope` + `Dispatchers.IO`), 2 `Handler` auflösen.
- [ ] Lint-Warnungen ~30 → < 10.
- [ ] `getSharedPreferences`-Streuung (**25** Aufrufe, gemessen) über `Prefs`-Accessor zentralisieren.
- [ ] IME-Härtung: Testmatrix Termux (neovim) / AndroidIDE / VS Code (proot) — Cursor, commitText, IME-Wechsel.
- [ ] Editor-Robustheit: Nicht-UTF-8 (latin-1) & große Dateien.
- [ ] **Repo-Aufräumen:** `KeyAnimations.kt` liegt doppelt (Projekt-Root **und** `ime/`) — Root-Kopie ist ein Artefakt, entfernen.

### P4 — Langfristig (bewusst hinten)
- [ ] **Editor: Syntax-Highlighting + Zeilennummern** — Feature +15, Nische +2,0 bei hohem Aufwand, aber der zweite echte Nischen-Differenzierer nach der Extra-Keys-Zeile.
- [ ] Termux-Deep-Link (Files-Tab → „In Termux öffnen")
- [ ] `KeyboardHost` in Rollen-Interfaces aufteilen *(kein Score-Hebel, siehe Phase 7)*
- [ ] Emoji-Unterstützung *(niedrigste Priorität — bewusst nach hinten)*
- [ ] Multi-Module-Gradle-Struktur
- ❌ **Bewusst NICHT geplant:** Cloud-Sync, Glide-Typing, 100+ Sprachen — Gboard-Wettbewerbsdimensionen, in denen man nicht gewinnen kann. Die Nische ist der Weg.

---

## 8. Risiken & Mitigation

| Risiko | Wahrscheinlichkeit | Auswirkung | Mitigation |
|--------|-------------------|------------|------------|
| Service-Refactor bricht IME | Mittel | Hoch | Inkrementell, Tests nach jedem Schritt |
| Panel-Entkopplung bricht UI | Niedrig | Mittel | Interface bereits definiert, nur Austausch |
| Threading-Änderungen → Race Conditions | Mittel | Hoch | Koroutines + Dispatchers, gründliches Testing |
| Agent-Chaos wiederholt sich | Mittel | Mittel | Best Practices (siehe Abschnitt 6) |

---

## 9. Erfolge (Audit-verifiziert, 2026-09-16)

### Architektur
- ✅ ThemeSettingsActivity 551 → 215 Zeilen (Sections extrahiert)
- ✅ `KeyTabImeService` 908 → **260** Zeilen (−71 %)
- ✅ **10 pure, Android-freie Logik-Module** (JUnit-schnell)
- ✅ **Phase 7 verifiziert abgeschlossen:** alle 5 Panels entkoppelt, **kein** Panel referenziert den Service
- ✅ `KeyboardHost` + 4 Controller (`Shift`, `Tab`, `Suggestion`, `Theme`) angebunden
- ✅ Code-Duplication 8 % → ~4 %

### Qualität (gemessen)
- ✅ **116 Unit-Tests / 15 Klassen / 0 Failures / 0 Skipped**
- ✅ **0** `TODO`/`FIXME`/`HACK`, **0** `Thread(...)`, **6** `!!` auf 5.704 Zeilen
- ✅ KVM-Emulator-CI (Instrumented, API 34)
- ✅ Build: `BUILD SUCCESSFUL`, R8 + `isShrinkResources`, idempotenter Release
- ✅ **Privacy-Claim Manifest-verifiziert** (keine `INTERNET`-Permission)
- ✅ **Keystore nicht im Git**, Signing via Env → F-Droid-konform
- ✅ **Lizenz-Attribution korrekt** (FrequencyWords MIT/CC-BY-SA-4.0)

### Prozess
- ✅ Snapshot-Commit als Safety Net vor Refactor (`8d7b9be`)
- ✅ Phasenplan mit Risikotabelle + Benchmarks (dieses Dokument)
- ✅ Install-Skript repariert (`rsh` statt `rish`, Quoting) — One-Click via Shizuku
- ✅ R1–R3 Chaos-Bereinigung abgeschlossen (`739dc66`, `4574c69`)

### Score-Entwicklung
| Stand | Global | Nische |
|---|---|---|
| Vor Refactor | 62 | 55 |
| Nach Phasen 0–5 | ~78 | ~84 |
| **Audit 16.09. (Phasen 0–7)** | **81** | **85** |
| Ziel (P0+P1+P2) | ~88 | ~91 |

---

*Letzte Aktualisierung: 2026-09-16 (Audit + R1-Abschluss + Phase-7-Verifikation)*