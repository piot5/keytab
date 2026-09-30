# F-Droid Submission — Vorbereitung (2026-09-30)

Status: **in Vorbereitung** · Paket-ID `com.piotv.keytab` · MIT · minSdk 24

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
| fdroiddata-Metadaten-Entwurf | `docs/fdroiddata-metadata-com.piotv.keytab.yml` | ✅ Entwurf, vor MR final gegenlenken |
| Reproducible-Build-Verifikation | `scripts/verify_reproducible.sh` | ✅ Skript, Erstrausführung nötig (Abschnitt 2) |
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

**Re-Verifikation nach jedem Release-Tag** (kurz vor dem MR erneut laufen
lassen, falls sich am Build geändert hat):

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

## 3. Der Antrag selbst (nur Kontoinhaber)

1. F-Droid-Metadaten als MR: Fork von
   <https://gitlab.com/fdroid/fdroiddata>, Datei
   `metadata/com.piotv.keytab.yml` aus unserem Entwurf (Abschnitt 1) einstellen,
   Merge Request gegen `master` eröffnen. Betreff: „Add app: KeyTab".
2. Im MR-Text kurz referenzieren: Lizenz MIT, kein `INTERNET`, 100 % Kotlin,
   Testumfang (501 Unit-Tests + Kover-Gate + 22 instrumentierte Tests), CI-Pipeline.
3. Auf fdroiddata-Build-Logs warten; wenn F-Droid baut und (optional) der
   Reproducible-Check gelingt, wird die App aufgenommen.

## 4. Danach

- F-Droid-Badge in die README-Download-Zeile
  (<https://f-droid.org/docs/Badge_/>), Status-Tabelle im README aktualisieren
  („F-Droid: ⏳ → ✅").
- `README.md`-Kanal-Tabelle und diesen Doc-Eintrag pflegen.

## Abgrenzung zu IzzyOnDroid

IzzyOnDroid listet unsere vor-signierten GitHub-Release-APKs und verlangt
**keinen** Reproducible-Build — der Antrag dort (`docs/IZZYONDROID_SUBMISSION.md`)
ist unabhängig von diesem Prozess und kann **jetzt** abgeschickt werden.
