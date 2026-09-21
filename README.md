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

Mobile IDE shaped like a keyboard (IME): a tabbed file manager, an editor/clipboard, snippets and a terminal tab, with offline word prediction. 100% Kotlin, builds with Gradle.

## Download

Grab the latest signed APK from the [GitHub Releases](https://github.com/piot5/keytab/releases/latest):

[![Download APK](https://img.shields.io/github/v/release/piot5/keytab?include_prereleases&label=download%20APK&color=3ddc84)](https://github.com/piot5/keytab/releases/latest)

Every tagged release (`v*`) is built and published automatically by CI:

| File | Purpose |
|---|---|
| `KeyTab-<version>.apk` | Signed release APK — install this |

Install: open the APK in a file manager (allow "install unknown apps"), then enable KeyTab in *Settings → System → Languages & input → On-screen keyboard* and switch to it in any text field.

## What it does

KeyTab is a **mobile IDE built as an IME**: every feature lives in its own tab on the keyboard strip, so the keyboard itself is the launch point for a *local‑first* dev environment — open the file manager, editor, snippets or terminal tab directly, and what you type still lands in the app field you're in. This is **deliberately not an AI keyboard**: word prediction and fuzzy correction run fully on‑device on a small n‑gram model plus Damerau‑Levenshtein heuristics (no network, no cloud, no data collection), and a 50 ms‑per‑keystroke performance budget keeps prediction off the main thread. If you work from Termux/Ubuntu‑proot, the keyboard becomes your shell launcher, file browser and snippet library in one input view.

**File manager in the keyboard** -- browse folders, switch tabs, navigate with back-stack and parent-navigation. Tapping a file inserts its path; in the app it opens via VIEW-Intent. Each tab remembers its own directory. Listing runs asynchronously so large folders don't freeze the UI.

**Word prediction** -- offline n-gram model (FrequencyWords, CC-BY-SA-4.0) with bigrams for next-word prediction, a user dictionary that learns as you type, prefix autocomplete, Damerau-Levenshtein fuzzy correction, and case matching. The top suggestion is rendered 2x wider with a green accent bar for easier tapping. Toggleable in settings. **Swipe typing** (v0.11, optional): glide over the keys and the route is scored against the same offline engine — clear winners auto-commit, otherwise the top candidates appear in the suggestion bar. **Circuit preview** (v0.11, optional): the likely next keys of the word you're typing are shown as a connected path on the keyboard, so your eye can follow the "current" before you tap. Both default off and are suppressed in password fields. See [`docs/SWIPE_PLAN.md`](docs/SWIPE_PLAN.md). **Snippet suggestions on sentence start**: when there are no word predictions to show and the cursor is at the beginning of a new sentence (empty field, or text ending in `. ! ?` followed by space/newline, with no word currently being typed), the suggestion bar shows your last 3 used snippets as tappable chips instead of the generic top-3 words — so frequently-inserted snippets are one tap away. This also triggers after deleting a whole word with backspace (the bar re-evaluates the empty state). Using a snippet records it as most-recent; typing a new character returns the normal prediction bar. Fully offline, stored in `MODE_PRIVATE` prefs (no extra permission). **Optional emoji suggestions** (settings toggle, **off by default**): a small built-in keyword→emoji catalog (de/en, offline) appends up to 2 thematic emojis behind the word suggestions — word suggestions always keep at least one slot.

**Dynamic key sizing** -- likely-next keys scale up to 1.30× (stepped grades 1.30×/1.15×), unlikely ones shrink down to 0.85× — but only in the direct neighborhood of enlarged keys, driven by the current suggestion scores. Toggleable in settings.

**Multi-language** -- modular latin-script support (7 languages: de, en, es, fr, it, pt, nl). Each language ships its own frequency corpus and language-specific accent popups (long-press). Switch instantly in settings; the suggestion engine reloads on the fly.

**Notes tab** -- editor and clipboard merged into one tab. The "Load" button opens a folder browser (IME dialog with proper window token). The clipboard holds up to 50 persistent entries; the clipboard button opens a picker dialog to insert any entry directly into the target field.

**Snippets tab** -- named commands / snippets as their own tab, backed by a plain-text file (`keytab_snippets.txt`, editable in the Files tab). Format is one `name = text` per line (`\n` is expanded to a newline, `#` starts a comment). Tap inserts the text into the focused field, long-press previews it; the ＋ / ✎ buttons in the panel header add a snippet or edit the backing file inline. Toggleable in settings.

**Likely highlighting** -- the most likely next key (from the current suggestion scores) glows in an adjustable highlight colour; when the typed word reaches the top suggestion, a pulse effect (colour flash + scale pulse + haptic) fires. Pure logic in `LikelyHighlightLogic`, unit-tested.

**Typing trail + correction trace** -- optional (`trail`, default off). The last key you hit stays tinted and fades step by step (3/5/7/10 steps) as you keep typing, drawn as a *foreground overlay* so key background, corner radius and press state stay untouched. With the correction trace on top (`trail_trace`), the letters of the current word turn **red** when autocorrect would replace it and **green** when the dictionary accepts it — a live, read-only view of what the engine is about to do, which never changes what you type. Pure logic in `TrailLogic`, unit-tested (18 tests).

**Never in password fields** -- the trail is suppressed in password fields and whenever an app sets `IME_FLAG_NO_PERSONALIZED_LEARNING`. This is enforced in code (`TrailLogic.isTrailAllowed`), not by preference: a visible trail over a `•` field would leak keystrokes through a visual side channel. Covered by four unit tests so the rule cannot be removed unnoticed.

**Theme customization** -- long-press the tab/☾ (or ☀) key opens a dedicated *Theme settings* page (also reachable from the app settings): choose dark or light, pick a gradient preset or build your own (color 1 → color 2, top→bottom / inverted / radial), and set the background, key, highlight and text colors **separately and per theme** with a live **color wheel** (hue/saturation), brightness and alpha sliders. Keys, tabs (abc/Notes/Files/Terminal/Snippets), popups and the suggestion bar all recolor while the default look stays identical to stock until you set something. The built-in dark theme uses a darker gray palette (`#1a1a1a` background, `#2e2e2e` keys).

**Terminal tab** -- optional command runner in the keyboard, togglable in settings. Black background, standard prompt `user@host:~$`, cd tracking. **Scope:** it runs the Android system shell (`/system/bin/sh`) inside the app sandbox — good for `ls`/`pwd`/`cat`/`wc`, but it has **no PTY, no userland and no access to Termux or a proot install**. For real work use Termux and KeyTab as the input method; the tab is a convenience, not the workspace (see `docs/REFACTORING_HISTORY.md` §4.3).

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

**343 unit tests in 36 suites, 0 failures** (verified 2026-09-21, `assembleDebug` + `testDebugUnitTest` both green):

| Suite | Tests | Kind |
|---|---:|---|
| `SuggestionEngineTest` | 32 | pure |
| `SwipePathLogicTest` | 23 | pure |
| `SwipeManagerTest` | 20 | Robolectric |
| `TextEditLogicTest` | 20 | pure |
| `SwipeScorerTest` | 19 | pure |
| `TrailLogicTest` | 18 | pure |
| `SuggestionReplaceLogicTest` | 17 | pure |
| `EditorHighlightLogicTest` | 11 | pure |
| `EmojiModuleTest` | 11 | pure |
| `FileManagerModelTest` | 11 | Robolectric |
| `KeyScaleLogicTest` | 10 | pure |
| `SectionsTest` | 10 | Robolectric |
| `TerminalPanelTest` | 10 | Robolectric |
| `SectionsMoreTest` | 9 | pure |
| `EditorPanelTest` | 8 | Robolectric |
| `InputRouterTest` | 8 | Robolectric |
| `KeyTabConfigTest` | 8 | pure |
| `LearnedDictionaryApiTest` | 8 | pure |
| `EmojiSuggestionsTest` | 7 | pure |
| `SettingsConfigTest` | 7 | Robolectric |
| `TermuxKeyMatrixTest` | 7 | pure |
| `ThemeApplierTest` | 7 | Robolectric |
| `CapsLogicTest` | 6 | Robolectric |
| `ShiftControllerTest` | 6 | Robolectric |
| `WordPredictionManagerSuggestionTest` | 6 | Robolectric |
| `ClipboardPanelTest` | 5 | Robolectric |
| `FileManagerPanelTest` | 5 | Robolectric |
| `LikelyHighlightLogicTest` | 5 | pure |
| `SettingsRegressionTest` | 5 | Robolectric |
| `SnippetPanelTest` | 5 | Robolectric |
| `FileManagerFragmentTest` | 4 | Robolectric |
| `KeyAnimationsTest` | 4 | Robolectric |
| `LiftSpanTest` | 4 | Robolectric |
| `SwipePerformanceTest` | 3 | pure |
| `PanelHeightsTest` | 2 | Robolectric |
| `TrailPerformanceTest` | 2 | pure |

Test code is 4,718 lines in 36 files against 9,380 lines of main code (57 files) — a **50.3 % test-to-main ratio**. Line coverage measured with Kover is **52.2 %** (`LINE` 2300/4404), branch coverage **39.8 %** (`BRANCH` 1287/3237); the CI gate is 20 % (re-measured 2026-09-21, all 343 tests green). Per package: the Android-free logic (`com.piotv.keytab.ime`: 49.7 %), the theme UI sections (`sections`: 93.6 %) and the in-app file manager (`file`: 77.9 %) — the latter two were historically untested and are now covered by `SectionsTest`, `SectionsMoreTest`, `EditorPanelTest`, `ClipboardPanelTest`, `SnippetPanelTest`, `TerminalPanelTest`, `FileManagerPanelTest`, `FileManagerFragmentTest` and `LearnedDictionaryApiTest`; remaining gaps are listed under [Known gaps](#known-gaps). The keyboard hot paths (correction trace → `autoCorrect`, swipe sampling → `charAt`/`dedup`, swipe scoring) are JVM-benchmarked in `TrailPerformanceTest` and `SwipePerformanceTest` (avg µs per call, asserted far below the 50 ms keystroke budget). Test names are written as specifications in German (e.g. `Doppel-Tap aktiviert CapsLock`). Instrumented tests (`app/src/androidTest`, 44 lines) run in CI on an API-34 emulator via `./gradlew :app:connectedDebugAndroidTest`.

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
| **Trail frame timing not measured on device** | The correction trace classifies the typed word against the engine on **every keystroke** (`TrailLogic.classifyTypedWord` → `SuggestionEngine.autoCorrect`). **Partially measured (2026-09-19):** the algorithm cost is JVM-benchmarked in `TrailPerformanceTest` — ~2 µs per classification on a 6,000-word corpus, ~4 orders of magnitude below the 50 ms keystroke budget (with a hard assertion so regressions fail the build). What remains open: **frame timing on a real display** (profiling on the device) and visual smoothness; the red/green trace contrast per theme palette is still not screenshot-verified. Mitigation if it stutters: restrict the trace to `knowsWord` and check `autoCorrect` only on word completion. |
| **Trail visuals not screenshot-verified** | The regression fix for contradictory trace states (see 0.9.7) is proven at the **state level** by unit tests — no screenshot or instrumented test asserts the rendered colours. The red/green contrast against each custom theme palette has not been measured. |
| **Swipe frame timing not measured on device** | The swipe hot path (`charAt` + `dedup` per Move-Event, `SwipeScorer.score` on release) is JVM-benchmarked in `SwipePerformanceTest` — `charAt` and `dedup` are asserted < 5 ms avg over 10 000 calls, the scorer < 50 ms on a 6 000-word corpus (hard assertions so regressions fail the build). What remains open: **frame timing on a real display** (profiling the overlay invalidate + edge redraw on the device) and visual smoothness of the circuit preview path. |
| ~~English locale incomplete~~ **Resolved 2026-09-21** | `values-en` now covers all 176 strings (was 92/163); no German fallback in English-locale devices any more. |
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
app/src/main/res/values-en/                    # English locale (176 strings — complete, 2026-09-21)
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

Release history and all notable changes live in **[CHANGELOG.md](CHANGELOG.md)** —
so this README stays focused on what the app does and how to build it.

The version itself is defined in exactly one place (`app/build.gradle.kts`,
`versionCode`/`versionName`) and is checked against the changelog and the
fastlane release notes by the CI job "docs" (`scripts/check_docs_drift.sh`).

1. **Finish the coroutine migration** — threading is centralised in `KeyTabExecutors`, the two
   remaining `Handler`s are shared; true coroutines are still open.
2. **Optional: PTY for the terminal tab** — it currently pipes stdin/stdout without a pseudo-terminal, so interactive TUI programs and ANSI colours cannot work. A PTY would turn the tab into a real terminal, but is a large change for a convenience feature; documenting the limitation was preferred (see §4.3).

Explicitly *not* planned: cloud sync, 100+ languages — those are Gboard dimensions that cannot be won here.

**Also not planned: an extra-keys row** (Esc/Ctrl/arrows). Termux already ships one (`extra-keys` in `termux.properties`), so a second row would duplicate it and eat key height.

## Contributing

Issues and pull requests are welcome. Before opening a PR:

```bash
sh scripts/check_docs_drift.sh                     # docs must match the code
sh ./gradlew :app:testDebugUnitTest --offline      # 323 tests must stay green
bash build_keytab.sh debug                         # must build
```

Conventions:

- 100 % Kotlin, no new dependencies without a clear reason.
- New logic goes into an Android-free, unit-tested class (a `*Logic` / model class); keep the panels and the service thin.
- Unit-test names are written as specifications (German is the established convention under `app/src/test`).
- Do not add an `INTERNET` permission — "no network, no data collection" is a core promise of the app.
- **Versioning**: the version lives only in `app/build.gradle.kts` (`versionCode`/`versionName`). Bump it there, move the fastlane release notes to `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and add a matching section at the top of `CHANGELOG.md` — the CI job `docs` fails if any of the three drifts apart.
- **Test counts in this README** are verified by the same check, so update the table when you add a suite.

## License

MIT -- see [LICENSE](LICENSE)
