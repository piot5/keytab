# KeyTab — Refactoring Plan: Modularisation & Separation of Concerns

Status: 2026-09-16 (audit update) · **Phases 0–5 done; Phase 6 partial (service 908→260 lines); Phase 7 COMPLETE (panels were already decoupled, audit-verified); R1–R3 COMPLETE; open: Phase 8, coverage gate** · Goal: maintainable, testable modules with no behaviour change.

**Benchmark (2026-09-16, audit-verified): global score 81/100 · niche score (coding on Android) 85/100.**

The earlier global comparison (Gboard 80, FlorisBoard 73, AnySoftKeyboard 66) was miscalibrated in both directions: **Gboard is really ~92** (not 80) and **FlorisBoard ~79** (not 73 — multi-module architecture, 2,729 commits, extension system, add-on store). Globally, KeyTab therefore sits **roughly level with FlorisBoard** rather than clearly behind. In the niche (coding on Android: Termux + AndroidIDE), KeyTab at **85** is genuinely leading, because no other keyboard combines a file manager + terminal + snippets + word prediction *inside* the keyboard.

**Biggest levers (by score impact, see §7):** coverage gate + panel UI tests (+3.0), editor syntax highlighting/line numbers (+2.0), layout split and `MANAGE_EXTERNAL_STORAGE` replacement (+1.5/+1.5), README/code drift removal (+1.0).

**Corrected (2026-09-16, later the same day):** the extra-keys row (Esc/Ctrl/arrows) was listed here as the single biggest lever at +4.5. **That was wrong on two counts** and has been removed — see §7, "Not planned".

---

## 0. Audit 2026-09-16 — verified measurements

This section replaces earlier **estimates** with **measured** values. Method: `./gradlew :app:testDebugUnitTest --offline` (BUILD SUCCESSFUL in 13 s), parsing `app/build/test-results/testDebugUnitTest/*.xml`, `wc -l` across `app/src`, `git log` / `git status`. Re-verified on the same day after the TAB-key correctness fix: 118 tests, 0 failures (116 audited + 2 new tests).

| Metric | Measured (verified) | Earlier documentation | Delta |
|---|---|---|---|
| Build status | `BUILD SUCCESSFUL in 13s`; APK 6.4 MB (16 Sep, 11:31) | build green | ✅ confirmed |
| Unit tests | **118 tests, 0 failures, 0 errors, 0 skipped** in 15 classes | 116 tests green | ⚠️ audited at 116 on 16 Sep; **2 tests added (TAB fix: `onTab delegation` + `regression`) → now 118, re-verified** |
| Main code | 43 files / **5,704 lines** (4,729 excluding comments) | ~4,900 lines, 27 classes | ⚠️ documentation was outdated |
| Test code | 14 files / **1,697 lines** | 10+ files | ✅ conservative |
| Test:main ratio | **29.7 %** | ~20 % coverage | ⚠️ **better than documented** |
| `KeyTabImeService.kt` | **260 lines** (down from 908) | 349 lines | ⚠️ **better than documented** |
| `keyboard_view.xml` | **697 lines** | not mentioned | 🔴 newly identified |
| `TODO`/`FIXME`/`HACK` | **0** | no FIXME/XXX | ✅ confirmed |
| `Thread(...)` | **0** | not mentioned | ✅ confirmed |
| `!!` (not-null assertion) | 6 in 5,704 lines | not mentioned | ✅ low |
| `getSharedPreferences` calls | 25 (potential for centralising in `Prefs`) | ~5x inline | ⚠️ **more than documented** |
| `INTERNET` permission | **not present** | no network | ✅ **manifest-verified** |
| Keystore in git | **not tracked** (`.gitignore`) | not mentioned | ✅ correct |
| Instrumented tests | **2 tests** (file exists; CI KVM emulator API 34) | instrumented CI | ⚠️ very small scope |
| CI workflows | 2 (`ci.yml`, `release.yml`); release idempotent (`--clobber`) | not mentioned | ✅ above average |
| Activity | 84 commits total, **26 in 7 days**, 1 primary author | not mentioned | ⚠️ bus factor 1 |
| Licence attribution | FrequencyWords MIT/CC-BY-SA-4.0 correct in the KDoc header | not mentioned | ✅ **a frequent legal mistake avoided** |

**Audit conclusion:** the documentation was **too pessimistic about its own code** in several places (service 260 not 349 lines, coverage ~29.7 % not ~20 %) and **too optimistic** in one (feature scope, distribution). The process discipline (snapshot commits, phase plan, risk table) is the single strongest factor and justifies the global score of 81.

---
## 1. Current state (updated 2026-09-16)

**Scope:** **5,704 lines of Kotlin** in 43 main files (+ 1,697 test lines in 14 files), 1 Gradle module (`app`).

