#!/bin/sh
# Device-first: die instrumentierte Suite auf einem ANGESCHLOSSENEN Geraet
# fahren — VOR dem Push.
#
# Warum: Der CI-Emulator ist extrem langsam (gemessene Frame-Zeit ~500 ms) und
# liefert dadurch Flakes, die auf echter Hardware nicht auftreten. Auf dem
# Geraet laeuft die Suite deutlich schneller und stabiler.
#
# Transport: `adb`, wenn ein Geraet ONLINE ist — sonst `rish` (Shizuku) als
# adb-Ersatz (siehe projects/rish/README.md; Shizuku muss laufen).
#
# Aufruf:
#   sh scripts/test_on_device.sh                     # ganze Suite
#   sh scripts/test_on_device.sh -e runner true      # mit Test-Runner-Anzeige
#   sh scripts/test_on_device.sh -e class com.piotv.keytab.KeyTabTypingTest
#
# WICHTIG: Das Geraet muss dabei in Ruhe gelassen werden — die Test-Activity
# muss im Vordergrund bleiben, sonst kann die Tastatur nicht eingeblendet
# werden und die Tests laufen in ihre Zeitlimits.
#
# ---------------------------------------------------------------------------
# Transport-Fix 2026-10-29 (Befund einer unabhaengigen Pruefung):
#
# Der rish-Weg kopierte die APKs bisher mit `cp` aus dem Arbeitsverzeichnis
# nach /data/local/tmp. Das kann nicht funktionieren: der Shell-User (uid 2000)
# hat auf /mnt/sdcard **keinen** Lesezugriff —
#
#     sh ~/bin/rsh 'ls /mnt/sdcard/'   ->  Permission denied
#
# `cp` scheiterte mit rc=1, der Fehler wurde verschluckt, und `pm install`
# installierte anschliessend ein ALTES APK, das noch in /data/local/tmp lag
# (gemessen: kt-debug.apk vom 2026-10-06, versionCode 31) — der Lauf meldete
# dann "ROT" fuer Code, der nie auf dem Geraet war. Zusaetzlich blieb der
# Debug-IME als Standard-Tastatur zurueck.
#
# Deshalb jetzt:
#   1. Transport ueber **stdin** (`cat > ziel < quelle`) — der proot liest die
#      Datei, der Shell-User schreibt sie; kein /mnt/sdcard-Zugriff noetig.
#   2. sha256-Abgleich lokal gegen Geraet VOR dem Install.
#   3. Exit-Code- und Output-Pruefung bei jedem Schritt ("Success" ist Pflicht).
#   4. Versionsabgleich: installierter versionCode == gebauter versionCode
#      (sonst wird ein veraltetes Binary getestet — genau der Fehler oben).
#   5. IME-Zustand wird gesichert und beim Verlassen wiederhergestellt.
#   6. Ausgabe wird stream-unabhaengig gelesen (rish schreibt je nach Zustand
#      auf stdout ODER stderr).
# ---------------------------------------------------------------------------
set -eu

PKG=com.piotv.keytab.debug
TEST_PKG=$PKG.test
RUNNER=androidx.test.runner.AndroidJUnitRunner
IME_COMPONENT=$PKG/com.piotv.keytab.ime.KeyTabImeService
APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
APK_META=app/build/outputs/apk/debug/output-metadata.json
STAGE=/data/local/tmp
RSH="${RSH:-$HOME/bin/rsh}"

# ---------------------------------------------------------------------------
# Transport waehlen: adb nur, wenn ein Geraet wirklich ONLINE ist.
# (Ein Eintrag "emulator-5554 offline" in `adb devices` reicht nicht — der
# bisherige Check `command -v adb` hing genau daran.)
# ---------------------------------------------------------------------------
use_adb=0
if command -v adb >/dev/null 2>&1 && [ "$(adb get-state 2>/dev/null || true)" = "device" ]; then
    use_adb=1
