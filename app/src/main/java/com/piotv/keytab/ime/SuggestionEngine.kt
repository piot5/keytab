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
        private const val DECAY_FACTOR = 0.98
        private const val MAX_WORD_LEN = 32
        /** Trenner für die Serialisierung des User-Dictionary. */
        private const val SEP_ENTRY = "\u0001"
        private const val SEP_FIELD = "\u0002"

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
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                        d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1) // transposition
                    }
                }
            }
            return d[n][m]
        }
    }

    /** Basiswortschatz: word → normalisierte Log-Frequenz (0..1). */
    private val baseFreq: MutableMap<String, Double> = ConcurrentHashMap()
    /** Gelernte Wörter: word → Gewicht. */
    private val userFreq = ConcurrentHashMap<String, Double>()
    /** Gelernte Bigramme: "prev next" → Gewicht. */
    private val bigrams = ConcurrentHashMap<String, Double>()
    /** Basiswörter nach Frequenz absteigend (für Next-Word-Fallback). */
    private val topBaseOrder: List<String> by lazy {
        baseFreq.entries.sortedByDescending { it.value }.map { it.key }
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

    fun knowsWord(word: String): Boolean =
        baseFreq.containsKey(word.lowercase()) || userFreq.containsKey(word.lowercase())

    /**
     * Lernen: [word] wurde soeben abgeschlossen (Space/Enter/Punkt), [prevWord]
     * war das Wort davor (oder null am Satzanfang).
     */
    fun learn(prevWord: String?, word: String) {
        val w = word.lowercase()
        if (!isLearnable(w)) return
        userFreq[w] = (userFreq[w] ?: 0.2) + 0.25
        if (userFreq.size > MAX_USER_WORDS) {
            userFreq.minByOrNull { it.value }?.key?.let { userFreq.remove(it) }
        }
        val p = prevWord?.lowercase()
                if (p != null && isLearnable(p)) {
            val key = "$p $w"
            bigrams[key] = (bigrams[key] ?: 0.1) + 0.3
            if (bigrams.size > MAX_BIGRAMS) {
                bigrams.minByOrNull { it.value }?.let { bigrams.remove(it.key) }
            }
        }
        // Sanftes Decay, Schwaches verliert an Gewichtung
        if (userFreq.size % 25 == 0) {
            for (k in userFreq.keys.toList()) userFreq[k] = userFreq[k]!! * DECAY_FACTOR
        }
    }

    /**
     * Vorschläge für den aktuellen Teilwort-Status.
     * @param currentWord aktuell getipptes (unvollständiges) Wort, evtl. leer
     * @param prevWord Wort vor dem aktuellen (für Bigram-Prediction), evtl. null
     */
    fun suggest(currentWord: String, prevWord: String?, max: Int = MAX_SUGGESTIONS): List<Suggestion> {
        val cur = currentWord.lowercase()
        return if (cur.isEmpty()) predictNext(prevWord?.lowercase(), max)
        else completeWord(cur, prevWord?.lowercase(), max)
    }

    /** Next-Word-Prediction: Bigramm zuerst, dann häufigste Basis-/Nutzerwörter. */
    private fun predictNext(prev: String?, max: Int): List<Suggestion> {
        val results = LinkedHashMap<String, Double>()
        if (prev != null) {
            bigrams.entries
                .filter { it.key.startsWith("$prev ") }
                .map { it.key.substringAfter(' ') to it.value }
                .sortedByDescending { it.second }.take(10)
                .forEach { (w, weight) ->
                    results[w] = weight * 2.0 + baseScore(w) * 0.5 + (userFreq[w] ?: 0.0)
                }
        }
        if (results.size < max) {
            var added = 0
            val limit = max * 8
            for ((w, bonus) in userFreq.entries) {
                if (w == prev || results.containsKey(w)) continue
                results[w] = baseScore(w) * 0.6 + bonus
                if (++added > limit) break
            }
            for (w in topBaseOrder) {
                if (w == prev || results.containsKey(w)) continue
                results[w] = baseScore(w) * 0.6
                if (++added > limit) break
            }
        }
        return results.entries.sortedByDescending { it.value }.take(max)
            .map { Suggestion(it.key, it.value) }
    }

    /** Autovervollständigung + Fehlerkorrektur für das aktuelle Teilwort. */
    private fun completeWord(cur: String, prev: String?, max: Int): List<Suggestion> {
        val results = LinkedHashMap<String, Double>()
        val bigramBonus: (String) -> Double = { w ->
            if (prev != null) bigrams["$prev $w"] ?: 0.0 else 0.0
        }
        val userBonus: (String) -> Double = { w -> userFreq[w] ?: 0.0 }
        fun consider(w: String, penalty: Double = 0.0) {
            if (w == cur && penalty == 0.0) return
            val s = baseScore(w) + userBonus(w) * 1.2 + bigramBonus(w) * 3.0 - penalty
            val existing = results[w]
            if (existing == null || existing < s) results[w] = s
        }
        for (w in baseFreq.keys) if (w.startsWith(cur)) consider(w)
        for (w in userFreq.keys) if (w.startsWith(cur)) consider(w)
        if (results.size < max) {
            val maxDist = if (cur.length >= 6) 2 else if (cur.length >= 4) 1 else 0
            if (maxDist > 0) {
                val first = cur[0]
                val second = cur.getOrNull(1)
                val pool = baseFreq.keys.asSequence() + userFreq.keys.asSequence()
                for (w in pool) {
                    if (w[0] != first && w.getOrNull(1) != second) continue
                    val dist = editDistance(cur, w)
                    if (dist in 1..maxDist) consider(w, penalty = dist * 0.45)
                }
            }
        }
        return results.entries.sortedByDescending { it.value }.take(max)
            .map { Suggestion(it.key, it.value) }
    }

    /** Groß-/Kleinschreibung des Getippten auf den Vorschlag übertragen. */
    fun matchCase(suggestion: String, typed: String): String =
        if (typed.isNotEmpty() && typed[0].isUpperCase()) {
            suggestion.replaceFirstChar { it.uppercase() }
        } else suggestion

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