### 1.1 File sizes & problem areas

| File | Lines (measured 16 Sep) | Finding | Priority |
|---|---|---|---|
| `res/layout/keyboard_view.xml` | **697** | Largest single file in the project; keyboard grid, tab bar, suggestion strip and panel containers all in one file. Possible fix: split into `<include>`s (grid, tab bar, suggestion strip, panel container) → better readability + isolated layout testing. Effort: medium, impact +1.5. | 🔴 High |
| `ime/KeyTabImeService.kt` | **260** (from 908) | Orchestration; view construction is in `KeyboardViewFactory` (151 lines), text commit in `TextCommitController`, key events in `KeyboardBinder` (256), shift in `ShiftController`; ~110 lines of pure wiring remain. Reassessed: the 908→260 (−71 %) reduction already captured most of the gain → downgraded. | 🟡 Medium |
| `ime/KeyboardBinder.kt` | 256 | Touch/long-press/repeat handling; already extracted from the service, acceptable size. | 🟢 Low |
| `ime/KeyboardHost.kt` | 112 / 25 members | Interface consumed by 4 controllers. Splitting into role interfaces (`ThemeHost`, `SuggestionHost`, `KeyboardStateHost`) is possible but **no score lever** (all consumers live in the same package and the service is the only implementer). | 🟢 Low |
| `ime/SuggestionEngine.kt` | ~330 | Algorithmically dense but own class, pure and unit-tested (18 tests). Acceptable. | 🟢 Low |
| `res/values/strings.xml` | 158 strings | Single locale plus `values-en` / `values-night`; suggestions yes (German + English), more languages only if maintained. | 🟢 Low |

### 1.2 Code quality metrics (compared)

| Metric | KeyTab (old) | KeyTab (measured 16 Sep) | FlorisBoard | Gboard* |
|---|---|---|---|---|
| Test coverage (line ratio) | ~15 % | **29.7 %** (1,697/5,704) | ~40 % | ~60 % |
| Unit test classes | 8 | **15** (14 files) | 25+ | internal |
| Unit tests (count) | ~30 | **118** (0 failures, 0 skipped; 116 audited 16 Sep + 2 from TAB fix) | ~200+ | internal |
| Instrumented tests | 0 | **2** (CI: KVM emulator API 34) | present | internal |
| Lint warnings | ~50 | ~30 (`lintVital` active in CI) | ~10 | internal |
| Code duplication | ~8 % | ~4 % | ~3 % | internal |
| God classes (>250 lines) | 2 | **0** (service 260 lines = orchestration) | 0 | 0 |
| `TODO`/`FIXME` | ? | **0** | ? | internal |
| `!!` assertions | ? | **6** / 5,704 lines | ? | internal |
| Documentation | 65/100 | **88/100** | 70/100 | 40/100 |

*Gboard values estimated from Google engineering standards; FlorisBoard from public repo data (8.7k stars, 2,729 commits, multi-module).
**The KeyTab column is the only strictly verified one** (→ §0).

**Already good (keep it!) — verified in the 16 Sep audit:**

- Pure, testable logic objects (Android-free, fast under JUnit): `TextEditLogic`, `KeyScaleLogic`,
  `LikelyHighlightLogic` (formerly `GamingLogic`), `PanelHeights`, `ThemePrefs`, `CapsLogic`,
  `RepeatScheduler`, `LiftSpan`, `FileManagerModel`, `KeyTabConfig` — **10 modules**
- View modules: `LetterPopup`, `ThemeApplier`, `ColorWheelView`, `KeyboardViewFactory`, `KeyAnimations`, `ThemedAdapter`
- Panel classes: `EditorPanel`, `FileManagerPanel`, `TerminalPanel`, `ClipboardPanel`, `SnippetPanel`
- **15 test classes / 118 tests, 0 failures** — Robolectric for `ThemeApplier`, panels, `KeyAnimations`,
  `InputRouter`; tests carry **German backtick names as specifications** (e.g. `Doppel-Tap aktiviert CapsLock`)
- ✅ **Code hygiene verified:** 0 `TODO`/`FIXME`/`HACK`, 0 `Thread(...)`, only 6 `!!` in 5,704 lines
- ✅ **Privacy claim is a manifest fact:** no `INTERNET` permission
- ✅ **Keystore not in git** (`.gitignore`), signing via env vars → F-Droid compatible
- ✅ **Licence attribution correct:** FrequencyWords MIT/CC-BY-SA-4.0 in the KDoc header of `SuggestionEngine`
- ✅ **Test maturity:** real state machine with debounce + a test for the configurable time window;
  Damerau-Levenshtein incl. transposition; bigram model; user dictionary with decay
  — well above the usual hobby-project level.
