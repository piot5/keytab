# Development

KeyTab builds with Gradle on Android 7+ (API 24+). No Android Studio needed.

## Build

```bash
# Debug build
bash build_keytab.sh debug

# Release build (signed, with R8 minification)
bash build_keytab.sh release
```

### Building on Android (Termux/proot)

The bundled `gradlew` works in Ubuntu-proot on Android. Two caveats:

- The Gradle wrapper JAR is re-downloaded on first run (TLS can break in proot). If that fails, run Gradle once with a working network connection.
- Building directly on the SD card is unreliable (the Gradle daemon gets killed, FUSE file locks cause issues). Copy the project to internal storage:

```bash
cp -r /path/to/keytab ~/build/
cd ~/build/keytab

# gradlew has no exec bit on FAT -- call it with sh
sh ./gradlew :app:assembleDebug --offline

cp app/build/outputs/apk/debug/app-debug.apk /path/to/keytab/app-debug-new.apk
```

Long-running builds should be started with `setsid … &` to avoid the terminal timeout killing the Gradle process. `--offline` saves time when no new dependencies are needed.

## Installation without a PC (via Shizuku/rish)

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

The easiest route is `sh install_keytab.sh <apk>` (staging to `/sdcard/Download`, copy via Shizuku, `pm install -d -r`).  
`sh build_install.sh debug` does build + tests + install in one go.

### Important: debug vs. release package

| Build | Package | Use |
|---|---|---|
| `assembleDebug` | `com.piotv.keytab.debug` | Development only |
| `assembleRelease` | `com.piotv.keytab` | The real install |

Both register a keyboard. If the debug package stays installed and enabled, Android can keep using it — a stale debug build then looks like "the app did not change". Always check which one is active:

```bash
sh ~/bin/rsh 'settings get secure default_input_method'
# -> com.piotv.keytab/com.piotv.keytab.ime.KeyTabImeService   (release, correct)
# -> com.piotv.keytab.debug/...                            (old debug build)
```

To switch back to the release build:

```bash
sh ~/bin/rsh 'settings put secure enabled_input_methods com.piotv.keytab/com.piotv.keytab.ime.KeyTabImeService'
sh ~/bin/rsh 'settings put secure default_input_method com.piotv.keytab/com.piotv.keytab.ime.KeyTabImeService'
sh ~/bin/rsh 'pm uninstall com.piotv.keytab.debug'   # remove the stale build
```

See [`docs/RELEASE.md`](docs/RELEASE.md) for the full release process. Dependency checksums and pinned GitHub Actions are documented in [`docs/SUPPLY_CHAIN.md`](docs/SUPPLY_CHAIN.md). Local gate runs and detekt baseline maintenance go through `sh scripts/baseline.sh --list`.

## Project structure

```
app/src/main/java/com/piotv/keytab/            # Kotlin files
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
    ├── KeyTabImeService.kt   # Keyboard core / orchestration
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

app/src/test/java/com/piotv/keytab/            # Unit tests (JUnit + Robolectric)
app/src/androidTest/                           # Instrumented typing tests (CI: API-34 emulator)
app/src/main/res/values/strings.xml            # Default strings (English)
app/src/main/res/values-en/                    # English locale
app/src/main/res/values-de/                    # German locale (partial)
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

## Architecture

`KeyTabImeService` is the keyboard core (orchestration). Every feature lives in its own class — panels for UI, controllers for stateful wiring, pure modules for logic (Android-free, unit-testable). All panels receive narrow dependencies (`Context`, `Executor`, `Handler`, callbacks) and **none of them references `KeyTabImeService`**, which makes them directly unit-testable with Robolectric and no service mock.

## Tests

Unit tests run via `./gradlew :app:testDebugUnitTest`. Pure-logic classes (`SuggestionEngine`, `TextEditLogic`, `KeyScaleLogic`, `CapsLogic`, `LiftSpan`, `LikelyHighlightLogic`, `TrailLogic`, `PanelHeights`) are fully Android-free and fast.

Test names are written as specifications in German (e.g. `Doppel-Tap aktiviert CapsLock`). Frame timing is measured on-device with `scripts/device_perf.py`.

### Known gaps

| Gap | Detail |
|---|---|
| Trail frame timing measured on device (backspace PASS, typing thermal-dependent) | Backspace-autorepeat **PASS**. Typing stays thermal-state dependent. Visual smoothness of the trail overlay and red/green trace contrast per theme palette still not screenshot-verified. |
| Trail visuals not screenshot-verified | No screenshot or instrumented test asserts the rendered colours. Red/green contrast against each custom theme palette not measured. |
| Swipe frame timing not measured on device | Only JVM-benchmarked in `SwipePerformanceTest`. Frame timing on a real display remains open. |
| Instrumented coverage is one broad pass | `KeyTabTypingTest` checks the real IME contract. Visual rendering (themes, trail colours) still not screenshot-asserted. |

## Contributing

Issues and pull requests are welcome. KeyTab is currently a single-maintainer project (**bus factor 1**) — contributors who can review or co-maintain are especially welcome. `CONTRIBUTING.md` contains an onboarding path that needs no prior knowledge of the codebase and doubles as the bus-factor handover plan.

### Prerequisites

- JDK 17 (the Gradle build is pinned to it)
- Android SDK — `compileSdk`/`targetSdk` 36, `minSdk` 24

### Before opening a PR

```bash
sh scripts/check_docs_drift.sh                     # docs must match the code
sh ./gradlew :app:testDebugUnitTest --offline      # unit tests must stay green
bash build_keytab.sh debug                         # must build
sh scripts/test_on_device.sh                       # instrumented typing test on a real device (before a push)
```

### Conventions

- 100 % Kotlin, no new dependencies without a clear reason.
- New logic goes into an Android-free, unit-tested class (a `*Logic` / model class); keep the panels and the service thin.
- Unit-test names are written as specifications (German is the established convention under `app/src/test`).
- Do not add an `INTERNET` permission — "no network, no data collection" is a core promise of the app.
- **Versioning**: the version lives only in `app/build.gradle.kts` (`versionCode`/`versionName`). Bump it there, move the fastlane release notes to `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and add a matching section at the top of `CHANGELOG.md` — the CI job `docs` fails if any of the three drifts apart.

See `CONTRIBUTING.md` for the full onboarding guide, local build/test instructions and the bus-factor handover plan.

## Roadmap

Release history lives in **[CHANGELOG.md](CHANGELOG.md)**; the version is defined in exactly one place (`app/build.gradle.kts`) and is checked against changelog + fastlane notes by the CI job 'docs'.

1. **Finish the coroutine migration** — `ClipboardPanel` now uses a service-owned lifecycle-aware scope with `Dispatchers.IO`; `FileManagerPanel` and `SuggestionEngine` remain on the shared executor/manager path.
2. **PTY/Shell in der App** — der Terminal-Tab wurde in v0.14 bewusst entfernt: eine Shell ohne PTY/Userland war keine echte Arbeitsumgebung. Für Termine/SSH bleibt KeyTab die Eingabemethode; die Shell macht Termux.

Explicitly *not* planned: cloud sync, 100+ languages — those are Gboard dimensions that cannot be won here.

Also not planned: an extra-keys row (Esc/Ctrl/arrows). Termux already ships one (`extra-keys` in `termux.properties`), so a second row would duplicate it and eat key height.

## F-Droid status

MR !50822 merged 2026-10-22 by `linsui`; F-Droid is configured. The pin `v0.18.1` (Tag `0baa0afe…`, versionCode 34) remains the reference for the reproducible build (`docs/RELEASE.md`, section 7).
