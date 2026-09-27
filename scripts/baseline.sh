#!/bin/sh
# KeyTab Baseline-/Gate-Werkzeug
#
# Zentrale Anlaufstelle fuer alles, was zwischen "Code geschrieben" und
# "committen" liegt: statische Analyse, Coverage, Lint, Baseline-Pflege.
#
# Aufruf:
#   sh scripts/baseline.sh                      # = Profil "check"
#   sh scripts/baseline.sh <profil>             # gespeichertes Profil
#   sh scripts/baseline.sh <profil> [opts...]   # Profil + Overrides
#   sh scripts/baseline.sh --list               # Profile anzeigen
#   sh scripts/baseline.sh --help
#
# Overrides (mit jedem Profil, zuletzt gewinnen):
#   --no-offline     Netzwerk erlauben (Default: --offline)
#   --offline        Offline-Modus erzwingen (Default)
#   --rerun          --rerun-tasks (sonst zaehlt Gradle gecachte Tasks)
#   --jobs N         --jobs N an Gradle durchreichen
#   --keep-baseline  bei "baseline" nicht ueberschreiben, nur vergleichen
#   --yes/-y         Rueckfragen bestaetigen (nicht-interaktiv)
#
# Warum ein eigenes Script: die Gradle-Kette lautet drei Task-Namen, die man
# sich merken muss, und :app:detektBaseline ueberschreibt die Baseline
# *sofort und ohne Rueckfrage*. Genau das hat im September 2026 zu 24 stale
# Baseline-Eintraegen gefuehrt. Dieses Script misst erst, vergleicht dann und
# schreibt nur auf ausdruecklichen Auftrag.
#
# Exit-Code: 0 = gruen, 1 = Gate rot oder Fehler, 2 = Aufruf falsch.

set -u

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR" || exit 2

BASELINE_FILE="config/detekt/baseline.xml"
LOG="/tmp/keytab_baseline.log"

GRADLE_OPTS="--offline"
RERUN=""
JOBS=""
KEEP_BASELINE=""
ASSUME_YES=""
PROFILE=""

usage() {
    sed -n '2,28p' "$0" | sed 's/^# \{0,1\}//'
}

# Alle Profile samt Task-Satz ausgeben. Bewusst als Daten im Script statt in
# einer Config-Datei: ein Gate-Satz, den niemand mitliest, verrottet.
list_profiles() {
    printf '\nKeyTab Baseline-Profile\n========================\n\n'
    printf '  %-9s %s\n' 'check'    'testDebugUnitTest koverVerify detektDebug  (vor jedem Commit)'
    printf '  %-9s %s\n' 'fast'     'testDebugUnitTest detektDebug               (Umbau-Schleife)'
    printf '  %-9s %s\n' 'baseline' 'detektBaseline + Diff gegen die bestehende Datei'
    printf '  %-9s %s\n' 'audit'    'Baseline-Neuzaehlung read-only, schreibt nichts'
    printf '  %-9s %s\n' 'clean'    'Gradle-Cache leeren, danach detektBaseline'
    printf '  %-9s %s\n' 'full'     'check + lintDebug + assembleRelease + Drift-Gate'
    printf '\nBeispiele:\n'
    printf '  sh scripts/baseline.sh check --rerun\n'
    printf '  sh scripts/baseline.sh audit\n'
    printf '  sh scripts/baseline.sh baseline --yes\n\n'
}

while [ $# -gt 0 ]; do
    case "$1" in
        -h|--help)    usage; exit 0 ;;
        --list|-l)     list_profiles; exit 0 ;;
        --no-offline)  GRADLE_OPTS="" ;;
        --offline)     GRADLE_OPTS="--offline" ;;
        --rerun)       RERUN="--rerun-tasks" ;;
        --keep-baseline) KEEP_BASELINE="1" ;;
        --yes|-y)      ASSUME_YES="1" ;;
        --jobs)        shift; JOBS="--jobs ${1:-}" ;;
        --jobs=*)      JOBS="$1" ;;
        -*)            printf 'Unbekanntes Flag: %s\n\n' "$1" >&2; usage >&2; exit 2 ;;
        *)             PROFILE="$1" ;;
    esac
    shift
done

[ -n "$PROFILE" ] || PROFILE="check"

