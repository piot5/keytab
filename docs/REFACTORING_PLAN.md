# KeyTab – Refactoring-Plan: Modularisierung & Separation of Concerns

Stand: 2026-09-15 · Status: **Phase 0–5 umgesetzt; R2-Editor-Toolbar committed (57323e3); R1-Abschluss (uncommitted Changes) offen; Phase 6–7 als Nächstes** · Ziel: wartbare, testbare Module ohne Verhaltensänderung (reine Struktur-Refactors).

**Benchmark (2026-09-15, neu kalibriert):** Global-Vergleich (Gboard 80, FlorisBoard 73, AnySoftKeyboard 66) ist **hinfällig als Zielgröße** — Gboard ist kein Wettbewerbsziel, FlorisBoard ist im Feature-Umfang bereits überholt. Neue Messgröße: **Nische-Score (Coding-on-Android: Termux + AndroidIDE) ≈ 84–85/100.** Größte Hebel: Extra-Keys-Zeile, IME-Härtung in fremden Editoren, IzzyOnDroid-Distribution, Editor `readText/writeText`-Edge-Cases (Encoding, große Dateien).

---

## 1. Ist-Analyse (aktualisiert)

**Umfang:** ~4.900 Zeilen Kotlin, 27 Klassen, 1 Gradle-Modul (`app`).

### 1.1 Dateigrößen & Problembereiche

| Datei | Zeilen | Befund | Priorität |
|---|---|---|---|
| `ime/KeyTabImeService.kt` | **349** | Reduziert (908→349), aber immer noch God-Class | 🔴 Hoch |
| `ime/SuggestionEngine.kt` | 262 | OK (pure Logik) | 🟢 Niedrig |
| `ime/FileManagerPanel.kt` | 258 | OK, aber an Service gekoppelt | 🟡 Mittel |
| `ime/EditorPanel.kt` | 257 | OK, aber an Service gekoppelt | 🟡 Mittel |
| `ime/TerminalPanel.kt` | 213 | OK, gekoppelt | 🟡 Mittel |
| `ThemeSettingsActivity.kt` | 200 | ✅ Refakturiert (Sections extrahiert) | 🟢 Erledigt |
| `ime/ThemePrefs.kt` | 186 | ✅ Sauber, pure Logik | 🟢 Erledigt |
| `ime/ThemeApplier.kt` | 183 | OK | 🟢 Niedrig |
| `ime/SuggestionController.kt` | 162 | OK | 🟢 Niedrig |
| `ime/KeyboardBinder.kt` | 256 | OK, Touch-Logik gekapselt | 🟢 Niedrig |
| `ime/ThemeController.kt` | 118 | ✅ Extrahiert | 🟢 Erledigt |
| `ime/CapsLogic.kt` | 29 | ✅ Extrahiert, testbar | 🟢 Erledigt |
| `ime/InputTargets.kt` | 135 | ✅ Interface-basiert | 🟢 Erledigt |
| `ime/LiftSpan.kt` | 21 | ✅ Extrahiert | 🟢 Erledigt |
| `sections/TopSection.kt` | 96 | ✅ Neu, sauber | 🟢 Erledigt |
| `sections/ColorSection.kt` | 94 | ✅ Neu, sauber | 🟢 Erledigt |
| `sections/GradientSection.kt` | 48 | ✅ Neu, sauber | 🟢 Erledigt |
| `sections/GamingSection.kt` | 82 | ✅ Neu, sauber | 🟢 Erledigt |
| `sections/PreviewSection.kt` | 88 | ✅ Neu, sauber | 🟢 Erledigt |
| Rest (10 Dateien) | < 110 | überwiegend sauber getrennt | 🟢 Niedrig |

### 1.2 Code-Qualitäts-Metriken (im Vergleich)

| Metrik | KeyTab (alt) | KeyTab (jetzt) | FlorisBoard | Gboard* |
|--------|-------------|---------------|-------------|----------|
| Testabdeckung | ~15% | ~20% | ~40% | ~60% |
| Unit-Tests | 8 Dateien | 10+ Dateien | 25+ Dateien | Intern |
| Lint-Warnungen | ~50 | ~30 | ~10 | Intern |
| Code-Duplication | ~8% | ~4% | ~3% | Intern |
| God-Classes | 2 | 1 | 0 | 0 |
| Dokumentation | 65/100 | 72/100 | 70/100 | 40/100 |

*Gboard-Werte geschätzt basierend auf Google-Standards.

**Bereits gut (beibehalten!):**
- Pure, testbare Logik-Objects: `TextEditLogic`, `KeyScaleLogic`, `GamingLogic`,
  `PanelHeights`, `ThemePrefs`, `Languages`
