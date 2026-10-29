# Changelog

All notable changes to KeyTab, newest first.

Format follows [Keep a Changelog](https://keepachangelog.com/1.1.0/); versioning follows [Semantic Versioning](https://semver.org/).

The version is **not** maintained here, but in `app/build.gradle.kts` (`versionCode` / `versionName`). `scripts/check_docs_drift.sh` (CI job "docs") checks on every push that this changelog's head, the fastlane changelog, and the Gradle version match.

## 0.22

- **Editor tab: automatic file loading removed.** The editor no longer polls its directory for files written by other programs — that mode is gone, together with its watcher class and the four config keys that only existed for it. The editor-tab badge (number of open files) and the long-press menu (close all / new file / save / reload) are unaffected.
- **No user-visible setting lost:** the switches had already left the settings pages in 0.20; only the two config keys that still gated auto-load disappear. Existing `keytab_config.txt` files keep those lines untouched — unknown keys are preserved and ignored, as before.
- **Smaller code base:** one class (the directory watcher) and two `EditorPanel` methods (start/stop of the watch) dropped.
- **Dead code removed:** the clipboard picker dialog (`ClipboardPanel.showPicker` — unreachable, the tab inserts on tap), `TabController.resetSymbols()`, three unused `LearnedDictionaryApi` helpers (`lookup`/`addWord`/`removeWord`), the never-read `tab_highlight_mode` getter with its four constants and its config key, three unused resources (`clip_pick_title`, `editor_cleared`, the id `editor_normal_height`), plus two unused local variables and three redundant safe calls in tests — the compiler warnings are down to a single intentional deprecation.
- **detekt baseline is empty.** Seven of its nine entries were stale (the code they named no longer exists — measured with `sh scripts/baseline.sh audit`); the two remaining findings (`TooManyFunctions` in `ThemePrefs` and `KeyTabImeService`) moved into the code as justified `@Suppress(...)`, matching the pattern already used in `EditorPanel`/`TabController`. A wildcard import in a test is gone, so `:app:detekt` (all source sets) is green too.
- **Docs corrected:** `docs/CONFIG.md` listed `gaming_mode` as a config key (it is only a legacy preference key migrated on read — the config key is `likely`), `Prefs.kt` carried a duplicated comment line, and two KDoc references pointed at files that never existed (`docs/DEVICE_PERF.md`, `docs/LEARNED_DICTIONARY_CLEANUP.md`).
- **Naming: the misleading `gaming` on-disk names are gone.** The likely-key highlight colour is written as `theme_dark_likely` / `theme_light_likely` (was `theme_*_gaming`), the switch is `likely` (was `gaming_mode`) and the leftover of the removed pulse effect (`gaming_effect`) is cleaned up. All three names date back to versions ≤ v0.9.6 and never had anything to do with gaming — they highlight the most likely next key. Nothing is lost: the colour has a read fallback, everything is renamed once by `ThemePrefs.migrateLegacyNames` (triggered by `likelyHighlighting` and by the config export), and old theme exports still import (legacy JSON key).
- **Editor tab could not be opened by tapping — fixed.** The tab carries a custom view for its long-press menu; `setOnLongClickListener` makes that view long-clickable, and a long-clickable view consumes the touch, so the tap never reached the tab and the Editor tab stayed closed. Files/Clip/Snippets were unaffected (no custom view). Measured 2026-10-29 on Android 16; the click is now forwarded to the tab explicitly.
- **Instrumented typing test is no longer a CI job.** The API-34 emulator is too slow to bring the IME window up, so the job was red for environmental reasons while looking like a product defect (it hid the Editor-tab bug above behind a wrong diagnosis). The suite now runs device-first: `sh scripts/test_on_device.sh` builds, installs with SHA-256 verification, checks that the *installed* `versionCode` matches the build, wakes/unlocks the device, runs the suite and restores the previous keyboard.
- **New versioning guard:** `scripts/check_release_tag.sh` verifies that a pushed tag matches the `versionName` inside its own tree, that the `versionCode` grows, and that release notes exist — the check that would have caught the mistagged `v0.19`. It runs in the `docs` CI job and as a hard gate in the release workflow before signing. `--audit` checks every local tag.
- 573 unit tests in 64 classes, 0 failures

## 0.21

- **Versioning corrected (F-Droid build failed).** `v0.19` was tagged on a commit that already carried `versionName 0.20` — F-Droid's `checkupdates` therefore reported "0.20 in the 0.19 tag" and the metadata update failed ([issue #1](https://github.com/piot5/keytab/issues/1)). Tags are immutable by ruleset, so `v0.19` stays as it is (its release notes are marked as a mis-tag); **0.21 (versionCode 37) is the first correctly tagged release after 0.18.1**.
- **Fixes on top of 0.20** (0.20 brought the optional file auto-load mode, the Shift-independent long-press row, the ß removal and the double-width Backspace — see below):
  - **Trace marking usable again.** `trail_trace` was forced to `false` on every read and even rewrote the stored value, so the green match marking could not be switched on at all. The switch honours the stored value again; the default stays **off**.
  - **"Learned dictionary" button works again.** Its wiring was lost in the 0.20 settings refactor — the button in the settings did nothing.
  - **Editor tab, as it actually ships:** the four mode switches are gone from the settings, so the editor-tab badge now simply shows the number of open files and the editor-tab long-press menu (close all / new file / save / reload) is available whenever the editor panel exists. Only auto-load stayed gated (two config keys, both off by default, settable via `keytab_config.txt`; dropped again in 0.22).
- **CI repaired (was red since 2026-10-22):** unit-test compilation (the panel takes `FileManagerPanel.FileManagerCallbacks`), the four `TermuxKeyMatrix` tests (JUnit 4 cannot run top-level test functions), the settings key count, detekt findings and the editor/trace expectations are green again — and the instrumented test source set compiles again (`UiObject2` has no `isVisible()`, only `getVisibleBounds`). New regression test for the file long-press menu (the `BadTokenException` crash from 2026-10-12).
- 568 unit tests in 64 classes, 0 failures

- **File auto-load mode (optional, removed again in 0.22).** KeyTab detected when an external program wrote files into the watched directory and loaded them automatically into the editor tab. Three independent sub-switches, all **off** by default and bundled under one main switch: **Auto-Load** (file polling, 1 s), **File badge** (number of open files on the editor tab), and **Tab context menu** (long-press on editor tab → Close all / New file / Save / Reload). The mode only watched the filesystem — it knew nothing about which program wrote the files, and it never talked to any API or token.
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