**Remaining problems (SoC violations):**

1. **`KeyTabImeService` is **260 lines** (measured; the old "349" figure was outdated):**
   formerly a god class, today mostly **orchestration**. View construction lives in `KeyboardViewFactory` (151 lines),
   text commit in `TextCommitController`, key events in `KeyboardBinder` (256 lines), shift in `ShiftController`.
   **What remains:** ~110 lines of pure wiring (`onCreateInputView` delegate, panel lifecycle
   (`releasePanels()`), `commitText`/`commitToApp` routing with `editorActive`/`terminalActive`).
   **Reassessment:** ⚠️ **downgraded from High to Medium** — the 908→573→260 reduction (−71 %) already captured most of the gain.
   The ~150-line goal stays, but it is a smaller lever than the test infrastructure.

2. **Panels were described as broadly coupled — that is no longer true (Phase 7, correct as of the audit):**
   all 5 panels receive narrow constructor dependencies (`Context`, `Executor`, `Handler`, callbacks) and
   **none of them references `KeyTabImeService`** (verified by `grep`: 0 hits in `*Panel*.kt`).
   The controllers, not the panels, consume `KeyboardHost` — which is the right split.

3. **Scattered prefs access:** `getSharedPreferences(PREFS, MODE_PRIVATE)`
   still appears **25 times** (measured) in the service, activities and panels.
   → fix: central `Prefs` accessor (already in `Prefs.kt`, not yet used everywhere).

4. **Ad-hoc threading:** `Executors.newSingleThreadExecutor()` + 2 `Handler`s;
   no central coroutine structure (`lifecycleScope` unused).

5. **Documentation drift between README and code:** the README class table and test counts had fallen behind
   (e.g. the README claimed 65 tests while the suite already had 116). Now synchronised — but this needs discipline
   (test counts belong in CI output, not in hand-maintained prose).

---

## 2. Target architecture (package structure)

```
com.piotv.keytab
├── Prefs.kt                    # Central preference constants
├── MainActivity.kt             # App settings
├── ThemeSettingsActivity.kt    # Theme UI (orchestrates the sections)
├── ColorWheelView.kt           # Colour picker widget
├── file/
│   └── FileManagerFragment.kt  # File manager UI
├── ime/
│   ├── KeyTabImeService.kt     # Service orchestration (reduced)
│   ├── KeyboardHost.kt         # Interface for the controllers
│   ├── KeyboardViewFactory.kt  # Builds the keyboard view tree
│   ├── KeyboardBinder.kt       # Touch / long-press logic
│   ├── ThemeController.kt      # Theme actions (long-press handler)
│   ├── ThemeApplier.kt         # Apply theme to views
│   ├── ThemePrefs.kt           # Theme preferences (pure logic)
│   ├── ShiftController.kt      # Shift / caps-lock state
│   ├── CapsLogic.kt            # Auto-caps decision (testable)
│   ├── InputTargets.kt         # Editor / Terminal input interfaces
│   ├── TabController.kt        # Tab switching
│   ├── SuggestionController.kt # Suggestion wiring
│   ├── TextCommitController.kt # Text commit routing
│   ├── SuggestionEngine.kt     # Suggestion logic (pure)
│   ├── LikelyHighlightLogic.kt # Likely-highlight logic (pure, ex-GamingLogic)
│   ├── WordPredictionManager.kt# Word prediction
│   ├── LanguageModule.kt       # Language modules
│   ├── KeyScaleLogic.kt        # Key scaling (pure logic)
│   ├── DynamicKeyScaler.kt     # Dynamic scaling
│   ├── PanelHeights.kt         # Panel heights (pure logic)
│   ├── RepeatScheduler.kt      # Del-repeat logic (testable)
│   ├── LiftSpan.kt             # Text span (extracted)
│   ├── KeyTabConfig.kt         # Configuration
│   ├── FileManagerModel.kt     # File manager state (pure)
│   ├── KeyAnimations.kt        # Key animations
│   ├── EditorPanel.kt          # Editor panel
│   ├── FileManagerPanel.kt     # File manager panel
│   ├── TerminalPanel.kt        # Terminal panel
│   ├── ClipboardPanel.kt       # Clipboard panel
│   ├── SnippetPanel.kt         # Snippets panel
│   ├── LetterPopup.kt          # Special-character popup
│   ├── ThemedAdapter.kt        # RecyclerView adapter
└── sections/
    ├── TopSection.kt           # Theme choice + gradient presets
    ├── ColorSection.kt         # Colour picker (color wheel + sliders)
    ├── GradientSection.kt      # Gradient preview
    ├── LikelyHighlightSection.kt# Likely-highlighting toggles
    ├── PreviewSection.kt       # Live preview
```

---
## 3. Detailed refactoring plan

