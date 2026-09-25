# KeyTab — Refactoring Plan: open Todos

Status: 2026-09-25 · Goal: maintainable, testable, release-ready modules with no unintended behaviour change.

> **This file tracks only OPEN work.** Everything completed — measured audits, phases 0–7,
> chaos cleanup R1–R3, evaluation model, external review (74/100 vs. internal 82/100),
> score evolution, decision records — lives in the archive:
> **[`REFACTORING_HISTORY.md`](REFACTORING_HISTORY.md)**.

---

## 1. Current snapshot (2026-09-24)

| Metric | Value |
|---|---|
| Unit tests | **492 in 50 suites**, 0 failures (`testDebugUnitTest` verified 25 Sep) |
| Coverage (Kover) | **67.0 % line** (2947/4401) / **53.8 % branch** (1742/3238), measured 22 Sep; **Gate: 60 % line / 45 % branch** |
| Test:main ratio | **75.3 %** (7,183 test lines / 9,543 main lines; 57 main files) |
| detekt baseline | **191 Einträge**; new findings remain CI-blocking |
| Android Lint | **0 errors / 174 warnings**; explicit CI gate added 24 Sep |
| i18n | `values-en` **185/185 Strings** (100 %, per Drift-Gate erzwungen) |
| Service size | 432 lines (from 908) |
| Working tree | **v0.12** plus verified editor and Clipboard coroutine changes (commits `80549e5`) |
| Repo hygiene | Local SDK/signing files are ignored; Gradle distribution SHA-256 pinned |

---

## 2. Open phases

### Phase 6 (remainder) — IME orchestration *(low priority, no arbitrary LOC target)*
- [ ] Extract orchestration only when it enables a concrete test or removes real coupling:
  panel lifecycle (`releasePanels()`), `commitText`/`commitToApp` routing and `KeyboardHost` delegation.
- [ ] Measure changed cyclomatic complexity and baseline findings before/after; do not pursue a line-count quota.
- Main size reduction (908→424) is banked; **do not trade test work for this**.

### Phase 8 — Threading & coroutines *(open)*
- [x] Part 1 done: `KeyTabExecutors` centralises the pools; the 2 `Handler`s are centralised, not gone.
- [x] `ClipboardPanel` migrated to a service-owned lifecycle-aware coroutine scope; I/O runs on `Dispatchers.IO`, UI/error callbacks stay on the main scope.
- [ ] `SuggestionEngine` → `suspend fun` with `Dispatchers.Default`
- [ ] `FileManagerPanel` → lifecycle-aware I/O
- Risk: medium (race conditions) — refactoring for consistency, **must not block higher-impact work**.

---

## 3. Chaos-cleanup leftovers (R2)

- [ ] Fix `ColorWheelView` multi-touch *(single-finger picking works; low priority)*
- [ ] Rename pref key `clip_tab_enabled` → `clipboard_tab_enabled` *(needs migration for existing installs)*
- [ ] String/emoji changes in the locale files

---

## 4. Backlog (open items)

### P1 — Test infrastructure (+3.0 global)
- [x] **Coverage-Gate verschärft (22 Sep):** Kover `koverVerify` erzwingt jetzt
  **60 % Zeilen- / 45 % Branch-Coverage** statt 20 % (gemessen: 67,0 / 53,8).
  Zusätzlich deckt das Doku-Gate Größen-/Sprachzahlen ab (Ratio, Service-Zeilen,
  `values`⇔`values-en`-Parität).
- [x] **Robolectric panel tests (21 Sep):** `SnippetPanelTest` (5: parser rules, defaults file,
  tap→commit, recent-history), `TerminalPanelTest` (10: prompt, cd tracking + invalid-target
  fallback, insert/delete/delete(word), empty command), `FileManagerPanelTest` (5: navigation,
  back-stack, up, file-tap commit, `fm_dir`/`fm_backstack` persistence).
  Historical snapshot: 484 tests / 50 suites. The suite now has 491 tests; remaining from
  the original P1 list: `InputRouter` cross-panel routing.
- [x] **Sicherheits-Vertrag für gesperrte Felder (22 Sep):** `WordPredictionManagerPrivacyTest`
  (8 Tests, je Pfad Sperr- + Gegenprobe) + Regel-Matrix in `TrailLogicTest` +
  Emoji-Katalog-Gate in `SuggestionControllerTest`.
