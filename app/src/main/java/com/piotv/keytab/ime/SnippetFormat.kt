package com.piotv.keytab.ime

/**
 * Escape-/Unescape-Logik für Snippet-Zeilen (`name = text`).
 *
 * Format: ein `#` startet einen Kommentar. Damit ein Snippet-Text trotzdem
 * `#`, `\` oder Zeilenumbrüche enthalten darf, werden diese drei Zeichen beim
 * Speichern escaped und beim Lesen zurückgedreht:
 *
 *   `\` → `\\`   ·   Zeilenumbruch → `\n`   ·   `#` → `\#`
 *
 * Android-frei (nur Kotlin-Stdlib), damit der Roundtrip unit-testbar ist.
 * Die frühere Ad-hoc-Escape in [SnippetPanel] escapete `#` gar nicht und las
 * `\\n` fehlerhaft als Zeilenumbruch → beide Richtungen verloren Daten (G10).
 */
internal object SnippetFormat {

    /** Escape einen Snippet-Text für die Speicherung in einer Zeile. */
    fun escapeValue(value: String): String = buildString {
        for (c in value) when (c) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '#' -> append("\\#")
            else -> append(c)
        }
    }

    /** Escape rückgängig machen (Lese-Richtung); unbekannte Escapes bleiben erhalten. */
    fun unescapeValue(stored: String): String = buildString {
        var i = 0
        while (i < stored.length) {
            val c = stored[i]
            if (c == '\\' && i + 1 < stored.length) {
                when (stored[i + 1]) {
                    '\\' -> { append('\\'); i += 2 }
                    'n' -> { append('\n'); i += 2 }
                    '#' -> { append('#'); i += 2 }
                    else -> { append(c); i += 1 }
                }
            } else {
                append(c); i += 1
            }
        }
    }

    /**
     * Index des ersten **nicht** escapeten `#` (Kommentar-Beginn) in [line],
     * oder -1. `\#` ist ein escapetes Hash (kein Kommentar), `\\#` ein
     * escapeter Backslash gefolgt von einem echten Kommentar.
     */
    fun commentStart(line: String): Int {
        var backslashes = 0
        for (i in line.indices) {
            when (line[i]) {
                '\\' -> backslashes++
                '#' -> if (backslashes % 2 == 0) return i else backslashes = 0
                else -> backslashes = 0
            }
        }
        return -1
    }
}
