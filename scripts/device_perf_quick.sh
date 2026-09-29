#!/bin/sh
# KeyTab — schneller Einzel-Lauf der Geraete-Perfmessung (ca. 2-3 min).
# Zeigt deutlich an, wann das Geraet NICHT beruehrt werden darf.
# Usage:  sh scripts/device_perf_quick.sh
set -u
RSH="sh $HOME/bin/rsh"
KEY="$(cd "$(dirname "$0")/.." && pwd)"
export KEYTAB_BS_MS=400
BANNER(){ echo; echo "########################################################"; echo "#  $1"; echo "########################################################"; echo; }

verify() {
  for i in 1 2 3 4 5; do
    $RSH 'dumpsys input_method' 2>/dev/null > /tmp/im_check.txt
    shown=$(grep -c 'mIsInputViewShown=true' /tmp/im_check.txt)
    itype=$(grep 'inputType=0x1 ' /tmp/im_check.txt | tail -1)
    if [ "$shown" -ge 1 ] && [ -n "$itype" ]; then return 0; fi
    $RSH 'input tap 610 190' >/dev/null 2>&1
    sleep 2
  done
  return 1
}

STAMP=$(date +%H%M%S)
START=$(date +%s)
$RSH 'am start -n com.piotv.keytab.debug/com.piotv.keytab.ImeTargetActivity' >/dev/null 2>&1
sleep 3
$RSH 'input tap 610 190' >/dev/null 2>&1
sleep 2
if ! verify; then echo "ABBRUCH: Zielfeld/IME nicht verifizierbar"; exit 2; fi
temp=$($RSH 'dumpsys battery | grep temperature' 2>/dev/null | grep -o '[0-9][0-9]*$' | tail -1)
echo "Setup OK $(date +%H:%M:%S) — Batt-Temp: ${temp:-?} (Kuehl-Kontext beachten!)"

cd "$KEY" || exit 1
J=build/device-perf

BANNER ">>> NICHT EINGREIFEN — Fuellen (Tastatur wird automatisch bedient) <<<"
python3 scripts/device_perf.py --scenarios typing --pkg com.piotv.keytab.debug --iterations 3 --json $J/q-$STAMP-fill.json >/dev/null

BANNER ">>> NICHT EINGREIFEN — MESSUNG Tippen <<<"
verify || { echo "ABBRUCH: Feld verloren"; exit 2; }
python3 scripts/device_perf.py --scenarios typing --pkg com.piotv.keytab.debug --iterations 3 --json $J/q-$STAMP-typing.json

BANNER ">>> NICHT EINGREIFEN — Nachfuellen <<<"
python3 scripts/device_perf.py --scenarios typing --pkg com.piotv.keytab.debug --iterations 3 --json $J/q-$STAMP-fill2.json >/dev/null

BANNER ">>> NICHT EINGREIFEN — MESSUNG Backspace (400ms-Bursts) <<<"
verify || { echo "ABBRUCH: Feld verloren"; exit 2; }
python3 scripts/device_perf.py --scenarios backspace --layout --pkg com.piotv.keytab.debug --iterations 12 --json $J/q-$STAMP-backspace.json

BANNER "FERTIG — Geraet wieder FREI. ($(($(date +%s)-START)) s Laufzeit)"
echo "JSON: $J/q-$STAMP-*.json"