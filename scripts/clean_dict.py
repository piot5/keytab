#!/usr/bin/env python3
"""
Dictionary cleanup for KeyTab base frequency dictionaries.

Removes artefacts that slipped into the FrequencyWords / OpenSubtitles
corpus (foreign names, English words in non-English dictionaries,
meaningless single-character tokens, duplicates, non-letter tokens).

Usage:
    python3 scripts/clean_dict.py
"""

import os
import re
import sys

ASSETS_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets",
)

# ---- Per-language configuration -------------------------------------------

# Valid single-character words per language (everything else is an artefact).
VALID_SINGLE_CHAR = {
    "de": set(),                    # German has no single-letter words
    "en": {"a", "i"},               # only 'a' and 'i' are valid English words
    "es": {"a", "y", "o"},          # 'a' (to), 'y' (and), 'o' (or) in Spanish
    "fr": {"y", "a"},               # 'y' (and/him), 'a' (has/to) in French
    "it": {"a", "e", "i", "o"},     # articles/prepositions in Italian
    "pt": {"a", "o", "e"},          # articles/prepositions in Portuguese
    "nl": set(),                    # Dutch has no single-letter words
}

# Known foreign-name / loan-word artefacts. These appear at the low-frequency
# tail of the OpenSubtitles corpus (freq ~1300-1500) and degrade suggestions.
KNOWN_ARTIFACTS = {
    "de": {
        "trevor", "winston", "butler", "clarke", "elvis",
        "carmen", "cathy", "carla", "andrea", "angie",
        "freddie", "kai", "vera", "debbie", "mario",
        "rocky", "sharon", "graham", "duncan", "wyatt",
        "jen", "pat",
        "force", "hot", "life", "first",
        "et", "oz", "sch", "wei",
    },
    "es": {"wallace", "doyle"},
    "fr": set(),
    "it": set(),
    "pt": set(),
    "nl": set(),
    "en": {"bon"},
}

# Pattern for valid words: letters only (incl. accented), no digits/punct.
# We use a simpler check: every character must be a Unicode letter.
def is_letter_word(word):
    return all(c.isalpha() for c in word)


def clean_dict(lang_code, lines):
    """Clean a dictionary. Returns (cleaned_list, removed_list)."""
    seen = set()
    cleaned = []
    removed = []
    valid_single = VALID_SINGLE_CHAR.get(lang_code, set())
    artifacts = KNOWN_ARTIFACTS.get(lang_code, set())

    for line in lines:
        line = line.strip()
        if not line:
            continue

        parts = line.split()
        if len(parts) != 2:
            removed.append((line, "(malformed)"))
            continue

        word, freq_str = parts
        freq = int(freq_str) if freq_str.isdigit() else 0
        lower = word.lower()

        # 1. Known artefacts
        if lower in artifacts:
            removed.append(f"{word} (artefact)")
            continue

        # 2. Single-char tokens that aren't valid words in this language
        if len(word) == 1 and lower not in valid_single:
            removed.append(f"{word} (single-char)")
            continue

        # 3. Non-letter tokens
        if not is_letter_word(word):
            removed.append(f"{word} (non-letter)")
            continue

        # 4. Duplicates (keep first/highest occurrence)
        if lower in seen:
            removed.append(f"{word} (duplicate)")
            continue

        seen.add(lower)
        cleaned.append((word, freq))

    # Stable sort by frequency desc, preserve insertion order for ties
    cleaned.sort(key=lambda x: x[1], reverse=True)
    cleaned_str = [f"{w} {f}" for w, f in cleaned]

    return cleaned_str, removed


def process_file(filepath, lang_code):
    """Process a single dictionary file. Returns (before_count, after_count)."""
    lang_name = {
        "de": "Deutsch", "en": "English", "es": "Espa\u00f1ol",
        "fr": "Fran\u00e7ais", "it": "Italiano", "pt": "Portugu\u00eas", "nl": "Nederlands",
    }.get(lang_code, lang_code)

    print(f"\n{'='*60}")
    print(f"  {lang_name} ({lang_code}) - {filepath}")
    print(f"{'='*60}")

    with open(filepath, "r", encoding="utf-8") as f:
        lines = f.readlines()

    original_count = len(lines)
    cleaned, removed = clean_dict(lang_code, lines)
    cleaned_count = len(cleaned)

    if removed:
        print(f"\n  Removed {len(removed)} entries:")
        for r in removed[:30]:
            print(f"    - {r}")
        if len(removed) > 30:
            print(f"    ... and {len(removed) - 30} more")

    print(f"\n  Before: {original_count} entries")
    print(f"  After:  {cleaned_count} entries (-{original_count - cleaned_count})")

    with open(filepath, "w", encoding="utf-8") as f:
        f.write("\n".join(cleaned))
        f.write("\n")

    return original_count, cleaned_count


def main():
    print("KeyTab Dictionary Cleanup")
    print("=" * 60)

    files = [
        ("de_freq_top6000.txt", "de"),
        ("en_freq_top6000.txt", "en"),
        ("es_freq_top6000.txt", "es"),
        ("fr_freq_top6000.txt", "fr"),
        ("it_freq_top6000.txt", "it"),
        ("pt_freq_top6000.txt", "pt"),
        ("nl_freq_top6000.txt", "nl"),
    ]

    total_before = 0
    total_after = 0
    for filename, lang_code in files:
        filepath = os.path.join(ASSETS_DIR, filename)
        if not os.path.exists(filepath):
            print(f"\n  {filename}: not found, skipping.")
            continue
        before, after = process_file(filepath, lang_code)
        total_before += before
        total_after += after

    print(f"\n{'='*60}")
    print(f"  TOTAL: {total_before} -> {total_after} entries "
          f"(-{total_before - total_after} removed)")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
