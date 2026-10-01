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

# Emulator-Flake: "KeyTab keyboard did not become visible" ist ein Cold-Start-
# Rennen. `am instrument` killt den Ziel-Prozess (der auch den IME-Service
# hostet) und startet ihn neu; der erste showSoftInput kann vor
# onCreateInputView verloren gehen. Ein zweiter Lauf trifft den warmen Prozess
# und ist fast immer gruen. Deshalb: bis zu zwei Versuche.
rc=1
try=0
while [ "$try" -lt 2 ]; do
    try=$((try + 1))
    echo "== connectedDebugAndroidTest, Versuch $try/2 =="
    set +e
    ./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace
    rc=$?
    set -e
    if [ "$rc" -eq 0 ]; then
        break
    fi
    echo "== Versuch $try fehlgeschlagen (Exit $rc) ==" >&2
    sleep 5
done

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
