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
git tag v0.21 && git push origin v0.21
```

Tags are immutable by GitHub ruleset — once pushed, a tag cannot be deleted or moved.

**Guard:** `sh scripts/check_release_tag.sh <tag>` (also runs in the `docs` CI job and as a hard gate in `release.yml` before signing) checks exactly that: tag name == `v` + `versionName` in the tagged tree, a higher `versionCode` than the previous tag, and a fastlane changelog for that versionCode. `--audit` checks every local tag; known exceptions live in `scripts/release-tag-allowlist.txt` (which must not grow).

### Rule 1: tag the release commit, never `main` HEAD

The tag must point at the commit that carries the version bump, not at whatever `main` happens to be:

```bash
# 1. the release commit is the one that changed the version
git log -1 --format='%h %s' -- app/build.gradle.kts
# 2. proof before pushing: the tag's tree must name the same version
git show v0.21:app/build.gradle.kts | grep -E 'versionCode|versionName'
# 3. the version must also match the tag name
git describe --tags --exact-match
```

**Why (incident 2026-10-28):** `v0.19` was tagged on `f511a74` — a docs commit *on top of* the 0.20 code. That tree already contained `versionCode 36` / `versionName 0.20`, so F-Droid's `checkupdates` reported "0.20 in the 0.19 tag" and the metadata update failed (`KeyTab-0.19.apk` really declares `0.20`). The tag could not be repaired (ruleset: `deletion` + `update` on tags), so `v0.20`/`v0.21` had to take over. Details: GitHub issue #1.

## F-Droid-Kanal

The store metadata is **not** in this repo: it lives in [`fdroid/fdroiddata`](https://gitlab.com/fdroid/fdroiddata) as `metadata/com.piotv.keytab.yml`.

- `AutoUpdateMode: Version` + `UpdateCheckMode: Tags` — after each new tag the `checkupdates` bot opens an MR that pins `versionName` / `versionCode` / `commit` and bumps `CurrentVersion` / `CurrentVersionCode`.
- `Binaries: https://github.com/piot5/keytab/releases/download/v%v/KeyTab-%v.apk` — the pattern depends on **the tag and the APK asset name matching the version in the tag**. A mis-tagged release breaks this check, not only `checkupdates`.
- `AllowedAPKSigningKeys` pins the release certificate; changing the keystore means a new key has to be allowed upstream first.
- The bot only verifies that the tag *exists* — the reviewer verifies that the tag *matches* the version inside. Both checks have to pass.
- **YAML trap in `versionName`:** values that look like a float must be quoted. `versionName: '0.21'` (also `'0.17'`, `'0.22'`) — unquoted, YAML parses a number and `fdroid rewritemeta` rejects the file. Three-part versions are not floats and must stay **unquoted**: `versionName: 0.18.1`. Getting this wrong cost a metadata commit on 2026-10-15 (`227517ce` was red, `169dbf17` green).
- **After each tag, update three fields together:** the new `Builds` entry (`versionName`, `versionCode`, `commit` = the tag's SHA), `CurrentVersion` and `CurrentVersionCode`. `versionCode` must be strictly increasing — `scripts/check_release_tag.sh --audit` verifies that across all tags.

**Checklist after a release:**

```bash
# tag ↔ version consistency (see Rule 1 above)
git show v0.21:app/build.gradle.kts | grep -E 'versionCode|versionName'
# the release + asset the Binaries URL will request
gh release view v0.21 --repo piot5/keytab
curl -sI https://github.com/piot5/keytab/releases/download/v0.21/KeyTab-0.21.apk | head -1
# the metadata MR the bot opened (labels: fdroid-bot, waiting-for-upstream)
glab api 'projects/fdroid%2Ffdroiddata/merge_requests?search=keytab&state=opened' \
  | jq -r '.[] | "!\(.iid) \(.title) \(.web_url)"'
```

**Protective rules (installed automatically):**

- `pre-push` hook (`scripts/git-hooks/pre-push`, installed via `sh scripts/install_git_hooks.sh`) blocks tag pushes and force-pushes by default — an intentional push needs `KEYTAB_ALLOW_TAG_PUSH=1` / `KEYTAB_ALLOW_FORCE_PUSH=1`.
- GitHub rulesets: „F-Droid-Schutz: Tags unveraenderlich" (no tag deletion/update) and „main: kein Force-Push, keine Loeschung".
- The hook exists so a tag is never pushed *while a metadata MR is open* (a newer version would make the reviewer redo the round). Once a metadata MR is merged, pushing the next tag is the regular path.
