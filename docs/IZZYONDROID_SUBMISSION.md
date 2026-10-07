# IzzyOnDroid — ABGELEHNT (2026-10-07): LLM/AI-Policy

Status: **ABGELEHNT** — https://codeberg.org/IzzyOnDroid/repodata/issues/684 (`[AppRequest] KeyTab`, Labels `app-request` + `llm/substantial` + `status/declined`) · Paket-ID `com.piotv.keytab` · MIT · eingereichte Version: **v0.17 (versionCode 32)**

WICHTIG: Das GitLab-Repo `IzzyOnDroid/repo` ist ARCHIVIERT (read-only, 403) — Anträge laufen über Codeberg (`codeberg.org/IzzyOnDroid/repodata/issues`, Formular „App Inclusion Request"). Antragstext (Archiv): `docs/IZZYONDROID_REQUEST_v017.md`.

## Ergebnis 2026-10-07

1. ✅ Der per API angelegte Antrag **#672** (2026-10-05) hatte keine Labels und lag
   damit außerhalb der Kette. Wir haben ihn am 2026-10-07 als Duplikat
   geschlossen (Kommentar mit Verweis).
2. ✅ **#684** wurde am 2026-10-07 20:10 korrekt **über das Formular** angelegt —
   die Labels `app-request` + `needs/apk-scan` wurden automatisch gesetzt, der
   Antrag lief also regulär in die Prüfung.
3. ❌ **#684 wurde vier Minuten später abgelehnt** (20:14, Maintainer `sidhant`),
   Labels `llm/substantial` + `status/declined`:

   > „Upon review of your application, we regret to inform you that the project
   > does not meet our requirements for inclusion. Our evaluation found that the
   > level of LLM assistance used in the development of this application
   > significantly exceeds our acceptable thresholds. Consequently, the
   > application does not comply with the standards outlined in our App
   > Inclusion AI Policy."

   Die App ist weiterhin **nicht gelistet**
   (`apt.izzysoft.de/fdroid/index/apk/com.piotv.keytab` → 404).

## Warum das nicht verhandelbar ist

Die [App Inclusion Policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/)
ist an dieser Stelle kategorisch:

> „We are strongly opposed to apps which are fully or in part created by
> generative AI tools. […] **Vibe-coded apps will be rejected.** […] Readme,
> Changelogs and similar documentation files are allowed to include
> LLM-generated texts, but **the code itself should be free of it**. Using LLMs
> for research, brainstorming, inspiration, debugging, look-ups, and comparable
> ‚read-only' tasks, is acceptable – provided their output is not included in
> the app's code. Any lack of transparency discovered by us, can lead to the
> project being degraded to rejected state."

Das Label `llm/substantial` ist im Tracker definiert als *„LLM was used
throughout development, or entire app vibe-coded"*. Die Einstufung stammt aus
der eigenen Auswertung des Repos, nicht aus der Formularantwort (dort stand
„Moderate – Used for specific tasks or modules") — KeyTab ist durchgehend
KI-unterstützt entstanden, und die Repo-Doku sagt das auch offen (u. a.
`docs/REFACTORING_HISTORY.md`, AI-Tool im Formular: Cline).

**Konsequenz:** Ein Widerspruch wäre nur aussichtsreich, wenn der Code frei von
LLM-Ausgaben wäre — das ist er nicht, und laut Policy kann „lack of
transparency" den Status sogar weiter verschlechtern. **IzzyOnDroid ist damit
für KeyTab kein Kanal**, solange der Code mit generativer KI entsteht. Das ist
eine Richtlinien-Entscheidung, kein behebbarer Mangel.

**F-Droid bleibt der Weg:** Bei den ebenfalls abgelehnten Anträgen #678/#679 hat
der Maintainer die Autoren ausdrücklich an F-Droid verwiesen („they don't have
an LLM Policy"). Unser MR `fdroid/fdroiddata!50822` ist grün (Pipeline
`2923311708`, 9/9, Reproducible Build bestätigt) — siehe
`docs/FDROID_SUBMISSION.md`.

**Kein weiterer Izzy-Antrag** (weder Wiederholung noch Umformulierung): Die
Einstufung ist reproduzierbar, und ein zweiter Anlauf mit „kleinerer"
AI-Angabe wäre eine Falschangabe.

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
Version to be listed: v0.15 (versionCode 30)
Signing: signed release APKs built by GitHub Actions, keystore only in CI
  secrets; SHA-256 checksum is published next to every APK.
Anti-features: none. No INTERNET permission — the app cannot send data
  anywhere. Word prediction runs fully on-device (n-gram corpus + Damerau-
  Levenshtein). allowBackup is false. No ads, no trackers, no analytics,
  no proprietary dependencies.
Build: 100% Kotlin, Gradle wrapper in the repo, R8 minification,
  reproducible tag builds; CI runs unit tests (501), detekt, Android Lint,
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

## 4. Screenshots — erledigt: echte Geräteaufnahmen (2026-09-29)

Die Screenshots sind inzwischen **echte Geräteaufnahmen** (2026-09-29) und
liegen in `docs/images/device/` (`abc-light.jpg`, `abc-dark.jpg`,
`snippets.jpg`, `files.jpg`, `editor.jpg`, `clipboard.jpg` plus Demo-GIF
`demo.gif`). Für Fastlane liegen Kopien der JPGs in
`images/phoneScreenshots/` (1.png–3.png wurden durch die Geräteaufnahmen
ersetzt). Falls neue Aufnahmen nötig sind:

```bash
# auf einem Gerät/Emulator mit aktivierter KeyTab-Tastatur, pro Screen:
sh ~/bin/rsh 'screencap -p /sdcard/Download/keytab-1.png'
sh ~/bin/rsh 'cp /sdcard/Download/keytab-1.png /data/local/tmp/'   # bei Bedarf
# dann 1:1 nach fastlane/metadata/android/en-US/images/phoneScreenshots/N.png
```

IzzyOnDroid skaliert auf 350 px an der kurzen Seite herunter und akzeptiert
**nur** PNG oder JPG — keine Rahmen, kein Mockup-Frame
(<https://izzyondroid.org/docs/general/Fastlane/>).
