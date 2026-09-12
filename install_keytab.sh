#!/bin/bash
# KeyTab One-Click Installer
# Verwendung: bash install_keytab.sh [pfad/zur/apk]

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"
APK_BASENAME="$(basename "$APK_PATH")"
APK_NAME="keytab.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "❌ Fehler: APK nicht gefunden: $APK_PATH"
    echo "   Führe zuerst './gradlew :app:assembleDebug' aus."
    exit 1
fi

echo "📦 Installiere KeyTab..."

# Prüfe Shizuku
if ! sh ~/bin/rsh 'echo test' >/dev/null 2>&1; then
    echo "⚠️  Shizuku nicht erreichbar. Starte Shizuku-App..."
    echo "   Alternativ: Kopiere APK manuell und installiere über Dateimanager"
    exit 1
fi

# Staging: APK in den /sdcard-Bereich kopieren (für rish/shell lesbar), dann per
# Shizuku nach /data/local/tmp kopieren und pm install. Ersetzt die fehlerhafte
# Quoting-Zeile aus v0.9.x (robust gegen Leerzeichen/Sonderzeichen im Pfad).
STAGING="/sdcard/Download"
mkdir -p "$STAGING"
cp -f "$APK_PATH" "$STAGING/$APK_NAME"

sh ~/bin/rsh "cp -f '$STAGING/$APK_NAME' /data/local/tmp/$APK_NAME"
# -d erlaubt Version-Downgrades (debuggable Pakete), z.B. 0.9.7 → 0.9.6;
# ohne -d scheitert pm install mit INSTALL_FAILED_VERSION_DOWNGRADE stillschweigend.
sh ~/bin/rsh "pm install -d -r /data/local/tmp/$APK_NAME"

# Aufräumen (best effort)
rm -f "$STAGING/$APK_NAME"

echo "✅ Installation erfolgreich!"
echo ""
echo "So aktivierst du KeyTab:"
echo "1. Einstellungen → System → Eingabemethoden"
echo "2. KeyTab aktivieren"
echo "3. In einem Textfeld: Lange gedrückt halten → Eingabemethode ändern"
echo ""
echo "Tastatur-Kurzbefehle:"
echo "  TAB → Tabulator senden (für Termux/SSH)"
echo "  Lange gedrückt → Sonderzeichen-Popup"
echo "  Z → Rückgängig"
