# KeyTab – Refactoring-Plan: Modularisierung & Separation of Concerns

Stand: 2026-09-13 · Status: **Phase 0–4 umgesetzt; Phase 6 als Nächstes** · Ziel: wartbare, testbare
Module ohne Verhaltensänderung (reine Struktur-Refactors).

---

## 1. Ist-Analyse

**Umfang:** 4.350 Zeilen Kotlin, 21 Klassen, 1 Gradle-Modul (`app`).

| Datei | Zeilen | Befund |
|---|---|---|
| `ime/KeyTabImeService.kt` | **908** | God-Class: 7+ Verantwortlichkeiten (s. u.) |
| `ThemeSettingsActivity.kt` | **551** | UI-Bau + Prefs-/Farb-Logik vermischt |
| `ime/SuggestionEngine.kt` | 262 | OK (pure Logik) |
| `ime/FileManagerPanel.kt` | 258 | OK, aber an Service gekoppelt |
| `ime/EditorPanel.kt` | 240 | OK, aber an Service gekoppelt |
| Rest (16 Dateien) | < 220 | überwiegend sauber getrennt |

**Bereits gut (beibehalten!):**
- Pure, testbare Logik-Objects: `TextEditLogic`, `KeyScaleLogic`, `GamingLogic`,
  `PanelHeights`, `ThemePrefs`, `Languages`
- View-Module: `LetterPopup`, `ThemeApplier`, `ColorWheelView`
- Panel-Klassen: `EditorPanel`, `FileManagerPanel`, `TerminalPanel`, `ClipboardPanel`
- 8 Unit-Test-Dateien inkl. Robolectric (`ThemeApplierTest`, `PanelsTest`)

**Probleme (SoC-Verletzungen):**

1. **`KeyTabImeService` bündelt 7 Verantwortlichkeiten:**
   - View-Aufbau + Theme + Config (`onCreateInputView`, ~110 Zeilen)
   - Tasten-Event-Handling (`hookKeyboardButtons`, `setupLetterButton`,
     `setupDelButton`, ~200 Zeilen Touch-/Long-Press-Logik)
   - Shift/CapsLock-Zustandsmaschine (`shifted`, `capsLock`, `lastShiftTap`,
     `applyLetterCase`, `updateShiftVisual`, `autoCapitalize`)
   - Gaming-Highlighting (`updateGamingKeys`, `restoreGamingKeys`,
     `gamingCompletionEffect` — View-Manipulation, obwohl `GamingLogic` schon pur ist)
   - Suggestion-Wiring (`setupSuggestions`, `updateSuggestions`, `applySuggestion`,
     `updateDynamicKeys`)
   - Tab-Umschaltung (`setupTabs`, ~70 Zeilen Listener-Logik)
   - Text-Routing (`commitText`, `commitToApp`, `deleteLastWord` mit
     `editorActive`/`terminalActive`-Verzweigungen)
2. **Duplizierte Konstanten:** `PREFS = "keytab_prefs"` und `KEY_USER_DICT`
   existieren in `KeyTabImeService` **und** `MainActivity` → Drift-Risiko.
3. **Breite Kopplung:** Panels erhalten den kompletten `KeyTabImeService`
   (`EditorPanel(this, ...)`), obwohl sie nur 3–4 Operationen brauchen.
4. **Verstreuter Prefs-Zugriff:** `getSharedPreferences(PREFS, MODE_PRIVATE)`
   erscheint ~10× inline im Service + in Activity/Panels.
5. **`ThemeSettingsActivity`:** baut UI programmatisch und enthält Farb-/Prefs-
   Logik (`writeToTarget`, `currentColor`, `gradientShader`) in einer Klasse.
6. **Inner Class `LiftSpan`** im Service → gehört in eigene Datei (wiederverwendbar).
7. **Threading ad hoc:** `Executors.newSingleThreadExecutor()` + 2 `Handler`;
   keine zentrale Koroutine-Struktur (`lifecycleScope` ungenutzt).

---

## 2. Zielarchitektur (Package-Struktur)

