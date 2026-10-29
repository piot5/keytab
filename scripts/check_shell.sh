#!/bin/sh
# KeyTab – Shell hygiene guard
#
# Fängt die Fehlerklasse, die kein anderes Gate sieht: Syntaxfehler und die
# `set -e`-Falle (Fehlerzweig hinter einem Aufruf, der die Shell schon beendet;
# `RC=$?` direkt nach einer Zuweisung ist konstant). Belegter Anlass
# (2026-10-29): install_keytab.sh brach bei fehlgeschlagenem rish-Aufruf
# kommentarlos ab, obwohl genau dafür ein Fehlerblock im Skript stand.
#
# Geprüft wird:
#   1. Syntax — jede Shell-Datei parst unter ihrem eigenen Interpreter
#      (Shebang; sh/dash/bash). Kein Toolchain nötig, läuft also auch auf dem Gerät.
#   2. shellcheck mit Severity=warning — Errors und Warnings blockieren.
#      Notes/Styles blockieren nicht: wo sie gewollt sind (z. B. SC2086 für
#      Flag-Listen in scripts/baseline.sh), steht ein shellcheck-disable-
#      Kommentar mit Begründung im Code. Fehlt shellcheck lokal, sagt der Lauf
#      das ausdrücklich; der CI-Job `shell` installiert es, damit dieser Teil
#      dort immer greift.
#
# Aufruf:  sh scripts/check_shell.sh
# Exit 0 = sauber, Exit 1 = Befund.

set -u

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT" || exit 1

RED=''; GRN=''; YLW=''; RST=''
if [ -t 1 ]; then
    RED=$(printf '\033[31m'); GRN=$(printf '\033[32m')
    YLW=$(printf '\033[33m'); RST=$(printf '\033[0m')
fi

FAIL=0
problem() { FAIL=$((FAIL + 1)); printf '  %sx%s %s\n    -> %s\n' "$RED" "$RST" "$1" "$2"; }
ok()      { printf '  %sOK%s %s\n' "$GRN" "$RST" "$1"; }
note()    { printf '  %s!%s %s\n' "$YLW" "$RST" "$1"; }

LIST=$(mktemp) || exit 1
trap 'rm -f "$LIST"' EXIT

# Alle Shell-Dateien: *.sh überall (ohne Build-/Cache-Bäume) plus die Hooks,
# die absichtlich keine .sh-Endung haben.
find . -name '*.sh' \
    -not -path './.git/*' -not -path './.gradle/*' \
    -not -path './build/*' -not -path './app/build/*' | sort > "$LIST"
find scripts/git-hooks -maxdepth 1 -type f 2>/dev/null | sort >> "$LIST"

printf '\nKeyTab Shell Hygiene Check\n==========================\n\n'

CHECKED=0
SKIPPED=0
while IFS= read -r f; do
    [ -n "$f" ] || continue
    shebang=$(sed -n '1s|^#!\([^ ]*\).*|\1|p' "$f" 2>/dev/null)
    interp=$(basename "${shebang:-sh}")
    if [ "$interp" = 'env' ]; then
        interp=$(sed -n '1s|^#!/usr/bin/env[[:space:]]\{1,\}\([^[:space:]]*\).*|\1|p' "$f")
    fi
    case "${interp:-sh}" in
        sh|dash|bash) : ;;
        *)
            note "$f: Shebang '$shebang' unbekannt — Syntaxprüfung übersprungen"
            SKIPPED=$((SKIPPED + 1))
            continue
            ;;
    esac
    if ! command -v "$interp" >/dev/null 2>&1; then
        note "$f: $interp nicht vorhanden — Syntaxprüfung übersprungen"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi
    if out=$("$interp" -n "$f" 2>&1); then
        CHECKED=$((CHECKED + 1))
    else
        problem "$f: Syntaxfehler ($interp -n)" "$out"
    fi
done < "$LIST"

if [ "$FAIL" -eq 0 ]; then
    ok "$CHECKED Shell-Dateien syntaktisch sauber (Shebang-Interpreter)"
fi
[ "$SKIPPED" -gt 0 ] && note "$SKIPPED Datei(en) übersprungen (siehe oben)"

# --------------------------------------------------------------------------
# Lauf von shellcheck: nur Errors und Warnings blockieren (Severity=warning)
# --------------------------------------------------------------------------
if command -v shellcheck >/dev/null 2>&1; then
    SC_FILES=0
    while IFS= read -r f; do
        [ -n "$f" ] || continue
        out=$(shellcheck -S warning -f gcc "$f" 2>/dev/null || true)
        if [ -n "$out" ]; then
            SC_FILES=$((SC_FILES + 1))
            printf '%s\n' "$out" | sed 's/^/    /'
        fi
    done < "$LIST"
    SC_VER=$(shellcheck --version 2>/dev/null | sed -n 's/^version: //p')
    if [ "$SC_FILES" -eq 0 ]; then
        ok "shellcheck ${SC_VER:-?}: keine Errors/Warnings"
    else
        problem "shellcheck: $SC_FILES Datei(en) mit Errors/Warnings" \
                "Beheben oder im Code begründen (shellcheck-disable-Kommentar mit SCxxxx)"
    fi
else
    note "shellcheck nicht installiert — nur der Syntax-Teil ist gelaufen."
    note "Installieren: apt-get install shellcheck  (CI-Job 'shell' tut das selbst)"
fi

printf '\n'
if [ "$FAIL" -eq 0 ]; then
    printf '%sShell hygiene check passed.%s\n\n' "$GRN" "$RST"
    exit 0
fi

printf '%sShell hygiene findings: %d Problem(e).%s\n\n' "$RED" "$FAIL" "$RST"
exit 1
