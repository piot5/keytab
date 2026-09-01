#!/bin/bash
# KeyTab One-Click Installer
# Verwendung: bash install_keytab.sh [pfad/zur/apk]

set -e

APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"
APK_NAME="keytab.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "❌ Fehler: APK nicht gefunden: $APK_PATH"
    echo "   Führe zuerst './gradlew :app:assembleDebug' aus."
    exit 1
fi

echo "📦 Installiere KeyTab..."

# Prüfe Shizuku
if ! sh ~/bin/rish 'echo test' >/dev/null 2>&1; then
    echo "⚠️  Shizuku nicht erreichbar. Starte Shizuku-App..."
    echo "   Alternativ: Kopiere APK manuell und installiere über Dateimanager"
    exit 1
fi

# Kopiere und installiere
sh ~/bin/rish "cp '/sdcard/$(echo $APK_PATH | sed 's|.*/||')" "/data/local/tmp/$APK_NAME" 2>/dev/null || cp "$APK_PATH" /sdcard/Download/$APK_NAME && sh ~/bin/rish 'cp /sdcard/Download/$APK_NAME /data/local/tmp/$APK_NAME'"

sh ~/bin/rish "pm install -r /data/local/tmp/$APK_NAME"

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