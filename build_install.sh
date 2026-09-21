#!/bin/sh
# KeyTab Build + Install (Kombi-Skript)
#
# Baut die APK im Hintergrund (über build_bg.sh, setsid-losgelöst), wartet bis
# der Build fertig ist und installiert die APK dann per install_keytab.sh
# (Shizuku/rish). Alle Schritte mit Klartext-Status, damit man sieht, wo man ist.
#
# Verwendung:
#   sh build_install.sh             # debug bauen + installieren
#   sh build_install.sh debug       # debug bauen + installieren
#   sh build_install.sh release     # release bauen + installieren (Signing-Creds nötig)
#
# Vorbedingung: Shizuku-Server muss laufen (für den Install-Schritt).
# Log des Builds: /tmp/keytab_build.log

set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

TARGET="${1:-debug}"

# ---------------- 1. Doku-Drift-Check (schnell, kein Gradle) ----------------
echo "── 1/4 Doku-Drift-Check ──────────────────────────────────"
if ! sh scripts/check_docs_drift.sh >/tmp/keytab_drift.log 2>&1; then
    echo "❌ Doku-Drift-Check fehlgeschlagen:"
    cat /tmp/keytab_drift.log
    exit 1
fi
echo "✅ Doku-Drift-Check bestanden"

# ---------------- 2. Build (Hintergrund, setsid) ----------------
echo ""
echo "── 2/4 Build ($TARGET) ────────────────────────────────────"
sh build_bg.sh "$TARGET"
BUILD_RC=$?
if [ "$BUILD_RC" -ne 0 ]; then
    # 'läuft bereits' o.ä. → nicht abbrechen, sondern mit-warten
    echo "ℹ️  build_bg.sh meldet $BUILD_RC (evtl. läuft schon) – warte ..."
fi

# Auf Build-Ende warten
if ! sh build_bg.sh wait; then
    echo "❌ Build fehlgeschlagen. Log:"
    tail -40 /tmp/keytab_build.log 2>/dev/null
    exit 1
fi
echo "✅ Build erfolgreich"

# APK-Pfad bestimmen
case "$TARGET" in
    release) APK="app/build/outputs/apk/release/app-release.apk" ;;
    *)       APK="app/build/outputs/apk/debug/app-debug.apk" ;;
esac

if [ ! -f "$APK" ]; then
    echo "❌ APK nicht gefunden: $APK"
    exit 1
fi

# ---------------- 3. Tests (nur debug, damit nichts Grünes kaputtgeht) ----------------
echo ""
echo "── 3/4 Unit-Tests ────────────────────────────────────────"
if [ "$TARGET" = "debug" ]; then
    sh build_bg.sh test
    sh build_bg.sh wait && echo "✅ Unit-Tests grün" || {
        echo "❌ Unit-Tests fehlgeschlagen:"
        tail -40 /tmp/keytab_build.log 2>/dev/null
        exit 1
    }
else
    echo "übersprungen (nur bei debug)"
fi

# ---------------- 4. Install (Shizuku/rish) ----------------
echo ""
echo "── 4/4 Install ───────────────────────────────────────────"
sh install_keytab.sh "$APK"
