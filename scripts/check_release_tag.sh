#!/bin/sh
# KeyTab – Release-Tag ↔ Version guard
#
# Warum: `v0.19` wurde auf einen Commit getaggt, dessen Baum bereits 0.20 war.
# F-Droids `checkupdates` meldete "0.20 im 0.19-Tag", der Metadaten-MR scheiterte
# (GitHub-Issue #1). Tags sind per Ruleset unveraenderlich — der Fehler ist
# nicht reparierbar, nur vermeidbar. Dieses Skript prueft ihn VOR dem Push.
#
# Aufruf:
#   sh scripts/check_release_tag.sh              # CI: prueft nur, wenn ein Tag gebaut wird
#   sh scripts/check_release_tag.sh v0.22        # ein bestimmtes Tag pruefen
#   sh scripts/check_release_tag.sh --audit      # alle lokalen Tags pruefen (braucht Tags)
#
# Geprueft wird nur, was eindeutig entscheidbar ist:
#   1. Tag-Name == "v" + versionName im Baum des Tags
#   2. versionCode des Tags ist groesser als der des vorherigen Tags
#   3. fastlane-Changelog fuer den versionCode des Tags existiert im Tag-Baum
#
# Ausnahmen stehen in scripts/release-tag-allowlist.txt (bekannte Fehl-Tags).
# Die Liste darf nicht wachsen: ein neuer Eintrag ist eine bewusste Entscheidung
# und keine stille Reparatur.

set -u

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT" || exit 1

RED=''; GRN=''; YLW=''; RST=''
if [ -t 1 ]; then
    RED=$(printf '\033[31m'); GRN=$(printf '\033[32m')
    YLW=$(printf '\033[33m'); RST=$(printf '\033[0m')
fi

ALLOWLIST='scripts/release-tag-allowlist.txt'
AUDIT=0
FAIL=0

problem() { FAIL=$((FAIL + 1)); printf '  %sx%s %s\n    -> %s\n' "$RED" "$RST" "$1" "$2"; }
ok()      { printf '  %sOK%s %s\n' "$GRN" "$RST" "$1"; }
note()    { printf '  %s!%s %s\n' "$YLW" "$RST" "$1"; }

is_allowlisted() {
    [ -f "$ALLOWLIST" ] || return 1
    # Erste Spalte ist der Tag, alles nach '#' ist Kommentar.
    sed -n 's/[[:space:]]*#.*$//p' "$ALLOWLIST" | awk '{print $1}' | grep -qxF "$1"
}

# versionName/versionCode aus dem Baum eines Refs lesen.
version_of() {
    git show "$1:app/build.gradle.kts" 2>/dev/null \
        | sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' | head -1
}
code_of() {
    git show "$1:app/build.gradle.kts" 2>/dev/null \
        | sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9]*\).*/\1/p' | head -1
}

check_tag() {
    tag="$1"
    vname=$(version_of "$tag")
    vcode=$(code_of "$tag")
    if [ -z "$vname" ] || [ -z "$vcode" ]; then
        problem "$tag: keine Version im Tag-Baum lesbar" \
                "Zeigt der Tag auf einen Commit mit app/build.gradle.kts?"
        return
    fi
    expected="v$vname"
    if [ "$tag" != "$expected" ]; then
        if is_allowlisted "$tag"; then
            note "$tag: Baum sagt $vname (bekannter Fehl-Tag, allowlistet) — $ALLOWLIST"
        else
            problem "$tag: Baum sagt versionName $vname, erwartet waere $expected" \
                    "Der Tag zeigt auf den falschen Commit. Neues Tag setzen, dieses nie bewegen."
        fi
        return
    fi
    if ! git show "$tag:fastlane/metadata/android/en-US/changelogs/$vcode.txt" >/dev/null 2>&1; then
        if [ "$AUDIT" -eq 1 ]; then
            # Historie: Tags vor der fastlane-Konvention haben keine Notes. Fuer
            # den *laufenden* Release bleibt es ein harter Fehler (unten).
            note "$tag: kein fastlane-Changelog $vcode.txt im Tag-Baum (Alt-Tag, Historie)"
        else
            problem "$tag: fastlane-Changelog $vcode.txt fehlt im Tag-Baum" \
                    "Release-Notes vor dem Tag anlegen (fastlane/.../changelogs/$vcode.txt)."
        fi
        return
    fi
    ok "$tag: versionName $vname, versionCode $vcode, Release-Notes vorhanden"
}

printf '\nKeyTab Release-Tag Check\n=========================\n\n'

if [ "${1:-}" = '--audit' ]; then
    AUDIT=1
    [ -f "$ALLOWLIST" ] || { printf 'Allowlist %s fehlt\n' "$ALLOWLIST" >&2; exit 1; }
    # Alle Tags nach versionCode sortieren — Monotonie ist nur so pruefbar.
    tmp=$(mktemp)
    for t in $(git tag -l 'v*'); do
        printf '%s %s\n' "$(code_of "$t")" "$t" >> "$tmp"
    done
    prev_code=''; prev_tag=''
    sort -n "$tmp" > "$tmp.sorted"
    while read -r vcode tag; do
        [ -n "$tag" ] || continue
        check_tag "$tag"
        if [ -n "$vcode" ] && [ -n "$prev_code" ]; then
            case "$vcode$prev_code" in
                *[!0-9]*) : ;;   # kein numerischer Vergleich moeglich
                *)
                    if [ "$vcode" -le "$prev_code" ] \
                        && ! is_allowlisted "$tag" && ! is_allowlisted "$prev_tag"; then
                        problem "$tag: versionCode $vcode ist nicht groesser als $prev_tag ($prev_code)" \
                                "Jede Version braucht einen hoeheren versionCode (F-Droid vergleicht ihn)."
                    fi
                    ;;
            esac
        fi
        prev_code="$vcode"; prev_tag="$tag"
    done < "$tmp.sorted"
    rm -f "$tmp" "$tmp.sorted"
else
    tag=''
    if [ -n "${1:-}" ]; then
        tag="$1"
    elif [ "${GITHUB_REF_TYPE:-}" = 'tag' ]; then
        tag="${GITHUB_REF_NAME:-}"
    else
        tag=$(git describe --tags --exact-match 2>/dev/null || true)
    fi
    if [ -z "$tag" ]; then
        printf '  Kein Tag-Ref (HEAD ist kein getaggter Commit) — nichts zu pruefen.\n'
        printf '  Ein Tag-Push prueft automatisch; alle Tags: %s --audit\n\n' "$0"
        exit 0
    fi
    check_tag "$tag"
fi

printf '\n'
if [ "$FAIL" -eq 0 ]; then
    printf '%sRelease-Tag Check bestanden.%s\n\n' "$GRN" "$RST"
    exit 0
fi
printf '%sRelease-Tag Check fehlgeschlagen (%d Problem(e)).%s\n\n' "$RED" "$FAIL" "$RST"
exit 1