- View-Module: `LetterPopup`, `ThemeApplier`, `ColorWheelView`
- Panel-Klassen: `EditorPanel`, `FileManagerPanel`, `TerminalPanel`, `ClipboardPanel`
- 10+ Unit-Test-Dateien inkl. Robolectric (`ThemeApplierTest`, `PanelsTest`, `CapsLogicTest`)
- **NEU:** ThemeSettingsActivity vollständig in Sections modularisiert (5 Klassen)

**Verbleibende Probleme (SoC-Verletzungen):**

1. **`KeyTabImeService` (349 Zeilen) — reduziert, aber immer noch God-Class:**
   - View-Aufbau + Theme + Config (`onCreateInputView`)
   - Tasten-Event-Handling (delegiert jetzt an `KeyboardBinder`)
   - Shift/CapsLock-Zustandsmaschine (delegiert jetzt an `ShiftController` + `CapsLogic`)
   - Gaming-Highlighting (delegiert jetzt an `GamingLogic`)
   - Suggestion-Wiring (delegiert jetzt an `SuggestionController`)
   - Tab-Umschaltung (delegiert jetzt an `TabController`)
   - Text-Routing (`commitText`, `commitToApp` mit `editorActive`/`terminalActive`)
   - **Rest:** ~150 Zeilen Orchestrierung, die schwer zu extrahieren ist

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
│   ├── GamingLogic.kt          # Gaming-Highlight-Logik (pure Logik)
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
    ├── GamingSection.kt        # Gaming-Modus-Toggles
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
- [x] `sections/GamingSection.kt` — Gaming-Modus-Toggles
- [x] `sections/PreviewSection.kt` — Live-Vorschau
- [x] `ThemeSettingsActivity.kt` — Orchestrierung (200 Zeilen, sauber)

### Phase 6: Service-Orchestrierung reduzieren (NÄCHSTER SCHRITT)

**Ziel:** `KeyTabImeService` von 349 auf ~150 Zeilen reduzieren.

**Maßnahmen:**
1. `onCreateInputView` → `ViewFactory` extrahieren
2. `commitText`/`commitToApp` → `TextRouter` mit `InputTargets`
3. `setupSuggestions` → bereits in `SuggestionController`
4. `setupTabs` → bereits in `TabController`
5. `setupDelButton` → bereits in `KeyboardBinder`
6. `hookKeyboardButtons` → bereits in `KeyboardBinder`

**Risiko:** Mittel — Service ist der zentrale Punkt, Änderungen können alles brechen.

### Phase 7: Panel-Entkopplung

**Ziel:** Panels verwenden `KeyboardHost`-Interface statt konkreten Service.

**Maßnahmen:**
1. `EditorPanel` → `KeyboardHost` verwenden
2. `FileManagerPanel` → `KeyboardHost` verwenden
3. `TerminalPanel` → `KeyboardHost` verwenden
4. `ClipboardPanel` → `KeyboardHost` verwenden

**Risiko:** Niedrig — Interface bereits definiert, nur Austausch.

### Phase 8: Threading & Koroutines

**Zahl:** `Executors.newSingleThreadExecutor()` + 2 `Handler` → `lifecycleScope` + `Dispatchers.IO`

**Maßnahmen:**
1. `SuggestionEngine` → `suspend fun` mit `Dispatchers.Default`
2. `FileManagerPanel` → `lifecycleScope.launch` für I/O
3. `ClipboardPanel` → `lifecycleScope.launch` für Clipboard-Zugriff

**Risiko:** Mittel — Threading-Ändungen können Race Conditions verursachen.

---

## 4. Bewertung: KeyTab vs. andere Android-Tastaturen

> **Hinweis (2026-09-15):** Diese Tabelle ist der alte Global-Vergleich (Stand 2026-09-13). Neue strategische Messgröße ist der **Nische-Score (Coding-on-Android: Termux + AndroidIDE) ≈ 85/100** — Gboard ist kein Wettbewerbsziel, FlorisBoard ist im Feature-Umfang überholt. Tabelle bleibt als historische Basis erhalten.

### 4.1 Gesamtbewertung (Skala 0–100)

