# KeyTab — Baseline-Pflege & Gates

`scripts/baseline.sh` ist die Anlaufstelle für alles zwischen „Code
geschrieben" und „committen". Es kapselt die Gradle-Aufrufketten und — vor
allem — das **sichere** Umgehen mit der detekt-Baseline.

```bash
sh scripts/baseline.sh --list        # Profile ansehen
sh scripts/baseline.sh --help         # Parameter
sh scripts/baseline.sh                # = check
```

## Profile

| Profil | Task-Satz | Wofür |
|---|---|---|
| `check` | `testDebugUnitTest koverVerify detektDebug` | vor jedem Commit |
| `fast` | `testDebugUnitTest detektDebug` | Umbau-Schleife |
| `baseline` | `detektBaseline` + Diff | Baseline bewusst neu erzeugen |
| `audit` | Neu-Zählung, **schreibt nichts** | prüfen, ob die Baseline stimmt |
| `clean` | `clean` + `detektBaseline` | nach Cache-Problemen |
| `full` | Drift + check + `lintDebug` + `assembleRelease` | vor dem Release |

## Warum das Script existiert

`:app:detektBaseline` überschreibt `config/detekt/baseline.xml` **sofort und
ohne Rückfrage**. Im September 2026 führte das zu 24 stale Einträgen: die
Baseline behauptete 181 Befunde, tatsächlich existierten 157. Solche Einträge
sind nicht harmlos — die ID enthält den Literalwert
(`MagicNumber:ThemePrefs.kt$ThemePrefs$24`), also würde derselbe Wert an
derselben Stelle künftig erneut durchgewunken. Eine abgelaufene Baseline ist
eine Liste präziser Erlaubnisse, die nicht ausläuft.

`audit` und `baseline` messen deshalb beide erst, zeigen den Diff und
schreiben nur nach Rückfrage. Vor dem Neuschreiben wird
`app/build/reports/detekt` gelöscht — sonst liefert Gradle gecachte
Ergebnisse und man misst denselben Stand ein zweites Mal.

Baseline-Einträge lassen sich nur durch Neuerzeugen entfernen, nicht durch
Editieren der Datei.

## Parameter

| Flag | Wirkung |
|---|---|
| `--offline` / `--no-offline` | Gradle-Netzmodus (Default `--offline`) |
| `--rerun` | `--rerun-tasks`, falls Gradle sonst cacht |
| `--jobs N` | an Gradle durchreichen |
| `--keep-baseline` | bei `baseline` nur vergleichen, nicht schreiben |
| `--yes` / `-y` | Rückfragen bestätigen (nicht-interaktiv) |

## Exit-Codes

`0` grün · `1` Gate rot oder Fehler · `2` Aufruf falsch.

Damit ist das Script direkt in einer Hakenkette nutzbar:

```bash
sh scripts/baseline.sh check && git commit -m "..."
```

Hinweis zur Implementierung: Gradle-Ausgaben werden bewusst erst in eine
Logdatei geschrieben und danach ausgegeben. `gradlew … | tee log` als
Bedingung zu verwenden wäre stiller Fehler — in einer Pipeline zählt der
Status des letzten Befehls, also immer von `tee`.
