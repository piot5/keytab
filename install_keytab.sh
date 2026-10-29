#!/bin/bash
# KeyTab One-Click Installer
# Verwendung: bash install_keytab.sh [pfad/zur/apk]
#
# Umgebungs-Overrides (Defaults = dieses Gerät):
#   KEYTAB_RSH      Pfad zum rish-Wrapper  (Default: $HOME/bin/rsh)
#   KEYTAB_STAGING  Staging-Ordner         (Default: /sdcard/Download)
#
# Der Staging-Ordner ist nötig, weil der Shell-User (uid 2000) /mnt/...-Pfade
# nicht lesen kann: APK nach /sdcard kopieren, von dort per Shizuku nach
# /data/local/tmp und erst dann installieren.

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"
APK_NAME="keytab.apk"
RSH_WRAPPER="${KEYTAB_RSH:-$HOME/bin/rsh}"
STAGING="${KEYTAB_STAGING:-/sdcard/Download}"

# rish immer über den Wrapper aufrufen (siehe docs/DEVELOPMENT.md). Als Funktion,
# damit an der Aufrufstelle kein Wort-Splitting/Quoting nötig ist.
rsh() { sh "$RSH_WRAPPER" "$@"; }

if [ ! -f "$APK_PATH" ]; then
    echo "❌ Fehler: APK nicht gefunden: $APK_PATH"
    echo "   Führe zuerst './gradlew :app:assembleDebug' aus."
    exit 1
fi

echo "📦 Installiere KeyTab..."

# Prüfe Shizuku/rish
if ! rsh 'echo test' >/dev/null 2>&1; then
    echo "⚠️  Shizuku/rish nicht erreichbar ($RSH_WRAPPER)."
    echo "   Erst Server-/Watchdog-Status prüfen (Shizuku Plus startet per Autoboot"
    echo "   und überwacht sich selbst):  sh $RSH_WRAPPER 'id'"
    echo "   Bei 'Request timeout' NICHT reflexhaft die Shizuku-App neu starten."
    echo "   Alternativ: APK manuell kopieren und über den Dateimanager installieren."
    exit 1
fi

# Staging: APK in den /sdcard-Bereich kopieren (für rish/shell lesbar), dann per
# Shizuku nach /data/local/tmp kopieren und pm install. Ersetzt die fehlerhafte
# Quoting-Zeile aus v0.9.x (robust gegen Leerzeichen/Sonderzeichen im Pfad).
#
# Staging und Transport prüfen und selbst sprechen: unter `set -e` bricht ein
# fehlgeschlagenes `mkdir`/`cp`/`rish cp` (fehlender Pfad, voller Speicher,
# Rechte, abgebrochenes Shizuku) die Shell ohne Meldung ab — der Aufrufer sah
# nur Exit 1. Genau dieser stille Abbruch war der Befund vom 2026-10-29.
if ! mkdir -p "$STAGING" || ! cp -f "$APK_PATH" "$STAGING/$APK_NAME"; then
    echo "❌ Staging fehlgeschlagen: $APK_PATH -> $STAGING/$APK_NAME"
    echo "   Platz/Rechte in $STAGING prüfen (KEYTAB_STAGING überschreibt den Ordner)."
    exit 1
fi

if ! rsh "cp -f '$STAGING/$APK_NAME' /data/local/tmp/$APK_NAME"; then
    echo "❌ Transport nach /data/local/tmp fehlgeschlagen (rish-Exit ≠ 0)."
    echo "   Shizuku-Status prüfen:  sh $RSH_WRAPPER 'id'"
    exit 1
fi
# -d erlaubt Version-Downgrades (debuggable Pakete), z.B. 0.9.7 → 0.9.6;
# ohne -d scheitert pm install mit INSTALL_FAILED_VERSION_DOWNGRADE stillschweigend.
#
# `OUT=$(cmd); RC=$?` ist unter `set -e` falsche Diagnose: ein fehlschlagender
# rish-Aufruf brach das Skript sofort ab, der RC-Check unten war toter Code und
# der Aufrufer bekam gar keine Meldung. Deshalb `|| RC=$?`.
INSTALL_RC=0
INSTALL_OUT=$(rsh "pm install -d -r /data/local/tmp/$APK_NAME" 2>&1) || INSTALL_RC=$?

# pm install trotzdem prüfen: HyperOS/MIUI blockiert Shell-Installationen
# (INSTALL_FAILED_USER_RESTRICTED), und das Skript meldete trotzdem Erfolg —
# der Aufrufer hat damit eine kaputte Installation fuer eine gute gehalten.
if [ "$INSTALL_RC" -ne 0 ] || ! printf '%s' "$INSTALL_OUT" | grep -q '^Success'; then
    echo "❌ Installation fehlgeschlagen:"
    printf '%s\n' "$INSTALL_OUT" | sed 's/^/   /'
    echo ""
    echo "Häufigste Ursache: Das Gerät blockiert Installationen per Shell"
    echo "(HyperOS/MIUI). Auf dem Gerät entsperren und die Bestätigungs-"
    echo "dialoge bestätigen, oder die APK manuell über den Dateimanager"
    echo "installieren. APK liegt unter: $STAGING/$APK_NAME"
    exit 1
fi

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