```
com.piotv.keytab
├── app/                      # Activities + App-UI
│   ├── MainActivity
│   ├── ThemeSettingsActivity # nur noch UI-Assembly
│   └── ColorWheelView
├── core/                     # reine Logik, KEINE Android-Views (JVM-testbar)
│   ├── TextEditLogic, KeyScaleLogic, GamingLogic, PanelHeights, Languages
│   └── Prefs.kt              # NEU: zentrale Einstellungen (Keys + Zugriff)
├── ime/
│   ├── KeyTabImeService      # dünn: nur Lifecycle + Delegation (< 200 Zeilen)
│   ├── KeyboardBinder.kt     # NEU: Tasten-Events (Click/Touch/Long-Press/Del-Repeat)
│   ├── ShiftController.kt    # NEU: Shift/CapsLock-State + applyLetterCase
│   ├── SuggestionController  # NEU: Suggestion-Wiring
│   ├── TabController         # NEU: Tab-Umschaltung
│   ├── ThemeController       # NEU: Theme-Aufbau + Rebuild
│   ├── KeyboardHost          # NEU: schmale Service-Schnittstelle
│   ├── RepeatScheduler       # NEU: Del-Repeat-Beschleunigung
│   └── LiftSpan              # NEU: aus Service extrahiert
├── panels/                   # Panel-Klassen (nach Phase 6)
│   ├── FileManagerPanel
│   ├── EditorPanel
│   ├── TerminalPanel
│   └── ClipboardPanel
└── panels/view/              # (optional, falls Panel-View-Logik wächst)
```

---

## 3. Phasen

| Phase | Beschreibung | Status |
|-------|--------------|--------|
| 0 | Sicherheitssnapshot (Safety-Net) | ✅ `8d7b9be` |
| 1 | `LiftSpan` extrahieren + zentrale Prefs | ✅ `a0b318e` + `622ed47` |
| 2 | `ShiftController` + `CapsLogic` + `InputRouter` | ✅ `31a4055` |
| 3 | `KeyboardBinder` + `RepeatScheduler` extrahieren | ✅ `94ee7ae` |
| 4 | `ThemeController` + `TabController` + `SuggestionController` | ✅ `8c29848` |
| 5 | `ThemeSettingsActivity` (Farb-/Prefs-Logik auslagern) | ⬜ |
| 6 | Panels auf schmale Interfaces (`KeyboardHost`) | ⬜ |
| 7 (optional) | Modernisierung (Coroutines, Multi-Modul) | ⬜ |

### Phase 0 – Safety Net (Snapshot vor Refactor)

- [x] `git checkout -b refactor/soc` + `git commit -m "pre-refactor: snapshot vor SoC-Refactor"`
- [x] Build + Tests grün vor Commit
- [x] Commit: `8d7b9be`

### Phase 1 – Zentrale Prefs + LiftSpan (½ Tag)

- [x] `LiftSpan` in eigene Datei extrahiert (`ime/LiftSpan.kt`, 1 Zeile)
- [x] `Prefs` als zentrales `object` mit `FILE`- und `KEY_*`-Konstanten
- [x] `PREFS`-Duplikate in `MainActivity` und `KeyTabImeService` entfernt
- [x] Ergebnis: `MainActivity` nutzt jetzt `Prefs.FILE`, Drift eliminiert

### Phase 2 – ShiftController + InputRouter (1 Tag)

- [x] `ShiftController` extrahiert (Zustandsmaschine: `tapShift`, `consume`, `resetForInput`)
- [x] `CapsLogic` entkoppelt
- [x] `InputRouter` extrahiert (Zielauswahl: App/Editor/Terminal)
- [x] Ergebnis: Service delegiert Shift/Routing an Module

### Phase 3 – KeyboardBinder + RepeatScheduler (1 Tag)

- [x] `KeyboardBinder` extrahiert: `hookKeyboardButtons`, `setupLetterButton`, `setupDelButton`,
  Touch-/Long-Press-Logik, `updateShiftVisual`, `applyLetterCase`
- [x] `RepeatScheduler` extrahiert: beschleunigendes Wort-Löschen
- [x] `consumeSingleShift()`-Muster im Service → Delegation
- [x] `isInitialized`-Guards im Service (`::keyboardBinder.isInitialized`)
- [x] Ergebnis: `KeyTabImeService` 871 → 573 Zeilen (−34 %);
  Ziel < 200 Zeilen nach Phase 3 (KeyboardBinder)


### Phase 4 – ThemeController + TabController + SuggestionController + KeyboardHost (1 Tag)

- [x] `ThemeController` extrahiert: Theme-Aufbau, Theme-Update, `applyLetterCase`
- [x] `TabController` extrahiert: Tab-Setup + Tab-Wechsel-Logik
- [x] `SuggestionController` extrahiert: `setupSuggestions`, `updateSuggestions`,
  `applySuggestion`, `updateDynamicKeys`, Gaming-Highlighting
