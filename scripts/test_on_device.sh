#!/bin/sh
# Device-first: die instrumentierte Suite auf einem ANGESCHLOSSENEN Geraet
# fahren — VOR dem Push.
#
# Warum: Der CI-Emulator ist extrem langsam (gemessene Frame-Zeit ~500 ms) und
# liefert dadurch Flakes, die auf echter Hardware nicht auftreten. Auf dem
# Geraet laeuft die Suite deutlich schneller und stabiler.
#
# Transport: `adb`, wenn vorhanden — sonst `rish` (Shizuku) als adb-Ersatz
# (siehe projects/rish/README.md; Shizuku muss laufen).
#
# Aufruf:
#   sh scripts/test_on_device.sh                     # ganze Suite
#   sh scripts/test_on_device.sh -e runner true      # mit Test-Runner-Anzeige
#   sh scripts/test_on_device.sh -e class com.piotv.keytab.KeyTabImeEndToEndTest
#
# WICHTIG: Das Geraet muss dabei in Ruhe gelassen werden — die Test-Activity
# muss im Vordergrund bleiben, sonst kann die Tastatur nicht eingeblendet
# werden und die Tests laufen in ihre Zeitlimits.
set -eu

PKG=com.piotv.keytab.debug
TEST_PKG=$PKG.test
RUNNER=androidx.test.runner.AndroidJUnitRunner
APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
RSH="${RSH:-$HOME/bin/rsh}"

use_adb=0
if command -v adb >/dev/null 2>&1; then
    use_adb=1
elif [ -x "$RSH" ] || [ -f "$RSH" ]; then
    use_adb=0
else
    echo "Weder adb noch $RSH gefunden — siehe projects/rish/README.md" >&2
    exit 1
fi

# Ein Befehl auf dem Geraet.
dev() {
    if [ "$use_adb" -eq 1 ]; then adb shell "$1"; else sh "$RSH" "$1"; fi
}

# Datei aufs Geraet legen und installieren.
install_apk() {
    src="$1"
    dst="$2"
    if [ "$use_adb" -eq 1 ]; then
        adb push "$src" "$dst" >/dev/null
        adb shell pm install -r -t "$dst"
    else
        # Der Shell-User (rish) darf /sdcard lesen; /data/local/tmp ist immer
        # beschreibbar.
        dev "cp $src $dst"
        dev "pm install -r -t $dst"
    fi
}

echo "== 1/3 bauen =="
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-arm64}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
sh ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon

echo "== 2/3 installieren =="
install_apk "$(pwd)/$APK" /data/local/tmp/kt-debug.apk
install_apk "$(pwd)/$TEST_APK" /data/local/tmp/kt-test.apk

echo "== 3/3 Testlauf =="
dev "ime enable $PKG/com.piotv.keytab.ime.KeyTabImeService" >/dev/null 2>&1 || true
dev "ime set $PKG/com.piotv.keytab.ime.KeyTabImeService" >/dev/null 2>&1 || true
dev "am force-stop $PKG" >/dev/null 2>&1 || true

# Auf dem Geraet laufen auch die langsamen/geraeteabhaengigen Klassen mit
# (Hoehen, Vorschlaege, Trail-Pixel) — im CI sind sie aus (3-Minuten-Budget).
out=$(mktemp)
set +e
if [ "$use_adb" -eq 1 ]; then
    adb shell am instrument -w -e slow true "$@" "$TEST_PKG/$RUNNER" | tee "$out"
else
    sh "$RSH" "am instrument -w -e slow true $* $TEST_PKG/$RUNNER" | tee "$out"
fi
set -e

echo
if grep -qE '^OK \([0-9]+ tests\)' "$out"; then
    echo "== GERÄT: GRÜN =="
    rc=0
elif grep -qE 'FAILURES!!!|INSTRUMENTATION_FAILED|Error in ' "$out"; then
    echo "== GERÄT: ROT =="
    grep -aE 'Error in |Tests run:|FAILURES' "$out" | head -20 || true
    rc=1
else
    echo "== GERÄT: unklar (kein Ergebnis gesehen) =="
    tail -20 "$out" || true
    rc=1
fi
rm -f "$out"
exit "$rc"
