#!/bin/sh
# KeyTab – Badge-Gesundheit der README prüfen
#
# Die Badges im README kommen von Dritten (shields.io, badgen.net) und von
# GitHub selbst. Sie können kaputt sein, ohne dass ein Commit sie angefasst
# hat: shields.io liefert bei erschöpftem Token-Pool
#   "Unable to select next GitHub token from pool"
# oder bei unlesbarer Antwort "invalid" / "repo not found" — im README sieht
# das aus wie ein Projektfehler, obwohl nur der Badge-Dienst klemmt.
#
#     sh scripts/check_readme_badges.sh            # nur berichten (Exit 0)
#     sh scripts/check_readme_badges.sh --strict   # Exit 1 bei kaputtem Badge
#
# Bewusst NICHT im CI-Job "docs": fremde Dienste sind flaky, ein roter Lauf
# wegen shields.io wäre ein falscher Alarm. Vor einem Release oder nach
# README-Änderungen manuell laufen lassen.

set -u

README="${README:-README.md}"
STRICT=0
[ "${1:-}" = "--strict" ] && STRICT=1

[ -f "$README" ] || { echo "$README nicht gefunden" >&2; exit 2; }

TMP=$(mktemp) || exit 2
trap 'rm -f "$TMP"' EXIT

# Alle Badge-Bild-URLs aus dem README (shields.io, badgen.net, GitHub-Workflow-Badges)
grep -oE 'https://(img\.shields\.io|badgen\.net)/[^) ]+|https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/actions/workflows/[A-Za-z0-9_.-]+\.yml/badge\.svg' "$README" \
    | sort -u > "$TMP"

count=$(wc -l < "$TMP" | tr -d ' ')
printf 'KeyTab Badge-Check (%s Badges in %s)\n' "$count" "$README"
printf '%s\n' '===================================================='

broken=0
while IFS= read -r url; do
    [ -n "$url" ] || continue
    title=$(curl -s --max-time 25 "$url" | sed -n 's:.*<title>\(.*\)</title>.*:\1:p' | head -1)
    [ -n "$title" ] || title="(keine Antwort)"
    case "$title" in
        *invalid*|*"not found"*|*Unable*|*error*|*"404"*|*"(keine Antwort)"*)
            broken=$((broken + 1))
            printf '  FEHLER  %-58s  %s\n' "$title" "$(echo "$url" | sed 's#https://##')" ;;
        *)
            printf '  ok      %-58s  %s\n' "$title" "$(echo "$url" | sed 's#https://##')" ;;
    esac
done < "$TMP"

printf '%s\n' '===================================================='
if [ "$broken" -eq 0 ]; then
    echo "Alle $count Badges antworten mit einem echten Wert."
    exit 0
fi
echo "$broken von $count Badges sind kaputt (Dienst antwortet mit Fehlertitel)."
if [ "$STRICT" -eq 1 ]; then
    exit 1
fi
echo "(Hinweis: Fremddienste sind flaky — erst erneut messen, dann URL tauschen."
echo " Ausweich-URLs: badgen.net/github/stars|last-commit|license/<repo> bzw."
echo " statische shields-Badges https://img.shields.io/badge/<label>-<text>-<color>)"
exit 0
