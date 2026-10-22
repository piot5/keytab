# Contributing to KeyTab

KeyTab is a mobile IDE built as an Android IME: a tabbed file manager, an
editor/clipboard and snippets, with offline word prediction. 100 % Kotlin.

This file is the onboarding path for new contributors **and** the handover plan
for the bus-factor-1 risk: it should let someone who has never seen the
codebase build, test and understand it in under an hour.

---

## 1. One-line architecture

```
app/src/main/java/com/piotv/keytab/
├── KeyTabImeService.kt     # the IME core (thin orchestration, ~400 lines)
├── ime/                    # panels, controllers and pure logic
│   ├── *Panel.kt           # Editor/FileManager/Clipboard/Snippet panels (UI)
│   ├── *Controller.kt      # TabController, SuggestionController,
│   │                       #   TextCommitController, ShiftController …
│   └── *Logic.kt / *Manager.kt  # Android-free, unit-tested domain logic
│   │                       #   (SuggestionEngine, TrailLogic, TextEditLogic,
│   │                       #    SwipeScorer, WordPredictionManager …)
├── sections/               # the Theme-settings screen sections
├── file/                   # file-manager helpers
├── Prefs.kt                # SharedPreferences keys (single access point)
├── MainActivity.kt         # settings entry point
└── ThemeSettingsActivity.kt
```

Rule of thumb (enforced by convention, not by the compiler):

- **Panels** receive narrow dependencies (`Context`, `Executor`, `Handler`,
  callbacks) and never reference `KeyTabImeService` — so they are Robolectric-
  testable without a service mock.
- **Controllers** consume the narrow `*Host` interfaces and wire the state.
- **Logic** classes are pure and Android-free — the bulk of the unit tests live
  here.

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
# Unit tests (Robolectric) — the fast feedback loop.
sh ./gradlew :app:testDebugUnitTest --offline

# Build the debug APK.
bash build_keytab.sh debug

# Build + test + install in one go (Shizuku/rish on device).
sh build_install.sh debug

# Docs must match the code (runs in CI as the "docs" job, no toolchain needed).
sh scripts/check_docs_drift.sh

# Instrumented typing test on a real device (before a push).
sh scripts/test_on_device.sh
```

The release pipeline (`release.yml`) builds and signs every `v*` tag; details in
[`docs/RELEASE.md`](docs/RELEASE.md).

---

## 4. CI gates (all must stay green)

| Job | What it proves |
|---|---|
| `docs` | README/CHANGELOG/fastlane/version are in sync (no stale numbers) |
| `build-test` | `assembleDebug` + all unit tests |
| `static-analysis` | Android Lint + detekt (baseline must not grow) |
| `coverage` | Kover gate: ≥ 60 % line / ≥ 45 % branch |
| `instrumented` | the real IME contract on an API-34 KVM emulator |

---

## 5. Conventions (short form)

- 100 % Kotlin; no new dependency without a clear reason.
- New logic goes into an Android-free `*Logic`/model class, unit-tested.
- Unit-test names are **specifications** (German is the convention under
  `app/src/test`) — read them to learn the intended behaviour.
- **No `INTERNET` permission.** "No network, no data collection" is a core
  promise and a privacy guarantee, not a preference.
- Version is defined **only** in `app/build.gradle.kts`. Bump it, move the
  fastlane notes and add a matching `CHANGELOG.md` heading — the `docs` job
  fails if any of the three drifts apart.
- README test/file/line numbers are **machine-checked** by
  `scripts/check_docs_drift.sh`. Update them when you add code.

---

## 6. Bus-factor handover plan

The single largest project risk is that one person holds all the context. To
reduce it, the following is kept deliberately discoverable in the repo:

- **Docs**: [`README.md`](README.md), [`CHANGELOG.md`](CHANGELOG.md),
  [`docs/`](docs/) (config, release, supply chain, baseline, device perf,
  docs-drift).
- **Open work**: `tofix.md` and `tofix-review-2026-10-16.md` are the current
  bug/backlog lists (German).
- **Process**: everything a reviewer would check by hand is already encoded in
  CI + `scripts/` — so a second maintainer can judge a PR by "are the gates
  green" rather than by tribal knowledge.

If you want to become a co-maintainer: start by running the four commands in
section 3, then pick an item from `tofix.md`, fix it, add a spec-named unit
test, and open a PR. That path exercises every gate without requiring any
pre-existing context.

---

## 7. Where things live

| Topic | File |
|---|---|
| Config format | `docs/CONFIG.md` |
| Release + F-Droid | `docs/RELEASE.md` |
| Supply chain / signing | `docs/SUPPLY_CHAIN.md` |
| Detekt baseline policy | `docs/BASELINE.md` |
| Device performance | `docs/DEVICE_PERF.md` |
| Docs-drift gate | `docs/DOCS_DRIFT.md`, `scripts/check_docs_drift.sh` |
| Third-party notices | `THIRD_PARTY_NOTICES.md` |
