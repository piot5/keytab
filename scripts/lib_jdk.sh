#!/bin/sh
# KeyTab — gemeinsame JDK-Auswahl fuer alle Gradle-Einstiegspunkte.
#
# Warum das eine eigene Datei ist: das Build ist auf JDK 17 gepinnt
# (CONTRIBUTING.md, docs/DEVELOPMENT.md), der Default dieses Hosts ist aber
# Java 25. Jedes Skript hat die Regel vorher selbst (oder gar nicht) umgesetzt —
# fehlte sie, brach Gradle mit einem Fehler ab, der nicht nach "falsches JDK"
# aussah. Eine Regel, eine Datei, alle Einstiegspunkte.
#
# Verwendung (absoluter Pfad, damit das cwd keine Rolle spielt):
#     . "$PROJECT_DIR/scripts/lib_jdk.sh"
#     keytab_require_jdk17
#
# Verhalten: setzt JAVA_HOME auf JDK 17, wenn es leer ist und JDK 17 lokal
# vorhanden ist; bricht mit Exit 2 ab, wenn JAVA_HOME auf ein JDK ausserhalb
# des unterstuetzten Bereichs zeigt. Kein JDK 17 / kein JAVA_HOME ist kein
# harter Fehler — dann entscheidet Gradle selbst.

# Major-Version der JVM unter $1 (JAVA_HOME-Pfad), leer wenn nicht lesbar.
keytab_jdk_major() {
    _java="$1/bin/java"
    [ -x "$_java" ] || return 1
    "$_java" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -1
}

# JDK fuer den Build festlegen und pruefen. Exit 2 = nicht unterstuetzte JVM.
keytab_require_jdk17() {
    if [ -z "${JAVA_HOME:-}" ] && [ -d /usr/lib/jvm/java-17-openjdk-arm64 ]; then
        JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
        export JAVA_HOME
    fi
    if [ -z "${JAVA_HOME:-}" ]; then
        echo "Hinweis: JAVA_HOME ist nicht gesetzt — Gradle nimmt die JVM aus dem PATH." >&2
        return 0
    fi
    _major=$(keytab_jdk_major "$JAVA_HOME" || true)
    case "${_major:-}" in
        17|18|19|20|21)
            return 0
            ;;
        '')
            echo "Hinweis: JAVA_HOME=$JAVA_HOME hat kein lesbares bin/java." >&2
            return 0
            ;;
        *)
            echo "FEHLER: JDK 17 wird erwartet (unterstuetzt 17-21), JAVA_HOME zeigt auf Java $_major." >&2
            echo "        JAVA_HOME=$JAVA_HOME" >&2
            echo "        Setzen:  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64" >&2
            exit 2
            ;;
    esac
}
