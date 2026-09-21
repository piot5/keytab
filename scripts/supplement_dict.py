#!/usr/bin/env python3
"""
Supplement KeyTab dictionaries with missing common words.

Adds high-utility words that are absent from the FrequencyWords corpus
or present at very low frequency (e.g. technology terms, compound nouns,
domain-specific vocabulary relevant to a coding keyboard).

Usage:
    python3 scripts/supplement_dict.py
"""

import os
import sys

ASSETS_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets",
)

# Missing German tech/compound words with assigned frequencies.
# Frequencies are in the range 1400–3000 (just above the existing tail at 1361),
# reflecting common typing usage in a coding/programming context.
DE_SUPPLEMENTS = [
    # Technology / programming
    ("terminal", 2200),
    ("software", 1950),
    ("hardware", 1450),
    ("editor", 1800),
    ("datei", 2100),
    ("ordner", 1850),
    ("speicher", 1400),
    ("festplatte", 1380),
    ("tastatur", 1550),
    ("server", 1650),
    ("client", 1420),
    ("browser", 1500),
    ("download", 1480),
    ("upload", 1440),
    ("funktion", 1750),
    ("variable", 1390),
    ("array", 1460),
    ("element", 1520),
    ("parameter", 1385),
    ("argument", 1410),
    ("return", 1380),
    ("loop", 1370),
    ("schleife", 1480),
    ("bedingung", 1370),
    ("code", 2000),
    ("programm", 1680),
    ("programmieren", 1390),
    ("compilieren", 1370),
    ("ausführung", 1380),
    ("fehler", 1500),
    ("warnung", 1365),
    ("debuggen", 1365),
    ("kommando", 1450),
    ("befehl", 1400),
    ("shell", 1380),
    ("skript", 1600),
    ("bibliothek", 1430),
    ("framework", 1370),
    ("schnittstelle", 1420),
    ("instanz", 1365),
    ("modul", 1370),
    ("paket", 1390),
    ("import", 1410),
    ("export", 1390),
    ("klasse", 1365),
    ("objekt", 1365),
    ("methode", 1365),
    ("konstruktor", 1365),
    ("vererbung", 1365),
    ("interface", 1370),

    # General vocabulary (common but low in the corpus due to domain skew)
    ("app", 1600),
    ("problem", 54783),   # already present, will be skipped
    ("lösung", 6951),     # already present
    ("anfang", 1450),
    ("ende", 1440),
    ("beginn", 1380),
    ("schritt", 1390),
    ("schritte", 1380),
    ("zustand", 1400),
    ("status", 1390),
    ("wichtig", 1480),    # dup for testing skip
    ("überall", 1420),
    ("irgendwie", 1380),
    ("einfach", 1450),
    ("möglich", 1480),
    ("richtig", 1400),
    ("falsch", 1380),
    ("schnell", 1390),
    ("langsam", 1380),
    ("leicht", 1400),
    ("schwer", 1380),
    ("vielleicht", 1420),
    ("hoffentlich", 1365),
    ("leider", 1380),
    ("übrigens", 1370),
]


def supplement_file(filepath, supplements):
    """Add words that aren't already in the file. Returns (added, skipped)."""
    with open(filepath, "r", encoding="utf-8") as f:
        existing = {}
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split()
            if len(parts) >= 2:
                existing[parts[0].lower()] = int(parts[1])

    added = []
    skipped = []
    for word, freq in supplements:
        if word.lower() in existing:
            skipped.append((word, freq, existing[word.lower()]))
            continue
        added.append(f"{word} {freq}")

    with open(filepath, "a", encoding="utf-8") as f:
        for entry in added:
            f.write(entry + "\n")

    # Re-sort by frequency descending (stable)
    with open(filepath, "r", encoding="utf-8") as f:
        lines = [l.strip() for l in f if l.strip()]

    entries = []
    for line in lines:
        parts = line.split()
        if len(parts) >= 2 and parts[1].isdigit():
            entries.append((parts[0], int(parts[1])))

    entries.sort(key=lambda x: x[1], reverse=True)

    with open(filepath, "w", encoding="utf-8") as f:
        for w, fr in entries:
            f.write(f"{w} {fr}\n")

    return added, skipped


def main():
    print("KeyTab Dictionary Supplementation")
    print("=" * 60)

    supplements = {
        "de_freq_top6000.txt": DE_SUPPLEMENTS,
    }

    for filename, words in supplements.items():
        filepath = os.path.join(ASSETS_DIR, filename)
        if not os.path.exists(filepath):
            print(f"  {filename}: not found, skipping.")
            continue

        added, skipped = supplement_file(filepath, words)

        print(f"\n  {filename}:")
        print(f"    Added:  {len(added)} entries")
        for entry in added[:5]:
            print(f"      + {entry}")
        if len(added) > 5:
            print(f"      ... and {len(added) - 5} more")
        print(f"    Skipped (already present): {len(skipped)} entries")
        for word, freq, existing_freq in skipped[:5]:
            print(f"      = {word} (freq {existing_freq}, requested {freq})")

    print(f"\n{'='*60}")


if __name__ == "__main__":
    main()