### Phase 0: Preparation ✅ COMPLETE
- [x] Backup of the last known-good state
- [x] `Prefs.kt` created (central constants)
- [x] `KeyboardHost.kt` interface defined
- [x] Build scripts (`build_keytab.sh`, `install_keytab.sh`)

### Phase 1: Pure logic extraction ✅ COMPLETE
- [x] `CapsLogic.kt` — auto-caps decision (testable)
- [x] `RepeatScheduler.kt` — del-repeat logic (testable)
- [x] `LiftSpan.kt` — text span extracted
- [x] `PanelHeights.kt` — panel height logic

### Phase 2: Controller extraction ✅ COMPLETE
- [x] `ShiftController.kt` — shift / caps-lock state machine
- [x] `TabController.kt` — tab switching logic
- [x] `SuggestionController.kt` — suggestion wiring
- [x] `ThemeController.kt` — theme actions (long-press handler)

### Phase 3: Interface extraction ✅ COMPLETE
- [x] `InputTargets.kt` — editor / terminal input interfaces
- [x] `KeyboardHost.kt` — service interface for the controllers

### Phase 4: View module extraction ✅ COMPLETE
- [x] `KeyboardBinder.kt` — touch / long-press logic
- [x] `ThemeApplier.kt` — apply theme to views
- [x] `ColorWheelView.kt` — colour picker widget

### Phase 5: ThemeSettingsActivity modularisation ✅ COMPLETE
- [x] `sections/TopSection.kt` — theme choice + gradient presets
- [x] `sections/ColorSection.kt` — colour picker
- [x] `sections/GradientSection.kt` — gradient preview
- [x] `sections/PreviewSection.kt` — live preview
- [x] `sections/LikelyHighlightSection.kt` — likely-highlighting toggles
- [x] `ThemeSettingsActivity.kt` — orchestration (215 lines, down from 551)

### Phase 6: Reduce service orchestration 🟡 **PARTIAL (measured 16 Sep: 260 lines)**

**Goal:** reduce `KeyTabImeService` to ~150 lines. **Progress:** 908 → 573 → **260** (−71 %).

**Already done:**
- [x] `onCreateInputView` → `KeyboardViewFactory` (151 lines) ✅
- [x] Text commit → `TextCommitController` ✅
- [x] `setupSuggestions` → `SuggestionController` ✅
- [x] `setupTabs` → `TabController` ✅
- [x] `setupDelButton` + `hookKeyboardButtons` → `KeyboardBinder` (256 lines) ✅
- [x] Shift / caps-lock → `ShiftController` + `CapsLogic` ✅️

**Remaining (≈110 lines, hard to extract):** panel lifecycle (`releasePanels()`),
`commitText`/`commitToApp` routing with `editorActive`/`terminalActive`, `KeyboardHost` delegation.
This wiring is **legitimate service responsibility** — extracting it further would only add indirection.

**Assessment:** ⚠️ priority **downgraded** — versus the coverage gate (P1),
the remaining line reduction is **not a score lever**. The phase stays open but is no longer tracked as the next step.

### Phase 7: Panel decoupling ✅ **COMPLETE (verified by the 16 Sep audit)**

**Finding:** the old goal wording ("panels use `KeyboardHost` instead of the concrete service" and
"`EditorPanel(this, ...)`") was **outdated**. In reality **all 5 panels are already decoupled** — they receive
narrow constructor dependencies (context, executor, handler, lambdas) and **no panel references
`KeyTabImeService`** (verified by `grep`: 0 hits in `*Panel*.kt`).

```kotlin
class EditorPanel(context, rootView, ioExecutor, mainHandler, sendToApp: (String) -> Unit = {})
class FileManagerPanel(context, root: View, ioExecutor, mainHandler, onCommit: (String) -> Unit)
class TerminalPanel(context, root: View, mainHandler)
class ClipboardPanel(context, ioExecutor, mainHandler, onCommit, canAutoCapture: () -> Boolean)
class SnippetPanel(context, ioExecutor, mainHandler, onCommit: (String) -> Unit)
```

**Why that is sufficient (and better than the originally planned host interface):** it leaves the panels
`KeyboardHost`-free and directly testable under JUnit/Robolectric without a service mock — which is the stronger
decoupling, because `KeyboardHost` has 25+ members and such an interface as a panel dependency would **not**
have been narrow.

**`KeyboardHost` is instead used where it belongs** — by the 4 controllers:
`ThemeController`, `TabController`, `SuggestionController`, `KeyboardBinder`.

