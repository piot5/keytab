#!/bin/sh
# Runs connected Android tests after the emulator is reachable and fully booted.
# The emulator action executes this file as one command; keeping state here
# prevents line-by-line shell state loss in the action's script input.
set -eu

chmod +x ./gradlew
adb wait-for-device

booted=0
attempt=0
while [ "$attempt" -lt 60 ]; do
    if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
        booted=1
        break
    fi
    attempt=$((attempt + 1))
    sleep 2
done
if [ "$booted" -ne 1 ]; then
    echo 'Android emulator did not finish booting within 120 seconds' >&2
    adb shell getprop >&2 || true
    exit 1
fi

adb shell input keyevent 82 || true

# ---------------------------------------------------------------------------
# 1) Bauen + Installieren — AUSSERHALB des Test-Budgets.
#
# Befund 2026-10-06: `./gradlew connectedDebugAndroidTest` macht Build, Install
# und Testlauf in einem; im 3-Minuten-Fenster blieben davon nur ~1 min fuer die
# Tests und das Budget riss. Deshalb getrennt: erst bauen/installieren (eigene
# Zeit), dann nur der Instrumentation-Lauf mit hartem Limit.
# ---------------------------------------------------------------------------
echo '== bauen =='
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon

echo '== installieren =='
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb shell am force-stop com.piotv.keytab.debug >/dev/null 2>&1 || true

# ---------------------------------------------------------------------------
# 2) Testlauf mit hartem Budget (Vorgabe: 3 Minuten).
#
# `am instrument` liefert den Testausgang NICHT im Exit-Code — das Ergebnis
# steht im Output ("OK (N tests)" vs. "FAILURES!!!"). Deshalb wird der Output
# gelesen, nicht der Exit-Status.
# ---------------------------------------------------------------------------
TEST_BUDGET_S=${TEST_BUDGET_S:-180}
echo "== am instrument, Budget ${TEST_BUDGET_S}s =="
set +e
timeout "$TEST_BUDGET_S" adb shell am instrument -w -e slow "${SLOW:-false}" \
    com.piotv.keytab.debug.test/androidx.test.runner.AndroidJUnitRunner \
    > /tmp/keytab-instrumented.out 2>&1
rc=$?
set -e
cat /tmp/keytab-instrumented.out || true
if [ "$rc" -eq 124 ]; then
    echo "== Zeitbudget von ${TEST_BUDGET_S}s gerissen — Suite kuerzen oder beschleunigen ==" >&2
fi

if grep -q 'OK (' /tmp/keytab-instrumented.out && ! grep -q 'FAILURES' /tmp/keytab-instrumented.out; then
    echo '== instrumentierte Tests: GRUEN =='
    exit 0
fi

echo '== instrumentierte Tests: ROT ==' >&2
grep -aE 'Error in |Tests run:|FAILURES|AssertionError|ComparisonFailure' \
    /tmp/keytab-instrumented.out | head -30 >&2 || true
adb shell pm list instrumentation >&2 || true
# -t 500 zeigte nur das Ende des Laufs: der Permission-Dialog z. B. war damit
# unsichtbar, obwohl er mitten im Test passiert war. Das komplette Logcat ist
# die einzige Grundlage, um den Zustand waehrend der Tests zu rekonstruieren.
adb logcat -d > /tmp/keytab-instrumented.logcat 2>/dev/null || true
exit 1
