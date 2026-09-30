package com.piotv.keytab.ime

import java.util.concurrent.ConcurrentHashMap

/**
 * Wortvorhersage/Autovervollständigung für KeyTab (offline, Android-frei, JUnit-testbar).
 *
 * Open-Source-Korpus:
 *   [FrequencyWords](https://github.com/hermitdave/FrequencyWords) —
 *   de_50k.txt (OpenSubtitles 2018), CC-BY-SA-4.0 → asset de_freq_top6000.txt.
 *
 * Architektur (angelehnt an LatinIME/AnySoftKeyboard, kompakt):
 *   - Unigram-Frequenzmodell (log-skaliert) als Basis.
 *   - Bigram-Modell für Next-Word-Prediction.
 *   - User-Dictionary mit Decay; Persistenz via serialize/restoreUserDict.
 *   - Prefix-Autovervollständigung + Damerau-Levenshtein-Fuzzy-Korrektur.
 */
class SuggestionEngine(baseWords: List<Pair<String, Int>>) {

    data class Suggestion(val word: String, val score: Double)

    companion object {
        const val MAX_SUGGESTIONS = 3
        const val MAX_USER_WORDS = 500
        const val MAX_BIGRAMS = 2000
        private const val DECAY_INTERVAL = 25
        private const val MAX_BIGRAM_PREDICTIONS = 10
        private const val USER_SCAN_LIMIT_FACTOR = 8
        private const val FUZZY_DIST1_MIN_LENGTH = 4
        private const val FUZZY_DIST2_MIN_LENGTH = 6
        private const val USER_WEIGHT = 1.2
        private const val BIGRAM_WEIGHT = 3.0
        private const val BASE_WEIGHT = 0.6
        private const val PREDICT_BASE_WEIGHT = 0.5
        private const val PREDICT_BIGRAM_WEIGHT = 2.0
        private const val DIST_PENALTY = 0.45
        private const val LEN_PENALTY_FACTOR = 0.01
        private const val WORD_LEARN_STEP = 0.25
        private const val INITIAL_WORD_WEIGHT = 0.2
        private const val BIGRAM_LEARN_STEP = 0.3
        private const val INITIAL_BIGRAM_WEIGHT = 0.1
        private const val DECAY_FACTOR = SuggestionTextRules.DECAY_FACTOR
        private const val SEP_ENTRY = SuggestionTextRules.SEP_ENTRY
        private const val SEP_FIELD = SuggestionTextRules.SEP_FIELD

        /** Trenner für die Snippet-History in SharedPreferences (NUL, wie Clipboard). */
        internal const val SEP_RECENT = SuggestionTextRules.SEP_RECENT

        /** Sichtbarer Marker-Tag, damit [SuggestionController] Snippet-Chips von
         *  Wortvorschlägen unterscheiden kann. */
        internal const val SNIPPET_TAG: Int = SuggestionTextRules.SNIPPET_TAG

        /** Marker-Tag für Emoji-Katalog-Chips (Klick → Emoji einfügen statt Wort). */
        internal const val EMOJI_TAG: Int = SuggestionTextRules.EMOJI_TAG

        fun isLearnable(word: String): Boolean = SuggestionTextRules.isLearnable(word)

        fun editDistance(a: String, b: String): Int = SuggestionTextRules.editDistance(a, b)

        fun sentenceStart(textBefore: String, typedWord: String): Boolean =
            SuggestionTextRules.sentenceStart(textBefore, typedWord)

        fun isSentenceStartContext(textBefore: String, typedWord: String): Boolean =
            SuggestionTextRules.isSentenceStartContext(textBefore, typedWord)

        fun recentSnippets(raw: String?, max: Int = 3): List<String> =
            SuggestionTextRules.recentSnippets(raw, max)

        fun recordRecent(raw: String?, text: String, max: Int = 3): String =
            SuggestionTextRules.recordRecent(raw, text, max)
    }

    private var revisionCounter: Long = 0

