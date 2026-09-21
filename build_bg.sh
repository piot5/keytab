#!/bin/sh
# KeyTab Build-Skript (Hintergrund, setsid-losgelöst)
#
# Startet den Gradle-Build in einer eigenen Session (setsid), damit der Prozess
# NICHT gekillt wird, wenn die aufrufende Shell/der Tool-Wrapper beendet wird
# (das war das Problem beim direkten `sh ./gradlew ... &`-Aufruf über den
# Terminal-Wrapper). stdout/stderr gehen ins Log; die PID wird gespeichert.
#
# Verwendung:
#   sh build_bg.sh              # debug-APK bauen (Default)
#   sh build_bg.sh debug        # debug-APK bauen
#   sh build_bg.sh release      # release-APK bauen (braucht Signing-Creds)
#   sh build_bg.sh test         # Unit-Tests (testDebugUnitTest)
#   sh build_bg.sh detekt       # statisches Qualitäts-Gate (:app:detekt)
#   sh build_bg.sh wait         # nur warten, bis der laufende Build fertig ist
#
# Status abfragen:
#   tail -f /tmp/keytab_build.log
#   sh build_bg.sh status
#   sh build_bg.sh wait
#
# Exit-Code: 0 = Build lief (oder ist schon fertig), 1 = Fehler.

set -u

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR" || exit 1

LOG="/tmp/keytab_build.log"
PID_FILE="/tmp/keytab_build.pid"
TARGET="${1:-debug}"

# ---------------- Hilfsfunktionen ----------------

is_running() {
    [ -f "$PID_FILE" ] || return 1
    PID=$(cat "$PID_FILE" 2>/dev/null || true)
    [ -n "$PID" ] || return 1
    kill -0 "$PID" 2>/dev/null
    # Doppelte Sicherheit: ist im Log schon ein finales BUILD-SUCCESSFUL/FAILED
    # verzeichnet, gilt der Build als beendet (falls der setsid-Kind-Prozess
    # schneller endete als kill -0 das bemerkt).
    if build_finished_ok || build_failed; then
        return 1
    fi
    return 0
}

build_finished_ok() {
    [ -f "$LOG" ] || return 1
    tail -50 "$LOG" 2>/dev/null | grep -q 'BUILD SUCCESSFUL'
}

build_failed() {
    [ -f "$LOG" ] || return 1
    tail -50 "$LOG" 2>/dev/null | grep -q 'BUILD FAILED'
}

# ---------------- Kommandos ----------------

case "$TARGET" in
    status)
        if is_running; then
            echo "laufend (PID $(cat "$PID_FILE"))"
            tail -5 "$LOG" 2>/dev/null
        elif build_finished_ok; then
            echo "fertig: BUILD SUCCESSFUL"
        elif build_failed; then
            echo "fehlgeschlagen: BUILD FAILED"
            tail -20 "$LOG" 2>/dev/null
        else
            echo "kein Build bekannt"
        fi
        exit 0
        ;;
    wait)
        echo "warte auf laufenden Build ..."
        while is_running; do sleep 5; done
        if build_finished_ok; then
            echo "fertig: BUILD SUCCESSFUL"
            exit 0
        elif build_failed; then
            echo "fehlgeschlagen: BUILD FAILED"
            tail -30 "$LOG" 2>/dev/null
            exit 1
        else
            echo "Build-Ende nicht erkennbar – Log prüfen: $LOG"
            exit 1
        fi
        ;;
esac

# Neuen Build starten (ggf. laufenden abbrechen, damit nicht zwei parallel)
if is_running; then
    echo "Abbruch: Build läuft bereits (PID $(cat "$PID_FILE"))."
    echo "  Beenden mit: kill \$(cat $PID_FILE)"
    echo "  Warten mit:  sh build_bg.sh wait"
    exit 1
fi

# Gradle-Task je Target
case "$TARGET" in
    debug)
        TASK=":app:assembleDebug"
        APK="app/build/outputs/apk/debug/app-debug.apk"
        ;;
    release)
        TASK=":app:assembleRelease"
        APK="app/build/outputs/apk/release/app-release.apk"
        # Signing-Creds laden, falls vorhanden
        if [ -f "keystore/keystore.properties" ]; then
            set -a; . ./keystore/keystore.properties; set +a
        fi
        ;;
    test)
        TASK=":app:testDebugUnitTest"
        APK=""
        ;;
    detekt)
        TASK=":app:detekt"
        APK=""
        ;;
    detekt-baseline)
        TASK=":app:detektBaseline"
        APK=""
        ;;
    coverage)
        TASK=":app:coverageGate"
        APK=""
        ;;
    *)
        echo "Unbekanntes Ziel: $TARGET (erwartet: debug|release|test|wait|status)"
        exit 1
        ;;
esac

# Log neu anlegen (alten Verlauf ersetzen)
: > "$LOG"

echo "🔨 Baue KeyTab ($TARGET) im Hintergrund ..."
echo "   Task:   $TASK"
echo "   Log:    $LOG"

# setsid → eigene Session, Prozess überlebt das Ende der aufrufenden Shell.
# stdin von /dev/null (nicht-interaktiv), stdout+stderr ins Log.
setsid sh ./gradlew "$TASK" --no-daemon --offline > "$LOG" 2>&1 < /dev/null &
PID=$!
echo "$PID" > "$PID_FILE"
disown 2>/dev/null || true

echo "   PID:    $PID  (in $PID_FILE)"
echo "   Status: sh build_bg.sh status | wait"
echo ""
echo "Installieren danach mit:"
[ -n "$APK" ] && echo "  sh install_keytab.sh $APK" || echo "  (Test-Run – keine APK)"
