# Release-Prozess (KeyTab)

Stand: v0.14 · Gilt für alle Releases ab jetzt.

## 1. Version

Die Version existiert **nur** in `app/build.gradle.kts`:

- `versionName` (z. B. `0.14`)
- `versionCode` (monoton steigend, z. B. `29`)

Alles andere folgt daraus und wird von `scripts/check_docs_drift.sh` geprüft:

1. `CHANGELOG.md` beginnt mit derselben `versionName`
2. `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` existiert
3. README nennt keine hartkodierte Version

## 2. Vor dem Release

```bash
sh scripts/check_docs_drift.sh      # Version, Testzahlen, i18n, Servicegröße
sh gradlew :app:testDebugUnitTest    # alle JVM-Tests
sh gradlew :app:detektDebug          # statische Analyse (Baseline-Gate)
sh gradlew :app:lintDebug            # Android Lint
```

Alles muss grün sein. Ein rotes Gate blockiert das Release — kein „temporär rot“.

## 3. Release bauen

```bash
sh build_keytab.sh release
# lädt keystore/keystore.properties (gitignored) und baut
# app/build/outputs/apk/release/app-release.apk (R8 + signiert)
```

Signatur-Credentials kommen **ausschließlich** aus `keystore/keystore.properties`
oder Umgebungsvariablen (`KEYTAB_KEYSTORE_PASSWORD`, `KEY_KEY_ALIAS`, `KEY_PASSWORD`).
Es gibt keine Defaults im Buildfile; relative Keystore-Pfade werden gegen den
Repository-Root aufgelöst.

## 4. Release verifizieren (lokal und in CI)

```bash
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
cat app/build/outputs/apk/release/output-metadata.json
```

Der CI-Workflow `.github/workflows/release.yml` (Tag `v*`) macht zusätzlich:

- Doku-Drift-Gate
- signierten Release-Build
- `apksigner verify`
- SHA-256-Datei
- Veröffentlichung von APK **und** `.sha256` als GitHub-Release-Assets

## 5. Installation auf dem eigenen Gerät

```bash
sh install_keytab.sh app/build/outputs/apk/release/app-release.apk
sh ~/bin/rsh 'dumpsys package com.piotv.keytab | grep -E "versionName|versionCode"'
```

**Debug-Paket entfernen**, damit nicht versehentlich die alte Debug-IME aktiv bleibt:

```bash
sh ~/bin/rsh 'pm uninstall com.piotv.keytab.debug'
sh ~/bin/rsh 'settings get secure default_input_method'
```

## 6. Nach dem Release

- `CHANGELOG.md` und fastlane-Changelog sind Teil des Release-Commits
- `docs/REFACTORING_HISTORY.md` bekommt abgeschlossene Arbeiten (Archiv)
- `docs/REFACTORING_PLAN.md` enthält danach **nur noch offene** Arbeit
