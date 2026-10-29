#!/bin/bash
# KeyTab Build-Skript
# Verwendung: bash build_keytab.sh [debug|release]

set -e

BUILD_TYPE="${1:-debug}"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "🔨 Baue KeyTab ($BUILD_TYPE)..."

cd "$PROJECT_DIR"

if [ "$BUILD_TYPE" = "release" ]; then
    # Signing-Credentials: keystore/keystore.properties (gitignored) ins Env laden,
    # falls vorhanden — sonst meckert der Build laut (kein stilles Default-Passwort)
    if [ -f "keystore/keystore.properties" ]; then
        # shellcheck source=/dev/null
        set -a; . ./keystore/keystore.properties; set +a
    fi
    GRADLE_TASK=":app:assembleRelease"
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
else
    GRADLE_TASK=":app:assembleDebug"
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

# JDK-Auswahl/Pruefung: eine Regel fuer alle Einstiegspunkte (scripts/lib_jdk.sh).
# shellcheck source=scripts/lib_jdk.sh
. "$PROJECT_DIR/scripts/lib_jdk.sh"
keytab_require_jdk17

# Gradle-Fehler explizit melden: unter `set -e` brach ein fehlschlagender
# Gradle-Aufruf die Shell sofort ab — der ❌-Zweig weiter unten war damit fuer
# diesen Fall unerreichbar, der Aufrufer sah nur einen nackten Exit-Code ohne
# Meldung. Deshalb den Aufruf hier abfangen und selbst sprechen.
if ! sh ./gradlew "$GRADLE_TASK"; then
    echo "❌ Build fehlgeschlagen (Gradle-Exit ≠ 0) — Ausgabe siehe oben."
    exit 1
fi

if [ -f "$APK_PATH" ]; then
    echo "✅ Build erfolgreich!"
    echo "📍 APK: $APK_PATH"
    echo ""
    echo "Installieren:"
    echo "  bash install_keytab.sh $APK_PATH"
else
    echo "❌ Gradle war erfolgreich, aber die APK fehlt: $APK_PATH"
    exit 1
fi