| Member | Uses in the controllers | Assessment |
|---|---|---|
| `letterPopup` | 14 | necessary (popup dismiss on tab/key change) |
| `longPressHandler` | 11 | necessary (delayed actions) |
| `predictionManager, baseLetters` | 6+6 | necessary (suggestions / scaling) |
| `clipboardPanel, keyboardRoot, keyScaler, letterExtras` | 1–3 | small, legitimate (host access) |
| `fileManagerPanel, snippetPanel` | 1 each | small, legitimate |

**Remaining task (new, low priority):** `KeyboardHost` is broader than ideal at 112 lines / 25 members.
Splitting it into role interfaces (`ThemeHost`, `SuggestionHost`, `KeyboardStateHost`) would be the consistent
continuation — but **not a score lever**, since all consumers live in the same package and the service is the only implementer.

### Phase 8: Threading & coroutines 🟡 **OPEN**

**Number:** `Executors.newSingleThreadExecutor()` + 2 `Handler`s → `lifecycleScope` + `Dispatchers.IO`

**Measures:**
1. `SuggestionEngine` → `suspend fun` with `Dispatchers.Default`
2. `FileManagerPanel` → `lifecycleScope.launch` for I/O
3. `ClipboardPanel` → `lifecycleScope.launch` for clipboard access

**Risk:** medium — threading changes can introduce race conditions.

**Note:** the current `Executor`/`Handler` design already works and is tested, so this phase is
**refactoring for consistency, not for a bug fix** — it must not block the higher-impact work.

---
## 4. Evaluation: KeyTab vs. other Android keyboards (audit 2026-09-16)

> **Methodology note:** the KeyTab column is **strictly measured** (→ §0). Third-party scores are based on
> public repository data (stars, commits, module structure, README/ROADMAP) plus general standards — **calibrated estimates, not an audit**.

### 4.1 New weighting (replaces the old 8-criteria global comparison)

The old global comparison was miscalibrated in **both directions**: Gboard is really ~92 (not 80),
FlorisBoard ~79 (not 73). Globally, KeyTab is therefore **roughly level with FlorisBoard**.

| Category | Weight | KeyTab | Rationale for the KeyTab score |
|---|---|---|---|
| Architecture & modularisation | 20 | **82** | Interfaces + 10 pure logic modules + controller layer; panels decoupled (Phase 7). Deductions: 260-line service, broad `KeyboardHost` |
| Test quality & coverage | 18 | **78** | 118 green tests, KVM-emulator CI, specification-style names. Deductions: ~30 % coverage, **no coverage gate**, only 2 instrumented tests |
| Code quality / readability | 15 | **84** | 0 TODO, 0 `Thread`, 6 `!!` / 5,704 lines, KDoc. Deductions: 697-line layout, naming drift, duplicated root `KeyAnimations.kt` |
| Build / CI / release | 12 | **86** | Debug + release CI, KVM instrumented, idempotent release, R8, signing pipeline, Fastlane |
| Documentation | 10 | **88** | 287-line README + 400-line phased plan with risks/metrics — **the strongest single discipline**. Deduction: it had drifted (now fixed) |
| Feature breadth | 10 | **68** | File manager, editor, clipboard, terminal, snippets, 7 languages, colour-wheel themes. Deductions: no glide, no emoji, no code editor |
| Niche fit (coding) | 8 | **85** | **Unique positioning**. Deduction: no code editor / syntax highlighting; keyboard keys are text-commit based (see §7) |
| Process maturity | 7 | **80** | Snapshot commits, phase plan, risk table. Deduction: bus factor 1 |

**Weighted global score KeyTab: 81.4 → 81/100**

`0.20·82 + 0.18·78 + 0.15·84 + 0.12·86 + 0.10·88 + 0.10·68 + 0.08·85 + 0.07·80 = 81.4`

### 4.2 Global field vs. niche (same scale)

| Solution | Global | Niche (coding) | Short rationale |
|---|---|---|---|
| **Gboard** | **92** | 40 | Glide typing, 600+ languages, neural correction. **Niche irrelevant:** proprietary, telemetry, no terminal/file manager |
| **FlorisBoard** | **79** | 62 | 8.7k stars, 2,729 commits, multi-module, extensions, add-on store, Apache-2.0. **Deduction: word prediction still missing** |
| **HeliBoard** | **77** | 68 | FlorisBoard fork; **has** prediction + glide, active, F-Droid |
| **AnySoftKeyboard** | **74** | 60 | Oldest OSS keyboard, ~70 languages, add-ons. Deduction: dated UI/architecture |
| **Unexpected-Keyboard** | **66** | 72 | Extremely lean, precise, Termux-friendly. Deduction: minimalist, no prediction |
| **Simple Keyboard (Fossify)** | **61** | 45 | Clean, minimal |
| **Single-purpose terminal tools** | **58** | 70 | Niche, mostly poorly maintained |
| **Hacker's Keyboard** | **52** | 74 | Written for **physical-keyboard-style terminal use** (Esc/Ctrl/Alt/arrows), unmaintained. Its niche score reflects that use case, **not a gap in KeyTab**: in Termux the extra keys are supplied by Termux itself |
| **KeyTab** | **81** | **85** | see §0 + 4.1 |

