# KeyTab — Doku-Drift vermeiden (CI-Gate `docs`)

Kurzanleitung für den Wächter, der verhindert, dass README, Changelog und
Versionsnummern vom Code wegdriften.

## Warum

Vor diesem Gate gab es mehrere Stellen, die bei einem Release **manuell**
synchron gehalten werden mussten:

| Stelle | Was drinsteht |
|---|---|
| `app/build.gradle.kts` | `versionCode` + `versionName` (die Wahrheit) |
| `CHANGELOG.md` | Versionshistorie, Kopf = aktuelle Version |
| `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` | Play-Store-Release-Notes |
| `README.md` | Testanzahl, Suite-Tabelle, Test-Ratio |

Beim Sprung auf 0.9.7 (versionCode 23) fehlte z. B. der fastlane-Changelog
komplett, und das README nannte weiter 164 Tests in 19 Suites, während es
tatsächlich 194 in 22 waren — genau die Art von Drift, die von außen wie
Nachlässigkeit wirkt, obwohl der Code grün war.

## Was geprüft wird

`scripts/check_docs_drift.sh` (reines `sh`, kein Gradle, kein SDK — läuft in
Sekunden):

1. **Versionsgleichlauf** — `versionName` == Kopf von `CHANGELOG.md` ==
   vorhandener fastlane-Changelog für `versionCode`. Zusätzlich: das README darf
   keine hartkodierte Version mehr enthalten.
2. **Testzahlen** — der README-Satz „**N unit tests in M suites**“ wird gegen die
   echten `@Test`-Annotationen und Testklassen-Dateien in `app/src/test`
   geprüft; jede in der Tabelle gelistete Suite muss existieren.
3. **Changelog-Zuhause** — im README darf kein Changelog-*Inhalt* stehen (nur ein
   Link auf `CHANGELOG.md`).
4. **Versions-Singularität** — `versionCode`/`versionName` dürfen nur in
   `app/build.gradle.kts` definiert sein, nicht in weiteren Gradle-Dateien.
5. **Größen-, Service- und Sprachzahlen** *(neu 2026-09-22)* — der README-Satz
   „Test code is `<X>` lines in `<Y>` files against `<Z>` lines of main code
   (`<N>` files)“ inklusive der daraus berechneten Ratio, die beiden
   Zeilenangaben für `KeyTabImeService.kt` („core (`<N>` lines …)“) sowie die
   String-Zahlen für `values/` und `values-en/`. Zusätzlich die **Parität**:
   `values-en` muss genauso viele Strings haben wie `values/` — sonst fällt eine
   neue String-Ressource ohne Übersetzung auf.

   Grund: genau diese Zahlen waren zuletzt still veraltet (README: „283 lines“
   Service statt 417, 4.718 statt 7.038 Testzeilen, „176 strings“ statt 170),
   obwohl der Code grün war.

## Lokal ausführen

```bash
sh scripts/check_docs_drift.sh
```

Exit 0 = alles konsistent, Exit 1 = Drift (mit Angabe, was zu tun ist).

## Release-Ablauf (die drei Stellen)

```bash
# 1. Version in app/build.gradle.kts anheben: versionCode 23 -> 24, versionName "0.9.8" -> "0.9.9"
# 2. Release Notes anlegen
$EDITOR fastlane/metadata/android/en-US/changelogs/24.txt
# 3. Changelog-Kopf ergänzen
$EDITOR CHANGELOG.md        # neuer Abschnitt "## 0.9.9" ganz oben
# 4. Prüfen (muss grün sein, sonst bricht CI/release ab)
sh scripts/check_docs_drift.sh
# 5. Taggen -> Release-Workflow baut, signiert und publiziert
git tag v0.9.9 && git push origin v0.9.9
```

## Wo das Gate hängt

- `.github/workflows/ci.yml` → Job **`docs`** (läuft zuerst, ohne Toolchain)
- `.github/workflows/release.yml` → Schritt **„Doku-Drift prüfen“** vor dem
  Keystore-Restore, damit ein inkonsistenter Tag nie ein Release erzeugt

## Bewusst nicht geprüft

Prosa, Formulierungen und Coverage-Prozente. Der Wächter prüft nur, was
eindeutig maschinell entscheidbar ist — sonst würde er bei legitimen
Textänderungen fälschlich rot. Coverage-Zahlen (Kover) bleiben eine
Momentaufnahme und werden im README als solche datiert.
