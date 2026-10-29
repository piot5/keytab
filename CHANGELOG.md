# Changelog

All notable changes to KeyTab, newest first.

Format follows [Keep a Changelog](https://keepachangelog.com/1.1.0/); versioning follows [Semantic Versioning](https://semver.org/).

The version is **not** maintained here, but in `app/build.gradle.kts` (`versionCode` / `versionName`). `scripts/check_docs_drift.sh` (CI job "docs") checks on every push that this changelog's head, the fastlane changelog, and the Gradle version match.

## 0.21

- **Versioning corrected (F-Droid build failed).** `v0.19` was tagged on a commit that already carried `versionName 0.20` — F-Droid's `checkupdates` therefore reported "0.20 in the 0.19 tag" and the metadata update failed ([issue #1](https://github.com/piot5/keytab/issues/1)). Tags are immutable by ruleset, so `v0.19` stays as it is (its release notes are marked as a mis-tag); **0.21 (versionCode 37) is the first correctly tagged release after 0.18.1**.
- **Fixes on top of 0.20** (0.20 brought Cline companion mode, the Shift-independent long-press row, the ß removal and the double-width Backspace — see below):
  - **Trace marking usable again.** `trail_trace` was forced to `false` on every read and even rewrote the stored value, so the green match marking could not be switched on at all. The switch honours the stored value again; the default stays **off**.
  - **"Learned dictionary" button works again.** Its wiring was lost in the 0.20 settings refactor — the button in the settings did nothing.
  - **Editor tab, as it actually ships:** the four companion switches are gone from the settings, so the editor-tab badge now simply shows the number of open files and the editor-tab long-press menu (close all / new file / save / reload) is available whenever the editor panel exists. Only auto-load stays gated (`cline_companion_mode` + `cline_auto_load`, both off by default, settable via `keytab_config.txt`).
- **CI repaired (was red since 2026-10-22):** unit-test compilation (the panel takes `FileManagerPanel.FileManagerCallbacks`), the four `TermuxKeyMatrix` tests (JUnit 4 cannot run top-level test functions), the settings key count, detekt findings and the editor/trace expectations are green again — and the instrumented test source set compiles again (`UiObject2` has no `isVisible()`, only `getVisibleBounds`). New regression test for the file long-press menu (the `BadTokenException` crash from 2026-10-12).
- 568 unit tests in 64 classes, 0 failures

- **Cline Companion Mode (optional).** KeyTab now detects when an external agent session (Cline CLI) writes files and loads them automatically into the editor tab. Three independent sub-switches, all **off** by default and bundled under the main switch "Cline companion mode": **Auto-Load** (file polling, 1 s), **File badge** (number of open files on the editor tab), and **Tab context menu** (long-press on editor tab → Close all / New file / Save / Reload). KeyTab remains tool-neutral: the mode only watches the filesystem and knows nothing about Cline sessions, APIs, or tokens.
- **Long-press row independent of Shift/Caps.** Previously the Shift state provided the uppercase row (`extras['A']`), while keys without an uppercase entry fell back to the lowercase row: the same key showed something different depending on Shift ("Ä …" instead of "ä …", but for `m`/`r` still "&"/"."). Now the same row applies at the base key everywhere; `LetterCaseRenderer.extrasFor` is the only source of language data — language files no longer contain uppercase entries (dead data).

## 0.19

- **Config file import now preserves unknown keys.** `SettingsConfig` previously dropped keys it did not recognize when rewriting `keytab_config.txt`. Unknown keys are now carried through unchanged.
- **Theme settings: `tab_highlight_mode` duplicated entry removed.** The key was listed twice with contradictory descriptions; now appears once (`active_tab` = bar above selected tab, default; `suggestion_top` = suggestion on top, no bar — experimental, no bar expression yet).
- **Swipe color keys:** `theme_dark_swipe` / `theme_dark_swipe_edge` (and light variants) added for swipe path nodes and edges. `default` removes the override.

## 0.18.1

- **Release build fixed (aapt2 dependency verification).** The release workflow for v0.18 broke in `compileReleaseNavigationResources` with `Dependency verification failed`: `gradle/verification-metadata.xml` was missing checksums for `com.android.tools.build:aapt2:8.13.2-14304508` (introduced by AGP 8.13.2 upgrade). Locally this stayed invisible because `~/.gradle/gradle.properties` sets `android.aapt2FromMavenOverride` to an ARM aapt2 — the Maven artifact is never resolved there. F-Droid's own build would have failed in the same task. Fixed in `ee9a530` (checksums from Google Maven; counter-proof: intentionally wrong sum → verification fails again).
- **Tag v0.18 remains on the broken commit.** It is immutable by GitHub ruleset (no bypass) and has no release. Instead **v0.18.1 (versionCode 34)** was tagged — same app plus build/test fix.
- **Lesson for the next tag:** run a release dry-run **without** aapt2 override so dependency verification really kicks in: `sh gradlew -Pandroid.aapt2FromMavenOverride= :app:assembleRelease`.

## 0.18

- **English locale complete.** `values-en` now covers all strings (was 92/163); no German fallback on English-locale devices anymore. German strings that had crept back into `values/` (17) and `values-en/` (8) were moved to `values-de/`: the default locale is English, and no file mixes languages anymore.
- **Swipe added.** Optional gesture input: finger glides over the keyboard, the route is evaluated against the engine (auto-commit on clear result, otherwise candidate bar). `swipe` default `false`. Disabled hard in password fields (same rule as privacy guard).
- **Theme: gradient off toggles.** `gradient_off_dark` / `gradient_off_light` (`false` default) deactivate the gradient for dark/light respectively. A flat `theme_*_bg` override or `gradient_off_* = true` deactivates the gradient; choosing a gradient color or preset in Themes reactivates it.
- **Background image fill modes:** `fit`, `cover`, `stretch`. Default `fit`. Max 2048 px per side before loading.
- **Config file: new keys.** `trail`, `trail_trace`, `trail_steps` now settable from `keytab_config.txt` (the importer gained the missing `int` branch — without it `trail_steps` would have been silently skipped).
- **Emoji suggestions removed.** `emoji_suggestions` (default `false`) was removed — it hung off the suggestion bar but was never wired.

## 0.17

- **Snippet panel: inline add/edit.** `+` / `✎` add or edit snippets inline in the snippets tab. Snippet file `keytab_snippets.txt` (`name = text` lines, `#` comments, `\n` expanded).
- **Dynamic key sizing.** Likely-next keys scale up (grades 1.21× / 1.15×, shrink 0.812× / 0.925×) and glow in an adjustable color. Both toggleable.
- **Typing trail + suggestion-match marking (optional).** Last keys tint and fade (3/5/7/10 steps, drawn as key foreground overlay); with the trace on, letters turn green when the typed word matches the top suggestion. Typos stay unmarked (no red). Pure logic in `TrailLogic`, unit-tested.
- **Emoji catalog.** Offline keyword→emoji catalog (de/en) behind the ☦ button, browsable page by page or as full grid; insertion does not disturb n-gram learning.

## 0.16

- **File manager in the keyboard.** Browse folders with back-stack and parent navigation; each tab remembers its own directory. Tapping a file inserts its path; in the app it opens via VIEW intent. Async listing so large folders don't freeze the UI.
- **Notes tab.** Editor and clipboard merged into one tab: folder-browser loading, persistent clipboard history (50 entries) with picker dialog.
- **Two key planes in one switch.** The `?123` key toggles `abc` ⇄ `?123`; the former third plane (`<v>` with extra symbols and the big arrow pad) was removed. The bottom row (switch, TAB, space, dot, Enter) and the suggestion/emoji row (`☺`, `‹`, `▦`, `⇲`) never disappear while paging.

## 0.15

- **Offline word prediction.** n-gram with FrequencyWords corpora (CC-BY-SA-4.0), Damerau-Levenshtein fuzzy correction, prefix autocomplete, case matching. 50 ms per-keystroke performance budget keeping prediction off the main thread.
- **Learning user dictionary.** In Settings, the learned dictionary can be scanned for artifacts and cleaned with explicit confirmation (preview first, base corpus untouched, entirely on-device).
- **Multi-language.** 7 latin-script languages (de, en, es, fr, it, pt, nl), each with its own frequency corpus and accent popups; instant switching with on-the-fly engine reload.
- **Privacy guard.** In password fields and whenever an app sets `IME_FLAG_NO_PERSONALIZED_LEARNING`, trail, swipe, suggestions, and dictionary learning are suppressed in code — not by preference. Covered by unit tests with positive controls in normal fields.

## 0.14

- **Terminal tab removed.** A shell without PTY / userland was not a real working environment. For terminals / SSH, KeyTab remains the input method; the shell is provided by Termux.
- **50 ms per-keystroke budget.** JVM benchmarks in `TrailPerformanceTest` and `SwipePerformanceTest` assert average µs per call far below the budget.

## 0.13

- **Tab key.** `KEYCODE_TAB` for tab completion in Termux / SSH. Shift / caps-lock, long-press accent popups, accelerating backspace (250 ms → 30 ms).

## 0.12

- **First public release.** Basic IME with QWERTZ / QWERTY layout, suggestions, and settings activity.

---

**Older versions** (pre-0.12) are not listed; they were internal development builds without public releases.
