# KeyTab — Refactoring Plan: open Todos

Status: 2026-09-25 · Goal: maintainable, testable, release-ready modules with no unintended behaviour change.

> **This file tracks only OPEN work.** Everything completed — measured audits, phases 0–7,
> chaos cleanup R1–R3, evaluation model, external review (74/100 vs. internal 82/100),
> score evolution, decision records — lives in the archive:
> **[`REFACTORING_HISTORY.md`](REFACTORING_HISTORY.md)**.

---

## 1. Current snapshot (2026-09-25)

| Metric | Value |
|---|---|
| Unit tests | **470 in 50 suites**, 0 failures (`testDebugUnitTest` verified 25 Sep; includes the new InputRouter cross-panel transition test) |
| Coverage (Kover) | **67.0 % line** (2947/4401) / **53.8 % branch** (1742/3238), measured 22 Sep; **Gate: 60 % line / 45 % branch** |
| Test:main ratio | **75.4 %** (6,866 test lines / 9,111 main lines; 56 main files) |
| detekt baseline | **181 Einträge** (von 191 reduziert, v0.14); new findings remain CI-blocking |
| Android Lint | **0 errors / 174 warnings**; explicit CI gate added 24 Sep |
| i18n | `values-en` **145/145 Strings** (100 %, per Drift-Gate erzwungen) |
| Service size | 390 lines (from 908) |
| Working tree | **v0.14** plus verified editor and Clipboard coroutine changes (commits `80549e5`) |
| Repo hygiene | Local SDK/signing files are ignored; Gradle distribution SHA-256 pinned |

---

## 2. Open work — priority by measurable user/release value

### P0 — prove the real IME contract
- [ ] Run `KeyTabImeEndToEndTest` on a runnable CI emulator; **the CI job is prepared** with a bounded boot wait and failure diagnostics, but the API-34 result is not yet verified locally.
- [ ] Verify character, Space, Tab, Enter and Backspace through an active IME against plain text and `EditText` targets.
- [ ] Verify password and `IME_FLAG_NO_PERSONALIZED_LEARNING`: no prediction, autocorrect, learning, swipe trail or cross-field context leak.
- [x] Add rotation, configuration-change and service-recreation scenarios (`activityRecreation_keepsImeUsableForNewField`).
- [x] Add the remaining `InputRouter` cross-panel routing regression test.

- **Done when:** CI proves the service lifecycle and sensitive-field contract, not only JVM/Robolectric tests.

### P1 — privacy, accessibility and failure behaviour
- [ ] Add TalkBack/Accessibility Scanner coverage for keyboard controls and file/editor panels.
- [x] Triage: unbenutzte Ressourcen entfernt (48 → 21), Lint wieder fehlerfrei.
- [ ] Triage der verbleibenden Lint-Warnungen; fix accessibility, hardcoded text, plural and unused-resource warnings first.
- [ ] Add clipboard session-only/retention controls, selective delete and a clear privacy disclosure.
- [ ] Replace silently swallowed clipboard/file exceptions with safe logging or a recoverable, localized error.
- [ ] Rotate any signing password ever stored in local plaintext and restrict production signing to CI secrets.
- **Done when:** warning categories have a tracked budget, no critical accessibility/privacy issue remains, and failure paths are visible.

### P2 — maintainability and supply chain
- [ ] Reduce real detekt findings in `KeyboardBinder`, `KeyTabImeService`, `SuggestionController`, `ThemePrefs` and `ClipboardPanel`; do not chase the baseline quota mechanically.
- [x] Gradle dependency verification enabled and the verification metadata completed for the Lint toolchain (v0.14).
- [ ] Test a clean-cache resolution on a fresh machine.
- [ ] Pin GitHub Actions to immutable commit SHAs with a documented update process.
- [x] Release workflow publishes an SHA-256 checksum next to the APK (v0.14).
- [ ] Add SLSA provenance/attestation for the release artifact.
- **Done when:** clean and warm builds resolve verified inputs and no new baseline IDs are accepted silently.

---

## 3. Conditional refactors — only when they remove a real problem

- [ ] `FileManagerPanel` → lifecycle-aware I/O, one module at a time; keep bounded executor/Handler use where it is already correct.
- [ ] `SuggestionEngine` → split only measured CPU-heavy operations; do not convert the whole engine to `suspend` without a device/emulator benchmark and concurrency tests.
- [ ] Extract remaining `KeyTabImeService` orchestration only to enable a concrete test or remove real coupling; no arbitrary LOC target.
- [ ] Fix `ColorWheelView` multi-touch; migrate `clip_tab_enabled` with a tested fallback.
- **Done when:** each change has a before/after metric and regression test; otherwise it stays deferred.

## 4. Product/distribution backlog — separate from refactoring

- [ ] IzzyOnDroid submission and reproducible-build verification.
- [ ] `targetSdk 35` after API 35/AGP toolchain is available; treat this as release maintenance, not a refactor.
- [ ] Editor robustness for non-UTF-8 and very large files.
- [ ] Consider Termux deep link, Emoji support and a multi-module build only as separate product decisions.
- [ ] Do not implement these by changing the current refactor priority.

## 5. Done already — do not repeat

- Coverage gate: 60% line / 45% branch; measured 67.0% / 53.8%.
- 470 unit tests in 50 suites; panel/security regression tests, including the InputRouter cross-panel transition test.
- Service reduced from 908 to 390 lines; `KeyTabExecutors` and Clipboard coroutine persistence are in place.
- 14 instrumented tests in 375 lines cover the real IME contract; API-34 execution remains CI verification.
- Permission minimization, `LearnedDictionaryApi` decision, CI reports and docs-drift gate.
- See [`REFACTORING_HISTORY.md`](REFACTORING_HISTORY.md) for completed phases, audits and measurements.

---

## 6. Risks & mitigation

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Threading changes → race conditions | Medium | High | One module, tests and lifecycle checks after each step |
| Coroutine migration breaks panels | Low | Medium | Keep correct existing executors; benchmark before API changes |
| Pref-key rename breaks existing installs | Medium | Low | Read old key with tested fallback |
| README/doc drift | Medium | Low | `scripts/check_docs_drift.sh` CI gate |
| Bus factor 1 | High | High | History/Plan split, small commits, explicit handoffs |

## 7. Working rules

1. Every change must name its user, release or maintenance benefit.
2. Build and test after each module; no broad speculative refactor.
3. Verify test counts and metrics from CI output, not hand-written prose.
4. Never add a permission for convenience; no `INTERNET` permission is a product feature.
5. Move completed work to [`REFACTORING_HISTORY.md`](REFACTORING_HISTORY.md); keep this file canonical for open work.
6. The refactor cycle is complete when P0–P2 acceptance criteria pass; conditional and product backlog items may remain deferred.

## 8. Review outcome

- The previous plan was directionally right but mixed verified runtime work, optional refactors, product features and score targets.
- The revised order prioritizes observable IME correctness, privacy/accessibility and release integrity before cleanup.
- Scores remain descriptive context, not acceptance criteria.