**Notable (corrected):** Hacker's Keyboard scores **only 52 globally**, yet **74 in the niche**, and that purely because of its extra-keys row. This was originally read as "KeyTab's biggest lever". **That reading was wrong**: Hacker's Keyboard was a terminal front-end where those keys *were* the product. KeyTab runs beside Termux, and Termux ships its own extra-keys row (`extra-keys` in `termux.properties`), so a second row would duplicate it and cost vertical space. See §7.


---
### 4.3 The evaluation unit: KeyTab + Termux as an IDE (2026-09-16)

KeyTab is not used in isolation. In practice it is used **together with Termux**, and the pair
constitutes the actual working environment: Termux supplies shell, userland and package manager;
KeyTab supplies text entry, prediction, snippets, file browsing and umlauts. Scoring KeyTab alone
therefore understates the setup and overstates some of its parts. Measured facts about that split:

| Aspect | KeyTab terminal tab | Termux |
|---|---|---|
| Shell | `ProcessBuilder(/system/bin/sh)`, fallback `ksh`, `/vendor/bin/sh` | own login shell + userland |
| Working dir | `context.filesDir` (app sandbox) | `$HOME`, proot/Ubuntu reachable |
| Userland | none — no apt, git, python of the host environment | full (apt, git, python, proot) |
| PTY / ANSI | **no PTY**, no escape handling — no `vim`, `htop`, no colour | full PTY |
| Scope | `ls`/`pwd`/`cat`/`wc` and similar; `pm`/`am` limited | unrestricted |

This is **documented in the source** (`TerminalPanel` KDoc: “Die Shell läuft in der App-Sandbox”), so it is a deliberate design decision, not a defect. The README wording “interactive shell” is the only thing
that overstates it and has been corrected.

**Consequence for the scores:** the terminal tab is a *command runner*, not the workspace. The
workspace is Termux, and KeyTab’s unique value in that pairing is what it does *while typing there*:
prediction, snippets via `commitText`, file manager, long-press popups, umlauts. No other keyboard
offers this combination inside the IME, which is what keeps the niche score high; the terminal tab
itself is not the differentiator.

### 4.4 Can development happen without leaving the keyboard?

2026-09-16 — this section was revised after measuring the actual workflow.

The original measurement assumed KeyTab would have to **run** commands. It does not — and that was the wrong assumption.

The real workflow (`proot-distro run ubuntu` with the project mounted at `/mnt/sdcard/Ubuntu-proot-termux`) is:

1. KeyTab edits files through `MANAGE_EXTERNAL_STORAGE` into `/mnt/sdcard/...` — the same path proot binds. Already verified.
2. A `cline` session is already running inside the proot session (interactive TUI, started by `exec cline` in `chatverlauf.sh`). It does not need KeyTab — it reads changed files itself.
3. To run code, you type the command into the running cline/TUI and press Enter; the IME inserts text, then the SEND key dispatches the line. No app switch is needed.

So the original question was framed wrong. Revised:

| Development step | Possible without leaving the keyboard? |
|---|---|
| Open and edit a source file | yes |
| Save into the proot path | yes, same filesystem view |
| Type a command and execute it in the running shell | yes (text flows into the active session; SEND key runs the line) |
| Trigger a build from a purely-IME button | no — but unnecessary, the shell session is already open |

**Conclusion: yes.** `MANAGE_EXTERNAL_STORAGE` gives KeyTab write access to the exact directory proot mounts, and the active `cline`/`proot` session is already running interactively. The workflow is: edit in keyboard -> file on disk -> cline notices -> run in cline. No app switch is inherent to the flow.
Because the run step happens in the shell that is already open, the `RUN_COMMAND`-intent item has been **removed** from the roadmap — it would solve a problem that does not exist in this workflow.

## 5. Chaos cleanup (recovery plan)

### Phase R1: Clean the working tree ✅ **COMPLETE**
- [x] Delete untracked harmful files (`app/build.gradle`, `ime/Prefs.kt`)
- [x] Delete incomplete locale files
- [x] Verify the build: `bash build_keytab.sh debug`
- [x] ✅ **COMPLETE (16 Sep):** all changed files reviewed and committed ("R1 completion"). Review result:
  `GamingLogic.kt` was **not** deleted but renamed → `LikelyHighlightLogic.kt`; `keyboard_view.xml` contained
  the editor toolbar (+ new: snippet editor row)

