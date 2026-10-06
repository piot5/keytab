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

# Zeitbudget fuer den Testlauf (Sekunden). Vorgabe: 3 Minuten.
#
# Befund 2026-10-06: Der CI-Emulator ist extrem langsam (gemessene Frame-Zeit
# ~500 ms). Zwei Versuche brauchten ~12 min, und der zweite Lauf hat nie etwas
# anderes gefunden als der erste — der Retry hat nur Zeit gekostet. Deshalb EIN
# Lauf mit hartem Limit: laeuft er darueber, bricht der Job sichtbar ab, statt
# 12 min zu verbrennen. Die Suite ist auf einem echten Geraet in ~2 min gruen
# (scripts/test_on_device.sh) — dort laeuft sie VOR dem Push.
TEST_BUDGET_S=${TEST_BUDGET_S:-180}
adb shell am force-stop com.piotv.keytab.debug >/dev/null 2>&1 || true

echo "== connectedDebugAndroidTest, Budget ${TEST_BUDGET_S}s =="
set +e
timeout "$TEST_BUDGET_S" ./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace
rc=$?
set -e
if [ "$rc" -eq 124 ]; then
    echo "== Zeitbudget von ${TEST_BUDGET_S}s gerissen — Suite kuerzen oder beschleunigen ==" >&2
fi

if [ "$rc" -ne 0 ]; then
    adb shell pm list instrumentation >&2 || true
    # -t 500 zeigte nur das Ende des Laufs: der Permission-Dialog z. B. war
    # damit unsichtbar, obwohl er mitten im Test passiert war. Das komplette
    # Logcat ist die einzige Grundlage, um den Zustand waehrend der Tests zu
    # rekonstruieren; der Job-Log haelt davon nur die ersten Zeilen.
    adb logcat -d > /tmp/keytab-instrumented.logcat 2>/dev/null || true
    tail -n 500 /tmp/keytab-instrumented.logcat >&2 || true
fi
exit "$rc"