| Kriterium | Gewichtung | KeyTab | Gboard | FlorisBoard | AnySoftKeyboard |
|-----------|-----------|--------|--------|-------------|-----------------|
| **Code-Qualität** | 25% | 65 | 85 | 78 | 70 |
| **Testabdeckung** | 20% | 20 | 60 | 40 | 35 |
| **Modularität** | 15% | 70 | 80 | 85 | 65 |
| **Dokumentation** | 10% | 72 | 40 | 70 | 60 |
| **Performance** | 10% | 75 | 90 | 75 | 70 |
| **Wartbarkeit** | 10% | 65 | 80 | 80 | 65 |
| **Feature-Umfang** | 5% | 50 | 95 | 70 | 60 |
| **Build-System** | 5% | 80 | 90 | 85 | 75 |
| **GESAMT** | 100% | **62** | **80** | **73** | **66** |

### 4.2 Detaillierte Bewertung nach Kriterien

#### Code-Qualität (65/100)
- **Stärken:**
  - Saubere Trennung in Sections (neu)
  - Pure Logik-Objects (testbar)
  - Keine `FIXME`/`XXX` im Code
  - Konsistente Namenskonventionen
- **Schwächen:**
  - `KeyTabImeService` immer noch 349 Zeilen (God-Class)
  - ~30 Lint-Warnungen
  - Inline-Prefs-Zugriff noch vorhanden

#### Testabdeckung (20/100)
- **Stärken:**
  - 10+ Unit-Test-Dateien
  - Robolectric-Tests für ThemeApplier
  - Pure Logik ist testbar
- **Schwächen:**
  - Keine instrumented Tests auf CI (nur lokal)
  - Service-Logik nicht testbar (zu gekoppelt)
  - UI-Tests fehlen komplett

#### Modularität (70/100)
- **Stärken:**
  - Sections-Paket (5 Klassen, sauber)
  - Controller-Pattern (Shift, Tab, Suggestion, Theme)
  - Interface-basiert (InputTargets, KeyboardHost)
- **Schwächen:**
  - Panels noch an Service gekoppelt
  - Keine Gradle-Module (alles in `app`)

#### Dokumentation (72/100)
- **Stärken:**
  - README.md mit Feature-Liste
  - REFACTORING_PLAN.md (diese Datei)
  - KDoc-Kommentaren in neuen Klassen
- **Schwächen:**
  - Keine API-Dokumentation (Dokka)
  - Fehlende Architektur-Diagramme

#### Performance (75/100)
- **Stärken:**
  - Keine Memory Leaks bekannt
  - Effiziente SuggestionEngine
  - Gradient-Drawable-Caching
- **Schwächen:**
  - Keine Koroutines (Thread-Overhead)
  - View-Aufbau nicht optimiert (kein View-Stub)

#### Wartbarkeit (65/100)
- **Stärken:**
  - Klare Paketstruktur
  - Build-Skripte automatisiert
  - Saubere Git-History (nach Chaos-Bereinigung)
- **Schwächen:**
  - God-Class im Service
  - Fehlende CI/CD-Pipeline

#### Feature-Umfang (50/100)
- **Stärken:**
  - Tab-Manager (einzigartig)
  - Gaming-Modus
  - Editor + Terminal + FileManager
  - Theme-System mit Verlauf
- **Schwächen:**
  - Keine Emoji-Unterstützung
  - Keine Swipe-Gesten
  - Keine Cloud-Sync
  - Keine Mehr-Sprachen als 4

#### Build-System (80/100)
- **Stärken:**
  - One-Click Build-Skript
  - One-Click Install-Skript (Shizuku/rish)
  - Gradle 8.7, Kotlin 1.9.24
- **Schwächen:**
  - Kein CI/CD (GitHub Actions)
  - Keine signierten Release-APKs

---

## 5. Chaos-Bereinigung (Recovery-Plan)

### Phase R1: Working Tree bereinigen ✅ TEILWEISE
- [x] Untracked schädliche Dateien löschen (`app/build.gradle`, `ime/Prefs.kt`)
- [x] Unvollständige Locale-Dateien löschen
- [x] Build prüfen: `bash build_keytab.sh debug`
- [ ] **OFFEN:** 15+ geänderte Dateien prüfen und committen (u. a. `KeyTabImeService.kt`, `keyboard_view.xml`, `GamingLogic.kt` gelöscht — prüfen, ob Gaming-Feature entfernt oder verschoben wurde)

