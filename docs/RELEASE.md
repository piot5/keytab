# Release Process

The version exists **only** in `app/build.gradle.kts` (`versionCode` / `versionName`). Everything else follows from it:

1. `CHANGELOG.md` starts with the same `versionName`
2. `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` exists
3. `README.md` contains no hard-coded version

The CI job `docs` (`scripts/check_docs_drift.sh`) verifies this before every release.

## Before a release

```bash
sh scripts/check_docs_drift.sh
sh ./gradlew :app:testDebugUnitTest
sh ./gradlew :app:detektDebug
sh ./gradlew :app:lintDebug
```

All gates must stay green. A red gate blocks the release.

## Build

```bash
bash build_keytab.sh release
```

Signing credentials come exclusively from `keystore/keystore.properties` or environment variables (`KEYTAB_KEYSTORE_PASSWORD`, `KEY_KEY_ALIAS`, `KEY_PASSWORD`). There are no defaults in the build file.

## Verify

```bash
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

## Install on your own device

```bash
sh install_keytab.sh app/build/outputs/apk/release/app-release.apk
sh ~/bin/rsh 'dumpsys package com.piotv.keytab | grep -E "versionName|versionCode"'
```

Remove the debug package so the old debug IME does not stay active:

```bash
sh ~/bin/rsh 'pm uninstall com.piotv.keytab.debug'
```

## Tagging

Tagging triggers the release workflow (`release.yml`), which builds, signs, verifies, and publishes the APK plus its SHA-256.

```bash
git tag v0.20 && git push origin v0.20
```

Tags are immutable by GitHub ruleset — once pushed, a tag cannot be deleted or moved.
