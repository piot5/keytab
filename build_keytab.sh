#!/bin/bash
# KeyTab Build-Skript
# Verwendung: bash build_keytab.sh [debug|release]

set -e

BUILD_TYPE="${1:-debug}"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "🔨 Baue KeyTab ($BUILD_TYPE)..."

cd "$PROJECT_DIR"

if [ "$BUILD_TYPE" = "release" ]; then
    sh ./gradlew :app:assembleRelease
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
else
    sh ./gradlew :app:assembleDebug
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

if [ -f "$APK_PATH" ]; then
    echo "✅ Build erfolgreich!"
    echo "📍 APK: $APK_PATH"
    echo ""
    echo "Installieren:"
    echo "  bash install_keytab.sh $APK_PATH"
else
    echo "❌ Build fehlgeschlagen!"
    exit 1
fi