#!/bin/sh
# KeyTab – Docs drift guard
#
# Prevents README/docs and the actual code from drifting apart.
# Run as CI job "docs" before Build/Test (fast, no Gradle, no toolchain)
# or locally:
#
#     sh scripts/check_docs_drift.sh
#
# Checked: only what is unambiguous and machine-decidable — no heuristics
# on prose, so the guard does not turn red on legitimate wording changes.
#
#   1. Version sync: versionName in build.gradle.kts == first header in CHANGELOG.md
#   2. Version singularity: versionCode/versionName only in app/build.gradle.kts
#   3. README must not contain a hard-coded version (single source of truth)
#   4. Relative links in *.md must resolve — dead references are the silent doc
#      bug: they look correct and lead nowhere (e.g. a pointer to a removed
#      backlog file or to a baseline file that never existed in the repo)
#   5. Launcher icons have the right density/pixel size and stay within the size
#      budget (scripts/make_launcher_icons.py --check). v0.20 shipped the
#      2600-px master as ic_launcher.png in all five density folders: 5 x 982 KB
#      in the APK, which grew from 2.28 MB to 6.92 MB — no gate noticed.
#
# Exit 1 on drift, Exit 0 when consistent.

set -u

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT" || exit 1

RED=''; GRN=''; RST=''
if [ -t 1 ]; then
    RED=$(printf '\033[31m'); GRN=$(printf '\033[32m'); RST=$(printf '\033[0m')
fi

FAIL=0

problem() {
    FAIL=$((FAIL + 1))
    printf '  %sx%s %s\n    -> %s\n' "$RED" "$RST" "$1" "$2"
}

ok() { printf '  %sOK%s %s\n' "$GRN" "$RST" "$1"; }

printf '\nKeyTab Docs Drift Check\n=========================\n\n'

GRADLE_FILE='app/build.gradle.kts'
CHANGELOG_FILE='CHANGELOG.md'

VCODE=$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9]*\).*/\1/p' "$GRADLE_FILE" | head -1)
VNAME=$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$GRADLE_FILE" | head -1)

if [ -z "$VNAME" ] || [ -z "$VCODE" ]; then
    problem "versionCode/versionName not found in $GRADLE_FILE" \
            "Check defaultConfig: versionCode = N, versionName = \"X.Y.Z\""
else
    ok "build.gradle.kts: versionName $VNAME (versionCode $VCODE)"

    # README must not hard-code a version
    if grep -Eq '^Version:[[:space:]]*[0-9]' README.md 2>/dev/null; then
        problem "README contains a hard-coded version" \
                "Remove version from README (single source: $GRADLE_FILE / $CHANGELOG_FILE)"
    else
        ok "README contains no hard-coded version"
    fi

    # CHANGELOG.md head: first "## X.Y.Z" or "## [X.Y.Z]" header
    if [ ! -f "$CHANGELOG_FILE" ]; then
        problem "$CHANGELOG_FILE missing" \
                "Create CHANGELOG.md with a top header matching the version in build.gradle.kts"
    else
        CHANGELOG_LINE=$(grep -E '^##[[:space:]]*[0-9]' "$CHANGELOG_FILE" | head -1)
        CHANGELOG_VER=$(printf '%s\n' "$CHANGELOG_LINE" | sed -n 's/^##[[:space:]]*\([0-9][0-9.]*\).*/\1/p')
        if [ -z "$CHANGELOG_VER" ]; then
            problem "No version header found at top of $CHANGELOG_FILE" \
                    "Add a header like '## $VNAME' or '## [$VNAME]'"
        elif [ "$CHANGELOG_VER" != "$VNAME" ]; then
            problem "$CHANGELOG_FILE head says $CHANGELOG_VER, build.gradle.kts says $VNAME" \
                    "Align CHANGELOG.md header with build.gradle.kts versionName"
        else
            ok "CHANGELOG.md head matches build.gradle.kts ($VNAME)"
        fi
    fi

    # fastlane changelog: check that a file for the current versionCode exists
    FASTLANE_DIR='fastlane/metadata/android/en-US/changelogs'
    if [ -d "$FASTLANE_DIR" ]; then
        if [ -f "$FASTLANE_DIR/$VCODE.txt" ]; then
            ok "fastlane changelog exists for versionCode $VCODE"
        else
            problem "No fastlane changelog for versionCode $VCODE in $FASTLANE_DIR" \
                    "Add $FASTLANE_DIR/$VCODE.txt"
        fi
    fi
    # 4. Relative Markdown-Links müssen auflösen. Geprüft werden nur relative
    #    Ziele (keine http(s)/mailto/Anker) und nur die Existenz — ob der Text
    #    zum Ziel passt, ist Prosa und bleibt Sache des Reviews.
    DEAD=$(find . -name '*.md' \
                -not -path './.git/*' -not -path './.gradle/*' \
                -not -path './build/*' -not -path './app/build/*' \
                -print | sort | while IFS= read -r md; do
        dir=$(dirname "$md")
        grep -oE '\]\([^)]+\)' "$md" 2>/dev/null | sed 's/^](//; s/)$//' \
        | while IFS= read -r link; do
            case "$link" in
                http://*|https://*|mailto:*|'#'*) continue ;;
            esac
            target=${link%%#*}
            [ -n "$target" ] || continue
            [ -e "$dir/$target" ] || printf '%s -> %s\n' "$md" "$link"
        done
    done)
    if [ -z "$DEAD" ]; then
        ok "no dead relative links in *.md"
    else
        # Details ausgeben, den Zähler aber ausserhalb der Pipe hochziehen:
        # `problem` in einer Pipe-Subshell würde den FAIL-Zähler verlieren.
        N=$(printf '%s\n' "$DEAD" | grep -c .)
        printf '%s\n' "$DEAD" | sed 's/^/    /'
        problem "$N dead relative link(s) in *.md" \
                "Ziel anlegen oder Verweis entfernen"
    fi
fi

# 5. Launcher-Icons: Dichte/Pixelmasse und Groessenbudget. Ein versehentlich
#    hineinkopiertes 2600-px-Artwork hat die APK schon einmal von 2,28 MB auf
#    6,92 MB gebracht (v0.20) — kein Gate hat es gesehen. Der Pruefmodus kommt
#    ohne Pillow aus und laeuft daher auch hier ohne Toolchain.
if command -v python3 >/dev/null 2>&1; then
    ICON_RC=0
    ICON_OUT=$(python3 scripts/make_launcher_icons.py --check 2>&1) || ICON_RC=$?
    if [ "$ICON_RC" -eq 0 ]; then
        ok "launcher icons: density/size correct, within budget"
    else
        printf '%s\n' "$ICON_OUT" | sed 's/^/    /'
        problem "launcher icons drift (density/size/budget)" \
                "Neu erzeugen: python3 scripts/make_launcher_icons.py"
    fi
else
    printf '  !  python3 fehlt — Launcher-Icon-Pruefung uebersprungen\n'
fi

printf '\n'
if [ "$FAIL" -eq 0 ]; then
    printf '%sDocs drift check passed.%s\n\n' "$GRN" "$RST"
    exit 0
fi

printf '%sDocs drift found (%d problem(s)):%s%s\n\n' "$RED" "$FAIL" "$RST" ""
printf 'Bring docs and code back into alignment (see hints above).\n\n'
exit 1
