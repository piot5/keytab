# Contributing to KeyTab

KeyTab is a mobile IDE built as an Android IME: a tabbed file manager, an editor/clipboard, snippets, and offline word prediction. 100 % Kotlin.

This file is the onboarding path for new contributors **and** the handover plan for the bus-factor-1 risk: it should let someone who has never seen the codebase build, test, and understand it in under an hour.

---

## 1. Architecture in one line

```
app/src/main/java/com/piotv/keytab/
├── KeyTabImeService.kt     # IME core (thin orchestration)
├── ime/                    # panels, controllers, pure logic
│   ├── *Panel.kt           # Editor / FileManager / Clipboard / Snippet panels (UI)
│   ├── *Controller.kt      # TabController, SuggestionController, TextCommitController, ShiftController …
│   └── *Logic.kt / *Manager.kt  # Android-free, unit-tested domain logic
│                              #   (SuggestionEngine, TrailLogic, TextEditLogic, SwipeScorer …)
├── sections/               # Theme-settings screen sections
├── file/                   # file-manager helpers
├── Prefs.kt                # SharedPreferences keys (single access point)
├── MainActivity.kt         # settings entry point
└── ThemeSettingsActivity.kt
```

Rule of thumb:

- **Panels** receive narrow dependencies (`Context`, `Executor`, `Handler`, callbacks) and never reference `KeyTabImeService` — Robolectric-testable without a service mock.
- **Controllers** consume narrow `*Host` interfaces and wire state.
- **Logic** classes are pure and Android-free — the bulk of unit tests lives here.

---

## 2. Toolchain

| Component | Version |
|---|---|
| JDK | 17 (pinned) |
| Gradle | 8.14.3 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 1.9.24 |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |

---

## 3. Build and test locally

```bash
# Unit tests (Robolectric) — fast feedback loop
sh ./gradlew :app:testDebugUnitTest --offline

# Build the debug APK
bash build_keytab.sh debug

# Build + test + install in one go (Shizuku/rish on device)
sh build_install.sh debug

# Docs must match the code (CI job "docs", no toolchain needed)
sh scripts/check_docs_drift.sh

# Instrumented typing test on a real device (before a push)
sh scripts/test_on_device.sh
```

The release pipeline (`release.yml`) builds and signs every `v*` tag; details in [`docs/RELEASE.md`](docs/RELEASE.md).

---

## 4. CI gates (all must stay green)

| Job | What it proves |
|---|---|
| `docs` | README / CHANGELOG / fastlane / version are in sync |
| `build-test` | `assembleDebug` + all unit tests |
| `static-analysis` | Android Lint + detekt (baseline must not grow) |
| `coverage` | Kover gate: ≥ 60 % line / ≥ 45 % branch |
| `instrumented` | real IME contract on an API-34 KVM emulator |

---

## 5. Conventions (short form)

- 100 % Kotlin; no new dependency without a clear reason.
- New logic goes into an Android-free `*Logic` / model class, unit-tested.
- Unit-test names are **specifications** (German is the convention under `app/src/test`) — read them to learn intended behaviour.
- **No `INTERNET` permission.** "No network, no data collection" is a core promise and a privacy guarantee, not a preference.
- Version is defined **only** in `app/build.gradle.kts`. Bump it, move the fastlane notes, add a matching `CHANGELOG.md` heading — the `docs` job fails if any of the three drifts apart.

---

## 6. Bus-factor handover plan

The single largest project risk is that one person holds all the context. To reduce it, the following is kept deliberately discoverable in the repo:

- **Docs**: [`README.md`](README.md), [`CHANGELOG.md`](CHANGELOG.md), [`docs/`](docs/) (development, release, supply chain).
- **Open work**: `tofix.md` and `tofix-review-2026-10-16.md` are the current bug / backlog lists (German).
- **Process**: everything a reviewer would check by hand is already encoded in CI + `scripts/` — a second maintainer can judge a PR by "are the gates green" rather than by tribal knowledge.

If you want to become a co-maintainer: run the four commands in section 3, pick an item from `tofix.md`, fix it, add a spec-named unit test, and open a PR. That path exercises every gate without requiring pre-existing context.

---

## 7. Where things live

| Topic | File |
|---|---|
| Build + install + architecture | `docs/DEVELOPMENT.md` |
| Release process | `docs/RELEASE.md` |
| Supply chain / signing / pinning | `docs/SUPPLY_CHAIN.md` |
| Third-party notices | `THIRD_PARTY_NOTICES.md` |
| Changelog | `CHANGELOG.md` |
