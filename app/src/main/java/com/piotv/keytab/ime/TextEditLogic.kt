package com.piotv.keytab.ime

/**
 * Reine (Android-freie) Logik der KeyTab-IME – bewusst ohne Android-Abhängigkeiten,
 * damit sie per JUnit testbar ist.
 */
object TextEditLogic {

    /**
     * Liefert den Index des Wortanfangs vor [cursor] (Wort-Lösch-Grenze):
     * 1. Nicht-Whitespace rückwärts überspringen (das Wort).
     * 2. Liegt davor ein Whitespace-Gap von ≥ 2 Zeichen, gehört er mit zum Löschen;
     *    ein einzelnes Trenn-Leerzeichen bleibt erhalten.
     * Steht der Cursor selbst im Whitespace, wird dieser rückwärts übersprungen
     * und das Wort davor gelöscht.
     */
    fun wordStartIndex(text: CharSequence, cursor: Int): Int {
        var i = cursor.coerceIn(0, text.length)
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        if (i == cursor) {
            // Cursor steht im/beginnt am Whitespace: erst WS, dann Wort überspringen
            while (i > 0 && text[i - 1].isWhitespace()) i--
            while (i > 0 && !text[i - 1].isWhitespace()) i--
            return i
        }
        var j = i
        while (j > 0 && text[j - 1].isWhitespace()) j--
        return if (i - j >= 2) j else i
    }

    /** Anzahl Zeichen, die "Wort löschen" vor [cursor] entfernen würde. */
    fun wordDeleteCount(text: CharSequence, cursor: Int): Int =
        cursor - wordStartIndex(text, cursor)

    /** Menschlich lesbare Dateigröße (B → KB → MB → GB → TB). */
    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.ROOT, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(java.util.Locale.ROOT, "%.1f MB", mb)
        val gb = mb / 1024.0
        if (gb < 1024) return String.format(java.util.Locale.ROOT, "%.1f GB", gb)
        val tb = gb / 1024.0
        return String.format(java.util.Locale.ROOT, "%.2f TB", tb)
    }

    /** Clipboard-Historie serialisieren (NUL-getrennt). */
    fun encodeClipHistory(entries: List<String>): String = entries.joinToString("\u0000")

    /** Clipboard-Historie deserialisieren; leere Einträge werden verworfen. */
    fun decodeClipHistory(raw: String): List<String> =
        raw.split('\u0000').filter { it.isNotEmpty() }

    /** Kompakte Anzeigezeile für die Clipboard-Liste (max. [maxLen] Zeichen). */
    fun clipDisplayText(s: String, maxLen: Int = 80): String =
        if (s.length > maxLen) s.take(maxLen) + "…" else s.replace("\n", " ")
}