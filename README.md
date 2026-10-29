# KeyTab

Mobile IDE built as an Android keyboard (IME): file manager, editor, snippets and offline word prediction — everything runs locally on your device.

[![CI](https://github.com/piot5/keytab/actions/workflows/ci.yml/badge.svg)](https://github.com/piot5/keytab/actions/workflows/ci.yml)
[![Latest release](https://badgen.net/github/release/piot5/keytab)](https://github.com/piot5/keytab/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/piot5/keytab/total?label=downloads)](https://github.com/piot5/keytab/releases)
[![F-Droid](https://img.shields.io/badge/f--droid-com.piotv.keytab-633?logo=f-droid&logoColor=white)](https://f-droid.org/en/packages/com.piotv.keytab/)
[![Platform](https://img.shields.io/badge/platform-Android%207%2B%20%28API%2024%2B%29-3ddc84?logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

## Download & Installation

**GitHub Releases:** every tagged release (`v*`) is built and published automatically by CI as a signed APK with SHA-256 checksums.  
→ [Latest APK](https://github.com/piot5/keytab/releases/latest)

**F-Droid:** [com.piotv.keytab](https://f-droid.org/en/packages/com.piotv.keytab/) — live since 2026-10-25. The store pin follows the newest tag (`AutoUpdateMode: Version` + `UpdateCheckMode: Tags` in `fdroiddata`), so the store page can lag a release by a review round. Release history: [CHANGELOG.md](CHANGELOG.md).

**Install:**

1. Open the APK in a file manager (allow "install unknown apps").
2. Enable KeyTab in *Settings → System → Languages & input → On-screen keyboard*.
3. Switch to KeyTab in any text field.

Release history lives in **[CHANGELOG.md](CHANGELOG.md)**.

## What it does

KeyTab turns your keyboard into a local-first development environment:

- **File manager** — browse folders, insert paths, open files via VIEW intent.
- **Notes editor** — clipboard history (50 entries), folder-based loading.
- **Snippets** — named commands (`name = text`) in a plain-text file; tap to insert, long-press to preview.
- **Offline word prediction** — n-gram + Damerau-Levenshtein correction, no network, no data collection.
- **Swipe typing** (optional) — route-based completion.
- **Emoji catalog** — offline de/en keyword lookup.
- **Themes** — dark/light, custom colors, background images.
- **Tab key** — for Termux/SSH tab completion.

KeyTab works especially well with Termux/Ubuntu-proot: it becomes your shell launcher, file browser and snippet library in one input view.

## Supported languages

de, en, es, fr, it, pt, nl — each with its own frequency corpus.

## How it is verified

Four CI jobs run on every push: `docs` (README / CHANGELOG / fastlane notes / version must agree — and a pushed tag must match the version inside its own tree), `build-test` (assemble + all unit tests), `static-analysis` (Android Lint + detekt) and `coverage` (Kover gate: ≥ 60 % lines, ≥ 45 % branches).

- **573 unit tests in 64 classes** (JUnit + Robolectric); measured coverage 71.7 % lines / 57.9 % branches (2026-10-29).
- **Instrumented typing test on a real device**: `sh scripts/test_on_device.sh` builds, installs (SHA-256-verified), checks the installed `versionCode`, wakes/unlocks the device, drives the real IME and restores the previous keyboard. It is deliberately **not** a CI job — the API-34 emulator is too slow to bring the IME window up, which reported environment failures as product failures.
- **Supply chain**: Gradle dependency verification by checksum (`gradle/verification-metadata.xml`) and GitHub Actions pinned to commit SHAs — see [`docs/SUPPLY_CHAIN.md`](docs/SUPPLY_CHAIN.md).

## Privacy

- No `INTERNET` permission. Nothing leaves your device.
- Minimal storage permissions, requested only on demand.
- In password fields (and when an app sets `IME_FLAG_NO_PERSONALIZED_LEARNING`), trail, swipe, suggestions and dictionary learning are suppressed in code.

## License

MIT. Bundled word-frequency corpora are CC-BY-SA-4.0 (see `THIRD_PARTY_NOTICES.md`).

## Contributing

Want to build from source or contribute? See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Setup as default keyboard

1. Install the APK.
2. Open **Settings → Languages & input → On-screen keyboard → Manage keyboards**.
3. Enable **KeyTab**.
4. Switch to KeyTab: tap any text field → keyboard selector → **KeyTab**.
5. Open KeyTab settings from the keyboard gear or app icon to choose language, theme, and options.

## Language switch

Inside KeyTab settings (gear icon on keyboard) choose `Language`. Available: German, English, Spanish, French, Italian, Portuguese, Dutch. The change applies immediately — no restart needed.

## Troubleshooting

| Problem | Fix |
|---|---|
| Keyboard does not appear | Enable KeyTab in **Settings → Languages & input → Manage keyboards** and select it as the input method. |
| Suggestions missing | Make sure `suggestions = true` in `keytab_config.txt` or settings. Password/sensitive fields suppress suggestions by design. |
| Swipe not working | Enable `swipe = true`. Disabled in password fields. |
| Config file not picked up | Edit via the Config button inside KeyTab so the path and permissions are correct. |
| Build fails locally | Run `sh scripts/check_docs_drift.sh` and `sh ./gradlew :app:testDebugUnitTest --offline` first. See [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md). |