elif [ -x "$RSH" ] || [ -f "$RSH" ]; then
    use_adb=0
else
    echo "Weder ein ONLINE-Geraet (adb) noch $RSH gefunden — siehe projects/rish/README.md" >&2
    exit 1
fi
if [ "$use_adb" -eq 1 ]; then
    echo "Transport: adb ($(adb devices | sed -n '2p'))"
else
    echo "Transport: rish ($RSH)"
fi

# Ein Befehl auf dem Geraet.
#
# stdout UND stderr werden zusammengefasst: rish (app_process/Shizuku) schreibt
# die Ausgabe des Kommandos je nach Zustand auf stdout ODER stderr — gemessen
# am 2026-10-29, gleicher Befehl in zwei Laeufen: einmal kam `sha256sum` auf
# stdout (Variable gefuellt), einmal auf stderr (Variable leer, Ausgabe im Log).
# Wer nur stdout liest, bekommt sporadisch leere Ergebnisse.
dev() {
    if [ "$use_adb" -eq 1 ]; then adb shell "$1" 2>&1; else sh "$RSH" "$1" 2>&1; fi
}

# Nutzlast aus der dev-Ausgabe ziehen: die Rausenzeilen von rish ("Waiting for
# Shizuku authorization...") duerfen nicht als Ergebnis durchgehen.
dev_sha() { dev "sha256sum $1" | grep -oE '[0-9a-f]{64}' | head -1; }

# Datei aufs Geraet legen: adb push bzw. stdin-Kopie. Der Quellpfad liegt auf
# /mnt/sdcard und ist NUR fuer den proot lesbar, nicht fuer den Shell-User.
push_file() {
    src="$1"; dst="$2"
    if [ "$use_adb" -eq 1 ]; then
        adb push "$src" "$dst" >/dev/null || {
            echo "FEHLER: adb push $src -> $dst fehlgeschlagen" >&2; exit 1; }
    else
        # `< "$src"` liest der proot; auf dem Geraet schreibt `cat`.
        sh "$RSH" "cat > $dst" < "$src" || {
            echo "FEHLER: stdin-Kopie $src -> $dst fehlgeschlagen" >&2; exit 1; }
    fi
    local_sum=$(sha256sum "$src" | awk '{print $1}')
    dev_sum=$(dev_sha "$dst")
    if [ -z "$dev_sum" ] || [ "$dev_sum" != "$local_sum" ]; then
        echo "FEHLER: Pruefsumme weicht ab ($src)" >&2
        echo "  lokal : $local_sum" >&2
        echo "  Geraet: ${dev_sum:-leer}" >&2
        exit 1
    fi
    echo "  OK $(basename "$src") (sha256 geprueft)"
}

# Installieren + Ergebnis pruefen. `pm install` liefert bei Fehlschlag einen
# Exit-Code != 0 UND die Zeile "Failure [...]" — beides wird geprueft, denn der
# Exit-Code allein hat den Downgrade-Fall oben nicht verhindert.
install_apk() {
    src="$1"; dst="$2"
    push_file "$src" "$dst"
    out=$(dev "pm install -r -t $dst" || true)
    case "$out" in
        *Success*) : ;;
        *)
            echo "FEHLER: Installation von $src fehlgeschlagen:" >&2
            printf '%s\n' "$out" >&2
            exit 1
            ;;
    esac
    echo "  OK installiert: $(basename "$src")"
}

# versionCode aus der Gradle-Ausgabe (nicht aus dem Skript raten).
built_version() {
    sed -n 's/.*"versionCode": \([0-9]*\).*/\1/p' "$APK_META" | head -1
}