- [x] `KeyboardHost`-Interface: schmale Service-Schnittstelle für Controller
      (wird in Phase 6 auch von Panels verwendet)
- [x] Shift-Reset-Pattern (`if (shifted && !capsLock) …`) → `consumeSingleShift()`
- [x] Ergebnis: `KeyTabImeService` 871 → 573 Zeilen (−34 %);
  Ziel < 200 Zeilen nach Phase 3 (KeyboardBinder)


### Phase 5 – ThemeSettingsActivity (½–1 Tag)

- [ ] Farb-/Prefs-Logik (`writeToTarget`, `currentColor`, `gradientShader`,
      `gamingPref`) → `ThemeSettingsModel` (nutzt `core/Prefs`)
- [ ] Section-Builder (`buildTop`, `gradientSection`, `gamingSection`,
      `colorSection`, `actionRow`) → eigene `sections/`-Dateien
      (Compose-Migration: separat entscheiden, nicht Teil dieses Refactorings)

### Phase 6 – Panels auf schmale Interfaces (½ Tag)

- [ ] `EditorPanel(this, ...)` → `EditorPanel(host: KeyboardHost, ...)`
      (analog Files/Terminal/Clipboard) → kein Panel kennt mehr den Service

### Phase 7 (optional, separat entscheiden) – Modernisierung

- [ ] Executor/Handler → Kotlin Coroutines (`lifecycleScope`, `Dispatchers.IO`)
- [ ] Gradle-Multi-Modul (`:core` als JVM-Modul) — lohnt erst, wenn `core/`
      stabil ist; einzelnes app-Modul bleibt vorerst OK


---

## 4. Best Practices (verbindlich für alle Phasen)

1. **Eine Verantwortung pro Klasse** (SRP): Service = Lifecycle + Delegation,
   Controller = ein Feature, `core/` = reine Logik ohne `android.view.*`
2. **Abhängigkeiten zeigen nach innen:** Views → Controller → Core.
   Core kennt keine Views. Module kennen den Service nur als `KeyboardHost`
3. **Umziehen statt Umschreiben:** Verhalten bleibt bit-identisch; Optimierungen
   erst nach Abschluss (sonst keine saubere Fehlerzuordnung)
4. **Test-Pyramide ausbauen:** jede extrahierte pure Klasse bekommt Unit-Tests
   (Shift-Übergänge, RepeatScheduler, InputRouter-Ziele, Tab-Matrix)
5. **Keine God-Params:** max. 4 Konstruktor-Parameter, sonst Datenklasse/Interface
6. **Konstanten/Keys genau einmal** (in `core/Prefs`)
7. **Sichtbarkeit minimal:** `private`/`internal` statt `public`; `internal var`
   wie `keyboardRoot` hinter Interface kappen
8. **Kommentare mitnehmen:** die vorhandenen Warum-Kommentare (z. B. TabLayout-
   baseBackgroundDrawable, Leak-Fix releasePanels) sind wertvoll → mit umziehen
9. **Nach jeder Phase:** Build + alle Unit-Tests + Smoke-Test auf dem Gerät
   (`install_keytab.sh`), Commit pro Phase (saubere `git bisect`-Historie)

---

## 5. Risiken & Gegenmaßnahmen

| Risiko | Gegenmaßnahme |
|---|---|
| IME-Lifecycle bricht (onCreateInputView-Rebuild, `releasePanels`-Leak-Fix) | Rebuild-Pfad (`maybeRebuildForThemeChange`) erst in Phase 4 anfassen; Leak-Test (Panel-Referenzen nach Rebuild) behalten |
| Touch-/Long-Press-Verhalten ändert sich subtil | Phase 3 = reiner Umzug; Smoke-Test-Liste: Long-Press-Popup, Drag-Auswahl, Del-Repeat-Beschleunigung, Shift-Double-Tap |
| Theme-Regressions (Alpha, Tab-Leiste, Verlauf) | `ThemeApplierTest` (7 Tests) muss grün bleiben; ThemeApplier selbst wird **nicht** umgebaut |
| Robolectric-Tests hängen an konkreten Klassen | Tests pro Phase migrieren, nie „am Ende" |
| Zeitdruck → halbe Refactors | Phasen sind unabhängig shippbar; jede Phase endet lauffähig |

## 6. Aufwand & Reihenfolge

