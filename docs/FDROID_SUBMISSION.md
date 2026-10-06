# F-Droid Submission — Antrag eingereicht (2026-10-01, v0.16-Update 2026-10-02)

Status: **Antrag eingereicht — MR läuft, v0.16-Fix eingespielt** ([`fdroid/fdroiddata!50822`](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50822)) · Paket-ID `com.piotv.keytab` · MIT · minSdk 24

**Stand 2026-10-06 (geprüft):** MR `opened`, `detailed_merge_status: mergeable`,
keine Konflikte, Labels `New App` / `reproducible-builds` / `review-requested`.
Head-Pipeline **grün** (Pipeline `2909014937`, 2026-10-03; 9/9 Jobs: `check apk`,
`fdroid build`, `fdroid lint`, `checkupdates`, `fdroid rewritemeta`,
`schema validation`, `tools check scripts`, `git redirect`, `check source code`).
Metadaten im MR stimmen mit dem Repo: `commit: 70246311e2d54b402502a1872491a5e46a3a90a5`
== Tag `v0.16` (versionName 0.16 / versionCode 31); `Binaries:` rollt über `%v`.

Reviewer `linsui` (2026-10-04): *„This MR is mostly ready. We'll test it later.
If everything works well we'll merge it."* — offene Punkte gibt es keine mehr
(Template, `subdir`, `output`, `Binaries` + `AllowedAPKSigningKeys` und der
`check apk`-Fehler sind erledigt). **Warteschlange:** 267 offene MRs mit Label
`review-requested`, KeyTab liegt auf Platz **221** (Sortierung nach Anlagedatum)
— die Aufnahme kann also dauern. Auflage des Reviewers: **bei einer neuen
Version den MR mitziehen** (`versionName`, `versionCode`, `commit:`,
`CurrentVersion`/`CurrentVersionCode` in `metadata/com.piotv.keytab.yml`).

