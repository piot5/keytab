# Supply Chain & Workflow-Pinning (KeyTab)

Stand: 2026-09-27 · Gilt für alle Releases ab jetzt.

Zwei voneinander unabhängige Absicherungen: **Gradle-Abhängigkeiten** werden über
Prüfsummen verifiziert, **GitHub Actions** sind auf unveränderliche Commit-SHAs
gepinnt. Kein Tag und kein Branch einer fremden Action wird ausgeführt.

## 1. Gradle Dependency Verification

Aktiviert in `gradle/verification-metadata.xml` (~3.300 Zeilen, deklarativ von
Gradle erzeugt). Sie enthält SHA-256/PGP-Angaben für:

- alle direkten und transitiven Compile-/Runtime-Abhängigkeiten
- die **Gradle-Distribution** selbst (Wrapper-Download wird gegen die in
  `gradle/wrapper/gradle-wrapper.properties` gepinnte SHA-256 geprüft)
- die **Android-Lint-Toolchain** und die JUnit-BOM-Moduldateien — beides
  ergänzt in v0.14, nachdem die CI-Auflösung genau daran rot wurde

Neue Abhängigkeit hinzufügen, ohne die Metadaten zu erweitern ⇒ `./gradlew
--write-verification-metadata` schlägt fehl, und **jede** CI-Job mit
Auflösung blockiert. Das ist gewollt: die Änderung muss sichtbar sein.

```bash
# Prüfen (Standard in jedem Build)
sh gradlew :app:dependencies

# Metadaten bewusst erweitern — Review-Pflicht, Commit message
# muss die hinzugefügte Dependency benennen
sh gradlew --write-verification-metadata sha256
```

**Regel:** `--write-verification-metadata` wird nie im selben Commit wie ein
funktionaler Umbau benutzt. Getrennter Commit, im Diff sichtbar.

## 2. Gepinnte GitHub Actions

Alle `uses:`-Referenzen zeigen auf 40-hex-stellige Commit-SHAs. Aktueller Stand:

| Action | Commit-SHA | Verwendet in |
|---|---|---|
| `actions/checkout` | `fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09` | ci.yml (5×), release.yml |
| `actions/setup-java` | `b6effb05e454b25005698d916606bdc6ffcbf961` | ci.yml (3×), release.yml |
| `actions/upload-artifact` | `b7c566a772e6b6bfb58ed0dc250532a479d7789f` | ci.yml (4×) |
| `reactivecircus/android-emulator-runner` | `324029e2f414c084d8b15ba075288885e74aef9c` | ci.yml (Emulator-Job, API 34) |

Bei mehrfach verwendeten Actions (`checkout` 6×, `setup-java` 4×,
`upload-artifact` 4×) muss **jede** Fundstelle denselben SHA tragen — ein
gemischter Pin-Zustand ist ein Fehler, kein Detail. Prüfbar mit dem Befehl in
§3 Schritt 4.

## 3. Update-Prozess (wie man einen Pin erneuert)

Ein SHA-Pin ohne dokumentierten Weg zum Erneuern ist ein Wartungsrisiko: der
nächste Aktualisierungsversuch wird aus Bequemlichkeit übersprungen und die
Pins veralten. Deshalb der feste Ablauf:

1. **Aktuelle Version feststellen.** `gh api repos/<owner>/<action>/tags`
   oder im Release der Action nachsehen. Nur offizielle Releases, keine
   Nightly-/Pre-Release-Tags ohne Notwend.
2. **Tag → SHA auflösen.**
   `git ls-remote https://github.com/<owner>/<action> refs/tags/<tag>`
   Bei annotierten Tags das dereferenzierte `^{}`-Objekt nehmen — der
   Tag-Objekt-SHA und der Commit-SHA unterscheiden sich sonst.
3. **Alle Vorkommen ersetzen.** Die gleiche Action kommt mehrfach vor
   (checkout 6×, setup-java 4×, upload-artifact 4×). `grep -n` über beide
   Workflows und alle Fundstellen ersetzen, nicht nur den ersten.
4. **Lokal prüfen.** Der Emulator-Job ist der einzige, der im PR-CI nicht
   läuft — SHA-Fehler fallen dort erst spät auf:
   ```bash
   grep -nE 'uses: [^@]+@' .github/workflows/*.yml   # kein @v / @main erlaubt
   sh scripts/check_docs_drift.sh
   ```
5. **Commit.** Eigener Commit, Message nach Schema
   `ci: actions/<name> auf <tag> gepinnt (<kurzer-sha>)`. Kein Mix mit
   Code-Änderungen — der Pin-Bump muss isoliert reviewbar sein.
6. **Nach dem ersten erfolgreichen Lauf** den Plan (`docs/REFACTORING_PLAN.md`)
   aktualisieren, falls der Punkt dort als offen geführt war.

### Was ausdrücklich nicht passiert

- **Kein `dependabot` für Actions.** Ein Auto-Bump würde die Prüfsummen- und
  Pin-Disziplin stillschweigend aufweichen; der Bus-Faktor-1-Effekt bleibt
  dieselbe, nur automatisiert. Der manuelle Weg in §3 ist vier Schritte kurz.
- **Keine Tag-Pins, auch nicht `v4`.** Ein Tag ist eine bewegliche Referenz;
  genau das eliminiert die Mehrwert-Absicherung dieser Maßnahme.
- **Kein `pull_request_target` / Fork-Code im Release-Workflow.**

## 4. Restarbeiten (bewusst offen)

- **SLSA-Provenance/Attestierung** des Release-Artefakts: nicht umgesetzt.
  `actions/attest-build-provenance` wäre der Weg; es braucht
  `id-token: write` und ist bewusst als eigener Punkt im Refactor-Plan
  geführt, nicht als Teil des Pinnings.
- **Clean-Cache-Auflösung auf einer frischen Maschine**: weiterhin nicht
  praktisch verifiziert — der Pin schützt vor verfälschten Artefakten, nicht
  vor einem Cache, der eine alte, korrekte Auflösung verdeckt.
- **Emulator-Job (API 34) im PR-CI**: siehe Plan P0. Der Pin auf
  `android-emulator-runner` ist gesetzt, der grüne Lauf steht noch aus.
