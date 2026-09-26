package com.piotv.keytab.ime

import com.piotv.keytab.R
import java.io.File

/**
 * Reine, Android-freie Zustands-Logik des Editor-Tabs (Cursor-Positionen,
 * Lösch-/Einfügebereiche, Send-to-App-Text, Gutter-Zeilennummern und
 * Ordner-Sortierung). Bewusst ohne `EditText`/`View`/`Context`, damit die
 * Berechnungen per JUnit testbar sind; [EditorPanel] wickelt sie nur noch in
 * View-Aufrufe ein.
 */
internal object EditorTextState {

    /** Halb-offener Zeichenbereich [start, end) im Editor-Text. */
    data class Range(val start: Int, val end: Int)

    /** Selection auf die Textlänge begrenzt (Android kann -1 liefern). */
    fun clamp(pos: Int, length: Int): Int = pos.coerceIn(0, length)

    /**
     * Bereich, den "vor dem Cursor löschen" entfernt: die [count] Zeichen
     * unmittelbar vor [cursor] (am Textanfang entsprechend kürzer).
     */
    fun deleteBeforeRange(length: Int, cursor: Int, count: Int): Range {
        val c = clamp(cursor, length)
        val start = (c - count.coerceAtLeast(0)).coerceAtLeast(0)
        return Range(start, c)
    }

    /**
     * Bereich für die Löschtaste: ein Zeichen zurück, bei [word] = true bis zum
     * Wortanfang (via [TextEditLogic.wordStartIndex]); fällt der Wortanfang auf
     * der Cursorposition, wird wie bei [word] = false ein Zeichen gelöscht.
     */
    fun deleteRange(text: CharSequence, cursor: Int, word: Boolean): Range {
        val c = clamp(cursor, text.length)
        if (c == 0) return Range(0, 0)
        if (word) {
            val start = TextEditLogic.wordStartIndex(text, c)
            return if (start < c) Range(start, c) else Range(c - 1, c)
        }
        return Range(c - 1, c)
    }

    /**
     * Ersetzungsbereich einer Einfügung: die markierte Auswahl, sonst nichts.
     * Der neue Cursor liegt hinter dem eingefügten Text (Panel-Sache).
     */
    fun replaceRange(length: Int, selStart: Int, selEnd: Int): Range {
        val start = clamp(selStart, length)
        return Range(start, clamp(selEnd, length).coerceAtLeast(start))
    }

    /**
     * Text für "↑ Send-to-App": die Auswahl, falls vorhanden, sonst der
     * gesamte Editor-Inhalt.
     */
    fun sendUpText(text: CharSequence, selStart: Int, selEnd: Int): String {
        val len = text.length
        val start = clamp(selStart, len)
        val end = clamp(selEnd, len)
        return if (start != end) text.substring(minOf(start, end), maxOf(start, end)).toString()
        else text.toString()
    }

    /**
     * Gutter-Inhalt aus den Zeilen-Starts eines Layouts: umbrochene
     * Fortsetzungszeilen (vorheriges Zeichen ist kein '\n') bekommen eine
     * Leerzeile, die Zahl bleibt an der logischen Zeile.
     */
    fun gutterForLineStarts(text: CharSequence, lineStarts: IntArray): String {
        val sb = StringBuilder()
        var logical = 1
        for (start in lineStarts) {
            val wrapped = start > 0 && text[start - 1] != '\n'
            if (wrapped) sb.append(' ') else { sb.append(logical); logical++ }
            sb.append('\n')
        }
        return sb.toString().trimEnd('\n')
    }

    /**
     * Gutter-Inhalt ohne Layout (keine Breite, z. B. in Tests): zählt die
     * logischen Zeilen und nummeriert sie durch.
     */
    fun gutterLogical(text: CharSequence): String {
        var count = 1
        for (c in text) if (c == '\n') count++
        val sb = StringBuilder()
        for (i in 1..count) sb.append(i).append('\n')
        return sb.toString().trimEnd('\n')
    }

    /** Beschriftung eines Eintrags im Ordner-Browser. */
    fun entryLabel(f: File): String = if (f.isDirectory) "📁 ${f.name}/" else "📄 ${f.name}"

    /** Sichtbare Ordner-Einträge: Ordner zuerst, dann alphabetisch (Name). */
    fun visibleEntries(dir: File?): List<File> {
        val raw = try { dir?.listFiles() } catch (_: Exception) { null }
        return raw?.filter { !it.isHidden }
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .orEmpty()
    }

    /** Liest [f] (fehlende Datei = leerer Text), wirft wie [File.readText]. */
    fun readFile(f: File): String = if (f.exists()) f.readText() else ""

    /** Schreibt [text] nach [f] und legt bei Bedarf das Elternverzeichnis an. */
    fun writeFile(f: File, text: String) {
        f.parentFile?.mkdirs()
        f.writeText(text)
    }

    /**
     * Zeilen des Ordner-Browsers: „▲ …" oben, falls es ein übergeordnetes
     * Verzeichnis gibt, sonst der Inhalt bzw. der Leerhinweis.
     */
    fun pickerItems(
        hasParent: Boolean,
        items: List<String>,
        upLabel: String,
        emptyLabel: String
    ): List<String> = when {
        hasParent -> listOf(upLabel) + items
        items.isEmpty() -> listOf(emptyLabel)
        else -> items
    }

    /** Farb-Ressource für eine Highlight-Art. */
    fun colorRes(kind: EditorHighlightLogic.Kind): Int = when (kind) {
        EditorHighlightLogic.Kind.COMMENT -> R.color.editor_comment
        EditorHighlightLogic.Kind.STRING -> R.color.editor_string
        EditorHighlightLogic.Kind.NUMBER -> R.color.editor_number
        EditorHighlightLogic.Kind.KEYWORD -> R.color.editor_keyword
    }
}