    /** Basiswortschatz: word → normalisierte Log-Frequenz (0..1). */
    private val baseFreq: MutableMap<String, Double> = ConcurrentHashMap()
    /** Gelernte Wörter: word → Gewicht (öffentlich lesbar/änderbar für die Cleanup-API). */
    val userFreq = ConcurrentHashMap<String, Double>()
    /** Gelernte Bigramme: "prev next" → Gewicht (öffentlich lesbar/änderbar für die Cleanup-API). */
    val bigrams = ConcurrentHashMap<String, Double>()
    /** Basiswörter nach Frequenz absteigend (für Next-Word-Fallback). */
    private val topBaseOrder: List<String> by lazy {
        baseFreq.entries.sortedByDescending { it.value }.map { it.key }
    }

    /**
     * Char-Index für Fuzzy-Matches (lazy, einmalig nach dem Korpus-Laden):
     * Basiswörter gruppiert nach erstem bzw. zweitem Buchstaben. Statt bei
     * jedem Space/Tastendruck alle ~6.000 Korpuswörter zu scannen, werden nur
     * die zwei relevanten Buchstabengruppen betrachtet (~10× kleiner). Der
     * Second-Char-Index bleibt nötig, damit Transpositionen wie "ahus" → "haus"
     * weiterhin gefunden werden.
     */
    private val byFirstChar: Map<Char, List<String>> by lazy {
        baseFreq.entries.groupBy({ it.key[0].lowercaseChar() }, { it.key })
    }
    private val bySecondChar: Map<Char, List<String>> by lazy {
        baseFreq.entries.groupBy({ it.key[1].lowercaseChar() }, { it.key })
    }

    /**
     * Kandidaten-Pool für Fuzzy-Matches: Basiswörter mit passendem erstem ODER
     * zweitem Buchstaben (über den Char-Index) plus alle User-Wörter
     * (≤ MAX_USER_WORDS, Direkt-Scan affordable). Dedupliziert.
     */
    private fun fuzzyCandidates(first: Char, second: Char?): List<String> {
        val seen = HashSet<String>()
        return buildList {
            byFirstChar[first]?.forEach { w -> if (seen.add(w)) add(w) }
            if (second != null) bySecondChar[second]?.forEach { w -> if (seen.add(w)) add(w) }
            userFreq.keys.forEach { w -> if (seen.add(w)) add(w) }
        }
    }

    init {
        // Log-Skalierung glättet die Extreme der Subtitle-Korpus-Frequenzen
        var maxLog = 0.0
        val tmp = HashMap<String, Double>()
        for ((w, f) in baseWords) {
            if (w.length < 2 || !w.all { it.isLetter() }) continue
            val lg = kotlin.math.ln(f.toDouble().coerceAtLeast(1.0))
            if (lg > maxLog) maxLog = lg
            tmp[w] = lg
        }
        val baseMaxLog = maxLog.coerceAtLeast(1.0)
        for ((w, lg) in tmp) baseFreq[w] = lg / baseMaxLog
    }

    /** Liefert die Basis-Frequenz eines Worts (0.0 wenn unbekannt). */
    fun baseScore(word: String): Double = baseFreq[word.lowercase()] ?: 0.0

    /**
     * Iteriert über alle Basiswörter, die mit [first] (lowercase) beginnen.
     * Wird vom Swipe-Scorer genutzt, um den Kandidaten-Pool ohne Kopie zu
     * durchlaufen (über den vorhandenen First-Char-Index).
     */
    fun forEachBaseWordStartingWith(first: Char, action: (String) -> Unit) {
        byFirstChar[first.lowercaseChar()]?.forEach(action)
    }

    fun knowsWord(word: String): Boolean =
        baseFreq.containsKey(word.lowercase()) || userFreq.containsKey(word.lowercase())

    val revision: Long get() = revisionCounter

    /** Jede Änderung im gelernten Bereich erhöht die Revision. */
    fun incrementRevision() {
        revisionCounter++
    }

