# Changelog

Alle nennenswerten Aenderungen an KeyTab, neueste zuerst.

Das Format folgt [Keep a Changelog](https://keepachangelog.com/de/1.1.0/);
die Versionierung folgt [Semantic Versioning](https://semver.org/lang/de/).

> Die Version wird **nicht** hier gepflegt, sondern in `app/build.gradle.kts`
> (`versionCode`/`versionName`). `scripts/check_docs_drift.sh` (CI-Job "docs")
> prueft bei jedem Push, dass der Kopf dieses Changelogs, der fastlane-Changelog
> und die Gradle-Version zusammenpassen.


## 0.9.8

- **Refactor (P3): KeyboardHost in Rollen-Interfaces aufgeteilt** — statt eines
  25-Member-Interfaces gibt es nun `ThemeHost`, `TabHost`, `SuggestionHost` und
  `KeyboardInputHost`. `ThemeController`, `TabController`, `SuggestionController`
  und `KeyboardBinder` deklarieren nur noch die Rolle, die sie wirklich brauchen
  (Interface Segregation); `KeyTabImeService` implementiert alle vier. Kein
  Verhalten geändert.
- **Refactor (P3): zentraler Prefs-Zugriff** — alle 25 direkten
  `context.getSharedPreferences(...)`-Aufrufe in 12 Dateien laufen jetzt über
  `Prefs.of(context)`. Dateiname (`keytab_prefs`) und Modus (`MODE_PRIVATE`)
  sind damit an genau einer Stelle definiert und können nicht mehr driften.
- **Doku: Changelog aus dem README ausgelagert** — der Changelog lebt jetzt in
  `CHANGELOG.md`; das README verlinkt ihn nur noch. Release Notes und
  Versionshistorie haben damit einen eigenen, auffindbaren Ort.
- **CI: Doku-Drift-Gate** — neuer CI-Job `docs` führt
  `scripts/check_docs_drift.sh` aus und bricht den Build ab, wenn
  versionName/versionCode, `CHANGELOG.md`-Kopf und fastlane-Changelog
  auseinanderlaufen, oder wenn die im README genannten Testzahlen und
  Suite-Namen nicht mehr zum Code passen.
- **Versionierung: Single Source of Truth** — die Version wird ausschließlich in
  `app/build.gradle.kts` gepflegt (0.9.8 / versionCode 23); der fastlane-
  Changelog `23.txt` wurde nachgezogen (fehlte seit 0.9.7).


## 0.9.7

- **Trail (typing trail + correction trace, theme settings section „Trail")**: the last clicked letter is highlighted and fades step by step with each new input until it disappears (`trail_steps`, default 5, adjustable 3/5/7/10). Drawn as a **foreground overlay** (`Button.setForeground`), so key background, corner radius and press state stay untouched (earlier bug: `SRC_IN` tint on the shared background drawable made keys invisible). Optional (`trail`, default off), colour via the theme colour wheel.

  **Correction trace** (`trail_trace`, default off): while typing, the letters of the current word are tinted **red** when automatic correction would replace the word, **green** when the dictionary accepts it. Pure classification in `TrailLogic.classifyTypedWord` (unit-tested); the trace is a read-only indicator — it never changes what gets typed.

  **One state per letter (bug fix):** the trace is applied **atomically per word** — `TrailManager.traceWord` clears every previous trace entry first (`clearTrace`, which leaves the plain typing trail untouched), and `snap` never overwrites an existing trace entry of the same letter. Both are required because `steps`/`kinds` are keyed by **letter**, not by occurrence: typing `hauss` had `traceWord("haus")` mark all four letters red, and the second `s` tap then overwrote `kinds['s']` with `TYPED` while `steps['s'] = 0` remained — the key showed a mixed colour. Since every occurrence of a letter maps to the same key, contradictory states were visible as overlay artefacts.

  **Safety:** the trail never appears in password fields or when an app sets `IME_FLAG_NO_PERSONALIZED_LEARNING` — `TrailLogic.isTrailAllowed` enforces this in code, not via preference, because a visible trail over a `•` field would leak keystrokes (`CapsLogic` already excluded the same password variations from auto-capitalisation).
- **Config file**: `trail`, `trail_trace` and `trail_steps` are now settable from `keytab_config.txt` (see `docs/CONFIG.md`). The importer gained the missing `int` branch — without it `trail_steps` would have been silently skipped.
- **Notes editor**: highlight rules extracted into `EditorHighlightLogic` (Android-free, unit-tested); shared executors moved to `KeyTabExecutors`.
- **Trail logic extracted** into `TrailLogic` (Android-free, unit-tested) — the decay formula now has a single source, previously duplicated between `TrailManager` and `ThemePrefs.trailColorWithAlpha`.

## 0.9.6

- **Theme**: dark theme default palette darkened to a deeper gray (`kbd_bg #1a1a1a`, `key_bg #2e2e2e`, `key_pressed #444444`).
- **Theme editor**: key background is now a separate color target ("Taste") — background, key, highlight and text are adjustable independently per theme (previously the key color was coupled to the background override). Fix: the Taste color now reliably recolors **every** key (abc letters, shift/del/enter/space/symbol keys, first highlighted suggestion) — the previous `constantState` drawable matching never fired for inflated key drawables, so those keys kept their default color; matching is now by drawable type (Button + StateListDrawable) plus explicit IDs for the suggestion views.
- **Likely Highlighting (theme settings section „Likely Highlighting")**: toggleable next-key highlighting — the most likely next key glows in an adjustable highlight color; when the typed word reaches the top suggestion (likelihood reached) a satisfying pulse effect (color flash + scale pulse + haptic) fires. Pure logic in `LikelyHighlightLogic` (unit-tested).
- **Tab bar fully themed**: the abc/Notes/Files/Terminal tab cells are now filled completely (flat background, no inset border) instead of leaving a gray rim, and Material's own `colorSurface` gray on the `TabLayout` is replaced — the gray no longer shows through behind/around the tab buttons.
- **Alpha/transparency consistency**: the two top rows (tab row + suggestion strip) used to stack their own `kbd_bg` backgrounds on top of the keyboard background (and the tab bar's own surface color), so with a semi-transparent background/key color they looked solid while the letter rows looked translucent. These container backgrounds are now transparent — the themed background comes from the keyboard root only, so alpha (and a configured gradient) behaves identically in every row.
- **Refactor**: theme application moved into `ThemeApplier` (recursive view-tree recolor, gradients, per-theme colors) — `KeyTabImeService` slims down.
- **CI**: instrumented tests now run on an API-34 emulator (ReactiveCircus runner, KVM-enabled; `testInstrumentationRunner` declared).
- **Fix**: quoting bug in `install_keytab.sh` — APK staging + Shizuku/rish copy + `pm install` is now clean and robust.
- **Quality**: test counts synchronized (75 unit tests across 8 suites at the time, incl. `ThemeApplierTest` for recolor/alpha/transparency behavior), instrumented-test package assertion tolerates the `.debug` suffix.
- **Snippets tab**: new optional tab for named commands/snippets, stored in `keytab_snippets.txt` (one `name = text` per line). Tap inserts, long-press previews; ＋ / ✎ in the panel header add a snippet or edit the backing file inline.
- **Uniform panel heights**: the Terminal tab uses the editor height (its keyboard stays visible as an input row) and tab labels are single-line (`maxLines=1`, 8sp) so long names no longer wrap.
- **Housekeeping**: version bumped to 0.9.6/versionCode 22, local mislabeled `v1.2.0` tag removed, `themedv0.9.5` branch kept (unmerged).

## 0.9.5

- **Theme settings as its own page**: long-press the ☾/☀ key (or use the "Theme settings" button in the app) opens a full settings screen instead of a popup. Every change applies instantly on the next keyboard focus.
- **Color wheel**: pick any color for background, key/pressed-highlight, text — and the two gradient colors — via an HSV color wheel with brightness and alpha sliders (per dark/light theme).
- **Gradient presets**: Grau, Nacht, Ozean, Wald, Abend, Lila — plus custom colors and modes (top→bottom, inverted, radial).
- **Everything recolors**: letter keys (incl. pressed state), the tab bar (abc/Notes/Files/Term), the suggestion strip and popups follow the chosen colors.
- **Icons**: monochrome SW pairs for save (⤓), load (⤒) and clipboard (▤), grouped tightly left in the editor row.
- Default look is unchanged (= 0.9.4) until you set a color.

## 0.9.4

- Release fix: the 0.9.3 tag had been moved after the fact, so the existing GitHub release blocked the CI upload → clean version 0.9.4 (versionCode 20). Content identical to 0.9.3.

## 0.9.3

- **Keys grow for real**: dynamic key sizing now affects the layout weight (actual size + hit area), not just the visual transform. Clipping is also disabled — enlarged keys now spill past the grid cell / container edge instead of being cut off.

## 0.8.0

- **Multi-language support** (modular, latin-script only): 7 languages (de, en, es, fr, it, pt, nl), each with its own frequency corpus and language-specific accent popups. Switch in settings; engine reloads on the fly.
- **Architecture**: refactored into dedicated modules — `WordPredictionManager` (suggestion orchestration), `DynamicKeyScaler` (neighbor-aware key sizing), `LanguageModule` (registry + accents), alongside existing `SuggestionEngine`, `KeyScaleLogic`, `TextEditLogic`.
- Clipboard history moved into a picker dialog (📋 button in Notes tab); inline clipboard list removed.
- Dynamic key sizing: stepped grades (1.30×/1.15×), shrink limited to direct neighbors (0.85×/0.925×).
- Terminal: black theme + standard prompt `user@host:~$` with cd tracking.
- BadToken fix for IME dialogs (proper window token).
- Upgraded unit tests: 58 tests across `SuggestionEngine`, `TextEditLogic`, `KeyScaleLogic`, panels.
## 0.7.2

- Housekeeping: removed stray debug APK from repo root, aligned version metadata (0.7.2, versionCode 14), fastlane changelog added

## 0.7.0

- Maintenance release: word-delete logic fixed (single separator space kept, multi-space gap deleted with the word)
- Case matching: suggestions capitalize on empty input
- CI: unit tests green, actions upgraded to v5
- Release automation: tag `v*` builds a signed APK and publishes a GitHub release

## 0.6.1

- Fix: `SuggestionEngine.topBaseOrder` is now lazily initialized (fixes a crash on instantiation)
- Fix: FileManagerPanel.navigate() null-sicher (Crash bei Navigation behoben)
- Fix: TerminalPanel Shell-Fallback für verschiedene Android-Geräte
- Fix: `SuggestionEngine` thread-safe (`ConcurrentHashMap`)
- Fix: KeyTabImeService.onDestroy() für korrektes Cleanup
- Feature: Build-Skripte (build_keytab.sh, install_keytab.sh)

## 0.6.0

- Word prediction with unigram frequencies, bigrams, user dictionary, prefix autocomplete, fuzzy correction
- Dynamic key sizing driven by suggestion scores
- Optional toggles for suggestions and dynamic keys in settings
- Notes tab merges editor and clipboard; folder browser on load (IME dialog fix)
- Terminal tab label spelled out; accelerating backspace
- Theme fix: sun symbol now visible in light mode
- 19 unit tests for SuggestionEngine

## 0.5.0

- Optional terminal tab with interactive shell, toggleable in settings
- Enter key keeps constant size and position across all tabs
- File manager state (current dir + back-stack) persisted across restarts
- formatSize supports GB and TB
- Long-Press popup extracted into LetterPopup class

## 0.4.0

- Long-press popups with punctuation on letter keys
- Drag selection in popup (Gboard-style)
- Dark/light toggle, persisted
- Number row toggle in settings
- TAB key and dot button in bottom row
- Files tab: long-press context menu

## 0.3.0

- Long-press Backspace deletes whole word
- File I/O runs asynchronously
- Long-press popup uses theme colors
- Text logic extracted into testable class

## 0.2.0

- Character layer (?123) with toggle
- File manager shows files with sizes
- Storage permission handling

## 0.1.0

- Initial MVP: keyboard with TAB key and tabbed file manager

