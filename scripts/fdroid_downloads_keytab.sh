#!/bin/sh
# KeyTab - F-Droid Downloadzahlen (einmalig)
# Aktuellste Download-Anzahl fuer com.piotv.keytab von der F-Droid-API.
# Erfordert: curl und python3 (vorhanden).

PROG="$(basename "$0")"

die() {
  echo "$PROG: Fehler: $*" >&2
  exit 1
}

[ -n "${FDROID_API:-}" ] || FDROID_API="https://fdroid.stats.k8s.project-fdroid.com/packages/statistics"
URL="$FDROID_API/com.piotv.keytab"

TMP=$(mktemp) || die "mktemp fehlt"
trap 'rm -f "$TMP"' EXIT

if command -v curl >/dev/null 2>&1; then
  HTTP=$(curl -sS -o "$TMP" -w '%{http_code}' --max-time 20 "$URL" 2>/dev/null)
elif command -v wget >/dev/null 2>&1; then
  # Gleiche Semantik wie der curl-Zweig: Body in die Datei, Statuscode in HTTP.
  # Vorher stand hier `HTTP=$(wget -qO- "$URL" | head -c 1)` — das lieferte das
  # erste Body-Zeichen statt "200" und liess $TMP leer; ohne curl war das Skript
  # damit garantiert erfolglos (der HTTP-Vergleich unten schlug immer fehl).
  if wget -q -O "$TMP" --timeout=20 "$URL" 2>/dev/null; then
    HTTP=200
  else
    HTTP=0
  fi
else
  die "weder curl noch wget gefunden"
fi

if [ "$HTTP" != "200" ]; then
  echo "$PROG: HTTP $HTTP von $URL" >&2
  exit 1
fi

python3 - "$TMP" <<'PY'
import sys, json
raw = open(sys.argv[1], encoding='utf-8', errors='ignore').read().strip()
try:
    data = json.loads(raw)
    print(json.dumps(data, ensure_ascii=False, indent=2))
except Exception:
    print(raw)
PY