    /**
     * Lernen: [word] wurde soeben abgeschlossen (Space/Enter/Punkt), [prevWord]
     * war das Wort davor (oder null am Satzanfang).
     */
    fun learn(prevWord: String?, word: String) {
        val w = word.lowercase()
        if (!isLearnable(w)) return
        userFreq[w] = (userFreq[w] ?: INITIAL_WORD_WEIGHT) + WORD_LEARN_STEP
        if (userFreq.size > MAX_USER_WORDS) {
            userFreq.minByOrNull { it.value }?.key?.let { userFreq.remove(it) }
        }
        val p = prevWord?.lowercase()
        if (p != null && isLearnable(p)) {
            val key = "$p $w"
            bigrams[key] = (bigrams[key] ?: INITIAL_BIGRAM_WEIGHT) + BIGRAM_LEARN_STEP
            if (bigrams.size > MAX_BIGRAMS) {
                bigrams.minByOrNull { it.value }?.let { bigrams.remove(it.key) }
            }
        }
        // Sanftes Decay, Schwaches verliert an Gewichtung
        if (userFreq.size % DECAY_INTERVAL == 0) {
            for (k in userFreq.keys.toList()) userFreq[k]?.let { cur -> userFreq[k] = cur * DECAY_FACTOR }
        }
    }

    /**
     * Vorschläge für den aktuell getippten Text.
     * @param currentWord aktuell getipptes (unvollständiges) Wort, evtl. leer
     * @param prevWord Wort vor dem aktuellen (für Bigram-Prediction), evtl. null
     * @param sentenceStart true, wenn der Cursor nach einem Satz-Terminator
     *   (. ! ?) oder am Dokument-Anfang steht — dann werden
     *   Satzanfangswörter bevorzugt und Vorschläge großgeschrieben
     */
    fun suggest(
        currentWord: String, prevWord: String?,
        max: Int = MAX_SUGGESTIONS, sentenceStart: Boolean = false
    ): List<Suggestion> {
        val cur = currentWord.lowercase()
        // Am Satzanfang: Bigramme vom vorherigen Wort nicht nutzen
        // (nach "." ist das vorherige Wort kein relevanter Kontext mehr).
        // Stattdessen satztypische Wortwahl via sentenceStartBoost.
        val effectivePrev = if (sentenceStart) null else prevWord?.lowercase()
        return if (cur.isEmpty()) predictNext(effectivePrev, max, sentenceStart)
               else completeWord(cur, prevWord?.lowercase(), max)
    }

        /** Next-Word-Prediction: Bigramm zuerst, dann häufigste Basis-/Nutzerwörter. */
    private fun predictNext(prev: String?, max: Int, sentenceStart: Boolean = false): List<Suggestion> {
        val results = LinkedHashMap<String, Double>()
        if (prev != null && !sentenceStart) {
            bigrams.entries
                .filter { it.key.startsWith("$prev ") }
                .map { it.key.substringAfter(' ') to it.value }
                .sortedByDescending { it.second }.take(MAX_BIGRAM_PREDICTIONS)
                .forEach { (w, weight) ->
                    results[w] = weight * PREDICT_BIGRAM_WEIGHT + baseScore(w) * PREDICT_BASE_WEIGHT + (userFreq[w] ?: 0.0)
                }
        }
        if (results.size < max) {
            var added = 0
            val limit = max * USER_SCAN_LIMIT_FACTOR
            for ((w, bonus) in userFreq.entries) {
                if (w == prev || results.containsKey(w)) continue
                var score = baseScore(w) * BASE_WEIGHT + bonus
                if (sentenceStart) score *= sentenceStartBoost(w)
                results[w] = score
                if (++added > limit) break
            }
            for (w in topBaseOrder) {
                if (w == prev || results.containsKey(w)) continue
                var score = baseScore(w) * BASE_WEIGHT
                if (sentenceStart) score *= sentenceStartBoost(w)
                results[w] = score
                if (++added > limit) break
            }
        }
        return results.entries.sortedByDescending { it.value }.take(max)
            .map { Suggestion(it.key, it.value) }
    }

    /** Satzanfang-Boost, siehe [SuggestionCorrection.sentenceStartBoost]. */
    private fun sentenceStartBoost(word: String): Double =
        SuggestionCorrection.sentenceStartBoost(word)