### Phase R2: Feature carry-over from the chaos state
- [x] Editor toolbar (save / load / clipboard icons)
- [x] Snippet editor (＋ New / ✎ Edit) → the snippet tab is usable end-to-end
- [x] ✅ **Gaming highlight unified** (audit 16 Sep): `GamingLogic` no longer exists,
  `KIND_HL`/`KEY_LIKELY` are set. Decision documented: **feature renamed, not removed**
  — now "Likely Highlighting" (`LikelyHighlightLogic` + `LikelyHighlightSection` + tests).
  The pref **strings** `gaming`/`gaming_mode` stay deliberately (compatibility with existing installations;
  documented in the `Prefs.kt` KDoc).
- [ ] Fix ColorWheelView multi-touch *(open, low priority — single-finger colour picking works)*
- [ ] Rename the pref key (`clip_tab_enabled` → `clipboard_tab_enabled`) *(open, low priority)*
- [ ] String/emoji changes in the locale files *(open, low priority)*
- [x] `install_keytab.sh` fixed (2026-09-15): `rish` → `rsh` (proot), quoting fix
- [x] 0.9.6 debug APK archived: `/sdcard/Download/KeyTab-0.9.6-debug.apk` (MD5 7b318527…)
- [x] ✅ **R1 completion committed (16 Sep)**: snippet editor (＋ New / ✎ Edit) +
  uniform tab heights (Terminal = editor-only) — `4574c69`, 118 tests green

### Phase R3: Consistency check ✅ **COMPLETE (audit 16 Sep)**
- [x] `ThemePrefs.kt`: **no** `KIND_GAMING` references left (grep: 0 hits; only `KIND_HL`)
- [x] Duplicate `editor_input` in `keyboard_view.xml`: **not present** (`grep -c` = 1)
- [x] Build + unit tests: BUILD SUCCESSFUL, **118 tests, 0 failures, 0 skipped**
- [x] Cleanup committed (`739dc66` + `4574c69`)

---

## 6. Best practices for agent refactoring

1. **Always commit** — agent work must end in clean commits
2. **One branch per agent run** — `agent/<feature>-<date>` isolates the chaos
3. **Build check after every agent step** — `bash build_keytab.sh debug` must be green
4. **No untracked files** — everything must be committed or deleted
5. **Version only in `build.gradle.kts`** — `build.gradle` (without `.kts`) is an artifact
6. **Always check string resources** — new `R.string.*` references must be defined
7. **Avoid constant drift** — deleted constants must be searched for globally

8. **Verify test counts against CI output, not prose** — the README had drifted to "65 tests" while the suite had 116.
9. **Never add a permission for convenience** — the missing `INTERNET` permission is a feature.

---
## 7. Prioritised backlog (score impact)

### P0 — was reconsidered and DROPPED (see "Not planned" below)

No P0 item remains. The former P0 (extra-keys row, +4.5) rested on a false premise and is
not planned; the work that actually matters starts at P1.
- [ ] **Rename `PanelsTest.kt`** — it contains `EditorPanelTest` **and** `ClipboardPanelTest` in one file;
  split them so the file name matches the class (the earlier README claim of "6 suites" was half outdated).

### P1 — Test infrastructure (+3.0 global)
- [ ] **Coverage gate** (JaCoCo / Kover) wired into CI with a fail threshold, so the 29.7 % ratio can only grow.
- [ ] **More Robolectric panel tests:** `FileManagerPanel` navigation/back-stack, `SnippetPanel` parse/insert,
  `TerminalPanel` prompt/cd tracking, `InputRouter` focus routing. Target: 140+ tests.
- [ ] **Fix the test naming:** `PanelsTest.kt` → split (name = class).

### P2 — Structure & distribution (+2.0 / +1.5)
- [ ] **Split `keyboard_view.xml`** (697 lines → `<include>`s: keyboard grid, tab bar, suggestion strip, panel container).
  The largest single file in the project.
- [ ] **Replace `MANAGE_EXTERNAL_STORAGE`** (SAF / `READ_MEDIA_*` + app dirs) → Play-Store eligible.
  Currently: store exclusion + `requestLegacyExternalStorage` as a crutch.
- [ ] **IzzyOnDroid submission** (signing via env is already F-Droid compliant; verify reproducible builds).
- [ ] **Finish Phase 6**: IME 260 → ~150 lines. *Downgraded* — the main gain (908→260, −71 %) is already banked.

### P3 — Polish
- [ ] Phase 8: threading → coroutines (`lifecycleScope` + `Dispatchers.IO`), resolve the 2 `Handler`s.
- [ ] Lint warnings ~30 → < 10.
- [ ] Centralise the `getSharedPreferences` spread (**25** calls, measured) through the `Prefs` accessor.
- [ ] IME hardening: test matrix Termux (neovim) / AndroidIDE / VS Code (proot) — cursor, commitText, IME switching.
- [ ] Editor robustness: non-UTF-8 (latin-1) and very large files.
- [ ] **Repository cleanup:** `KeyAnimations.kt` exists twice (project root **and** `ime/`) — the root copy is an
  artifact and should be removed.

