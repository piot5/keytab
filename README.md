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

**Word prediction** -- offline n-gram model (FrequencyWords de_50k, CC-BY-SA-4.0) with bigrams for next-word prediction, a user dictionary that learns as you type, prefix autocomplete, Damerau-Levenshtein fuzzy correction, and case matching. The top suggestion is rendered 2x wider with a green accent bar for easier tapping. Toggleable in settings.

**Dynamic key sizing** -- likely-next keys scale up to 1.15x, unlikely ones down to 0.85x, driven by the current suggestion scores. Toggleable in settings.

**Notes tab** -- editor and clipboard merged into one tab. The "Load" button opens a folder browser (IME dialog with proper window token). The clipboard holds up to 50 persistent entries; tapping one inserts directly into the target field (the editor does not intercept it -- only Load fills the editor).

**Terminal tab** -- optional interactive shell in the keyboard, togglable in settings.

**Keyboard** -- full InputMethodService with TAB key (sends KEYCODE_TAB, useful for Termux/SSH), shift/caps-lock, long-press popups for umlauts/special characters, accelerating backspace on long-press (250ms down to 30ms).

**Privacy** -- no network permission, no data collection.

## Architecture

`KeyTabImeService` is the keyboard core. Sub-features live in their own classes:

| Class | Responsibility |
|---|---|
| `FileManagerPanel` | File manager (navigation, async listing) |
| `EditorPanel` | Notes editor with save/load and folder browser |
| `ClipboardPanel` | Clipboard history |
| `SuggestionEngine` | Word prediction (offline, testable) |
| `TerminalPanel` | Interactive shell |
| `TextEditLogic` | Pure, Android-free text logic |

All panels share a background executor for file I/O and a main handler for UI updates; stale results are discarded on navigation.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

20 unit tests for `TextEditLogic`, 11 Robolectric panel tests, 19 tests for `SuggestionEngine`. The logic class is intentionally Android-free so it runs without an emulator.

## Build

### On a PC

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and Android SDK (compileSdk 34). Set the SDK path in `local.properties` (`sdk.dir=...`) or via `ANDROID_HOME`.

### Release signing

The release keystore lives at **`keystore/keytab-release.jks`** (local only, git-ignored — **back it up**, it is required to sign updates with the same signature).

```bash
# Defaults: keystore/keytab-release.jks, store/key password "keytab-release", alias "keytab"
./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Override via environment variables:

| Variable | Purpose |
|---|---|
| `KEYTAB_KEYSTORE` | Path to the `.jks` keystore |
| `KEYTAB_KEYSTORE_PASSWORD` | Keystore password |
| `KEYTAB_KEY_ALIAS` | Key alias (`keytab`) |
| `KEYTAB_KEY_PASSWORD` | Key password |

> CI note: lintVital is disabled (`lint { checkReleaseBuilds = false }`) because `lint-gradle` downloads break behind restrictive TLS environments.

### On a device (ARM64 / Proot / Termux)

Google's `aapt2` is x86_64-only. Use the ARM build tools from <https://github.com/lzhiyong/android-sdk-tools> and set in `~/.gradle/gradle.properties`:
```
android.aapt2FromMavenOverride=/path/to/aapt2
```

Building directly on the SD card is unreliable (the Gradle daemon gets killed, FUSE file locks cause issues). Copy the project to internal storage and build there:

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
├── MainActivity.kt              # Settings: enable keyboard, theme, toggles
├── file/FileManagerFragment.kt  # File manager in the app (with DiffUtil)
└── ime/
    ├── KeyTabImeService.kt      # Keyboard core: keys, shift, popups, tabs
    ├── FileManagerPanel.kt      # IME file manager
    ├── EditorPanel.kt           # Notes editor with folder browser
    ├── ClipboardPanel.kt        # Clipboard history
    ├── SuggestionEngine.kt      # Word prediction
    ├── TerminalPanel.kt         # Interactive shell
    ├── LetterPopup.kt           # Long-press characters + drag selection
    └── TextEditLogic.kt         # Pure, testable text logic
app/src/test/java/com/piotv/keytab/ime/
├── PanelsTest.kt                # 11 Robolectric tests
├── SuggestionEngineTest.kt      # 19 unit tests
└── TextEditLogicTest.kt         # 20 unit tests
app/src/main/assets/
└── de_freq_top6000.txt          # Frequency corpus (CC-BY-SA-4.0)
```

## Changelog

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