- [x] **Clipboard persistence errors are observable (24 Sep):** `ClipboardPanel` logs
  load/save failures and forwards save errors to a localized UI callback; `ClipboardPanelTest`
  covers the failure path (10 tests green). Session-only retention remains open.

### P2 — Structure & distribution (+2.0 / +1.5)
- [x] `keyboard_view.xml` split, `MANAGE_EXTERNAL_STORAGE` replaced — done (History)
- [x] **Permissions minimiert (22 Sep):** `READ_MEDIA_AUDIO`/`READ_MEDIA_VIDEO` entfernt
  (kein Codepfad liest Audio/Video); Feature→Permission-Zuordnung steht im Manifest.
- [x] **`LearnedDictionaryApi` entschieden (22 Sep):** `internal`, kein ContentProvider
  (Begründung: `docs/API_INTERFACE_EXTERNAL_CLEANUP.md` §0). Entfernung bleibt Option.
- [ ] **IzzyOnDroid submission** (signing via env is F-Droid compliant; verify reproducible builds)
- [ ] **`targetSdk 35`** (Play-Vorgabe für Neu-Releases) — in diesem Build-Umfeld blockiert:
  installiert ist nur `platforms/android-34` + AGP 8.5.2; `compileSdk 35` braucht das
  API-35-Platform-Paket und AGP ≥ 8.6.

### P3 — Polish
- [x] Threading part 1, prefs centralisation, KeyboardHost role split, docs/versioning single
  source, detekt Quick-Wins (21 Sep) — done (History)
- [x] **Performance (22 Sep):** Prefix-Vervollständigung läuft über `byFirstChar` statt
  Voll-Scan über ~6.000 Wörter (case-insensitiver Index, Test in `SuggestionEngineTest`).
- [x] **Hygiene (22 Sep):** 16 `build_*.log` entfernt, Root-Artefakte nach `../archive/`.
- [ ] Lint warnings ~30 → **< 10**
- [ ] **IME hardening test matrix:** Termux (neovim) / AndroidIDE / VS Code (proot) —
  cursor, commitText, IME switching
- [ ] Editor robustness: non-UTF-8 (latin-1) and very large files

### P4 — Long term
- [x] Editor line numbers, heuristic syntax highlighting, KeyboardHost role split — done (History)
- [ ] Termux deep link (Files tab → "Open in Termux")
- [ ] Emoji support *(lowest priority)*
- [ ] Multi-module Gradle structure

