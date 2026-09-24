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

# --------------------------------- 5. Groessen-, Service- und Sprachzahlen
# Neu 2026-09-22: genau die Zahlen, die zuletzt still veraltet sind. Das README
# nannte "283 lines" fuer den Service (tatsaechlich 417), 4.718 Testzeilen /
# 50.3 % Ratio (tatsaechlich 7.038 / 74.5 %) und "176 strings" (tatsaechlich
# 170). Alles ohne Toolchain berechenbar (find/wc/grep) und damit CI-tauglich.
printf '5) Groessen-, Service- und Sprachzahlen\n'

kt_files() { find "$1" -name '*.kt' | wc -l | tr -d ' '; }
kt_lines() { find "$1" -name '*.kt' -exec cat {} + | wc -l | tr -d ' '; }
uncomma() { printf '%s' "$1" | tr -d ','; }

T_LINES=$(kt_lines app/src/test)
T_FILES=$(kt_files app/src/test)
M_LINES=$(kt_lines app/src/main)
M_FILES=$(kt_files app/src/main)
RATIO=$(awk -v t="$T_LINES" -v m="$M_LINES" 'BEGIN { printf "%.1f", (m > 0 ? 100 * t / m : 0) }')

SIZES=$(sed -n 's/.*Test code is \([0-9,]*\) lines in \([0-9,]*\) files against \([0-9,]*\) lines of main code (\([0-9,]*\) files).*/\1 \2 \3 \4/p' README.md | head -1)
if [ -z "$SIZES" ]; then
    problem 'README enthaelt nicht "Test code is <X> lines in <Y> files against <Z> lines of main code (<N> files)"' \
            'Formulierung beibehalten - der Waechter liest genau diese Zahlen'
else
    C_TL=$(uncomma "$(printf '%s' "$SIZES" | cut -d' ' -f1)")
    C_TF=$(uncomma "$(printf '%s' "$SIZES" | cut -d' ' -f2)")
    C_ML=$(uncomma "$(printf '%s' "$SIZES" | cut -d' ' -f3)")
    C_MF=$(uncomma "$(printf '%s' "$SIZES" | cut -d' ' -f4)")
    [ "$C_TL" = "$T_LINES" ] || problem "README nennt $C_TL Testzeilen, tatsaechlich $T_LINES" \
        "Testzeilen im README auf $T_LINES korrigieren"
    [ "$C_TF" = "$T_FILES" ] || problem "README nennt $C_TF Testdateien, tatsaechlich $T_FILES" \
        "Testdateien im README auf $T_FILES korrigieren"
    [ "$C_ML" = "$M_LINES" ] || problem "README nennt $C_ML Main-Zeilen, tatsaechlich $M_LINES" \
        "Main-Zeilen im README auf $M_LINES korrigieren"
    [ "$C_MF" = "$M_FILES" ] || problem "README nennt $C_MF Main-Dateien, tatsaechlich $M_FILES" \
        "Main-Dateien im README auf $M_FILES korrigieren"
fi

C_RATIO=$(sed -n 's/.*\*\*\([0-9.]*\) % test-to-main ratio\*\*.*/\1/p' README.md | head -1)
if [ -z "$C_RATIO" ]; then
    problem 'README enthaelt keine "**X % test-to-main ratio**"' \
            'Ratio-Angabe (aus Test-/Main-Zeilen berechnet) beibehalten'
elif [ "$C_RATIO" != "$RATIO" ]; then
    problem "README nennt $C_RATIO % Test:Main-Ratio, tatsaechlich $RATIO %" \
            "Ratio im README auf $RATIO korrigieren"
else
    ok "Test:Main-Ratio stimmt ($RATIO %, $T_LINES/$M_LINES Zeilen)"
fi

SVC_ACT=$(wc -l < app/src/main/java/com/piotv/keytab/ime/KeyTabImeService.kt | tr -d ' ')
SVC_CLAIMS=$( { sed -n 's/.*keyboard core (\([0-9]*\) lines of orchestration).*/\1/p' README.md
                sed -n 's/.*orchestration (\([0-9]*\) lines).*/\1/p' README.md; } )
if [ -z "$SVC_CLAIMS" ]; then
    problem 'README nennt keine Zeilenzahl fuer KeyTabImeService.kt' \
            'Angabe "(<N> lines)" im README behalten'
else
    for C_SVC in $SVC_CLAIMS; do
        [ "$C_SVC" = "$SVC_ACT" ] || problem "README nennt $C_SVC Zeilen fuer KeyTabImeService.kt, tatsaechlich $SVC_ACT" \
            "Zahl im README auf $SVC_ACT korrigieren"
    done
    ok "Service-Groesse stimmt ($SVC_ACT Zeilen)"
fi

S_DE=$(grep -c '<string name=' app/src/main/res/values/strings.xml)
S_EN=$(grep -c '<string name=' app/src/main/res/values-en/strings.xml)
if [ "$S_DE" != "$S_EN" ]; then
    problem "values-en ist unvollstaendig: $S_EN/$S_DE Strings" \
            "Fehlende Uebersetzungen in values-en/strings.xml ergaenzen"
else
    ok "i18n vollstaendig ($S_EN/$S_DE Strings)"
fi
C_DE=$(sed -n 's/.*# \([0-9]*\) strings (default.*/\1/p' README.md | head -1)
C_EN=$(sed -n 's/.*# English locale (\([0-9]*\) strings.*/\1/p' README.md | head -1)
[ "$C_DE" = "$S_DE" ] || problem "README nennt $C_DE Strings fuer values/, tatsaechlich $S_DE" \
    "Zahl im README auf $S_DE korrigieren"
[ "$C_EN" = "$S_EN" ] || problem "README nennt $C_EN Strings fuer values-en/, tatsaechlich $S_EN" \
    "Zahl im README auf $S_EN korrigieren"
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