- Gesamt: **~4–5 Tage** (Phasen 1–6), Phase 0 vorher (½ Tag)
- Empfohlene Reihenfolge: 0 → 1 → 2 → 4 → 3 → 6 → 5
  (Phase 2+4 entkernen den Service am schnellsten; Phase 3 ist der größte
  Block und profitiert von `InputRouter` aus Phase 2)
- Abbruchkriterium: Nach jeder Phase ist das Projekt release-fähig —
  das Refactoring kann jederzeit pausiert werden.

---

## 7. Agent-Chaos: Ist-Zustand & Rescue-Plan (2026-09-13)

> **Hinweis:** Dieser Abschnitt dokumentiert den Zustand nach einem fehlgeschlagenen
> Agent-Refactoring. Der Agent hat **ohne Commit** gearbeitet und dabei mehrere
> Dateien in einen inkonsistenten Zustand gebracht. Der letzte gute Stand ist
> Commit `57323e3` (feat: Editor-Tab rename + Clip-Tab + Editor-Toolbar ...).

### 7.1 Versionswiderspruch (kritisch)

| Quelle | Version | versionCode |
|--------|---------|-------------|
| `app/build.gradle.kts` (commit-t) | 0.9.6 | 22 |
| `app/build.gradle` (**untracked!**) | 0.9.5 | 30 |
| Installierte APK (vermutlich) | ? | ? |

Die Datei `app/build.gradle` (ohne `.kts`) **blockiert jeden Gradle-Build** — Gradle
bevorzugt `.gradle` gegenüber `.gradle.kts` und würde hier scheitern (unvollständig,
keine Plugins). **Sofortmaßnahme:** Datei löschen.

### 7.2 Working Tree: 9 geänderte Dateien + 4 untracked

**Geändert (working tree):**

| Datei | Problem |
|-------|---------|
| `EditorPanel.kt` | **Doppeltes `loadFile`** — kollidierende Methoden, kaputte Klammerung, fehlende String-Resourcen (`editor_loaded`, `editor_load_failed`, etc.) |
| `FileManagerPanel.kt` | `MAX_CLIP_CONTENT_BYTES` Konstante gelöscht, aber Zeile 140 referenziert sie weiter → **Kompilierfehler** |
| `SuggestionController.kt` | Gaming-Farbe jetzt `KIND_HL` statt `KIND_GAMING` (in ThemeSettingsActivity schon entfernt, aber `KIND_GAMING` noch in ThemePrefs!) |
| `ThemeApplier.kt` | `sugLayer()` nutzt jetzt `hl` statt `defPrimary` (mit `SuggestionController` synchron) |
| `Prefs.kt` (root) | Key `clip_tab_enabled` → `clipboard_tab_enabled` umbenannt (Break für alte Prefs?) |
| `ColorWheelView.kt` | `event.action` → `event.actionMasked` (Multi-Touch-Ready, aber ungetested) |
| `ThemeSettingsActivity.kt` | `KIND_GAMING` aus Farbpalette entfernt |
| `keyboard_view.xml` | ~141 Zeilen neu (Editor-Toolbar-Sektion), aber **doppeltes `editor_input` EditText** in altem Bereich? |
| `strings.xml` | Emoji-Änderungen ⤓→💾, ⤒→📂 |

**Untracked:**
- `app/build.gradle` — **schädlich**, muss gelöscht werden
- `app/src/main/java/.../ime/Prefs.kt` — Dublette (2 Zeilen Code), muss gelöscht werden
- `values-de/`, `values-ja/`, `values-ja-rJP/` — unvollständige Locales (je 1 Zeile)

### 7.3 Kompilierbare Fehlerliste (Build bricht)

1. ❌ `FileManagerPanel.kt:140` — `MAX_CLIP_CONTENT_BYTES` nicht definiert
2. ❌ `EditorPanel.kt:281+` — doppelte `loadFile`-Definition (Scope-Problem)
3. ❌ `EditorPanel.kt` — `editor_loaded`, `editor_load_failed` etc. nicht in `strings.xml`
4. ❌ `app/build.gradle` blockiert Gradle komplett

### 7.4 Rescue-Phasen (neu)

#### Phase R1 – Sofort-Bereinigung (½ Stunde)

- [x] `git checkout -- .` (Working Tree zurücksetzen)
- [x] Untracked schädliche Dateien löschen:
  - `app/build.gradle` (blockiert Gradle)
  - `app/src/main/java/com/piotv/keytab/ime/Prefs.kt` (Dublette)
- [x] Unvollständige Locale-Dateien löschen:
  - `values-de/strings.xml`
  - `values-ja/strings.xml`
  - `values-ja-rJP/strings.xml`
