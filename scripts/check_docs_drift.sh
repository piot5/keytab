#!/bin/sh
# KeyTab – Doku-Drift-Wächter
#
# Verhindert, dass README/Doku und der tatsächliche Code auseinanderlaufen.
# Wird in CI als eigener Job "docs" VOR Build/Test ausgeführt (schnell, kein
# Gradle, keine Toolchain nötig) und kann lokal identisch aufgerufen werden:
#
#     sh scripts/check_docs_drift.sh
#
# Geprüft wird nur, was eindeutig und maschinell entscheidbar ist — bewusst
# keine Heuristik auf Prosa, damit der Wächter nicht bei legitimen
# Formulierungsänderungen fälschlich rot wird. Die abgedeckten Drift-Klassen:
#
#   1. Versionsgleichlauf  versionName == CHANGELOG.md-Kopf == fastlane-Changelog
#                          (die drei Stellen, die bei Releases manuell synchron
#                          gehalten werden mussten)
#   2. Testzahlen          README „N unit tests … M suites“ == tatsächliche
#                          @Test-Anzahl und Testklassen-Dateien
#   3. Changelog-Zuhause   Changelog lebt in CHANGELOG.md, nicht im README
#   4. Versions-Singularität  versionCode/versionName nur in app/build.gradle.kts
#
# Exit 1 bei Drift, Exit 0 wenn alles konsistent ist.

set -u

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT" || exit 1

RED=''; YEL=''; GRN=''; RST=''
if [ -t 1 ]; then
    RED=$(printf '\033[31m'); YEL=$(printf '\033[33m')
    GRN=$(printf '\033[32m'); RST=$(printf '\033[0m')
fi

FAIL=0
PROBLEMS=''

# problem <Beschreibung> <Fix-Hinweis>
problem() {
    FAIL=$((FAIL + 1))
    PROBLEMS="$PROBLEMS
  ${RED}x${RST} $1
    ${YEL}->${RST} $2"
}

ok() { printf '  %sOK%s %s\n' "$GRN" "$RST" "$1"; }

printf '\nKeyTab Doku-Drift-Check\n========================\n\n'

# ---------------------------------------------------------------- Hilfsmittel
# Anzahl @Test-Methoden in einer Kotlin-Testdatei (nur echte Annotationen am
# Zeilenanfang, damit Erwähnungen in Kommentaren/Strings nicht mitzählen).
count_tests() {
    grep -c '^[[:space:]]*@Test' "$1" 2>/dev/null || true
}

# ------------------------------------------------- 1. Versionsgleichlauf
printf '1) Versionsgleichlauf\n'

GRADLE_FILE='app/build.gradle.kts'
CHANGELOG_FILE='CHANGELOG.md'

VCODE=$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9]*\).*/\1/p' "$GRADLE_FILE" | head -1)
VNAME=$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$GRADLE_FILE" | head -1)

if [ -z "$VNAME" ] || [ -z "$VCODE" ]; then
    problem "versionCode/versionName nicht in $GRADLE_FILE gefunden" \
            "DefaultConfig pruefen: versionCode = N, versionName = \"X.Y.Z\""
else
    ok "build.gradle.kts: versionName $VNAME (versionCode $VCODE)"

    # README darf die Version nirgends hart nennen - sie steht in build.gradle.kts.
    if grep -q '^Version:[[:space:]]*[0-9]' README.md 2>/dev/null; then
        problem "README enthaelt eine harte Versionsangabe" \
                "Version aus README entfernen (Single Source: $GRADLE_FILE / $CHANGELOG_FILE)"
    else
        ok "README enthaelt keine hartkodierte Version"
    fi

    # CHANGELOG.md-Kopf: erste "## X.Y.Z" bzw. "## [X.Y.Z]" Ueberschrift
    if [ ! -f "$CHANGELOG_FILE" ]; then
        problem "$CHANGELOG_FILE fehlt" \
                "Changelog aus dem README nach $CHANGELOG_FILE verschieben"
    else
        CL_VER=$(sed -n 's/^##[[:space:]]*\[\{0,1\}\([0-9][0-9.]*\).*/\1/p' "$CHANGELOG_FILE" | head -1)
        if [ -z "$CL_VER" ]; then
            problem "keine Versionsueberschrift in $CHANGELOG_FILE gefunden" \
                    "Format: '## $VNAME' bzw. '## [$VNAME] - YYYY-MM-DD'"
        elif [ "$CL_VER" != "$VNAME" ]; then
            problem "$CHANGELOG_FILE beginnt mit $CL_VER, build.gradle.kts ist $VNAME" \
                    "Neuesten Changelog-Abschnitt auf $VNAME setzen (oder versionName anpassen)"
        else
            ok "$CHANGELOG_FILE beginnt mit $CL_VER"
        fi

        # fastlane-Changelog pro versionCode (Play-Store-Konvention)
        FL="fastlane/metadata/android/en-US/changelogs/${VCODE}.txt"
        if [ ! -f "$FL" ]; then
            problem "fastlane-Changelog fuer versionCode $VCODE fehlt ($FL)" \
                    "Neu anlegen: $FL (Release Notes fuer $VNAME)"
        else
            ok "fastlane-Changelog $FL vorhanden"
        fi
    fi
fi
# ------------------------------------------------- 2. Testzahlen im README
printf '2) Testzahlen im README\n'

TEST_DIR='app/src/test/java'
if [ ! -d "$TEST_DIR" ]; then
    problem "$TEST_DIR fehlt" "Testverzeichnis pruefen"
