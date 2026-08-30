package com.piotv.keytab.ime

/**
 * Reine (Android-freie) Logik der KeyTab-IME – bewusst ohne Android-Abhängigkeiten,
 * damit sie per JUnit testbar ist.
 */
object TextEditLogic {

    /**
     * Liefert den Index des Wortanfangs vor [cursor]:
     * 1. Whitespace rückwärts überspringen, 2. Nicht-Whitespace rückwärts überspringen.
     * Der Rückgabewert zeigt auf das erste Zeichen des letzten Worts vor dem Cursor.
     */
    fun wordStartIndex(text: CharSequence, cursor: Int): Int {
        var i = cursor.coerceIn(0, text.length)
        while (i > 0 && text[i - 1].isWhitespace()) i--
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        return i
    }

    /** Anzahl Zeichen, die "Wort löschen" vor [cursor] entfernen würde. */
    fun wordDeleteCount(text: CharSequence, cursor: Int): Int =
        cursor - wordStartIndex(text, cursor)

    /** Menschlich lesbare Dateigröße. */
    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.ROOT, "%.0f KB", kb)
        val mb = kb / 1024.0
        return String.format(java.util.Locale.ROOT, "%.1f MB", mb)
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