# KeyTab — Refactoring Plan: open Todos

Status: 2026-09-21 · Goal: maintainable, testable modules with no behaviour change.

> **This file tracks only OPEN work.** Everything completed — measured audits, phases 0–7,
> chaos cleanup R1–R3, evaluation model, external review (74/100 vs. internal 82/100),
> score evolution, decision records — lives in the archive:
> **[`REFACTORING_HISTORY.md`](REFACTORING_HISTORY.md)**.

---

## 1. Current snapshot (2026-09-22)

| Metric | Value |
|---|---|
| Unit tests | **484 in 50 suites**, 0 failures (verified 22 Sep on the device; `testDebugUnitTest` + `detektDebug` + `coverageGate` green) |
| Coverage (Kover) | **67.0 % line** (2947/4401) / **53.8 % branch** (1742/3238), measured 22 Sep (XML report, debug+release); **Gate: 60 % line / 45 % branch**; `sections` 93.6 %, `file` 77.9 %, `ime` 68.7 % |
| Test:main ratio | **74.5 %** (7,038 test lines / 9,450 main lines; 57 main files) |
| detekt baseline | **198 Einträge** (unverändert; Gate grün) |
| i18n | `values-en` **170/170 Strings** (100 %, per Drift-Gate erzwungen) |
| Service size | 417 lines (from 908) |
| Working tree | v0.11 + Passwort-Sperre + Gate-/Wächter-/Cleanup-Paket — **committet 22 Sep** |
| Repo hygiene | `KeyAnimations.kt` duplication resolved; `build_*.log` entfernt, Root-Artefakte in `../archive/` |

---

## 2. Open phases

### Phase 6 (remainder) — IME ~290 → ~150 lines *(downgraded, low priority)*
- [ ] Extract the remaining ~110 lines of pure wiring: panel lifecycle (`releasePanels()`),
  `commitText`/`commitToApp` routing (`editorActive`/`terminalActive`), `KeyboardHost` delegation.
- Main gain (908→~290) is banked; do **not** trade test work for this.

### Phase 8 — Threading & coroutines *(open)*
- [x] Part 1 done: `KeyTabExecutors` centralises the pools; the 2 `Handler`s are centralised, not gone.
- [ ] `SuggestionEngine` → `suspend fun` with `Dispatchers.Default`
- [ ] `FileManagerPanel` → `lifecycleScope.launch` for I/O
- [ ] `ClipboardPanel` → `lifecycleScope.launch` for clipboard access
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
  Suite total **484 tests / 50 suites**. Still open from the original P1 list:
  `InputRouter` focus routing has `InputRouterTest` (8) but no cross-panel routing case.
- [x] **Sicherheits-Vertrag für gesperrte Felder (22 Sep):** `WordPredictionManagerPrivacyTest`
  (8 Tests, je Pfad Sperr- + Gegenprobe) + Regel-Matrix in `TrailLogicTest` +
  Emoji-Katalog-Gate in `SuggestionControllerTest`.
- [ ] **Instrumented swipe/touch integration test** (external lever #2, +2.0): IME touch
  interaction + swipe path — only 2 instrumented tests exist today

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

## 5. detekt baseline — the hard remainder (198 entries; 156 legacy + 42 new v0.11 findings)

| Rule | Count | Approach |
|---|---|---|
| `MagicNumber` | 111 | Config decision: raise thresholds / add `ignoreAnnotated` for UI constants — do **not** pollute code with 111 named constants |
| `CyclomaticComplexMethod` | 14 | Real smell; extract predicate helpers (external lever #1 remainder, ~+1.0) |
| `LoopWithTooManyJumpStatements` | 8 | Extract loop bodies into helpers |
| `NestedBlockDepth` | 6 | Early-return / guard clauses |
| `LongMethod` | 4 | Function extraction (pairs with CCM work) |
| `ComplexCondition` | 4 | Named boolean helpers |
| `EmptyFunctionBlock` | 7 | Anonymous listeners `{}` — replace with SAM/interface or targeted `@Suppress` |
| `TooManyFunctions` | 2 | Service + MainActivity; folds into Phase 6 |
| `MaxLineLength`/misc | 0–2 | Ongoing |

Strategy: one cycle = CCM+LongMethod together, then NestedBlockDepth+ComplexCondition,
then decide MagicNumber via config. The 42 new v0.11 findings (SwipeManager,
SuggestionEngine/WordPredictionManager prediction lines, `hideKeyboard` growth) were
baselined by regeneration on 21 Sep — they join the same cleanup cycles.

---

## 6. External-review levers still open (§8.3 of History)

| Rank | Lever | Est. score | Tracked in |
|---|---|---|---|
| 1 | detekt baseline reduction (hard cases) | +2.0 | §5 above |
| 2 | Instrumented swipe/touch tests | +2.0 | P1 above |
| 3 | ~~`values-en` completeness~~ **DONE 2026-09-21: 176/176 strings** | ✅ | i18n item closed |
| 4 | Kover report as CI artifact **on success** too (not only on failure) | +1.0 (partial) | CI tweak, cheap |

---

## 7. Risks & mitigation

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Threading changes → race conditions | Medium | High | Coroutines + Dispatchers, thorough testing |
| Coroutines migration breaks panels | Low | Medium | One panel per step, tests after each |
| Pref-key rename breaks existing installs | Medium | Low | Migration read of old key with fallback |
| README/doc drift from code | Medium | Low | `scripts/check_docs_drift.sh` CI gate |
| Bus factor 1 | High | High | This documentation; ARCHIVE + PLAN split |

---

## 8. Best practices for agent refactoring

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

## 9. Score targets

| Frame | Current | Target | Levers |
|---|---|---|---|
| Internal | 82 | ~88 global / ~91 niche | P1 + P2 + P3 |
| External | 74 | ~85 | §6 levers (detekt, instrumented tests, i18n, CI artifact) |
