# Release-Prozess (KeyTab)

Stand: v0.15 · Gilt für alle Releases ab jetzt.

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
- Refactoring-Plan und sein Archiv (abgeschlossene Arbeiten) liegen **außerhalb**
  dieses Repos — im Repo bleibt nur, was zum Bauen, Testen und Veröffentlichen
  nötig ist

## 7. F-Droid-Schutz (verbindlich, solange ein MR offen ist)

F-Droid baut die App **selbst** aus einem gepinnten Commit und vergleicht das
Ergebnis mit unserem Release-APK. Solange der MR
[`!50822`](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50822) offen ist,
ist dieser Stand unantastbar:

- MR-Metadaten: `versionName: '0.18.1'`, `versionCode: 34`,
  `commit: 0baa0afeb839b200275a0d6c84e656fca85c1a73` (= Tag `v0.18.1`),
  `AllowedAPKSigningKeys: debe1146…`
- `Binaries:` zeigt auf
  `https://github.com/piot5/keytab/releases/download/v0.18.1/KeyTab-0.18.1.apk`
  — **Release und Asset dürfen nicht gelöscht oder ersetzt werden** (dagegen gibt
  es keine automatische Sperre: GitHub schützt Releases nicht per Ruleset).

### 7.1 Vorfall 2026-10-15: `v0.18` kam ohne Release heraus (aapt2-Verifikation)

- Der Release-Workflow für `v0.18` (`4fda1d5`) brach in
  `compileReleaseNavigationResources` mit `Dependency verification failed` ab:
  in `gradle/verification-metadata.xml` fehlten die Prüfsummen für
  `com.android.tools.build:aapt2:8.13.2-14304508` (mit dem AGP-8.13.2-Upgrade
  eingeführt). Lokal blieb das unsichtbar, weil
  `~/.gradle/gradle.properties` per `android.aapt2FromMavenOverride` ein
  ARM-aapt2 einsetzt — das Maven-Artefakt wird dort nie aufgelöst. **F-Droids
  eigener Build wäre im selben Task gescheitert** (der MR wurde deshalb noch
  nicht auf 0.18 gezogen).
- Gefixt in `ee9a530` (Prüfsummen von Google Maven; Gegenprobe: absichtlich
  falsche Summe → Verifikation schlägt wieder fehl).
- Der Tag `v0.18` ist per Ruleset unveränderlich (kein Bypass) und **bleibt** auf
  dem kaputten Commit stehen. Er hat kein Release und wird von F-Droid nicht
  referenziert; statt ihn zu verschieben, wurde **`v0.18.1` (versionCode 34)**
  getaggt — inhaltlich dieselbe App plus Build-/Test-Fix.
- **Lehre für den nächsten Tag:** vorher ein Release-Trockenlauf **ohne**
  aapt2-Override, damit die Dependency-Verifikation wirklich greift:

  ```bash
  sh gradlew -Pandroid.aapt2FromMavenOverride= :app:assembleRelease
  ```

Drei Schutzschichten verhindern versehentliche Beschädigung (gesetzt 2026-10-08):

| Schicht | Wirkung |
|---|---|
| GitHub-Ruleset **„F-Droid-Schutz: Tags unveraenderlich"** (`target: tag`, `~ALL`, Regeln `deletion` + `update`) | Tags können **nicht gelöscht oder verschoben** werden. Ein verschobener Tag war am 2026-10-07 die Ursache eines falschen Repro-Vergleichs (Build A auf `3af3c3f`, Build B auf `135babd`) |
| GitHub-Ruleset **„main: kein Force-Push, keine Loeschung"** (`refs/heads/main`, Regeln `deletion` + `non_fast_forward`) | `main` kann nicht umgeschrieben oder gelöscht werden → `135babd` bleibt dauerhaft erreichbar |
| Git-Hook **`pre-push`** (`scripts/git-hooks/pre-push`, Installation `sh scripts/install_git_hooks.sh`) | blockiert **lokal** jeden Push, der einen Tag oder einen Force-Push enthält — greift vor dem Netzwerk |

Die Rulesets sind serverseitig und damit auch für andere Rechner/Klone wirksam;
sie sind nur im Web-UI bzw. per API (`gh api repos/piot5/keytab/rulesets`) änderbar.

**Regel für das nächste Release:** erst den MR auf die neue Version ziehen
(`versionName`, `versionCode`, `commit:`, `CurrentVersion`/`CurrentVersionCode`),
**dann** taggen. Ein neuer Tag ohne MR-Pull-forward lässt F-Droids `checkupdates`
eine neuere Version sehen und rollt den Review neu auf.

Notausgänge des Hooks (bewusst und einzeln):

```bash
KEYTAB_ALLOW_TAG_PUSH=1   git push --tags
KEYTAB_ALLOW_FORCE_PUSH=1 git push --force-with-lease
```

Ein normaler Branch-Push (`git push origin main`) läuft ohne Sonderfall durch.