- [ ] Build prüfen: `bash build_keytab.sh debug`

#### Phase R2 – Feature-Übernahme aus Chaos (1–2 Tage)

Gezielt die brauchbaren Änderungen aus dem Chaos re-commiten:

1. **Editor-Toolbar** (`keyboard_view.xml` + `EditorPanel.kt`):
   - Neue Toolbar-Sektion (Load/Save/Send/Clear/Copy/Reload) übernehmen
   - Doppelte `loadFile`-Methode bereinigen (nur die ZIP-fähige behalten)
   - Fehlende String-Ressourcen in `strings.xml` eintragen
   - `editor_send_up_up` String fixen

2. **Gaming-Highlight vereinheitlichen**:
   - `SuggestionController.kt`: `KIND_GAMING` → `KIND_HL` (bereits gemacht)
   - `ThemeApplier.kt`: `sugLayer()` nutzt `hl` (bereits gemacht)
   - `ThemeSettingsActivity.kt`: `KIND_GAMING` aus Palette entfernt (bereits gemacht)
   - `ThemePrefs.kt`: `KIND_GAMING`-Konstante entfernen (nach Phase R2)

3. **ColorWheelView Multi-Touch**:
   - `event.action` → `event.actionMasked` (bereits gemacht, testen!)

4. **Prefs-Key-Umbenennung**:
   - `clip_tab_enabled` → `clipboard_tab_enabled` (bereits gemacht)
   - Migration für bestehende User-Prefs? (Default `false` → kein Break, aber prüfen)

5. **String-Emoji-Änderungen**:
   - `editor_save`: ⤓ → 💾
   - `editor_load`: ⤒ → 📂
   - Locale-Dateien entsprechend ergänzen (de, en, ja)

#### Phase R3 – Konsistenz-Check (½ Tag)

- [ ] `ThemePrefs.kt`: `KIND_GAMING`-Referenzen entfernen (nach R2)
- [ ] `MAX_CLIP_CONTENT_BYTES` in `FileManagerPanel.kt` wieder einfügen (oder Referenz entfernen)
- [ ] Doppelte `editor_input`-Definition in `keyboard_view.xml` prüfen
- [ ] Build + Unit-Tests + Smoke-Test auf Gerät
- [ ] Commit: `fix: Agent-Chaos bereinigt, Editor-Toolbar + Gaming-HL vereinheitlicht`

### 7.5 Best Practices für Agent-Refactoring (neu)

1. **Immer Commits machen** — Agent-Arbeit muss in sauberen Commits enden,
   nie im Working Tree verlassen werden.
2. **Branch pro Agent-Lauf** — `agent/<feature>-<datum>` isoliert den Chaos.
3. **Build-Check nach jedem Agent-Schritt** — `bash build_keytab.sh debug`
   muss grün sein, bevor der Agent weiter macht.
4. **Keine untracked Dateien** — Alles muss committed oder gelöscht sein.
5. **Version nur in `build.gradle.kts`** — `build.gradle` (ohne .kts) ist
   ein Artefakt und muss gelöscht werden.
6. **String-Ressourcen immer prüfen** — Neue `R.string.*`-Referenzen müssen
   in `values/strings.xml` (und idealerweise `values-en/`) definiert sein.
7. **Konstanten-Drift vermeiden** — Löschte Konstanten müssen global
   gesucht werden (`grep -rn 'KONSTANTE' app/src/`).

---

## 8. Aktuelle ToDo-Liste (Stand 2026-09-13)

- [x] **R1**: Working Tree bereinigen (untracked löschen, geänderte Dateien zurücksetzen)
- [ ] **R2**: Editor-Toolbar aus Chaos übernehmen (mit String-Ressourcen)
- [ ] **R2**: Gaming-Highlight vereinheitlichen (`KIND_GAMING` → `KIND_HL`)
- [ ] **R2**: `MAX_CLIP_CONTENT_BYTES` in `FileManagerPanel.kt` wieder einfügen
- [ ] **R2**: String-Emoji-Änderungen in Locale-Dateien ergänzen
- [ ] **R3**: `ThemePrefs.kt` von `KIND_GAMING`-Referenzen befreien
- [ ] **R3**: Doppelte `editor_input` in `keyboard_view.xml` prüfen
- [ ] **R3**: Build + Tests + Smoke-Test
- [ ] **R3**: Commit der Bereinigung
- [ ] Dann: Phase 5 (ThemeSettingsActivity) fortsetzen