> **Decision record (do NOT plan):** extra-keys row, cloud sync, glide typing, 100+ languages —
> reasons in [History, Appendix](REFACTORING_HISTORY.md#appendix-not-planned-decision-record-from-7).

---

## 5. detekt baseline — hard remainder (191 findings, measured 24 Sep)

| Rule | Count | Approach |
|---|---:|---|
| `MagicNumber` | 139 | Review as policy/config debt; change only where names improve domain clarity |
| `CyclomaticComplexMethod` | 17 | Extract named predicates/routing helpers with unit tests |
| `LoopWithTooManyJumpStatements` | 9 | Extract loop bodies/state transitions |
| `NestedBlockDepth` | 7 | Early returns and guard clauses |
| `LongMethod` | 7 | Extract cohesive operations, pairing with complexity work |
| `ComplexCondition` | 5 | Named boolean helpers |
| `TooManyFunctions` | 3 | Split only cohesive service/activity responsibilities |
| Other | 4 | `VariableNaming`, `SwallowedException`, `NewLineAtEndOfFile`, `LongParameterList` |

Strategy: reduce **real** complexity findings before cosmetic `MagicNumber` cleanup. One cycle may
pair cyclomatic complexity and long methods, then nested depth and conditions. Never regenerate the
whole baseline merely to make a count look better; compare IDs before and after each cycle.

---

## 6. External-review levers still open

| Rank | Lever | Acceptance criterion |
|---|---|---|
| 1 | detekt baseline reduction | Real complexity IDs removed without suppression; baseline decreases in reviewed commits |
| 2 | Real IME integration tests | API-34 tests commit characters, Tab, Enter, Backspace and sensitive-field behavior through an active IME |
| 3 | Android Lint backlog | 0 errors maintained; warning baseline categorized and reduced from 174, prioritizing accessibility/resources |
| 4 | Clipboard privacy | Session-only mode or expiry, selective deletion, transparent user-facing disclosure, robust I/O errors |
| 5 | Supply-chain hardening | Dependency locking/verification and immutable action references; release SHA-256/provenance published |
| 6 | Signing hygiene | Production credentials only in CI/secret store; locally exposed passwords rotated |

Coverage and static-analysis reports are now uploaded with `if: always()`, so that former cheap CI
lever is closed. Values-en completeness is closed at 184/184.

---

## 7. Prioritised execution plan (not implemented in this pass)

### P0 — real IME confidence
- [x] **Real IME E2E harness compiled (24 Sep):** `ImeTargetActivity` and
  `KeyTabImeEndToEndTest` cover character, Space, Tab, Enter, Backspace, password and
  `NO_PERSONALIZED_LEARNING` behavior. Device execution is blocked on the connected
  Android-16 device by `INSTALL_FAILED_USER_RESTRICTED`; rerun on a CI emulator or a
  device allowing ADB test-package installation.
- [ ] Verify commit paths for characters, Space, Tab, Enter and Backspace against plain and
  `EditText` targets on a runnable emulator.
- [ ] Verify password and `IME_FLAG_NO_PERSONALIZED_LEARNING` on-device: no prediction, autocorrect, learning, swipe trail or cross-field context leak.
- [ ] Add rotation, configuration-change and service-recreation scenarios.
- **Done when:** CI proves the real service lifecycle and sensitive-field contract, not only activity smoke tests.

### P1 — security and accessibility
- [ ] Add TalkBack/Accessibility Scanner coverage for keyboard controls and file/editor panels.
- [ ] Triage the 174 Lint warnings; fix accessibility, hardcoded text, plural and unused-resource warnings first.
- [ ] Add clipboard session-only/retention controls, selective delete and an explicit privacy disclosure.
- [ ] Stop silently swallowing clipboard/file exceptions; log safely or surface a recoverable error.
- [ ] Rotate any signing password ever stored in local plaintext and restrict production signing to CI secrets.
- **Done when:** Lint warnings have a tracked budget and no critical accessibility/privacy issue remains.

### P2 — maintainability and supply chain
- [ ] Reduce real detekt findings in `KeyboardBinder`, `KeyTabImeService`, `SuggestionController`, `ThemePrefs` and `ClipboardPanel`.
- [ ] Enable dependency locking and Gradle dependency verification; test clean-cache resolution.
- [ ] Pin GitHub Actions to immutable commit SHAs with an update process.
- [ ] Publish release APK SHA-256 and provenance/attestation.
- **Done when:** clean and warm builds resolve identical verified inputs and no new baseline IDs are accepted silently.

### P3 — deferred refactors
- [ ] Migrate bounded I/O from shared executors/handlers to lifecycle-aware coroutines one module at a time.
- [ ] Fix `ColorWheelView` multi-touch and migrate `clip_tab_enabled` with a tested fallback.
- [ ] Consider PTY support for the terminal; retain the documented non-PTY limitation until implemented.

---

## 8. Risks & mitigation

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Threading changes → race conditions | Medium | High | Coroutines + Dispatchers, thorough testing |
| Coroutines migration breaks panels | Low | Medium | One panel per step, tests after each |
| Pref-key rename breaks existing installs | Medium | Low | Migration read of old key with fallback |
| README/doc drift from code | Medium | Low | `scripts/check_docs_drift.sh` CI gate |
| Bus factor 1 | High | High | This documentation; ARCHIVE + PLAN split |

---

## 9. Best practices for agent refactoring

1. **Always commit** — agent work must end in clean commits
2. **One branch per agent run** — `agent/<feature>-<date>` isolates the chaos
3. **Build check after every agent step** — `bash build_keytab.sh debug` must be green
4. **No untracked files** — everything must be committed or deleted
5. **Version only in `build.gradle.kts`** — `build.gradle` (without `.kts`) is an artifact
6. **Always check string resources** — new `R.string.*` references must be defined
7. **Avoid constant drift** — deleted constants must be searched for globally
8. **Verify test counts against CI output, not prose**
9. **Never add a permission for convenience** — the missing `INTERNET` permission is a feature
10. **History is append-only, Plan is the todo list** — completed items move to
    [`REFACTORING_HISTORY.md`](REFACTORING_HISTORY.md), never deleted

---

## 10. Score targets

| Frame | Current | Target | Levers |
|---|---|---|---|
| Internal | 82 | ~88 global / ~91 niche | P1 + P2 + P3 |
| External | 72 | ~85 | Real IME tests, Lint backlog, clipboard privacy, supply-chain/signing hardening, baseline reduction |
