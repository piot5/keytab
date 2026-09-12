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

**Theme customization** -- long-press the tab/☾ (or ☀) key opens a dedicated *Theme settings* page (also reachable from the app settings): choose dark or light, pick a gradient preset or build your own (color 1 → color 2, top→bottom / inverted / radial), and set the background, key, highlight and text colors **separately and per theme** with a live **color wheel** (hue/saturation), brightness and alpha sliders. Keys, tabs (abc/Notes/Files/Terminal), popups and the suggestion bar all recolor while the default look stays identical to stock until you set something. The built-in dark theme uses a darker gray palette (`#1a1a1a` background, `#2e2e2e` keys).

**Terminal tab** -- optional interactive shell in the keyboard, togglable in settings. Black background with standard prompt `user@host:~$` and cd tracking.

**Keyboard** -- full InputMethodService with TAB key (sends KEYCODE_TAB, useful for Termux/SSH), shift/caps-lock, long-press popups for umlauts/special characters, accelerating backspace on long-press (250ms down to 30ms).

**Privacy** -- no network permission, no data collection. All data stays on the device.
## Architecture

`KeyTabImeService` is the keyboard core. Every feature lives in its own class —
panels for UI, pure modules for logic (Android-free and unit-testable):

| Class | Responsibility |
|---|---|
| `FileManagerPanel` | File manager (navigation, async listing) |
| `EditorPanel` | Notes editor with save/load and folder browser |
| `ClipboardPanel` | Clipboard history (picker dialog) |
| `TerminalPanel` | Interactive shell |
| `WordPredictionManager` | Suggestion orchestration (engine load, bar render, learn words, language reload) |
| `DynamicKeyScaler` | Maps suggestion scores → key sizes via neighbor-aware scaling |
| `SuggestionEngine` | Pure word prediction (offline, unit-tested) |
| `KeyScaleLogic` | Pure scaling math (stepped grades 1.30×/1.15×, shrink 0.85×/0.925×) |
| `LanguageModule` | Multi-language registry (7 latin scripts) + per-language accents |
| `TextEditLogic` | Pure, Android-free text logic |
| `ThemeSettingsActivity` | Settings page: dark/light, gradients, per-theme colors (color wheel) |
| `ColorWheelView` | HSV color wheel (hue/saturation) + brightness/alpha |
| `ThemePrefs` | Theme pref keys, presets, version counter for IME rebuild |
| `ThemeApplier` | Applies gradients/colors to the keyboard view tree (recursive recolor) — extracted from the IME service |

All panels share a background executor for file I/O and a main handler for UI updates; stale results are discarded on navigation.

## Tests

Unit tests run via `./gradlew :app:testDebugUnitTest` (Robolectric for Android-dependent panels). The pure-logic classes (`SuggestionEngine`, `TextEditLogic`, `KeyScaleLogic`) are fully Android-free and fast.

**65 unit tests** across 6 suites (18 `SuggestionEngine`, 20 `TextEditLogic`, 10 `KeyScaleLogic`, 6 `KeyTabConfig`, 11 Robolectric panels `EditorPanel`/`ClipboardPanel`). Instrumented tests (`app/src/androidTest`) run in CI on an API-34 emulator via `./gradlew :app:connectedDebugAndroidTest`.

```bash
# Run all unit tests
sh ./gradlew :app:testDebugUnitTest

# Run only the Android-free logic tests (fast, no Robolectric)
sh ./gradlew :app:testDebugUnitTest --tests "com.piotv.keytab.ime.SuggestionEngineTest" --tests "com.piotv.keytab.ime.TextEditLogicTest" --tests "com.piotv.keytab.ime.KeyScaleLogicTest" --tests "com.piotv.keytab.ime.KeyTabConfigTest"

# Run a single test class
sh ./gradlew :app:testDebugUnitTest --tests "com.piotv.keytab.ime.SuggestionEngineTest"
```

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
app/src/main/java/com/piotv/keytab/
├── MainActivity.kt                  # Settings: enable keyboard, theme, language, toggles
├── ThemeSettingsActivity.kt         # Theme settings: color wheel, gradients, per-theme colors
├── ColorWheelView.kt                # HSV color wheel widget
├── file/FileManagerFragment.kt      # File manager in the app (with DiffUtil)
└── ime/
    ├── KeyTabImeService.kt          # Keyboard core: keys, shift, popups, tabs
    ├── ThemePrefs.kt                # Theme keys/presets + rebuild version
    ├── ThemeApplier.kt              # Recursive theme application on the keyboard view tree
    ├── FileManagerPanel.kt          # IME file manager
    ├── EditorPanel.kt               # Notes editor with folder browser
    ├── ClipboardPanel.kt            # Clipboard history (picker dialog)
    ├── TerminalPanel.kt             # Interactive shell
    ├── WordPredictionManager.kt     # Suggestion orchestration (engine load, bar render, language reload)
    ├── DynamicKeyScaler.kt          # Maps suggestion scores → key sizes (neighbor-aware)
    ├── SuggestionEngine.kt          # Pure word prediction (offline, unit-tested)
    ├── KeyScaleLogic.kt             # Pure scaling math (stepped grades)
    ├── LanguageModule.kt            # Multi-language registry (7 latin scripts) + accents
    ├── LetterPopup.kt               # Long-press characters + drag selection
    └── TextEditLogic.kt             # Pure, testable text logic
