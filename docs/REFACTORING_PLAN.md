# KeyTab – Refactoring-Plan: Modularisierung & Separation of Concerns

Stand: 2026-09-12 · Status: **Plan (nicht umgesetzt)** · Ziel: wartbare, testbare
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
│   ├── InputRouter.kt        # NEU: commitText/delete-Routing (App|Editor|Terminal)
│   ├── SuggestionController.kt # NEU: Wiring Engine↔Views↔Scaler
│   ├── GamingHighlighter.kt  # NEU: View-Highlighting/-Effekte (nutzt GamingLogic)
│   ├── TabController.kt      # NEU: Tab-Listener + Panel-Sichtbarkeit
│   ├── ThemeController.kt    # NEU: Dark-Toggle, Rebuild bei themeVersion-Change
│   ├── panels/               # EditorPanel, FileManagerPanel, TerminalPanel, ClipboardPanel
│   ├── suggest/              # SuggestionEngine, WordPredictionManager, DynamicKeyScaler
│   └── theme/                # ThemePrefs→core, ThemeApplier, LiftSpan
└── file/
    └── FileManagerFragment
```

**Zentrale Interfaces (Dependency Inversion):**

```kotlin
/** Schmale Schnittstelle Service→Module (ersetzt Übergabe des ganzen Service). */
interface KeyboardHost {
    val context: Context
    val prefs: Prefs
    fun commit(text: String)          // ins aktive Ziel (App/Editor/Terminal)
    fun haptic()
    fun isDarkMode(): Boolean
}

/** Wohin Text fließt – eine Implementierung, drei Ziele. */
interface InputTarget {
    fun insert(text: String)
    fun delete(chars: Int)
    fun deleteLastWord()
    fun textBefore(count: Int): String
}
// Implementierungen: AppTarget (InputConnection), EditorTarget, TerminalTarget
// → entfernt ALLE `if (editorActive) ... else if (terminalActive) ...`-Ketten
```


---

## 3. Umsetzungsphasen

Jede Phase: **klein, unabhängig, buildbar** — nach jeder Phase
`./gradlew testDebugUnitTest assembleDebug` + manuelle Smoke-Tests
(Tippen, Shift, Long-Press, Tabs, Theme, Gaming, Editor/Files/Terminal).

### Phase 0 – Sicherheitsnetz (½ Tag)
- [ ] Bestands-Tests grün dokumentieren (Baseline: 8 Test-Dateien)
- [ ] Fehlende Tests für extrahierbare Logik ergänzen, **bevor** sie umzieht:
      Shift-Zustandsübergänge (neu als pure Klasse testbar), `autoCapitalize`
      (rein: EditorInfo → Boolean), Tab-Visibility-Matrix (pos → Sichtbarkeiten)
- [ ] Git-Tag `pre-refactor` als Rollback-Punkt

### Phase 1 – Low-Hanging Fruit (½ Tag, ~0 Risiko)
- [ ] `core/Prefs.kt`: alle Pref-Keys + Zugriff zentral
      (eliminiert Duplikate `PREFS`/`KEY_USER_DICT` aus Service & MainActivity)
- [ ] `LiftSpan` → eigene Datei `ime/theme/LiftSpan.kt`
- [ ] Dead Code entfernen (Kommentar-Leichen wie „keyNeighborLetters moved")

### Phase 2 – ShiftController + InputRouter (1 Tag, Kern-Entkopplung)
- [ ] `ShiftController(shifted, capsLock, lastShiftTap, onShiftChanged)` —
      State-Machine pur, View-Update via Callback → unit-testbar ohne Robolectric
- [ ] `InputRouter` mit `InputTarget`-Implementierungen → ersetzt
      `editorActive`/`terminalActive`-Verzweigungen in `commitText`,
      `deleteLastWord`, `WordPredictionManager.InputOperations`
- [ ] Service delegiert nur noch; `applyLetterCase`/`updateShiftVisual` ziehen um

### Phase 3 – Tasten-Events: KeyboardBinder (1 Tag)
- [ ] `hookKeyboardButtons`, `setupLetterButton`, `setupDelButton` →
      `KeyboardBinder(host, letterPopup, callbacks)`
- [ ] Del-Repeat-Beschleunigung (`WORD_DELETE_*`) als pure Klasse
      `RepeatScheduler` (Zeitlogik testbar)
- [ ] Long-Press/Touch-Logik bleibt 1:1 (nur Umzug, kein Rewrite!)

### Phase 4 – Feature-Controller (1 Tag)
- [ ] `GamingHighlighter`: `updateGamingKeys`/`restoreGamingKeys`/
      `gamingCompletionEffect` aus dem Service (View-Manipulation) —
      `GamingLogic` bleibt pure Entscheidungslogik
- [ ] `SuggestionController`: `setupSuggestions`/`updateSuggestions`/
      `applySuggestion`/`updateDynamicKeys`
- [ ] `TabController`: `setupTabs` inkl. Panel-Visibility + PanelHeights-Aufruf
- [ ] `ThemeController`: `toggleDarkMode`, `setupThemeButton`,
      `maybeRebuildForThemeChange`
- Ergebnis: `KeyTabImeService` < 200 Zeilen (Lifecycle + onCreateInputView-Gerüst)


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
