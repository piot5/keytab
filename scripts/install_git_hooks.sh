#!/bin/sh
# KeyTab — installiert die Git-Hooks aus scripts/git-hooks/.
#
#     sh scripts/install_git_hooks.sh
#
# Warum ein Installer: Git versioniert Hooks nicht, sie müssen nach jedem
# frischen Klon einmal eingerichtet werden.
#
# Besonderheit dieses Geräts: Das Repo liegt unter /mnt/sdcard (fuseblk-Bind von
# /storage/emulated/0). Dort ist `chmod` wirkungslos — ein Hook in .git/hooks
# bliebe ohne Exec-Bit und würde von Git STILL ignoriert. Der Installer prüft das
# Exec-Bit deshalb nach und weicht sonst auf ~/.keytab-git-hooks aus (f2fs/tmpfs,
# dort greift chmod) und setzt `core.hooksPath` entsprechend.
#
# Aktuell installiert:
#   pre-push — blockiert Tag-Pushs und Force-Pushs (Schutz der laufenden
#              F-Droid-Aufnahme; Details im Kopf der Hook-Datei und in
#              docs/RELEASE.md, Abschnitt 7).
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

SRC=scripts/git-hooks
[ -d "$SRC" ] || { echo "Fehler: $SRC fehlt" >&2; exit 1; }

GIT_HOOKS=$(git rev-parse --absolute-git-dir)/hooks
FALLBACK="$HOME/.keytab-git-hooks"

copy_hooks() {
    dir="$1"
    mkdir -p "$dir"
    for f in "$SRC"/*; do
        [ -f "$f" ] || continue
        name=$(basename "$f")
        cp "$f" "$dir/$name"
        chmod +x "$dir/$name" 2>/dev/null || true
        echo "  kopiert: $dir/$name"
    done
}

# 1) Standardort .git/hooks
echo "1) Versuch: $GIT_HOOKS"
copy_hooks "$GIT_HOOKS"
if [ -x "$GIT_HOOKS/pre-push" ]; then
    echo "OK: Hooks sind ausfuehrbar — keine weitere Konfiguration noetig."
    exit 0
fi

# 2) Fallback: Exec-Bit auf diesem Mount nicht setzbar (fuseblk/sdcardfs)
echo "   Exec-Bit auf diesem Dateisystem nicht setzbar (chmod wirkungslos)."
echo "2) Ausweichort: $FALLBACK"
copy_hooks "$FALLBACK"
if [ ! -x "$FALLBACK/pre-push" ]; then
    echo "FEHLER: Auch in $FALLBACK ist der Hook nicht ausfuehrbar." >&2
    echo "  Lokaler Schutz nicht moeglich — es greifen nur die serverseitigen" >&2
    echo "  GitHub-Rulesets (docs/RELEASE.md, Abschnitt 7)." >&2
    exit 1
fi

git config core.hooksPath "$FALLBACK"
echo "OK: core.hooksPath -> $FALLBACK"
echo
echo "Hinweis: core.hooksPath gilt nur fuer dieses Arbeitsverzeichnis (.git/config,"
echo "nicht versioniert). Auf einem normalen Dateisystem genuegt Weg 1."
echo "Notausgaenge stehen im Kopf von $SRC/pre-push."