    /** Autovervollständigung + Fehlerkorrektur für das aktuelle Teilwort. */
    private fun completeWord(cur: String, prev: String?, max: Int): List<Suggestion> {
        val results = LinkedHashMap<String, Double>()
        val bigramBonus: (String) -> Double = { w ->
            if (prev != null) bigrams["$prev $w"] ?: 0.0 else 0.0
        }
        val userBonus: (String) -> Double = { w -> userFreq[w] ?: 0.0 }
        fun consider(w: String, penalty: Double = 0.0) {
            // Exakt getipptes Wort nie vorschlagen ( Nutzer tippt es ja schon )
            if (w.lowercase() == cur) return
            // Längere Kandidaten leicht abwerten: kürzere Vervollständigungen
            // sind näher an der Eingabe (Deterministisch, winziger Faktor).
            val lenPenalty = (w.length - cur.length).coerceAtLeast(0) * LEN_PENALTY_FACTOR
            val s = baseScore(w) + userBonus(w) * USER_WEIGHT + bigramBonus(w) * BIGRAM_WEIGHT -
                penalty - lenPenalty
            val existing = results[w]
            if (existing == null || existing < s) results[w] = s
        }
        // Prefix-Kandidaten kommen aus dem Char-Index (case-insensitiv aufgebaut,
        // [cur] ist bereits lowercase) statt aus einem Voll-Scan über alle
        // ~6.000 Korpuswörter pro Tastendruck — gleiches Ergebnis, ~10x weniger
        // Kandidaten (vgl. [fuzzyCandidates] für den Fuzzy-Pfad).
        forEachBaseWordStartingWith(cur[0]) { w ->
            if (w.lowercase().startsWith(cur)) consider(w)
        }
        for (w in userFreq.keys) if (w.startsWith(cur)) consider(w)
        if (results.size < max) {
            val maxDist = if (cur.length >= FUZZY_DIST2_MIN_LENGTH) 2
                else if (cur.length >= FUZZY_DIST1_MIN_LENGTH) 1 else 0
            if (maxDist > 0) {
                val first = cur[0]
                val second = cur.getOrNull(1)
                for (w in fuzzyCandidates(first, second)) {
                    // Längen-Differenz ist eine Untergrenze der Edit-Distanz
                    if (Math.abs(w.length - cur.length) > maxDist) continue
                    val dist = editDistance(cur, w)
                    if (dist in 1..maxDist) consider(w, penalty = dist * DIST_PENALTY)
                }
            }
        }
        return results.entries.sortedByDescending { it.value }.take(max)
            .map { Suggestion(it.key, it.value) }
    }

    /** Groß-/Kleinschreibung übertragen, siehe [SuggestionCorrection.matchCase]. */
    fun matchCase(suggestion: String, typed: String, sentenceStart: Boolean = false): String =
        SuggestionCorrection.matchCase(suggestion, typed, sentenceStart)

    // ---------- Persistenz ----------

    /** User-Dictionary + Bigramme als kompakter String serialisieren. */
    fun serializeUserDict(): String {
        val sb = StringBuilder()
        fun fmt(d: Double) = String.format(java.util.Locale.ROOT, "%.2f", d)
        for ((w, f) in userFreq) sb.append(w).append(SEP_FIELD).append(fmt(f)).append(SEP_ENTRY)
        sb.append(SEP_ENTRY)
        for ((k, f) in bigrams) sb.append(k).append(SEP_FIELD).append(fmt(f)).append(SEP_ENTRY)
        return sb.toString()
    }

    /** Serialisiertes User-Dictionary wiederherstellen (fehertolerant). */
    fun restoreUserDict(raw: String) {
        userFreq.clear()
        bigrams.clear()
        var inBigrams = false
        for (entry in raw.split(SEP_ENTRY)) {
            if (entry.isEmpty()) { inBigrams = true; continue }
            val parts = entry.split(SEP_FIELD)
            if (parts.size != 2) continue
            val f = parts[1].toDoubleOrNull() ?: continue
            if (!inBigrams) userFreq[parts[0]] = f else bigrams[parts[0]] = f
        }
    }
}

