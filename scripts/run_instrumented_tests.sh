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
set +e
./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace
rc=$?
set -e
if [ "$rc" -ne 0 ]; then
    adb shell pm list instrumentation >&2 || true
    adb logcat -d -t 500 >&2 || true
fi
exit "$rc"
