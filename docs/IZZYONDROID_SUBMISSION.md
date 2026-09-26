# IzzyOnDroid — Aufnahmeantrag (vorbereitet, 2026-09-26)

Stand: v0.14 (`versionCode 29`) · Paket-ID `com.piotv.keytab` · MIT

IzzyOnDroid listet Apps aus dem Upstream-Repo und zieht Beschreibung, Icon und
Screenshots aus dem **Fastlane-Baum** des Repos (Quelle:
<https://izzyondroid.org/docs/general/Fastlane/>). Dieser Baum ist hier
vollständig vorhanden — der Antrag selbst ist ein Formular/Issue, das nur der
Kontoinhaber abschicken kann (GitHub-Token reicht dafür nicht).

## 1. Was im Repo bereits liegt (nichts zu tun)

| Pflichtfeld | Pfad | Status |
|---|---|---|
| Titel | `fastlane/metadata/android/en-US/title.txt` | ✅ `KeyTab` |
| Kurzbeschreibung (max. 80 Zeichen) | `fastlane/metadata/android/en-US/short_description.txt` | ✅ 73 Zeichen |
| Langbeschreibung | `fastlane/metadata/android/en-US/full_description.txt` | ✅ |
| Changelog je Release | `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` | ✅ bis `29.txt` |
| Screenshots (nur `phoneScreenshots`, PNG/JPG) | `fastlane/metadata/android/en-US/images/phoneScreenshots/1..3.png` | ⚠️ generiert, s. Abschnitt 4 |
| Signierte Releases + SHA-256 | GitHub Releases (`KeyTab-<version>.apk` + `.apk.sha256`) | ✅ ab v0.14 |
| Keine Tracker, kein Netz | `AndroidManifest.xml`: kein `INTERNET` | ✅ |
| Lizenz | `LICENSE` (MIT) | ✅ |

## 2. Antragsformular (ausfüllen, abschicken)

- **Formular:** <https://izzyondroid.org/docs/contributing/> → dort der Link zum
  Aufnahme-Wunsch (in der Regel ein GitLab-Issue bei `IzzyOnDroid/repo` bzw. das
  Community-Forum). Vor dem Absenden einmal die Seite prüfen, weil sich der Weg
  gelegentlich ändert.
- **Alternative bei Rückfragen:** `IzzyOnDroid` auf GitLab, Kanal/Issue mit
  dem Betreff „Inclusion request: KeyTab“.

Fertiger Antragstext (kopieren):

```
App name: KeyTab
Package ID: com.piotv.keytab
Source code: https://github.com/piot5/keytab
License: MIT (LICENSE in the repo)
Latest release: https://github.com/piot5/keytab/releases/latest
Version to be listed: v0.14 (versionCode 29)
Signing: signed release APKs built by GitHub Actions, keystore only in CI
  secrets; SHA-256 checksum is published next to every APK.
Anti-features: none. No INTERNET permission — the app cannot send data
  anywhere. Word prediction runs fully on-device (n-gram corpus + Damerau-
  Levenshtein). allowBackup is false. No ads, no trackers, no analytics,
  no proprietary dependencies.
Build: 100% Kotlin, Gradle wrapper in the repo, R8 minification,
  reproducible tag builds; CI runs unit tests (470), detekt, Android Lint,
  a Kover coverage gate (60% line / 45% branch) and instrumented tests on an
  API-34 emulator.
Fastlane: full structure in the repo at fastlane/metadata/android/en-US/
  (title, short/full description, changelogs for every versionCode, phone
  screenshots).
```

## 3. Nach der Aufnahme

- Badge in die README-Zeile der Download-Badges aufnehmen (IzzyOnDroid liefert
  Shields-Einbettungen unter <https://izzyondroid.org/docs/resources/>).
- Jedes Release (`v*`-Tag) wird automatisch übernommen, sobald der Fastlane-
  Changelog für den neuen `versionCode` im Tag vorhanden ist — das erzwingt
  `scripts/check_docs_drift.sh` bereits.

## 4. Offener Punkt: Screenshots sind generiert, nicht fotografiert

`scripts/make_screens.py` erzeugt die drei PNGs aus den echten Ressourcen
(Farben, Tastatur-Layout) und schreibt sie nach `docs/images/`; für Fastlane
liegen Kopien in `images/phoneScreenshots/`. Sie sind korrekt, aber **keine
Geräteaufnahmen** (keine Animation, keine echten Schatten/Antialiasing der
Android-Views). Vor oder direkt nach dem Antrag ersetzen:

```bash
# auf einem Gerät/Emulator mit aktivierter KeyTab-Tastatur, pro Screen:
sh ~/bin/rsh 'screencap -p /sdcard/Download/keytab-1.png'
sh ~/bin/rsh 'cp /sdcard/Download/keytab-1.png /data/local/tmp/'   # bei Bedarf
# dann 1:1 nach fastlane/metadata/android/en-US/images/phoneScreenshots/N.png
```

IzzyOnDroid skaliert auf 350 px an der kurzen Seite herunter und akzeptiert
**nur** PNG oder JPG — keine Rahmen, kein Mockup-Frame
(<https://izzyondroid.org/docs/general/Fastlane/>).