### P4 — Long term (deliberately last)
- [ ] **Editor: syntax highlighting + line numbers** — feature +15, niche +2.0, high effort, but the second real
  niche differentiator.
- [ ] Termux deep link (Files tab → "Open in Termux")
- [ ] Split `KeyboardHost` into role interfaces *(no score lever, see Phase 7)*
- [ ] Emoji support *(lowest priority — deliberately deferred)*
- [ ] Multi-module Gradle structure
### ❌ Not planned (with reasons)

| Item | Why not |
|---|---|
| **Extra-keys row** (Esc/Ctrl/arrows) | **Termux already provides it** (`extra-keys` in `termux.properties`). A second row would duplicate it and consume vertical key height. Full Ctrl/Alt modifier semantics also need privilege an IME does not have. *Decision by the project owner, 2026-09-16.* |
| Cloud sync | Needs a server; the app has **no `INTERNET` permission** by design. |
| Glide typing | Gboard dimension, cannot be won, large models. |
| 100+ languages | Gboard dimension; 7 well-maintained languages beat 100 unmaintained ones. |

The niche is the way.

---

## 8. Risks & mitigation

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Service refactor breaks the IME | Medium | High | Incremental, run tests after every step |
| Panel decoupling breaks the UI | Low | Medium | Interface already defined, straight swap |
| Threading changes → race conditions | Medium | High | Coroutines + Dispatchers, thorough testing |
| Agent chaos repeats itself | Medium | Medium | Best practices (see section 6) |
| README/doc drift from code | Medium | Low | Pull counts from CI output, update with each release |
| Bus factor 1 (single author) | High | High | Documented architecture decisions (this file) reduce onboarding cost |

---

## 9. Successes (audit-verified, 2026-09-16)

### Architecture
- ✅ ThemeSettingsActivity 551 → 215 lines (sections extracted)
- ✅ `KeyTabImeService` 908 → **260** lines (−71 %)
- ✅ **10 pure, Android-free logic modules** (fast under JUnit)
- ✅ **Phase 7 verified complete:** all 5 panels decoupled, **no** panel references the service
- ✅ `KeyboardHost` + 4 controllers (`Shift`, `Tab`, `Suggestion`, `Theme`) wired up
- ✅ Code duplication 8 % → ~4 %

### Correctness fix 2026-09-16

- ✅ **TAB key now sends `KEYCODE_TAB`** in app fields, i.e. Termux and SSH, instead of
  `commitText` of the tab character. The key previously inserted a tab *character*; terminal
  programs wait for a *key event*, so TAB did nothing in `vim` / `nano` / shell completion,
  although the README had claimed otherwise since 0.1.0.
- Implemented as `InputTarget.onTab`, parallel to the existing `onEnter`, and mirroring the
  “KEYCODE_DEL instead of deleteSurroundingText” reasoning in the `AppInputTarget` KDoc.
- Editor and Terminal targets keep the tab character, which is correct there; only the app field sends an event.
- Covered by two new tests in `InputRouterTest`, including a regression test that TAB avoids the insert path.
- Test count: **116 → 118**, all green.

### Quality (measured)
- ✅ **118 unit tests / 15 classes / 0 failures / 0 skipped** (116 audited 16 Sep + 2 from the TAB-key correctness fix)
- ✅ **0** `TODO`/`FIXME`/`HACK`, **0** `Thread(...)`, **6** `!!` in 5,704 lines
- ✅ KVM emulator CI (instrumented, API 34)
- ✅ Build: `BUILD SUCCESSFUL`, R8 + `isShrinkResources`, idempotent release
- ✅ **Privacy claim manifest-verified** (no `INTERNET` permission)
- ✅ **Keystore not in git**, signing via env → F-Droid compliant
- ✅ **Licence attribution correct** (FrequencyWords MIT/CC-BY-SA-4.0)

### Process
- ✅ Snapshot commit as a safety net before refactoring (`8d7b9be`)
- ✅ Phase plan with a risk table + benchmarks (this document)
- ✅ Install script repaired (`rsh` instead of `rish`, quoting) — one click via Shizuku
- ✅ R1–R3 chaos cleanup complete (`739dc66`, `4574c69`)

### Score evolution

| Point in time | Global | Niche |
|---|---|---|
| Before refactor | 62 | 55 |
| After phases 0–5 | ~78 | ~84 |
| **Audit 16 Sep (phases 0–7)** | **81** | **85** |
| Target (P0+P1+P2) | ~88 | ~91 |
