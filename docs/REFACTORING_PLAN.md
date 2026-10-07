# KeyTab — Refactoring Plan: open Todos

Status: 2026-09-25 · Goal: maintainable, testable, release-ready modules with no unintended behaviour change.

> **This file tracks only OPEN work.** Everything completed — measured audits, phases 0–7,
> chaos cleanup R1–R3, evaluation model, external review (74/100 vs. internal 82/100),
> score evolution, decision records — lives in the archive:
> **[`REFACTORING_HISTORY.md`](REFACTORING_HISTORY.md)**.

---

## 1. Current snapshot (2026-09-28)

> ⚠️ **Snapshot veraltet** (Stand 2026-09-28, wird vom Doku-Drift-Wächter nicht
> geprüft). Aktueller Stand **v0.15** (2026-09-30): **501 Unit-Tests / 52 Suiten**,
> Kover **70,8 % Line / 55,7 % Branch**, Test:Main-Ratio **68,9 %** (7.462/10.832),
> detekt-Baseline **47 Einträge**, i18n **141/141 Strings**, Service **351 Zeilen**.
> Maßgeblich: `README.md` + `scripts/check_docs_drift.sh` (grün) + `docs/BASELINE.md`.

| Metric | Value |
|---|---|
| Unit tests | **526 in 54 suites**, 0 failures (`testDebugUnitTest` + `testReleaseUnitTest` + `koverVerify` green 28 Sep; +28 ggü. 498: `ColorWheelInputLogicTest` (18), `SwipePreviewAvailabilityTest` (6), `MainActivitySettingsTest` 1→5) |
| Coverage (Kover) | **70.8 % line** (3228/4561) / **55.7 % branch** (1775/3186), measured 28 Sep; **Gate: 60 % line / 45 % branch** — `koverVerify` green same run |
| Test:main ratio | **71.3 %** (7,789 test lines / 10,918 main lines; 75 main files, 54 test files) — re-measured 29 Sep |
| detekt baseline | **152 Einträge** (re-measured 27 Sep; 181 in the repo, of which **24 were already stale**) — unchanged 28 Sep, gate green |
| Android Lint | **0 errors / 121 warnings** (`lintDebug` 28 Sep; CI-Gate added 24 Sep) |
| i18n | `values-en` **149/149 Strings** (100 %, per Drift-Gate erzwungen) |
| Service size | **369 lines** (from 908) — re-measured 28 Sep; the earlier "358" figure was stale |
| Working tree | **v0.14** plus: height/maximize fixes (abc start state, IME-window-filling maximize, equal normal heights, shorter maximize row, no max row in the editor), the **defaults takeover** from the user config (`keytab_config.txt`, 2026-09-28) and **per-tab heights in the config** (`normal_height_*` / `max_height_*`) |
| Repo hygiene | Local SDK/signing files are ignored; Gradle distribution SHA-256 pinned |

> **Baseline-Neumessung (27.09):** Die eingecheckte Baseline nannte 181 Befunde,
> tatsächlich existieren **157** — 24 Einträge zeigten auf Code, der es nicht
> mehr gibt (u. a. `CATALOG_PER_PAGE`, das nach `SuggestionEmojiBrowser`
> umgezogen ist). Eine Baseline, die nicht mehr auflösbare IDs enthält, ist
> kein Schutz: neue Befunde mit *bekanntem* Muster werden stillschweigend
> durchgewunken. Baseline deshalb nach Struktur-Umbauten neu erzeugen
> (`:app:detektBaseline`) statt sie zu pflegen.
>
> Snapshot-Tabelle und Code-Stand werden **nicht** vom Doku-Drift-Wächter
> geprüft — der vergleicht README ↔ Code. Diese Tabelle daher bei jedem
> Struktur-Commit (neue Klasse, Test-Datei, Zeilen im Service) nachziehen.

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
- [ ] Reduce real detekt findings in `KeyboardBinder`, `KeyTabImeService`, `SuggestionController`, `ThemePrefs` and `ClipboardPanel`; do not chase the baseline quota mechanically. **Progress 27 Sep:** `KeyboardBinder` done — the label layout (5 Magic Numbers + the cyclomatic complexity) moved to `LetterLabelComposer`, covered by 7 new tests; verified finding delta **exactly −5, +0 new**. Remaining: `KeyTabImeService` (3), `SuggestionController` (15), `ThemePrefs` (9); `ClipboardPanel` is already finding-free.
- [x] Gradle dependency verification enabled and the verification metadata completed for the Lint toolchain (v0.14).
- [ ] Test a clean-cache resolution on a fresh machine.
- [x] Pin GitHub Actions to immutable commit SHAs with a documented update process (`docs/SUPPLY_CHAIN.md`; checkout/setup-java/upload-artifact/emulator-runner all on 40-hex SHAs).
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

- [x] Reproducible-Build-Verifikation (`scripts/verify_reproducible.sh`, v0.15 und v0.17 bit-identisch) und F-Droid-Antrag (MR `!50822`, Pipeline grün). **IzzyOnDroid ist abgelehnt** (deren AI-Policy verbietet LLM-generierten Code, Issue #684 vom 2026-10-07) — dort kein weiterer Antrag.
- [ ] `targetSdk 35` after API 35/AGP toolchain is available; treat this as release maintenance, not a refactor.
- [ ] Editor robustness for non-UTF-8 and very large files.
- [ ] Consider Termux deep link, Emoji support and a multi-module build only as separate product decisions.
- [ ] Do not implement these by changing the current refactor priority.

## 5. Done already — do not repeat

- Coverage gate: 60% line / 45% branch; measured 67.0% / 53.8%.
- 477 unit tests in 51 suites; panel/security regression tests, including the InputRouter cross-panel transition test.
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
