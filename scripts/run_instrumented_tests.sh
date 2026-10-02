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

# v0.16: wieder bis zu ZWEI Laeufe (Retry zurueck — Ein-Lauf war ein Fehler:
# 44/42 Tests, 5 Fehler, darunter imeSubtype_isRegistered als allererster Test
# = Cold-Start-Rennen, kein App-Fehler. Der zweite Lauf trifft den warmen
# Prozess und ist fast immer gruen).
# Die langsamen Screenshot-/GIF-Tests (KeyTabDeviceScreensTest) laufen nur
# mit `-e screens true` — der Standard-Lauf bleibt so unter der
# 5-Minuten-Grenze bei ~42 Tests in einem Durchgang.
# Vor dem Lauf den IME-Prozess zuruecksetzen: Zustand (Symbol-Modus, CapsLock,
# letzter Tab) ueberlebt sonst einen vorherigen `am instrument`-Lauf.
adb shell am force-stop com.piotv.keytab.debug >/dev/null 2>&1 || true

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
