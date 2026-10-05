# IzzyOnDroid-Antrag — KeyTab v0.16 (Codeberg, Stand 2026-10-05)

Tracker (NEU, GitLab-Repo ist archiviert!): https://codeberg.org/IzzyOnDroid/repodata/issues
Dort: `New issue` → Titel: `[AppRequest] KeyTab`
Body: untenstehender Text.

---

### Guidelines

- [x] I am the developer of the app. (If not, please explain the developer's stance on this inclusion request in the Further Notices section.)
- [x] The app complies with the [App Inclusion Policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/).
- [x] The app is not already listed in the repo or issue tracker.
- [x] The [Fastlane](https://izzyondroid.org/docs/general/Fastlane/) folder is available in the app's repo.

### Link to the source code

https://github.com/piot5/keytab

### Link to app in another app store

https://github.com/piot5/keytab/releases/latest (F-Droid submission pending: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50822)

### License used

MIT

### Categories

System

### Summary

Privacy-first keyboard (IME) with tabbed file manager, offline word prediction and TAB key.

### Description

KeyTab is a privacy-first keyboard (IME) for Android, 100% Kotlin and free software (MIT).

File manager in the keyboard: browse folders in tabs, navigate with back-stack and parent-navigation. Tapping a file inserts its path or opens it via a VIEW intent. Each tab remembers its own directory. Listing runs asynchronously, so large folders never freeze the UI.

Offline word prediction: an n-gram model with bigrams for next-word prediction, a self-learning user dictionary, prefix autocomplete, fuzzy correction (Damerau-Levenshtein) and case matching — all on-device. No network permission. No analytics, no tracking, no data collection.

Keyboard features: dedicated TAB key (great for Termux/SSH), shift/caps-lock, long-press popups for umlauts and special characters, accelerating backspace on long-press. Dynamic key sizing (likely-next keys scale up), toggleable in settings. Notes/editor tab with save/load plus persistent clipboard history (up to 50 entries).

Fastlane metadata in repo at fastlane/metadata/android/en-US/ (title, short/full description, per-versionCode changelogs, phone screenshots). Current version: v0.16 (versionCode 31).

### Build instructions

Requires JDK 17 and the Gradle wrapper in the repo.

```
git clone https://github.com/piot5/keytab.git
cd keytab
git checkout v0.16
sh ./gradlew :app:assembleRelease --no-daemon
```

Output: `app/build/outputs/apk/release/app-release.apk` (unsigned; release signing in CI via keystore from secrets, verified with apksigner, SHA-256 published next to every APK on GitHub Releases).

### Assistance Level

Moderate -- AI-assisted development with full human review

### AI Tool(s)

Muse Spark / Cline (coding agent, Termux environment)

### What did the tools help with, and how?

Scaffolding, refactoring, tests (515 unit tests + 42 instrumented tests), docs/fastlane maintenance, CI scripts. All outputs were reviewed, edited and verified by the human developer (builds, tests on device + emulator).

### AI Accountability

- [x] The human developer(s) reviewed and edited all "AI"-generated outputs
- [x] The human developer(s) ran manual tests and manually verified all changes

### Further Notices

F-Droid submission for the same version is pending (fdroid/fdroiddata!50822). Reproducible tag builds verified (v0.15 bit-identical APK hashes). APK well below the 30 MB limit.
