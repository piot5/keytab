#!/usr/bin/env python3
"""KeyTab — i18n-Gate: jede Locale muss die Basissprache vollständig tragen.

Warum: Deutsch war zu 25 % übersetzt (42 von 167 Strings), während Lint 125
`MissingTranslation` meldete — und niemand sah es, weil kein Gate Übersetzungen
kannte. Schlimmer: die englische Basis enthielt sechs deutsche Reststrings
(`snip_edit` = „✎ Bearbeiten", `theme_export_toast` = „Theme-Farben exportiert …"),
die nur deshalb niemandem auffielen, weil `values-en/` sie überdeckte. Diese
Datei war ein 100-%-Duplikat der Basis und ist entfernt.

Geprüft wird (alles maschinell entscheidbar):
  1. Jede `values-*/`-Locale hat genau die Keys der Basis — keine fehlenden,
     keine verwaisten.
  2. Format-Platzhalter (`%1$s`, `%2$d`, `%s` …) stimmen je Key mit der Basis
     überein — ein fehlendes `%1$s` ist ein Laufzeitfehler, kein Stilfehler.
  3. Keine doppelten Keys innerhalb einer Locale.

Basis = alle `<string>`-Keys aus `values/*.xml` (die Basis ist auf zwei Dateien
aufgeteilt: `strings.xml` und `background_strings.xml`).

Aufruf:  python3 scripts/check_i18n.py          # Exit 1 = Befund
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

STRING_RE = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
SPEC_RE = re.compile(r"%[0-9]*\$?[sdf]")


def read_keys(directory: Path):
    """{key: (value, quelldatei)} aller <string>-Einträge eines Res-Ordners."""
    found: dict[str, tuple[str, str]] = {}
    duplicates: list[str] = []
    for xml in sorted(directory.glob("*.xml")):
        text = xml.read_text(encoding="utf-8")
        for key, value in STRING_RE.findall(text):
            if key in found:
                duplicates.append(f"{key} ({found[key][1]} und {xml.name})")
            found[key] = (value, xml.name)
    return found, duplicates


def main() -> int:
    base_dir = RES / "values"
    if not base_dir.is_dir():
        print(f"FEHLER: {base_dir} fehlt", file=sys.stderr)
        return 2

    base, base_dups = read_keys(base_dir)
    problems: list[str] = []
    for key in base_dups:
        problems.append(f"Basis: doppelter Key {key}")

    locales = sorted(d for d in RES.glob("values-*") if d.is_dir())
    checked: list[str] = []
    for locale in locales:
        keys, dups = read_keys(locale)
        if not keys and not dups:
            continue  # z. B. values-night mit reinen Farben
        checked.append(locale.name)
        for key in dups:
            problems.append(f"{locale.name}: doppelter Key {key}")
        for key in sorted(set(base) - set(keys)):
            problems.append(f"{locale.name}: fehlt {key}")
        for key in sorted(set(keys) - set(base)):
            problems.append(f"{locale.name}: verwaister Key {key} (nicht in der Basis)")
        for key in sorted(set(keys) & set(base)):
            want = sorted(SPEC_RE.findall(base[key][0]))
            got = sorted(SPEC_RE.findall(keys[key][0]))
            if want != got:
                problems.append(
                    f"{locale.name}: {key}: Platzhalter {got or 'keine'} statt {want or 'keine'}")

    if problems:
        for line in problems:
            print(f"x {line}")
        print(f"\n{len(problems)} i18n-Problem(e) — Details: scripts/check_i18n.py")
        return 1

    print(f"OK {len(base)} Strings, {len(checked)} Locale(s) vollständig: "
          + ", ".join(checked))
    return 0


if __name__ == "__main__":
    sys.exit(main())