printf '\nKeyTab Baseline  Profil: %s\n' "$PROFILE"
baseline_count() {
    [ -f "$1" ] || { echo 0; return; }
    grep -c '<ID>' "$1" 2>/dev/null || echo 0
}

# IDs als normalisierte Liste, um sauber diffen zu koennen.
baseline_ids() {
    [ -f "$1" ] || return 0
    grep -oE '<ID>[^<]+' "$1" 2>/dev/null | sed 's/<ID>//' | sort
}

# Gradle-Report-Verzeichnis loeschen. Ohne das liefert detektBaseline
# gecachte Ergebnisse und man "misst" den alten Stand ein zweites Mal.
purge_detekt_reports() {
    rm -rf app/build/reports/detekt
}

# Aktuelle Befundlage messen, ohne die echte Baseline zu verlieren.
# Schreibt das Ergebnis nach <tmp>.measured und stellt danach wieder her.
measure_baseline() {
    _tmp="$1"
    cp "$BASELINE_FILE" "$_tmp" 2>/dev/null || : > "$_tmp"
    purge_detekt_reports
    printf '<SmellBaseline>\n</SmellBaseline>\n' > "$BASELINE_FILE"
    printf '  Messe aktuellen Code-Stand (Baseline temporaer leer) ...\n'
    sh ./gradlew :app:detektBaseline $GRADLE_OPTS $RERUN >/dev/null 2>&1
    cp "$BASELINE_FILE" "$_tmp.measured"
    cp "$_tmp" "$BASELINE_FILE"
}

# Stale Eintraege sind Befunde, deren Code es nicht mehr gibt. Sie sind nicht
# harmlos: die ID enthaelt den Literalwert, also wuerde derselbe Wert an
# derselben Stelle beim naechsten Mal erneut durchgewunken.
# Rueckgabe 1, wenn neue Befunde entstanden sind.
report_delta() {
    _old="$1"
    _new="$2"
    printf '\n  Baseline: %s -> %s Eintraege\n' \
        "$(baseline_count "$_old")" "$(baseline_count "$_new")"

    baseline_ids "$_old" > "$_old.ids"
    baseline_ids "$_new" > "$_new.ids"

    _gone=$(comm -23 "$_old.ids" "$_new.ids" | wc -l | tr -d ' ')
    _added=$(comm -13 "$_old.ids" "$_new.ids" | wc -l | tr -d ' ')

    if [ "$_gone" -gt 0 ]; then
        printf '\n  Entfallen (%s) - darunter stale (Code existiert nicht mehr):\n' "$_gone"
        comm -23 "$_old.ids" "$_new.ids" | sed 's/^/    - /'
    fi
    if [ "$_added" -gt 0 ]; then
        printf '\n  NEU (%s) - wuerde das Gate roten:\n' "$_added"
        comm -13 "$_old.ids" "$_new.ids" | sed 's/^/    + /'
        return 1
    fi
    printf '\n  Keine neuen Befunde.\n'
    return 0
}

confirm() {
    [ -n "$ASSUME_YES" ] && return 0
    printf '\n%s [j/N] ' "$1"
    read -r _ans || return 1
    case "$_ans" in
        j|J|y|Y) return 0 ;;
        *)       return 1 ;;
    esac
}

# Gradle ausfuehren, Ausgabe ins Log UND auf den Bildschirm, Exit-Code
# korrekt durchreichen.
#
# Wichtig: `gradlew ... | tee log` als Bedingung zu benutzen waere ein
# stiller Fehler — in einer Pipeline zaehlt der Status des LETZTEN Befehls,
# also immer von tee (0). Ein rotes Gate wuerde als gruen gemeldet. Deshalb
# erst in die Logdatei schreiben, den Status merken, dann ausgeben.
run_logged() {
    sh ./gradlew "$@" $GRADLE_OPTS $RERUN $JOBS > "$LOG" 2>&1
    _rc=$?
    cat "$LOG"
    return "$_rc"
}
case "$PROFILE" in

check)
    printf '  -> testDebugUnitTest koverVerify detektDebug\n'
    if run_logged :app:testDebugUnitTest :app:koverVerify :app:detektDebug; then
        printf '\nOK  alle Gates gruen. Baseline: %s Eintraege\n' \
            "$(baseline_count "$BASELINE_FILE")"
        exit 0
    fi
    printf '\nFEHLER  Gate rot - Log: %s\n' "$LOG" >&2
    exit 1
    ;;

