#!/bin/sh
# KeyTab — Reproducible-Build-Verifikation (F-Droid-Vorbereitung)
#
# Baut dieselbe Revision zweimal (Clean-Builds aus frischem Klon) und vergleicht
# die SHA-256 der Release-APKs. Identische Hashes = reproduzierbarer Build, wie
# F-Droid ihn verifiziert (https://f-droid.org/docs/Reproducible_Builds/).
#
# Voraussetzungen: Android SDK (local.properties: sdk.dir), JDK 17.
# Signing: fuer den Hash-Vergleich muessen BEIDE Builds identisch signiert sein;
# ohne Credentials entstehen beide Male unsignierte APKs — ebenfalls vergleichbar.
#
# Verwendung:  sh scripts/verify_reproducible.sh [tag]     (Default: HEAD)
# Exit 0 = reproduzierbar, 1 = Hashes weichen ab oder Build fehlgeschlagen.

set -eu

PROJECT=$(cd "$(dirname "$0")/.." && pwd)
TAG="${1:-HEAD}"
SRC_A=$(mktemp -d /tmp/keytab-repro-a-src.XXXXXX)
SRC_B=$(mktemp -d /tmp/keytab-repro-b-src.XXXXXX)
OUT_A=$(mktemp -d /tmp/keytab-repro-a-out.XXXXXX)
OUT_B=$(mktemp -d /tmp/keytab-repro-b-out.XXXXXX)
cleanup() { rm -rf "$SRC_A" "$SRC_B" "$OUT_A" "$OUT_B"; }
trap cleanup EXIT INT TERM

echo "== KeyTab Reproducible-Build-Check =="
echo "   Referenz: $TAG"

build_once() {
    SRC=$1
    OUT=$2
    git clone --quiet "$PROJECT" "$SRC"
    (cd "$SRC"
     [ "$TAG" != "HEAD" ] && git checkout --quiet "$TAG"
     REV=$(git rev-parse --short HEAD)
     echo "== Build $3 (Rev $REV) =="
     # gradlew ist im Repo nicht als +x committed — bewusst via sh aufrufen,
     # damit der Lauf vom committeten Inhalt abhaengt, nicht vom Dateisystem-Bit
     # im frischen Klon.
     # SDK-Lage aus der lokalen Umgebung übernehmen (local.properties ist
     # gitignored und liegt im frischen Klon daher nicht vor). F-Droid setzt
     # ANDROID_HOME; lokal fällt es auf den Pfad der Arbeitskopie zurueck.
     if [ -z "${ANDROID_HOME:-}" ]; then
         SDK_DIR=$(sed -n 's/^sdk\.dir=//p' "$PROJECT/local.properties" 2>/dev/null || true)
         [ -n "$SDK_DIR" ] && echo "sdk.dir=$SDK_DIR" > local.properties
     fi
     sh ./gradlew --no-daemon --no-build-cache \
         -Dorg.gradle.caching=false \
         -Dmaven.repo.local="$OUT/m2" \
         :app:assembleRelease
     find app/build/outputs/apk/release -name '*.apk' -exec cp {} "$OUT/" \;)
}

build_once "$SRC_A" "$OUT_A" A
build_once "$SRC_B" "$OUT_B" B

echo "== Hash-Vergleich =="
FAIL=0
for apk_a in "$OUT_A"/*.apk; do
    name=$(basename "$apk_a")
    apk_b="$OUT_B/$name"
    if [ ! -f "$apk_b" ]; then
        echo "  x $name: fehlt in Build B"; FAIL=1; continue
    fi
    ha=$(sha256sum "$apk_a" | cut -d' ' -f1)
    hb=$(sha256sum "$apk_b" | cut -d' ' -f1)
    if [ "$ha" = "$hb" ]; then
        echo "  OK $name $ha"
    else
        echo "  x $name ABWEICHEND"
        echo "      A: $ha"
        echo "      B: $hb"
        FAIL=1
    fi
done

if [ "$FAIL" -eq 0 ]; then
    echo "== ERGEBNIS: reproduzierbar (identische Hashes) =="
    exit 0
fi
echo "== ERGEBNIS: NICHT reproduzierbar — haeufigste Ursachen: Zeitstempel im"
echo "   APK, nicht-deterministische Codegenerierung, Locale/umask. Siehe"
echo "   docs/FDROID_SUBMISSION.md, Abschnitt Reproducible Builds."
exit 1
