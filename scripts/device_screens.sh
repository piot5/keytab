#!/bin/sh
# device_screens.sh — ECHTE Screenshots & GIFs vom laufenden KeyTab-IME.
#
# Kern ist der Instrumented Test KeyTabDeviceScreensTest (UiDevice läuft auch
# auf Android 16, wo das Shell-`uiautomator dump` mit "null root node" kaputt
# ist). Dieses Skript orchestriert nur:
#   1. Test-APK bauen (build_bg.sh androidtest) + installieren (Shizuku/rish)
#   2. am instrument mit der Screenshot-Testklasse ausführen
#   3. PNGs aus /data/local/tmp/keytab_frames holen
#   4. GIFs bauen (Pillow) → docs/images/device/
#
# Usage:  sh scripts/device_screens.sh [outdir]
# Voraussetzung: Shizuku-Server läuft, KeyTab debug installiert.

set -u
cd "$(dirname "$0")/.." || exit 1
OUT="${1:-docs/images/device}"
TMP="$OUT/.frames"
mkdir -p "$OUT" "$TMP"

RSH() { sh "$HOME/bin/rsh" "$@"; }

# ---------- 1. Test-APK bauen -------------------------------------------------

echo "== Baue androidTest-APK (Hintergrund) ..."
sh build_bg.sh androidtest >/dev/null 2>&1 || true
sh build_bg.sh wait || { echo "❌ Build fehlgeschlagen:"; tail -40 /tmp/keytab_build.log; exit 1; }

TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$TEST_APK" ] || { echo "❌ $TEST_APK fehlt"; exit 1; }

echo "== Installiere APKs ..."
sh install_keytab.sh "$APP_APK" >/dev/null || { echo "❌ App-Install fehlgeschlagen"; exit 1; }
cp -f "$TEST_APK" /sdcard/Download/kt-test.apk
RSH "cp -f /sdcard/Download/kt-test.apk /data/local/tmp/kt-test.apk"
INSTALL_OUT=$(RSH "pm install -d -r -t /data/local/tmp/kt-test.apk" 2>&1)
printf '%s\n' "$INSTALL_OUT" | grep -q '^Success' || \
  { echo "❌ Test-APK-Install fehlgeschlagen:"; printf '%s\n' "$INSTALL_OUT"; exit 1; }

# ---------- 2. Instrumented Test ausführen ------------------------------------

echo "== Führe KeyTabDeviceScreensTest aus (dauert ~1-2 Min) ..."
RSH "am instrument -w -e screens true -e class com.piotv.keytab.KeyTabDeviceScreensTest \
  com.piotv.keytab.debug.test/androidx.test.runner.AndroidJUnitRunner" \
  2>&1 | grep -E "DeviceScreens|Time:|OK |FAILURES|INSTRUMENTATION_STATUS: stack" | head -40

# ---------- 3. Frames holen ---------------------------------------------------

RSH "mkdir -p /sdcard/Download/kt_frames && cp -f /data/local/tmp/keytab_frames/*.png /sdcard/Download/kt_frames/ 2>/dev/null"
cp -f /sdcard/Download/kt_frames/*.png "$TMP"/ 2>/dev/null
RSH "rm -rf /sdcard/Download/kt_frames /sdcard/Download/kt-test.apk"

N_TAB=$(ls "$TMP"/tab_*.png 2>/dev/null | wc -l)
N_TR=$(ls "$TMP"/trail_*.png 2>/dev/null | wc -l)
N_SC=$(ls "$TMP"/scroll_*.png 2>/dev/null | wc -l)
echo "== Geholt: $N_TAB Screenshots, $N_TR Trail-Frames, $N_SC Scroll-Frames"

# ---------- 4. GIFs bauen (Pillow) --------------------------------------------

make_gif() { # make_gif prefix out.gif [dauer_ms]
  n=$(ls "$TMP"/$1_*.png 2>/dev/null | wc -l)
  [ "$n" -eq 0 ] && { echo "   ⚠ keine Frames für $2"; return 0; }
  python3 - "$TMP" "$1" "$2" "${3:-180}" <<'EOF'
import glob, sys
from PIL import Image
tmp, prefix, out, dur = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
files = sorted(glob.glob(f"{tmp}/{prefix}_*.png"))
frames = [Image.open(f).convert("RGB") for f in files]
# Skalierung spart ~90% der GIF-Größe bei 1220px Breite
frames = [f.resize((f.width // 2, f.height // 2)) for f in frames]
frames[0].save(out, save_all=True, append_images=frames[1:],
               duration=dur, loop=0, optimize=True)
print(f"   🎞 {out} ({len(frames)} Frames)")
EOF
}

echo "== Baue GIFs ..."
make_gif trail "$OUT/trail.gif" 160
make_gif scroll "$OUT/scrolling.gif" 180

cp -f "$TMP"/tab_*.png "$OUT"/ 2>/dev/null
rm -rf "$TMP"
echo "== Fertig → $OUT"
ls -la "$OUT"
