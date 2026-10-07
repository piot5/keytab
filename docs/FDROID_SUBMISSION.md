# F-Droid Submission — Antrag eingereicht (2026-10-01, v0.17-Update 2026-10-07)

Status: **Antrag eingereicht — MR läuft, v0.17 nachgezogen** ([`fdroid/fdroiddata!50822`](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50822)) · Paket-ID `com.piotv.keytab` · MIT · minSdk 24

**Stand 2026-10-07 (geprüft):** MR `opened`, Labels `New App` /
`reproducible-builds` / `review-requested`, keine Konflikte, keine offenen
Review-Threads. Metadaten im MR: `versionName: '0.17'`, `versionCode: 32`,
`commit: 135babd1cccd459eab01f9d82b4d38814b292b3c` (= Tag `v0.17`, Release mit
APK + SHA-256 veröffentlicht), `Binaries:` rollt über `%v`.

**Review-Runde 2026-10-06 (`mezinster`):** Der statische Review ist vollständig
bestanden (Lizenz MIT, Dependencies nur AndroidX/Material/kotlinx-coroutines,
FrequencyWords CC-BY-SA-4.0 korrekt credited, Fastlane `en-US` komplett,
VirusTotal 0/67, kein `INTERNET`). **Ein** offener Punkt: Die
Speicher-Berechtigungen wurden in `MainActivity.onCreate` **beim App-Start**
angefragt — ein IME kann den System-Dialog nicht selbst zeigen, und ein Ablehnen
unterbrach das Tippen bei jedem Start erneut.

**Adressiert in v0.17 (2026-10-07):**

- Kein Prompt beim Start; stattdessen Status-Zeile + Button „Grant file access“
  in den Einstellungen („Granted“ / „Only selected photos“ / „Not granted“).
  Endgültig abgelehnte Berechtigungen führen per Dialog in die App-Einstellungen.
- Hintergrundbild über den **System-Foto-Picker**
  (`ActivityResultContracts.PickVisualMedia`) statt `OpenDocument` — braucht
  **keine** Speicher-Berechtigung mehr.
- Files-Tab ohne Zugriff listet nur Ordner und nennt den neuen Button.
- Neuer Regressionsschutz `MainActivityTest` („App-Start fragt keine
  Speicher-Berechtigung an“); 520 Unit-Tests in 55 Suites, 0 Failures.
- APK-Berechtigungen unverändert (`READ_EXTERNAL_STORAGE` maxSdk 32,
  `READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED`) — **kein** `INTERNET`,
  keine neuen Permissions.

**Warteschlange:** 271 offene MRs mit Label `review-requested`, KeyTab auf Platz
**211** (Sortierung nach Anlagedatum) — die Aufnahme kann dauern. Auflage des
Reviewers: **bei einer neuen Version den MR mitziehen** (`versionName`,
`versionCode`, `commit:`, `CurrentVersion`/`CurrentVersionCode` in
`metadata/com.piotv.keytab.yml`) — mit v0.17 erledigt.

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

**Re-Verifikation: 2026-10-07, Tag `v0.17` — BESTANDEN.** Beide Clean-Builds
lieferten bit-identisch:

```
app-release-unsigned.apk
dd98362ded0b260994b9cdfe2d69d70b27b3d6e87f5372ecca5070be283904d8
```

**Maßgeblich ist aber F-Droids eigener Vergleich:** Pipeline `2923311708`
(2026-10-07, **9/9 Jobs grün**) hat `v0.17` auf der F-Droid-Toolchain gebaut und
protokolliert *„compared built binary to supplied reference binary
successfully"* — der F-Droid-Build ist also byte-identisch mit unserem
Release-`KeyTab-0.17.apk`. Das ist der Test, der zählt (x86_64-Standard-aapt2,
fremde Maschine), nicht der lokale Lauf.

**Caveat des lokalen Laufs:** Auf diesem Gerät ist das SDK-aapt2 x86-64 und
damit nicht lauffähig; `~/.gradle/gradle.properties` setzt deshalb
`android.aapt2FromMavenOverride` auf ein **Vendor-aapt2 (2.19 aus einem
OEM-Build-Tools-Paket)** statt aapt2 8.5.2 aus Maven. Der lokale Check prüft
damit Determinismus *innerhalb dieser Toolchain*: 4 von 5 Läufen von `v0.17`
ergaben denselben Hash, ein unter Last laufender Lauf wich ab (Ressourcen-
Encoding des Vendor-aapt2). Das Skript weist auf einen aktiven Override jetzt
explizit hin.

**Re-Verifikation nach jedem Release-Tag** (falls sich am Build geändert hat,
vor dem nächsten Release erneut laufen lassen):

**Wichtig — Reproduzierbarkeit gilt *pro Revision*.** Das APK enthält
`META-INF/version-control-info.textproto` (AGP-Feature) mit dem Commit-Hash;
`local_root_path` ist der pfadunabhängige Platzhalter `$PROJECT_DIR`:

```
repositories {
  system: GIT
  local_root_path: "$PROJECT_DIR"
  revision: "135babd1cccd459eab01f9d82b4d38814b292b3c"
}
```

Zwei Builds **desselben Commits** sind bit-identisch (so vergleicht auch F-Droid:
eigener Build des Tags vs. unser `Binaries`-APK). Zwei Builds **verschiedener
Commits** unterscheiden sich zwangsläufig — genau das erzeugte am 2026-10-07
einen falschen Alarm, weil Tag `v0.17` mitten im Lauf verschoben wurde
(Build A auf `3af3c3f`, Build B auf `135babd`). `scripts/verify_reproducible.sh`
pinnt die Revision deshalb jetzt **vor** den beiden Builds und bricht ab, wenn
die gebauten Revisionen auseinanderlaufen.

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
4. ✅ v0.17-Pull (2026-10-07): Der Review-Punkt 1 (`mezinster`, 2026-10-06 —
   Permission-Prompt beim Start) ist umgesetzt: kein Prompt beim Start,
   Status-Zeile + „Grant file access“ in den Einstellungen, Hintergrundbild über
   den System-Foto-Picker. Release `v0.17` (versionCode 32, Commit
   `135babd1cccd459eab01f9d82b4d38814b292b3c`, signiertes APK + SHA-256
   veröffentlicht). Metadaten im MR auf v0.17 gehoben (`versionName`,
   `versionCode`, `commit:`, `CurrentVersion`/`CurrentVersionCode`).
5. ✅ v0.17-Pipeline `2923311708` (2026-10-07): **9/9 Jobs grün** — u. a.
   `check apk` und `fdroid build` mit *„compared built binary to supplied
   reference binary successfully"* (Reproducible Build bestätigt). ⏳ Offen:
   On-Device-Test durch den Reviewer und Merge (Warteschlange, Platz 211).

## 4. Danach

- F-Droid-Badge in die README-Download-Zeile
  (<https://f-droid.org/docs/Badge_/>), Status-Tabelle im README aktualisieren
  („F-Droid: ⏳ → ✅").
- `README.md`-Kanal-Tabelle und diesen Doc-Eintrag pflegen.

## Abgrenzung zu IzzyOnDroid

IzzyOnDroid listet unsere vor-signierten GitHub-Release-APKs und verlangt
**keinen** Reproducible-Build — der Antrag dort (`docs/IZZYONDROID_SUBMISSION.md`)
ist unabhängig von diesem Prozess und kann **jetzt** abgeschickt werden.