app/src/test/java/com/piotv/keytab/ime/
├── PanelsTest.kt                    # Clipboard + editor panels (Robolectric)
├── KeyTabConfigTest.kt              # 6 unit tests
├── SuggestionEngineTest.kt          # 18 unit tests
├── TextEditLogicTest.kt             # 20 unit tests
└── KeyScaleLogicTest.kt             # 10 unit tests
app/src/main/assets/
├── de_freq_top6000.txt              # German corpus (CC-BY-SA-4.0)
├── en_freq_top6000.txt              # English corpus (CC-BY-SA-4.0)
├── es_freq_top6000.txt              # Spanish corpus (CC-BY-SA-4.0)
├── fr_freq_top6000.txt              # French corpus (CC-BY-SA-4.0)
├── it_freq_top6000.txt              # Italian corpus (CC-BY-SA-4.0)
├── pt_freq_top6000.txt              # Portuguese corpus (CC-BY-SA-4.0)
└── nl_freq_top6000.txt              # Dutch corpus (CC-BY-SA-4.0)
```
## Changelog

### 0.9.6

- **Theme**: dark theme default palette darkened to a deeper gray (`kbd_bg #1a1a1a`, `key_bg #2e2e2e`, `key_pressed #444444`).
- **Theme editor**: key background is now a separate color target ("Taste") — background, key, highlight and text are adjustable independently per theme (previously the key color was coupled to the background override). Fix: the Taste color now reliably recolors **every** key (abc letters, shift/del/enter/space/symbol keys, first highlighted suggestion) — the previous `constantState` drawable matching never fired for inflated key drawables, so those keys kept their default color; matching is now by drawable type (Button + StateListDrawable) plus explicit IDs for the suggestion views.
- **Completion coloring (new section „Vervollständigung" in theme settings)**: toggleable next-key coloring — the most likely next key glows in an adjustable "Färbung" color; when the typed word reaches the top suggestion (likelihood reached) a satisfying pulse effect (color flash + scale pulse + haptic) fires. Pure logic in `GamingLogic` (unit-tested).
- **Tab bar fully themed**: the abc/Notes/Files/Terminal tab cells are now filled completely (flat background, no inset border) instead of leaving a gray rim, and Material's own `colorSurface` gray on the `TabLayout` is replaced — the gray no longer shows through behind/around the tab buttons.
- **Alpha/transparency consistency**: the two top rows (tab row + suggestion strip) used to stack their own `kbd_bg` backgrounds on top of the keyboard background (and the tab bar's own surface color), so with a semi-transparent background/key color they looked solid while the letter rows looked translucent. These container backgrounds are now transparent — the themed background comes from the keyboard root only, so alpha (and a configured gradient) behaves identically in every row.
- **Refactor**: theme application moved into `ThemeApplier` (recursive view-tree recolor, gradients, per-theme colors) — `KeyTabImeService` slims down.
- **CI**: instrumented tests now run on an API-34 emulator (ReactiveCircus runner, KVM-enabled; `testInstrumentationRunner` declared).
- **Fix**: quoting bug in `install_keytab.sh` — APK staging + Shizuku/rish copy + `pm install` is now clean and robust.
- **Quality**: test counts synchronized (75 unit tests across 8 suites, incl. `ThemeApplierTest` for recolor/alpha/transparency behavior), instrumented-test package assertion tolerates the `.debug` suffix.
- **Housekeeping**: version bumped to 0.9.6/versionCode 22, local mislabeled `v1.2.0` tag removed, `themedv0.9.5` branch kept (unmerged).

### 0.9.5

- **Theme settings as its own page**: long-press the ☾/☀ key (or use the "Theme settings" button in the app) opens a full settings screen instead of a popup. Every change applies instantly on the next keyboard focus.
- **Color wheel**: pick any color for background, key/pressed-highlight, text — and the two gradient colors — via an HSV color wheel with brightness and alpha sliders (per dark/light theme).
- **Gradient presets**: Grau, Nacht, Ozean, Wald, Abend, Lila — plus custom colors and modes (top→bottom, inverted, radial).
- **Everything recolors**: letter keys (incl. pressed state), the tab bar (abc/Notes/Files/Term), the suggestion strip and popups follow the chosen colors.
- **Icons**: monochrome SW pairs for save (⤓), load (⤒) and clipboard (▤), grouped tightly left in the editor row.
- Default look is unchanged (= 0.9.4) until you set a color.

### 0.9.4

- Release-Fix: 0.9.3-Tag war nachträglich verschoben worden, das existierende GitHub-Release blockierte den CI-Upload → saubere Version 0.9.4 (versionCode 20). Inhaltlich identisch zu 0.9.3.

### 0.9.3

- **Tasten wachsen real**: dynamische Tastengröße wirkt jetzt auf das Layout-Gewicht (echte Größe + Trefferfläche), nicht nur auf die visuelle Transformation; zusätzlich ist das Clipping deaktiviert — vergrößerte Tasten ragen jetzt über die Rasterzelle/den Containerrand hinaus, statt abgeschnitten zu werden.

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

- Fix: SuggestionEngine.topBaseOrder jetzt lazy-initialisiert (Crash bei Instanziierung behoben)
- Fix: FileManagerPanel.navigate() null-sicher (Crash bei Navigation behoben)
- Fix: TerminalPanel Shell-Fallback für verschiedene Android-Geräte
- Fix: SuggestionEngine thread-safe (ConcurrentHashMap)
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

## License

MIT -- see [LICENSE](LICENSE)