Anders als IzzyOnDroid (listet unsere CI-APKs) **baut F-Droid die App selbst**
aus dem Quellcode und verifiziert idealerweise, dass der F-Droid-Build binär
identisch mit unserem Release-APK ist (**Reproducible Builds**,
<https://f-droid.org/docs/Reproducible_Builds/>). Deshalb ist der Weg hier
länger als bei IzzyOnDroid — aber einmal verifiziert, ist KeyTab im
renommiertesten FOSS-Store.

## 1. Bereits vorbereitet (in diesem Repo)

| Baustein | Pfad | Status |
|---|---|---|
| Fastlane-Baum (Titel, Beschreibungen, Changelogs, Screenshots) | `fastlane/metadata/android/en-US/` | ✅ vollständig (gleiche Struktur, die F-Droid liest) |
| fdroiddata-Metadaten | `docs/fdroiddata-metadata-com.piotv.keytab.yml` | ✅ als MR eingereicht (Abschnitt 3) |
| Reproducible-Build-Verifikation | `scripts/verify_reproducible.sh` | ✅ Skript, Erstlauf bestanden (Abschnitt 2) |
| Kein `INTERNET`, keine Tracker | `AndroidManifest.xml` | ✅ |
| Signierte Releases + SHA-256 | GitHub Releases | ✅ |

## 2. Reproducible Build verifizieren (Pflicht vor dem Antrag)

```sh
sh scripts/verify_reproducible.sh v0.15
```

Das Skript klont die Referenz zweimal frisch, baut beide Male `assembleRelease`
ohne Build-Cache und vergleicht die SHA-256. **Exit 0 = reproduzierbar.**

**Erstrausführung: 2026-09-30, Tag `v0.15` — BESTANDEN.** Beide Builds lieferten
bit-identische APKs:

```
app-release-unsigned.apk
8cba900a3890a37192b5dc15b3fa12887f468b4566f8770497c2b36307b95ec2
```

Dabei traten zwei Prüfsteine auf (beide im Skript dauerhaft gelöst):

1. **`gradlew` ohne Executable-Bit** (Mode 100644) — im frischen Klon ist
   `./gradlew` nicht startbar; das Skript ruft es deshalb via `sh ./gradlew` auf.
2. **Fehlende `local.properties`** (gitignored, liegt im Klon nicht vor) — das
   Skript übernimmt `sdk.dir` aus der Arbeitskopie bzw. respektiert `ANDROID_HOME`
   (so baut auch F-Droid).

Beide Punkte sind genau die häufigsten F-Droid-Build-Ablehnungen — sie sind
hier als Skriptverhalten abgefangen, nicht als lokale Frickelei.

**Re-Verifikation nach jedem Release-Tag** (falls sich am Build geändert hat,
vor dem nächsten Release erneut laufen lassen):

Typische Stolpersteine, falls ein späterer Lauf abweicht (in dieser Reihenfolge
prüfen):

1. **ZIP-Einträge mit Zeitstempel** — Neuere AGP-Versionen setzen deterministische
   Timestamps (`fixedTimestamp`), i. d. R. kein Handlungsbedarf; prüfen, ob beide
   Läufe exakt dieselbe Gradle-/AGP-Version nutzen (Wrapper im Repo, `--no-daemon`
   stellt das sicher).
2. **Locale/Umask des Bauers** — Skript setzt nichts voraus, aber F-Droid baut in
   `sr_CL`-artigen Umgebungen; falls Hashes abweichen: `LC_ALL=C` im Skript
   ergänzen und erneut testen.
3. **Nicht-deterministische Codegenerierung** (Room/Compose) — Dateien in
   `app/build/generated` zweier Läufe diffen; ggf. AGP-/Kotlin-Upgrade als Fix.

## 3. Der Antrag (eingereicht am 2026-10-01)

1. ✅ F-Droid-Metadaten als MR: Fork von
   <https://gitlab.com/fdroid/fdroiddata>, Datei
   `metadata/com.piotv.keytab.yml` aus unserem Entwurf (Abschnitt 1) eingestellt,
   Merge Request gegen `master` eröffnet. Betreff: „Add app: KeyTab".
   MR: <https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50822> (`!50822`, Status `opened`).
2. ✅ Im MR-Text referenziert: Lizenz MIT, kein `INTERNET`, 100 % Kotlin,
   Testumfang (515 Unit-Tests + Kover-Gate + 42 instrumentierte Tests), CI-Pipeline.
3. ✅ v0.16-Fix (2026-10-02): `check apk` schlug fehl mit
   `Found extra signing block 'Dependency metadata'` (AGP-8.5-Default, Block-ID
   `0x504B4453`). Fix in `app/build.gradle.kts`:
   `dependenciesInfo { includeInApk = false; includeInBundle = false }` —
   verifiziert (v0.15-APK enthält den Marker, `KeyTab-0.16.apk` nicht; gleicher
   Release-Key, `AllowedAPKSigningKeys` unverändert). MR per Commit `0cd86735`
   auf v0.16 (versionCode 31, Commit `70246311…`) gehoben, `@linsui` um Re-Run gebeten.
4. ⏳ Läuft: Fork-Pipeline `2907680313` + MR-`check apk` abwarten; wenn F-Droid baut
   und (optional) der Reproducible-Check gelingt, wird die App aufgenommen.

## 4. Danach

- F-Droid-Badge in die README-Download-Zeile
  (<https://f-droid.org/docs/Badge_/>), Status-Tabelle im README aktualisieren
  („F-Droid: ⏳ → ✅").
- `README.md`-Kanal-Tabelle und diesen Doc-Eintrag pflegen.

## Abgrenzung zu IzzyOnDroid

IzzyOnDroid listet unsere vor-signierten GitHub-Release-APKs und verlangt
**keinen** Reproducible-Build — der Antrag dort (`docs/IZZYONDROID_SUBMISSION.md`)
ist unabhängig von diesem Prozess und kann **jetzt** abgeschickt werden.
