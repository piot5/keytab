# KeyTab

[![CI](https://github.com/piot5/keytab/actions/workflows/ci.yml/badge.svg)](https://github.com/piot5/keytab/actions/workflows/ci.yml)
[![Release](https://github.com/piot5/keytab/actions/workflows/release.yml/badge.svg)](https://github.com/piot5/keytab/actions/workflows/release.yml)
[![Latest release](https://img.shields.io/github/v/release/piot5/keytab?include_prereleases&label=release)](https://github.com/piot5/keytab/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/piot5/keytab/total?label=downloads)](https://github.com/piot5/keytab/releases)
[![Stars](https://img.shields.io/github/stars/piot5/keytab?logo=github&labelColor=gray)](https://github.com/piot5/keytab/stargazers)
[![Issues](https://img.shields.io/github/issues/piot5/keytab)](https://github.com/piot5/keytab/issues)
[![PRs](https://img.shields.io/github/issues-pr/piot5/keytab)](https://github.com/piot5/keytab/pulls)
[![Last commit](https://img.shields.io/github/last-commit/piot5/keytab/main?label=last%20commit)](https://github.com/piot5/keytab/commits/main)
![Platform](https://img.shields.io/badge/platform-Android%207%2B%20(API%2024%2B)-3ddc84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/language-100%25%20Kotlin-7f52ff?logo=kotlin&logoColor=white)
[![License](https://img.shields.io/github/license/piot5/keytab)](LICENSE)

Keyboard app (IME) with a tabbed file manager and word prediction. 100% Kotlin, builds with Gradle.

## Download

Grab the latest signed APK from the [GitHub Releases](https://github.com/piot5/keytab/releases/latest):

[![Download APK](https://img.shields.io/github/v/release/piot5/keytab?include_prereleases&label=download%20APK&color=3ddc84)](https://github.com/piot5/keytab/releases/latest)

Every tagged release (`v*`) is built and published automatically by CI:

| File | Purpose |
|---|---|
| `KeyTab-<version>.apk` | Signed release APK — install this |

Install: open the APK in a file manager (allow "install unknown apps"), then enable KeyTab in *Settings → System → Languages & input → On-screen keyboard* and switch to it in any text field.

## What it does

**File manager in the keyboard** -- browse folders, switch tabs, navigate with back-stack and parent-navigation. Tapping a file inserts its path; in the app it opens via VIEW-Intent. Each tab remembers its own directory. Listing runs asynchronously so large folders don't freeze the UI.

**Word prediction** -- offline n-gram model (FrequencyWords, CC-BY-SA-4.0) with bigrams for next-word prediction, a user dictionary that learns as you type, prefix autocomplete, Damerau-Levenshtein fuzzy correction, and case matching. The top suggestion is rendered 2x wider with a green accent bar for easier tapping. Toggleable in settings.

**Dynamic key sizing** -- likely-next keys scale up to 1.30× (stepped grades 1.30×/1.15×), unlikely ones shrink down to 0.85× — but only in the direct neighborhood of enlarged keys, driven by the current suggestion scores. Toggleable in settings.

**Multi-language** -- modular latin-script support (7 languages: de, en, es, fr, it, pt, nl). Each language ships its own frequency corpus and language-specific accent popups (long-press). Switch instantly in settings; the suggestion engine reloads on the fly.

**Notes tab** -- editor and clipboard merged into one tab. The "Load" button opens a folder browser (IME dialog with proper window token). The clipboard holds up to 50 persistent entries; the clipboard button opens a picker dialog to insert any entry directly into the target field.

**Snippets tab** -- named commands / snippets as their own tab, backed by a plain-text file (`keytab_snippets.txt`, editable in the Files tab). Format is one `name = text` per line (`\n` is expanded to a newline, `#` starts a comment). Tap inserts the text into the focused field, long-press previews it; the ＋ / ✎ buttons in the panel header add a snippet or edit the backing file inline. Toggleable in settings.

**Likely highlighting** -- the most likely next key (from the current suggestion scores) glows in an adjustable highlight colour; when the typed word reaches the top suggestion, a pulse effect (colour flash + scale pulse + haptic) fires. Pure logic in `LikelyHighlightLogic`, unit-tested.

**Typing trail + correction trace** -- optional (`trail`, default off). The last key you hit stays tinted and fades step by step (3/5/7/10 steps) as you keep typing, drawn as a *foreground overlay* so key background, corner radius and press state stay untouched. With the correction trace on top (`trail_trace`), the letters of the current word turn **red** when autocorrect would replace it and **green** when the dictionary accepts it — a live, read-only view of what the engine is about to do, which never changes what you type. Pure logic in `TrailLogic`, unit-tested (18 tests).

**Never in password fields** -- the trail is suppressed in password fields and whenever an app sets `IME_FLAG_NO_PERSONALIZED_LEARNING`. This is enforced in code (`TrailLogic.isTrailAllowed`), not by preference: a visible trail over a `•` field would leak keystrokes through a visual side channel. Covered by four unit tests so the rule cannot be removed unnoticed.

**Theme customization** -- long-press the tab/☾ (or ☀) key opens a dedicated *Theme settings* page (also reachable from the app settings): choose dark or light, pick a gradient preset or build your own (color 1 → color 2, top→bottom / inverted / radial), and set the background, key, highlight and text colors **separately and per theme** with a live **color wheel** (hue/saturation), brightness and alpha sliders. Keys, tabs (abc/Notes/Files/Terminal/Snippets), popups and the suggestion bar all recolor while the default look stays identical to stock until you set something. The built-in dark theme uses a darker gray palette (`#1a1a1a` background, `#2e2e2e` keys).

**Terminal tab** -- optional command runner in the keyboard, togglable in settings. Black background, standard prompt `user@host:~$`, cd tracking. **Scope:** it runs the Android system shell (`/system/bin/sh`) inside the app sandbox — good for `ls`/`pwd`/`cat`/`wc`, but it has **no PTY, no userland and no access to Termux or a proot install**. For real work use Termux and KeyTab as the input method; the tab is a convenience, not the workspace (see `docs/REFACTORING_PLAN.md` §4.3).

**Keyboard** -- full InputMethodService with a TAB key that sends `KEYCODE_TAB` (so Tab-completion, `vim` and `nano` work in Termux/SSH), shift/caps-lock, long-press popups for umlauts/special characters, accelerating backspace on long-press (250ms down to 30ms).

**Privacy** -- no network permission, no data collection. All data stays on the device.

## Architecture

`KeyTabImeService` is the keyboard core (283 lines of orchestration). Every feature lives in its own class —
panels for UI, controllers for stateful wiring, pure modules for logic (Android-free, unit-testable).

### Panels (UI features)

All five panels are decoupled from the service by construction — they receive narrow dependencies
(`Context`, `Executor`, `Handler`, callbacks) and **none of them references `KeyTabImeService`**,
which makes them directly unit-testable with Robolectric and no service mock.

| Class | Responsibility |
|---|---|
| `FileManagerPanel` | File manager (navigation, async listing) |
| `EditorPanel` | Notes editor with save/load and folder browser |
| `ClipboardPanel` | Clipboard history (picker dialog) |
| `TerminalPanel` | Command runner (Android shell, no PTY) |
| `SnippetPanel` | Named snippets/commands (own tab, inline editor) |

### Controllers (stateful logic, wired via `KeyboardHost`)

| Class | Responsibility |
|---|---|
| `KeyboardBinder` | Touch / long-press handling, backspace repeat |
| `ShiftController` | Shift / caps-lock state machine |
| `TabController` | Tab switching, uniform panel heights, label sizing |
| `SuggestionController` | Suggestion bar wiring |
| `ThemeController` | Theme long-press actions |
| `TextCommitController` | `commitText` / `commitToApp` routing |
| `KeyboardViewFactory` | Builds the keyboard view tree (`onCreateInputView`) |

### Pure logic (Android-free, unit-tested)

| Class | Responsibility |
|---|---|
| `SuggestionEngine` | Word prediction (n-gram, fuzzy correction, user dict) |
| `TextEditLogic` | Text / cursor / word-delete logic |
| `KeyScaleLogic` | Scaling math (stepped grades 1.30×/1.15×, shrink 0.85×/0.925×) |
| `CapsLogic` | Auto-capitalisation decision |
| `RepeatScheduler` | Backspace repeat timing |
| `LikelyHighlightLogic` | Likely-next-key scoring and reached detection |
| `TrailLogic` | Typing-trail decay curve, correction-trace classification, password-field guard |
| `PanelHeights` | Uniform panel height computation |
| `LiftSpan` | Text span for the key-lift effect |
| `EditorHighlightLogic` | Syntax/highlight rule matching for the notes editor |
| `KeyTabExecutors` | Shared background executor + main handler |
| `ThemePrefs` | Theme keys, presets, rebuild version counter |
| `KeyTabConfig` | Config / constants |
| `InputTargets` | Editor / Terminal input interfaces |
| `KeyboardHost` | Service interface consumed by the controllers |
| `Prefs` | Central preference keys |

### Support

| Class | Responsibility |
|---|---|
| `WordPredictionManager` | Suggestion orchestration (engine load, bar render, learn, language reload) |
| `TrailManager` | Renders the typing trail / correction trace as key foreground overlays |
| `DynamicKeyScaler` | Maps suggestion scores → key sizes via neighbour-aware scaling |
| `LanguageModule` | Multi-language registry (7 latin scripts) + per-language accents |
| `FileManagerModel` | File-manager state (current dir, back-stack) — pure and testable |
| `LetterPopup` | Long-press characters + drag selection |
| `BackgroundImage` | Keyboard background image loading (fit/cover/stretch) |
| `SettingsConfig` | Imports/exports `keytab_config.txt` (fills missing keys, fingerprint diffing) |
| `KeyAnimations` | Key press / scale animation helpers |
| `ThemedAdapter` | RecyclerView adapter with per-theme colours |
| `ColorWheelView` | HSV color wheel (hue/saturation) + brightness / alpha |
| `ThemeApplier` | Recursive theme application on the keyboard view tree |
| `ThemeSettingsActivity` | Theme settings page, split into section classes (Top / Color / Gradient / Background / Preview / LikelyHighlight / Trail) |
| `MainActivity, file/FileManagerFragment` | App settings, in-app file manager |

All panels share a background executor for file I/O and a main handler for UI updates; stale results are discarded on navigation.
## Tests

Unit tests run via `./gradlew :app:testDebugUnitTest` (Robolectric for Android-dependent panels). The pure-logic classes (`SuggestionEngine`, `TextEditLogic`, `KeyScaleLogic`, `CapsLogic`, `LiftSpan`, `LikelyHighlightLogic`, `TrailLogic`, `PanelHeights`) are fully Android-free and fast.

**164 unit tests in 19 suites, 0 failures** (verified 2026-09-17, `assembleDebug` + `testDebugUnitTest` both green):

| Suite | Tests | Kind |
|---|---:|---|
| `TextEditLogicTest` | 20 | pure |
| `SuggestionEngineTest` | 19 | pure |
| `TrailLogicTest` | 18 | pure |
| `FileManagerModelTest` | 11 | pure |
| `EditorHighlightLogicTest` | 11 | pure |
| `KeyScaleLogicTest` | 10 | pure |
| `KeyTabConfigTest` | 8 | pure |
| `InputRouterTest` | 8 | Robolectric |
| `EditorPanelTest` | 8 | Robolectric |
| `ThemeApplierTest` | 7 | Robolectric |
| `SettingsConfigTest` | 7 | Robolectric |
| `ShiftControllerTest` | 6 | pure |
| `CapsLogicTest` | 6 | pure |
| `SettingsRegressionTest` | 5 | Robolectric |
| `LikelyHighlightLogicTest` | 5 | pure |
| `ClipboardPanelTest` | 5 | Robolectric |
| `LiftSpanTest` | 4 | pure |
| `KeyAnimationsTest` | 4 | Robolectric |
| `PanelHeightsTest` | 2 | pure |

Test code is 2,350 lines in 19 files against 7,020 lines of main code (51 files) — a **33.5 % test-to-main ratio**. Line coverage measured with Kover is **33.5 %** (`LINE` 1147/3428), branch coverage **31.8 %** (`BRANCH` 784/2462); the CI gate is 20 %. Coverage is concentrated in the Android-free logic (`com.piotv.keytab.ime`: 41.3 %), while the theme UI sections (`sections`: 9.9 %) and the in-app file manager (`file`: 0 %) are untested — see [Known gaps](#known-gaps). Test names are written as specifications in German (e.g. `Doppel-Tap aktiviert CapsLock`). Instrumented tests (`app/src/androidTest`, 44 lines) run in CI on an API-34 emulator via `./gradlew :app:connectedDebugAndroidTest`.

```bash
# Run all unit tests
sh ./gradlew :app:testDebugUnitTest

# Run only the Android-free logic tests (fast, no Robolectric)
sh ./gradlew :app:testDebugUnitTest --tests "com.piotv.keytab.ime.SuggestionEngineTest" --tests "com.piotv.keytab.ime.TextEditLogicTest" --tests "com.piotv.keytab.ime.KeyScaleLogicTest" --tests "com.piotv.keytab.ime.KeyTabConfigTest"

# Run a single test class
sh ./gradlew :app:testDebugUnitTest --tests "com.piotv.keytab.ime.SuggestionEngineTest"
```

## Known gaps

Documented honestly rather than implied away — these are the things that are **not** verified or covered:

| Gap | Detail |
|---|---|
| **Trail performance not measured** | The correction trace classifies the typed word against the engine on **every keystroke** (`TrailLogic.classifyTypedWord` → `SuggestionEngine.autoCorrect`, a Damerau-Levenshtein pass over the char index). No frame timing, no profiling, no benchmark exists. Logic is unit-tested; smoothness on a real display is **not** verified. Mitigation if it stutters: restrict the trace to `knowsWord` and check `autoCorrect` only on word completion. |
| **Trail visuals not screenshot-verified** | The regression fix for contradictory trace states (see 0.9.7) is proven at the **state level** by unit tests — no screenshot or instrumented test asserts the rendered colours. The red/green contrast against each custom theme palette has not been measured. |
| **Theme UI sections untested** | `com.piotv.keytab.sections` sits at **9.9 %** line coverage; the colour wheel, gradient editor and background-image picker have no automated interaction tests. |
| **In-app file manager untested** | `com.piotv.keytab.file` is at **0 %** line coverage — the fragment depends on `RecyclerView`/`DiffUtil` and has no Robolectric suite. |
| **English locale incomplete** | `values-en` has 92 strings against 163 in the default (German) file; the rest fall back to German in an English-locale device. |
| **Terminal has no PTY** | By design — see the Terminal description above. It is the Android system shell in the app sandbox, not a Termux replacement. |
| **Instrumented tests are thin** | 2 tests in 44 lines. They run in CI on an API-34 emulator but do not exercise the keyboard UI. |

## Build

KeyTab builds with Gradle on-device (Android 7+, API 24+). No Android Studio needed.

```bash
# Debug build
bash build_keytab.sh debug

# Release build (signed, with R8 minification)
bash build_keytab.sh release

# Install via Shizuku/rish
bash install_keytab.sh
```

### Building on Android (Termux/proot)

The bundled `gradlew` works in Ubuntu-proot on Android. Two caveats:

- The Gradle wrapper JAR is re-downloaded on first run (TLS can break in proot). If that fails, run Gradle once with a working network connection.
- Building directly on the SD card is unreliable (the Gradle daemon gets killed, FUSE file locks cause issues). Copy the project to internal storage and build there:
```bash
cp -r /path/to/keytab ~/build/
cd ~/build/keytab

# gradlew has no exec bit on FAT -- call it with sh
sh ./gradlew :app:assembleDebug --offline

cp app/build/outputs/apk/debug/app-debug.apk /path/to/keytab/app-debug-new.apk
```

Long-running builds should be started with `setsid … &` to avoid the terminal timeout killing the Gradle process. `--offline` saves time when no new dependencies are needed.

## Installation (without a PC, via Shizuku/rish)

Requires a running Shizuku server and the `~/bin/rsh` wrapper.

```bash
# Copy APK to /data/local/tmp (the shell cannot see /mnt/... paths)
sh ~/bin/rsh 'cp /sdcard/path/to/keytab/app-debug-new.apk /data/local/tmp/keytab.apk'

# Install (use -r for update)
sh ~/bin/rsh 'pm install -r /data/local/tmp/keytab.apk'
# -> "Success"

# Verify
sh ~/bin/rsh 'rm -f /data/local/tmp/keytab.apk; dumpsys package com.piotv.keytab | grep lastUpdateTime'
```

Or copy the APK, open it in a file manager, and confirm the package installer dialog. Then enable the keyboard in *Settings -> System -> Languages & input -> On-screen keyboard* and switch to it in any text field.

## Project structure

```
app/src/main/java/com/piotv/keytab/            # 51 Kotlin files, 7,020 lines
├── Prefs.kt                       # Central preference keys
├── MainActivity.kt                # Settings: enable keyboard, theme, language, toggles
├── ThemeSettingsActivity.kt       # Theme settings: color wheel, gradients, per-theme colors
├── ColorWheelView.kt              # HSV color wheel widget
├── file/FileManagerFragment.kt    # File manager in the app (with DiffUtil)
├── sections/                      # Theme settings page, split into section classes
│   ├── TopSection.kt              #   theme choice + gradient presets
│   ├── ColorSection.kt            #   color wheel + sliders
│   ├── GradientSection.kt         #   gradient preview
│   ├── BackgroundSection.kt       #   background image (fit/cover/stretch)
│   ├── LikelyHighlightSection.kt  #   likely-highlighting toggles
│   ├── TrailSection.kt            #   typing trail: on/off, steps, correction trace
│   └── PreviewSection.kt          #   live preview
└── ime/
    ├── KeyTabImeService.kt   # Keyboard core / orchestration (283 lines)
    ├── KeyboardHost.kt       # Interface consumed by the controllers
    ├── KeyboardViewFactory.kt # Builds the keyboard view tree
    ├── KeyboardBinder.kt     # Touch / long-press, backspace repeat
    ├── ShiftController.kt    # Shift / caps-lock state machine
    ├── TabController.kt      # Tab switching + uniform panel heights
    ├── SuggestionController.kt # Suggestion bar wiring
    ├── ThemeController.kt    # Theme long-press actions
    ├── TextCommitController.kt # commitText / commitToApp routing
    ├── ThemePrefs.kt         # Theme keys/presets + rebuild version
    ├── ThemeApplier.kt       # Recursive theme application on the view tree
    ├── BackgroundImage.kt    # Keyboard background image decoding
    ├── SettingsConfig.kt     # keytab_config.txt import/export
    ├── FileManagerPanel.kt   # IME file manager
    ├── EditorPanel.kt        # Notes editor with folder browser
    ├── ClipboardPanel.kt     # Clipboard history (picker dialog)
    ├── TerminalPanel.kt      # Command runner (Android sh, no PTY)
    ├── SnippetPanel.kt       # Snippets/commands tab with inline editor
    ├── WordPredictionManager.kt # Suggestion orchestration
    ├── DynamicKeyScaler.kt   # Maps suggestion scores to key sizes
    ├── SuggestionEngine.kt   # Pure word prediction (offline, unit-tested)
    ├── LanguageModule.kt     # Multi-language registry (7 latin scripts)
    ├── KeyScaleLogic.kt      # Pure scaling math (stepped grades)
    ├── TextEditLogic.kt      # Pure, testable text logic
    ├── EditorHighlightLogic.kt # Highlight rules for the notes editor
    ├── CapsLogic.kt          # Auto-capitalisation decision
    ├── RepeatScheduler.kt    # Backspace repeat timing
    ├── LikelyHighlightLogic.kt # Likely-next-key scoring
    ├── TrailLogic.kt         # Trail decay + correction-trace classification
    ├── TrailManager.kt       # Renders the trail as key foreground overlays
    ├── PanelHeights.kt       # Uniform panel heights
    ├── FileManagerModel.kt   # File-manager state (pure)
    ├── LetterPopup.kt        # Long-press characters + drag selection
    ├── KeyAnimations.kt      # Key press/scale animation helpers
    ├── ThemedAdapter.kt      # RecyclerView adapter with per-theme colors
    ├── KeyTabExecutors.kt    # Shared executor + main handler
    └── …                     # InputTargets, LiftSpan, KeyTabConfig

app/src/test/java/com/piotv/keytab/ime/        # 19 test classes, 164 tests, 2,350 lines
app/src/androidTest/                           # 2 instrumented tests (CI: API 34 emulator)
app/src/main/res/values/strings.xml            # 163 strings (default = German)
app/src/main/res/values-en/                    # English locale (92 strings — partial, falls back to German)
app/src/main/res/values-night/                 # Night-mode resource qualifiers
app/src/main/assets/
├── de_freq_top6000.txt              # corpus (CC-BY-SA-4.0)
├── en_freq_top6000.txt              # corpus (CC-BY-SA-4.0)
├── es_freq_top6000.txt              # corpus (CC-BY-SA-4.0)
├── fr_freq_top6000.txt              # corpus (CC-BY-SA-4.0)
├── it_freq_top6000.txt              # corpus (CC-BY-SA-4.0)
├── pt_freq_top6000.txt              # corpus (CC-BY-SA-4.0)
└── nl_freq_top6000.txt              # Dutch corpus (CC-BY-SA-4.0)
```
## Changelog

### 0.9.7

- **Trail (typing trail + correction trace, theme settings section „Trail")**: the last clicked letter is highlighted and fades step by step with each new input until it disappears (`trail_steps`, default 5, adjustable 3/5/7/10). Drawn as a **foreground overlay** (`Button.setForeground`), so key background, corner radius and press state stay untouched (earlier bug: `SRC_IN` tint on the shared background drawable made keys invisible). Optional (`trail`, default off), colour via the theme colour wheel.

  **Correction trace** (`trail_trace`, default off): while typing, the letters of the current word are tinted **red** when automatic correction would replace the word, **green** when the dictionary accepts it. Pure classification in `TrailLogic.classifyTypedWord` (unit-tested); the trace is a read-only indicator — it never changes what gets typed.

  **One state per letter (bug fix):** the trace is applied **atomically per word** — `TrailManager.traceWord` clears every previous trace entry first (`clearTrace`, which leaves the plain typing trail untouched), and `snap` never overwrites an existing trace entry of the same letter. Both are required because `steps`/`kinds` are keyed by **letter**, not by occurrence: typing `hauss` had `traceWord("haus")` mark all four letters red, and the second `s` tap then overwrote `kinds['s']` with `TYPED` while `steps['s'] = 0` remained — the key showed a mixed colour. Since every occurrence of a letter maps to the same key, contradictory states were visible as overlay artefacts.

  **Safety:** the trail never appears in password fields or when an app sets `IME_FLAG_NO_PERSONALIZED_LEARNING` — `TrailLogic.isTrailAllowed` enforces this in code, not via preference, because a visible trail over a `•` field would leak keystrokes (`CapsLogic` already excluded the same password variations from auto-capitalisation).
- **Config file**: `trail`, `trail_trace` and `trail_steps` are now settable from `keytab_config.txt` (see `docs/CONFIG.md`). The importer gained the missing `int` branch — without it `trail_steps` would have been silently skipped.
- **Notes editor**: highlight rules extracted into `EditorHighlightLogic` (Android-free, unit-tested); shared executors moved to `KeyTabExecutors`.
- **Trail logic extracted** into `TrailLogic` (Android-free, unit-tested) — the decay formula now has a single source, previously duplicated between `TrailManager` and `ThemePrefs.trailColorWithAlpha`.

### 0.9.6

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

### 0.9.5

- **Theme settings as its own page**: long-press the ☾/☀ key (or use the "Theme settings" button in the app) opens a full settings screen instead of a popup. Every change applies instantly on the next keyboard focus.
- **Color wheel**: pick any color for background, key/pressed-highlight, text — and the two gradient colors — via an HSV color wheel with brightness and alpha sliders (per dark/light theme).
- **Gradient presets**: Grau, Nacht, Ozean, Wald, Abend, Lila — plus custom colors and modes (top→bottom, inverted, radial).
- **Everything recolors**: letter keys (incl. pressed state), the tab bar (abc/Notes/Files/Term), the suggestion strip and popups follow the chosen colors.
- **Icons**: monochrome SW pairs for save (⤓), load (⤒) and clipboard (▤), grouped tightly left in the editor row.
- Default look is unchanged (= 0.9.4) until you set a color.

### 0.9.4

- Release fix: the 0.9.3 tag had been moved after the fact, so the existing GitHub release blocked the CI upload → clean version 0.9.4 (versionCode 20). Content identical to 0.9.3.

### 0.9.3

- **Keys grow for real**: dynamic key sizing now affects the layout weight (actual size + hit area), not just the visual transform. Clipping is also disabled — enlarged keys now spill past the grid cell / container edge instead of being cut off.

### 0.8.0

- **Multi-language support** (modular, latin-script only): 7 languages (de, en, es, fr, it, pt, nl), each with its own frequency corpus and language-specific accent popups. Switch in settings; engine reloads on the fly.
- **Architecture**: refactored into dedicated modules — `WordPredictionManager` (suggestion orchestration), `DynamicKeyScaler` (neighbor-aware key sizing), `LanguageModule` (registry + accents), alongside existing `SuggestionEngine`, `KeyScaleLogic`, `TextEditLogic`.
- Clipboard history moved into a picker dialog (📋 button in Notes tab); inline clipboard list removed.
- Dynamic key sizing: stepped grades (1.30×/1.15×), shrink limited to direct neighbors (0.85×/0.925×).
- Terminal: black theme + standard prompt `user@host:~$` with cd tracking.
- BadToken fix for IME dialogs (proper window token).
- Upgraded unit tests: 58 tests across `SuggestionEngine`, `TextEditLogic`, `KeyScaleLogic`, panels.
### 0.7.2

- Housekeeping: removed stray debug APK from repo root, aligned version metadata (0.7.2, versionCode 14), fastlane changelog added

### 0.7.0

- Maintenance release: word-delete logic fixed (single separator space kept, multi-space gap deleted with the word)
- Case matching: suggestions capitalize on empty input
- CI: unit tests green, actions upgraded to v5
- Release automation: tag `v*` builds a signed APK and publishes a GitHub release

### 0.6.1

- Fix: `SuggestionEngine.topBaseOrder` is now lazily initialized (fixes a crash on instantiation)
- Fix: FileManagerPanel.navigate() null-sicher (Crash bei Navigation behoben)
- Fix: TerminalPanel Shell-Fallback für verschiedene Android-Geräte
- Fix: `SuggestionEngine` thread-safe (`ConcurrentHashMap`)
- Fix: KeyTabImeService.onDestroy() für korrektes Cleanup
- Feature: Build-Skripte (build_keytab.sh, install_keytab.sh)

### 0.6.0

- Word prediction with unigram frequencies, bigrams, user dictionary, prefix autocomplete, fuzzy correction
- Dynamic key sizing driven by suggestion scores
- Optional toggles for suggestions and dynamic keys in settings
- Notes tab merges editor and clipboard; folder browser on load (IME dialog fix)
- Terminal tab label spelled out; accelerating backspace
- Theme fix: sun symbol now visible in light mode
- 19 unit tests for SuggestionEngine

### 0.5.0

- Optional terminal tab with interactive shell, toggleable in settings
- Enter key keeps constant size and position across all tabs
- File manager state (current dir + back-stack) persisted across restarts
- formatSize supports GB and TB
- Long-Press popup extracted into LetterPopup class

### 0.4.0

- Long-press popups with punctuation on letter keys
- Drag selection in popup (Gboard-style)
- Dark/light toggle, persisted
- Number row toggle in settings
- TAB key and dot button in bottom row
- Files tab: long-press context menu

### 0.3.0

- Long-press Backspace deletes whole word
- File I/O runs asynchronously
- Long-press popup uses theme colors
- Text logic extracted into testable class

### 0.2.0

- Character layer (?123) with toggle
- File manager shows files with sizes
- Storage permission handling

### 0.1.0

- Initial MVP: keyboard with TAB key and tabbed file manager

## Roadmap

Planned, in priority order (see `docs/REFACTORING_PLAN.md` for the full audit and rationale):

1. ~~**Coverage gate** (Kover) in CI~~ — **done**: `:app:coverageGate` with a 20 % line threshold,
   baseline **33.5 %** (`LINE` 1147/3428, measured 2026-09-17); CI job `coverage`
   uploads the report. Next: more Robolectric panel tests — the theme sections (9.9 %)
   and the in-app file manager (0 %) are the weakest areas.
2. ~~**Split the 697-line `keyboard_view.xml`**~~ — **done**: rows now live in
   `panel_keyboard_letters.xml` (106 lines) + `panel_keyboard_symbols.xml` (61 lines),
   embedded via `<include>` (head file 579 lines).
3. ~~**Replace `MANAGE_EXTERNAL_STORAGE`**~~ — **done** (permission + button + intent removed;
   `READ_MEDIA_*` + app dirs remain) → Play-Store eligible permission-wise.
4. ~~**Editor: line numbers**~~ and **heuristic syntax highlighting** — **done**
   (`EditorHighlightLogic` + gutter in `EditorPanel`).
5. **Finish the coroutine migration** — threading is centralised in `KeyTabExecutors`, the two
   remaining `Handler`s are shared; true coroutines are still open.
6. **Optional: PTY for the terminal tab** — it currently pipes stdin/stdout without a pseudo-terminal, so interactive TUI programs and ANSI colours cannot work. A PTY would turn the tab into a real terminal, but is a large change for a convenience feature; documenting the limitation was preferred (see §4.3).

Explicitly *not* planned: cloud sync, glide typing, 100+ languages — those are Gboard dimensions that cannot be won here.

**Also not planned: an extra-keys row** (Esc/Ctrl/arrows). Termux already ships one (`extra-keys` in `termux.properties`), so a second row would duplicate it and eat key height.

## Contributing

Issues and pull requests are welcome. Before opening a PR:

```bash
sh ./gradlew :app:testDebugUnitTest --offline   # 146 tests must stay green
bash build_keytab.sh debug                      # must build
```

Conventions:

- 100 % Kotlin, no new dependencies without a clear reason.
- New logic goes into an Android-free, unit-tested class (a `*Logic` / model class); keep the panels and the service thin.
- Unit-test names are written as specifications (German is the established convention under `app/src/test`).
- Do not add an `INTERNET` permission — "no network, no data collection" is a core promise of the app.

## License

MIT -- see [LICENSE](LICENSE)