fast)
    printf '  -> testDebugUnitTest detektDebug\n'
    if run_logged :app:testDebugUnitTest :app:detektDebug; then
        printf '\nOK  Tests + detekt gruen.\n'
        exit 0
    fi
    printf '\nFEHLER  - Log: %s\n' "$LOG" >&2
    exit 1
    ;;

audit)
    # Read-only: misst, meldet, schreibt nichts. Das Profil, um zu pruefen,
    # ob die eingecheckte Baseline noch stimmt.
    _tmp=$(mktemp)
    measure_baseline "$_tmp"
    report_delta "$_tmp" "$_tmp.measured"
    _rc=$?
    _measured_count=$(baseline_count "$_tmp.measured")
    printf '\n  Die Baseline-Datei wurde NICHT veraendert (Profil "audit").\n'
    printf '  Zum Uebernehmen: sh scripts/baseline.sh baseline\n'
    rm -f "$_tmp" "$_tmp.measured" "$_tmp.ids" "$_tmp.measured.ids"
    if [ "$_rc" -ne 0 ]; then
        printf '  Hinweis: neue Befunde gefunden - das Gate waere rot.\n' >&2
        exit 1
    fi
    printf '  Baseline aktuell: %s Eintraege.\n' "$_measured_count"
    exit 0
    ;;

baseline)
    # Erst messen und den Diff zeigen, DANN auf Nachfrage ueberschreiben.
    _tmp=$(mktemp)
    measure_baseline "$_tmp"
    report_delta "$_tmp" "$_tmp.measured"
    _rc=$?

    if [ -n "$KEEP_BASELINE" ]; then
        printf '\n  --keep-baseline: Datei bleibt unangetastet.\n'
        rm -f "$_tmp" "$_tmp.measured" "$_tmp.ids" "$_tmp.measured.ids"
        exit "$_rc"
    fi

    if confirm "  Baseline jetzt mit dem gemessenen Stand ueberschreiben?"; then
        cp "$_tmp.measured" "$BASELINE_FILE"
        printf '\nOK  Baseline aktualisiert: %s Eintraege\n' \
            "$(baseline_count "$BASELINE_FILE")"
        rm -f "$_tmp" "$_tmp.measured" "$_tmp.ids" "$_tmp.measured.ids"
        exit 0
    fi
    printf '\n  Abgebrochen - Baseline unveraendert.\n'
    rm -f "$_tmp" "$_tmp.measured" "$_tmp.ids" "$_tmp.measured.ids"
    exit 0
    ;;

clean)
    printf '  -> Gradle-Cache leeren, dann detektBaseline neu erzeugen\n'
    purge_detekt_reports
    sh ./gradlew clean $GRADLE_OPTS || exit 1
    purge_detekt_reports
    sh ./gradlew :app:detektBaseline $GRADLE_OPTS || exit 1
    printf '\nOK  Baseline neu erzeugt: %s Eintraege\n' \
        "$(baseline_count "$BASELINE_FILE")"
    printf '  Anschliessend pruefen: sh scripts/baseline.sh check\n'
    exit 0
    ;;

full)
    printf '  -> Doku-Drift + check + lintDebug + assembleRelease\n'
    _rc=0
    sh scripts/check_docs_drift.sh || _rc=1
    run_logged :app:testDebugUnitTest :app:koverVerify :app:detektDebug :app:lintDebug || _rc=1
    # Zweiter Lauf haengt sein Log an das erste an.
    sh ./gradlew :app:assembleRelease $GRADLE_OPTS $RERUN $JOBS >> "$LOG" 2>&1 || _rc=1
    tail -20 "$LOG"
    if [ "$_rc" -eq 0 ]; then
        printf '\nOK  alle Gates + Release-Kompilierung gruen.\n'
        exit 0
    fi
    printf '\nFEHLER  - Log: %s\n' "$LOG" >&2
    exit 1
    ;;

*)
    printf '\nUnbekanntes Profil: %s\n\n' "$PROFILE" >&2
    list_profiles >&2
    exit 2
    ;;
esac
