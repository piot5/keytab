package com.piotv.keytab.ime

/**
 * Reine, Android-freie Highlight-Logik für den Editor-Tab (Roadmap-Punkt
 * „Editor: Syntax-Highlighting"). Heuristisch, aber wohldefiniert:
 *
 * - Zeilenkommentare: `# …` und `// …` bis Zeilenende (inkl. Shebang)
 * - Strings: `"…"` und `'…'` mit Backslash-Escape, enden am Zeilenende
 * - Zahlen: dezimal (mit `.`) und hex (`0x…`)
 * - Schlüsselwörter: Identifier-Matches gegen [KEYWORDS] (Kotlin/Shell/
 *   Python-Lite – ein universeller Satz für die typischen Editor-Dateien)
 *
 * Ergebnis ist aufsteigend sortiert und überlappungsfrei (Kommentar/String
 * „gewinnen" gegen alles Innere). Dateien über [MAX_SCAN_CHARS] werden gar
 * nicht erst hervorgehoben (Performance-Guard im Panel).
 */
object EditorHighlightLogic {

    enum class Kind { COMMENT, STRING, NUMBER, KEYWORD }

    /** Halboffener Bereich [start, end) in Zeichen-Offsets. */
    data class Span(val start: Int, val end: Int, val kind: Kind)

    /**
     * Gemeinsamer Schlüsselwortsatz (Kotlin + Shell + Python-Lite). Heuristik:
     * Editor lädt beliebige Texte; ein kleiner universeller Satz färbt die
     * typischen Dateien sinnvoll, ohne eine echte Parser-Abhängigkeit.
     */
    val KEYWORDS = setOf(
        // Kotlin/Java-ish
        "val", "var", "fun", "if", "else", "when", "for", "while", "do", "return",
        "class", "object", "interface", "import", "package", "true", "false", "null",
        "this", "super", "try", "catch", "finally", "throw", "is", "in", "as",
        "break", "continue", "const", "let", "new", "public", "private", "static",
        "void", "int", "float", "string", "bool", "struct", "function",
        // Shell
        "echo", "cd", "ls", "cat", "grep", "export", "local", "sudo", "chmod",
        "kill", "exit", "pwd", "alias", "source", "type", "then", "fi", "elif",
        "case", "esac",
        // Python-lite
        "def", "end", "elif", "and", "or", "not", "print", "async", "await", "self"
    )

    /** Performance-Guard: darüber wird nicht mehr hervorgehoben. */
    const val MAX_SCAN_CHARS = 20_000

    /** Liefert sortierte, nicht überlappende Span-Bereiche für [text]. */
    fun spans(text: String, maxChars: Int = MAX_SCAN_CHARS): List<Span> {
        if (text.isEmpty()) return emptyList()
        val n = minOf(text.length, maxChars)
        val out = ArrayList<Span>()
        var i = 0
        while (i < n) {
            val c = text[i]
            when {
                c == '#' || (c == '/' && i + 1 < n && text[i + 1] == '/') -> {
                    var end = i
                    while (end < n && text[end] != '\n') end++
                    out.add(Span(i, end, Kind.COMMENT))
                    i = end
                }
                c == '"' || c == '\'' -> {
                    val quote = c
                    var end = i + 1
                    while (end < n) {
                        val e = text[end]
                        if (e == '\\') { end += 2; continue }
                        if (e == quote || e == '\n') break
                        end++
                    }
                    val close = if (end < n && text[end] == quote) end + 1 else minOf(end, n)
                    out.add(Span(i, close, Kind.STRING))
                    i = close
                }
                c.isDigit() -> {
                    var end = i
                    if (c == '0' && i + 1 < n && (text[i + 1] == 'x' || text[i + 1] == 'X')) {
                        end = i + 2
                        while (end < n && text[end] in "0123456789abcdefABCDEF") end++
                    } else {
                        while (end < n && (text[end].isDigit() || text[end] == '.')) end++
                    }
                    out.add(Span(i, end, Kind.NUMBER))
                    i = end
                }
                c.isLetter() || c == '_' -> {
                    var end = i
                    while (end < n && (text[end].isLetterOrDigit() || text[end] == '_')) end++
                    if (text.substring(i, end) in KEYWORDS) out.add(Span(i, end, Kind.KEYWORD))
                    i = end
                }
                else -> i++
            }
        }
        return out
    }
}