# ---------------------------------------------------------------------------
# IME-Zustand sichern (der Testlauf setzt den Debug-IME und laesst ihn stehen).
# ---------------------------------------------------------------------------
prev_ime=$(dev 'settings get secure default_input_method' | grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | tail -1)
restore_ime() {
    [ -n "${prev_ime:-}" ] || return 0
    dev "ime enable $prev_ime" >/dev/null 2>&1 || true
    dev "ime set $prev_ime" >/dev/null 2>&1 || true
    now=$(dev 'settings get secure default_input_method' | grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.]+' | tail -1)
    echo "IME wiederhergestellt: ${now:-leer} (vorher: $prev_ime)"
}
trap 'restore_ime' EXIT INT TERM

echo "== 1/3 bauen =="
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-arm64}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
sh ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon

echo "== 2/3 installieren =="
install_apk "$(pwd)/$APK" "$STAGE/kt-debug.apk"
install_apk "$(pwd)/$TEST_APK" "$STAGE/kt-test.apk"

# Beweis, dass wirklich der gebaute Stand laeuft (verhindert das stille
# Testen eines alten Binaries).
want_vcode=$(built_version)
have_vcode=$(dev "dumpsys package $PKG" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1 | tr -d '\r')
have_vname=$(dev "dumpsys package $PKG" | sed -n 's/.*versionName=\(.*\)/\1/p' | head -1 | tr -d '\r')
if [ -z "$want_vcode" ] || [ "$want_vcode" != "$have_vcode" ]; then
    echo "FEHLER: installiert ist versionCode ${have_vcode:-leer}, gebaut wurde $want_vcode" >&2
    exit 1
fi
echo "  Geprueft: versionCode $have_vcode / versionName $have_vname"

echo "== 3/3 Testlauf =="
# Display wecken + entsperren. Ein Display im Standby oder ein aktiver
# Lock-Screen hat kein fokussiertes Eingabefeld: der IME-Dienst bekommt dann nie
# ein Fenster und JEDER Test scheitert mit "keyboard did not become visible"
# (gemessen 2026-10-29: 4/4 rot, `mImeWindowVis=0`, `mServedView=null`). Der
# CI-Emulator macht genau das mit `adb shell input keyevent 82`; auf einem echten
# Geraet braucht es zusaetzlich `wm dismiss-keyguard` (kein PIN vorausgesetzt).
echo "  Display wecken/entsperren"
dev "input keyevent 224" >/dev/null 2>&1 || true   # WAKEUP
dev "input keyevent 82" >/dev/null 2>&1 || true    # MENU
dev "wm dismiss-keyguard" >/dev/null 2>&1 || true  # Lock-Screen weg (ohne PIN)
dev "ime enable $IME_COMPONENT" >/dev/null 2>&1 || true
dev "ime set $IME_COMPONENT" >/dev/null 2>&1 || true
dev "am force-stop $PKG" >/dev/null 2>&1 || true

# Auf dem Geraet laufen auch die langsamen/geraeteabhaengigen Klassen mit
# (Hoehen, Vorschlaege, Trail-Pixel) — im CI sind sie aus (3-Minuten-Budget).
out=$(mktemp)
set +e
if [ "$use_adb" -eq 1 ]; then
    adb shell am instrument -w -e slow true "$@" "$TEST_PKG/$RUNNER" 2>&1 | tee "$out"
else
    sh "$RSH" "am instrument -w -e slow true $* $TEST_PKG/$RUNNER" 2>&1 | tee "$out"
fi
set -e

echo
if grep -qE '^OK \([0-9]+ tests?\)' "$out"; then
    echo "== GERAET: GRUEN (versionCode $have_vcode, sha256 geprueft) =="
    rc=0
elif grep -qE 'FAILURES!!!|INSTRUMENTATION_FAILED|Error in ' "$out"; then
    echo "== GERAET: ROT =="
    grep -aE 'Error in |Tests run:|FAILURES' "$out" | head -20 || true
    rc=1
else
    echo "== GERAET: unklar (kein Ergebnis gesehen) =="
    tail -20 "$out" || true
    rc=1
fi
rm -f "$out"
exit "$rc"
