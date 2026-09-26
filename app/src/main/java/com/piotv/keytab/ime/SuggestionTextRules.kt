package com.piotv.keytab.ime

/**
 * Reine, Android-freie Textregeln der Wortvorhersage (aus [SuggestionEngine]
 * ausgelagert, damit sie ohne Engine-Instanz testbar bleiben).
 *
 * Enthält ausschließlich Funktionen ohne `this`-Bezug zur Engine sowie die
 * Trenner-/Marker-Konstanten. [SuggestionEngine.Companion] delegiert die
 * öffentliche API eins zu eins hierher, damit alle Aufrufer unverändert
 * weiter funktionieren.
 */
internal object SuggestionTextRules {

    internal const val DECAY_FACTOR = 0.98
    internal const val MAX_WORD_LEN = 32
    /** Trenner für die Serialisierung des User-Dictionary. */
    internal const val SEP_ENTRY = "\u0001"
    internal const val SEP_FIELD = "\u0002"

    /** Trenner für die Snippet-History in SharedPreferences (NUL, wie Clipboard). */
    internal const val SEP_RECENT = "\u0000"

    /** Sichtbarer Marker-Tag, damit [SuggestionController] Snippet-Chips von
     *  Wortvorschlägen unterscheiden kann (`tv.setTag(SNIPPET_TAG, text)`,
     *  vs. `tv.tag = word` für normale Vorschläge). */
    internal const val SNIPPET_TAG: Int = 0x7f000001

    /** Marker-Tag für Emoji-Katalog-Chips (Klick → Emoji einfügen statt Wort). */
    internal const val EMOJI_TAG: Int = 0x7f000002

    fun isLearnable(word: String): Boolean =
        word.length in 2..MAX_WORD_LEN && word.all { it.isLetter() }

    /** Damerau-Levenshtein-Distanz (Restricted Edit Distance, +Transposition). */
    fun editDistance(a: String, b: String): Int {
        val n = a.length
        val m = b.length
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(
                    d[i - 1][j] + 1,      // deletion
                    d[i][j - 1] + 1,      // insertion
                    d[i - 1][j - 1] + cost // substitution
                )
                if (isTransposition(a, i, b, j)) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1) // transposition
                }
            }
        }
        return d[n][m]
    }

    /** Damerau-Sonderfall: die letzten beiden Zeichen sind gegenlaeufig vertauscht. */
    private fun isTransposition(a: String, i: Int, b: String, j: Int): Boolean {
        if (i < 2 || j < 2) return false
        return isCrossedPair(a, i, b, j)
    }

    /** Vergleicht die beiden gekreuzten Zeichenpaare [a[i-1]]/[b[j-2]] und [a[i-2]]/[b[j-1]]. */
    private fun isCrossedPair(a: String, i: Int, b: String, j: Int): Boolean =
        a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]

    /**
     * Satzanfang-Erkennung für die Snippet-Leiste: gilt als Satzanfang, wenn
     * gerade kein Wort in der Eingabe (Zwischen-Wörter-Puffer leer) ist und
     * das, was vor dem Cursor steht, leer ist oder mit einem Satz-Terminator
     * (. ! ?) endet (optional gefolgt von Leer-/Zeilenwechsel).
     *
     * Wird von [WordPredictionManager.updateSuggestions] genutzt, um zu
     * entscheiden, ob die Vorschlags-Leiste durch zuletzt eingefügte
     * Snippets ersetzt wird (statt der generischen Top-3-Wortvorschläge).
     */
    fun sentenceStart(textBefore: String, typedWord: String): Boolean {
        if (typedWord.isNotEmpty()) return false
        val b = textBefore.trimEnd()
        return b.isEmpty() || b.last() in ".!?"
    }

    /**
     * Satzanfang-Erkennung **auch während des Tippens**: wie [sentenceStart],
     * aber das aktuell getippte Wort wird aus dem Kontext entfernt, bevor
     * geprüft wird. Damit wird erkannt, dass der Nutzer gerade am Satzanfang
     * tippt (z. B. nach "Hallo. w" → Kontext "Hallo." → Satzanfang), auch
     * wenn [typedWord] nicht leer ist.
     *
     * Wird für die Großschreibung von Vorschlägen während des Tippens
     * verwendet ([WordPredictionManager.updateSuggestions] / [applySuggestion]).
     */
    fun isSentenceStartContext(textBefore: String, typedWord: String): Boolean {
        if (typedWord.isEmpty()) return sentenceStart(textBefore, "")
        val stripped = if (textBefore.endsWith(typedWord))
            textBefore.removeSuffix(typedWord) else textBefore
        val b = stripped.trimEnd()
        return b.isEmpty() || b.last() in ".!?"
    }

    /**
     * Parst die persistente Snippet-History (SEP_RECENT-getrennt,
     * most-recent-first) und liefert höchstens [max] Einträge zurück —
     * dedupliziert, leere/Whitespace-Einträge übersprungen.
     */
    fun recentSnippets(raw: String?, max: Int = 3): List<String> =
        raw.orEmpty().split(SEP_RECENT).map { it.trim() }
            .filter { it.isNotEmpty() }.distinct().take(max)

    /**
     * Fügt [text] an den Anfang der History (most-recent-first), entfernt
     * Duplikate (Move-to-Front) und begrenzt auf [max] Einträge → liefert den
     * neuen Roh-String zum Schreiben in SharedPreferences. Leer/Whitespace
     * wird nicht aufgenommen.
     */
    fun recordRecent(raw: String?, text: String, max: Int = 3): String {
        val t = text.trim()
        if (t.isEmpty()) return raw.orEmpty()
        val cur = raw.orEmpty().split(SEP_RECENT).map { it.trim() }.filter { it.isNotEmpty() }
        val ordered = buildList { add(t); for (e in cur) if (e != t) add(e) }.take(max)
        return ordered.joinToString(SEP_RECENT)
    }
}