else
    ACTUAL_TESTS=0
    ACTUAL_SUITES=0
    for f in $(find "$TEST_DIR" -name '*Test.kt' | sort); do
        n=$(count_tests "$f")
        ACTUAL_TESTS=$((ACTUAL_TESTS + n))
        ACTUAL_SUITES=$((ACTUAL_SUITES + 1))
    done
    ok "gefunden: $ACTUAL_TESTS Tests in $ACTUAL_SUITES Testklassen"

    # README-Claim: "**N unit tests in M suites, 0 failures**"
    CLAIM=$(sed -n 's/.*\*\*\([0-9][0-9]*\) unit tests in \([0-9][0-9]*\) suites.*/\1 \2/p' README.md | head -1)
    if [ -z "$CLAIM" ]; then
        problem 'README enthaelt keinen Satz "<N> unit tests in <M> suites"' \
                'Formulierung beibehalten: **N unit tests in M suites, 0 failures**'
    else
        C_TESTS=$(echo "$CLAIM" | cut -d' ' -f1)
        C_SUITES=$(echo "$CLAIM" | cut -d' ' -f2)
        if [ "$C_TESTS" != "$ACTUAL_TESTS" ]; then
            problem "README nennt $C_TESTS Tests, tatsaechlich $ACTUAL_TESTS" \
                    "Zahl in README auf $ACTUAL_TESTS korrigieren"
        else
            ok "Testanzahl stimmt ($ACTUAL_TESTS)"
        fi
        if [ "$C_SUITES" != "$ACTUAL_SUITES" ]; then
            problem "README nennt $C_SUITES Suites, tatsaechlich $ACTUAL_SUITES" \
                    "Zahl in README auf $ACTUAL_SUITES korrigieren"
        else
            ok "Suite-Anzahl stimmt ($ACTUAL_SUITES)"
        fi

        # Jede im README gelistete Suite muss als Datei existieren. Nur
        # Tabellenzeilen betrachten ("| `XTest` | N |"), sonst wuerden
        # Gradle-Kommandos wie `:app:testDebugUnitTest` als Suite gelten.
        MISSING=''
        for name in $(grep -o '^|[[:space:]]*`[A-Za-z0-9_]*Test`' README.md | grep -o '`[A-Za-z0-9_]*`' | tr -d '`' | sort -u); do
            if ! find "$TEST_DIR" -name "$name.kt" | grep -q .; then
                MISSING="$MISSING $name"
            fi
        done
        if [ -n "$MISSING" ]; then
            problem "README listet unbekannte Testklassen:$MISSING" \
                    "Zeilen fuer nicht mehr existierende Suites entfernen"
        else
            ok "alle im README gelisteten Suites existieren"
        fi
    fi
fi
printf '\n'

# ------------------------------------------------- 3. Changelog-Zuhause
printf '3) Changelog-Zuhause\n'

# Verboten ist nur ein eigener *Inhalts*-Abschnitt: eine Versions-Ueberschrift
# wie "### 0.9.7" unter "## Changelog". Ein reiner Link-Abschnitt auf
# CHANGELOG.md ist ausdruecklich erwuenscht.
VLINES=$(awk '
    /^#{1,3}[[:space:]]*Changelog/ { in_cl = 1; next }
    /^#{1,2}[[:space:]]/           { in_cl = 0 }
    in_cl && /^#{3,6}[[:space:]]*[0-9]/ { c++ }
    END { print (c == "" ? 0 : c) }
' README.md)
if [ "$VLINES" != "0" ]; then
    problem "README enthaelt $VLINES Versionsabschnitt(e) direkt unter 'Changelog'" \
            "Inhalt nach $CHANGELOG_FILE verschieben, im README nur verlinken"
else
    ok "README enthaelt keinen eigenen Changelog-Inhalt"
fi

if [ -f "$CHANGELOG_FILE" ] && grep -q 'CHANGELOG.md' README.md; then
    ok "README verlinkt $CHANGELOG_FILE"
else
    problem "README verlinkt $CHANGELOG_FILE nicht" \
            "Im README einen Link auf $CHANGELOG_FILE setzen (z. B. unter 'Download')"
fi
printf '\n'

# ------------------------------------------------- 4. Versions-Singulaeritaet
printf '4) Versions-Singulaeritaet\n'

STRAY=$(grep -rn 'versionName[[:space:]]*=\|versionCode[[:space:]]*=' \
        --include=*.gradle.kts --include=*.gradle . 2>/dev/null \
        | grep -v "^\./$GRADLE_FILE:" | grep -v '/build/' || true)
if [ -n "$STRAY" ]; then
    problem "versionCode/versionName auch ausserhalb von $GRADLE_FILE gesetzt" \
            "Nur $GRADLE_FILE darf die Version definieren (sonst Drift zwischen Modulen)"
    printf '%s\n' "$STRAY" | sed 's/^/      /'
else
    ok "Version wird nur in $GRADLE_FILE definiert"
fi
printf '\n'

# ------------------------------------------------------------------- Ergebnis
if [ "$FAIL" -eq 0 ]; then
    printf '%sDoku-Drift-Check bestanden.%s\n\n' "$GRN" "$RST"
    exit 0
fi

printf '%sDoku-Drift gefunden (%d Problem(e)):%s%s\n\n' "$RED" "$FAIL" "$RST" "$PROBLEMS"
printf 'Bitte Doku und Code wieder in Deckung bringen (siehe Hinweise oben).\n\n'
exit 1
printf '\n'