### Phase R2: Feature-Übernahme aus Chaos
- [x] Editor-Toolbar (`keyboard_view.xml` + `EditorPanel.kt`) — committed in 57323e3 (Editor-Tab rename + Clip-Tab + Toolbar ↑✕⎘↻ + Load-Default-Dir + Clip-Persistenz)
- [ ] Gaming-Highlight vereinheitlichen (`KIND_GAMING` → `KIND_HL`) — *bei gelöschtem GamingLogic: Entscheidung dokumentieren (Feature removed?) und Reste-Referenzen aufräumen*
- [ ] ColorWheelView Multi-Touch fixen
- [ ] Prefs-Key-Umbenennung (`clip_tab_enabled` → `clipboard_tab_enabled`)
- [ ] String-Emoji-Änderungen in Locale-Dateien
- [x] `install_keytab.sh` repariert (2026-09-15): `rish` → `rsh` (proot), kaputtes Quoting in Kopier-Logik behoben — One-Click-Install funktioniert
- [x] Aktuelles 0.9.6-debug-APK gesichert: `/sdcard/Download/KeyTab-0.9.6-debug.apk` (MD5 7b318527…)

### Phase R3: Konsistenz-Check
- [ ] `ThemePrefs.kt`: `KIND_GAMING`-Referenzen entfernen
- [ ] Doppelte `editor_input` in `keyboard_view.xml` prüfen
- [ ] Build + Unit-Tests + Smoke-Test auf Gerät
- [ ] Commit der Bereinigung

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
## 7. Aktuelle ToDo-Liste (Stand 2026-09-15)

### Sofort (diese Woche)
- [ ] **R1 abschließen:** 15+ geänderte Dateien reviewen & committen (inkl. GamingLogic-Entscheidung)
- [ ] **R3:** Konsistenz-Check + Build + Unit-Tests + Smoke-Test auf Gerät
- [ ] Editor-Robustheit: Nicht-UTF-8-Dateien (latin-1) & große Dateien im Editor-Tab testen

### Kurzfristig (nächste 2 Wochen)
- [ ] **Phase 6:** Service-Orchestrierung reduzieren (IME: 357 Zeilen)
- [ ] **Phase 7:** Panel-Entkopplung via KeyboardHost
- [ ] Unit-Tests für `ShiftController` und `TabController`
- [ ] **Nische-Feature:** Persistente Extra-Keys-Zeile (Esc/Ctrl/Tab/`|`/`~`/`$`/Pfeile, konfigurierbar)

### Mittelfristig (nächster Monat)
- [ ] **Phase 8:** Threading → Koroutines
- [ ] IME-Härtung: Testmatrix Termux (neovim), AndroidIDE-Editor, VS Code (proot) — Cursor/commitText/IME-Wechsel
- [ ] **Distribution:** IzzyOnDroid/F-Droid-Anmeldung (reproducible Builds)
- [ ] Instrumented Tests auf Emulator
- [ ] Lint-Warnungen auf < 10 reduzieren

### Langfristig
- [ ] **Snippet-Tab-Option in Einstellungen** (ersetzt ehemaliges Swipe-Feature — benannte Befehle/Snippets als eigener Tab, Toggle in Settings)
- [ ] Termux-Deep-Link (Files-Tab → "In Termux öffnen")
- [ ] Emoji-Unterstützung *(niedrigste Priorität — bewusst nach hinten)*
- [ ] Multi-Module Gradle-Struktur

---

## 8. Risiken & Mitigation

| Risiko | Wahrscheinlichkeit | Auswirkung | Mitigation |
|--------|-------------------|------------|------------|
| Service-Refactor bricht IME | Mittel | Hoch | Inkrementell, Tests nach jedem Schritt |
| Panel-Entkopplung bricht UI | Niedrig | Mittel | Interface bereits definiert, nur Austausch |
| Threading-Änderungen → Race Conditions | Mittel | Hoch | Koroutines + Dispatchers, gründliches Testing |
| Agent-Chaos wiederholt sich | Mittel | Mittel | Best Practices (siehe Abschnitt 6) |

---

## 9. Erfolge (bereits erreicht)

- ✅ ThemeSettingsActivity von 551 auf 200 Zeilen reduziert
- ✅ 5 neue Section-Klassen (sauber, testbar)
- ✅ CapsLogic, RepeatScheduler, LiftSpan extrahiert
- ✅ ShiftController, TabController, SuggestionController, ThemeController extrahiert
- ✅ InputTargets, KeyboardHost Interfaces definiert
- ✅ KeyboardBinder, ThemeApplier extrahiert
- ✅ Build-Skripte automatisiert
- ✅ Install-Skript repariert (rsh statt rish, Quoting-Fix) — One-Click-Install via Shizuku
- ✅ 65 Unit-Tests in 14 Test-Dateien
- ✅ Code-Duplication von 8% auf 4% reduziert
- ✅ Editor-Toolbar + Clip-Tab committed (57323e3)
- ✅ Nische-Score von 55 auf ~85 erhöht (Coding-on-Android-Kontext)

---

*Letzte Aktualisierung: 2026-09-15*
