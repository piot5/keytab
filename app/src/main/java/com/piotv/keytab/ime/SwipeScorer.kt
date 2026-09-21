package com.piotv.keytab.ime

/**
 * Reiner Swipe-Scorer (Android-frei, JUnit-testbar): bewertet die aus einer
 * gefahrenen Route gewonnene Buchstabenfolge gegen die [SuggestionEngine] und
 * liefert die besten Kandidaten für die Vorschlags-Leiste (Auto-Commit bei
 * klarem Ergebnis).
 *
 * **Design** (vgl. `docs/SWIPE_PLAN.md` §3): der Scorer nutzt ausschließlich die
 * lokale Engine (kein Netz, kein neues Modell). Die Route liefert eine
 * Buchstabenfolge (Tastenwechsel, keine Wiederholungen). Bewertet werden alle
 * Basis-/User-Wörter, deren **Buchstabenfolge eine Teilfolge der Route** ist
 * (Reihenfolge erhalten, Lücken erlaubt – das ist die für Gesten typische
 * „Zickzack“-Eigenschaft). Der Score kombiniert:
 *  - Treffer-Quote (abgearbeitete Buchstaben des Kandidaten / Kandidatenlänge),
 *  - Wortfrequenz (Basis + User + Bigram-Bonus),
 *  - Kompaktheit (weniger Lücken in der Route → höher),
 *  - Längenanpassung (Kandidat nicht viel länger als die Route).
 *
 * So bleibt der Scorer deterministisch, offline und ohne Trainingsdaten und
 * skaliert über den vorhandenen Char-Index der Engine.
 */
object SwipeScorer {

    /** Auto-Commit-Schwelle (fix im ersten Release, Empfehlung aus dem Plan). */
    const val AUTO_COMMIT_THRESHOLD = 1.35

    /** Maximal bewertete Kandidaten (Deckel gegen lange Listen). */
    const val MAX_CANDIDATES = 12

    /** Bewertungsergebnis für einen Kandidaten. */
    data class Candidate(val word: String, val score: Double)

    /**
     * Bewertet eine Swipe-Buchstabenfolge ([route]) gegen die Engine und liefert
     * die besten Kandidaten (absteigend sortiert). [prev] ist das Wort vor dem
     * aktuellen (Bigram-Bonus), darf null sein.
     *
     * Leere Route → leere Liste. Engine null (noch ladend) → leere Liste
     * (Swipe-Fallback = normaler Tap, siehe Plan §8).
     */
    fun score(
        route: String,
        engine: SuggestionEngine?,
        prev: String? = null,
        max: Int = MAX_CANDIDATES
    ): List<Candidate> {
        if (route.isEmpty() || engine == null) return emptyList()
        val r = route.lowercase()
        // Pool: Basiswörter mit passendem Startbuchstaben + User-Wörter.
        // Der Startbuchstabe der Route MUSS der erste Buchstabe des Kandidaten
        // sein (die Route beginnt auf der ersten Taste des Worts).
        val first = r[0]
        val pool = LinkedHashSet<String>()
        for (w in engine.userFreq.keys) if (w.startsWith(first)) pool.add(w)
        engine.forEachBaseWordStartingWith(first) { pool.add(it) }
        val out = ArrayList<Candidate>(pool.size)
        for (w in pool) {
            val s = scoreWord(w, r, engine, prev)
            if (s > 0.0) out.add(Candidate(w, s))
        }
        out.sortByDescending { it.score }
        return out.take(max)
    }

    /**
     * Wahr, wenn der Top-Kandidat die Auto-Commit-Schwelle klar übersteigt
     * (→ Swipe kann das Wort direkt committen, ohne Leiste).
     */
    fun autoCommit(candidates: List<Candidate>): String? {
        if (candidates.isEmpty()) return null
        val top = candidates.first()
        val second = candidates.getOrNull(1)
        if (top.score < AUTO_COMMIT_THRESHOLD) return null
        // Klarer Vorsprung: Top mindestens 1.3× so stark wie der Zweitbeste.
        if (second != null && top.score < second.score * 1.3) return null
        return top.word
    }

    /**
     * Bewertet ein einzelnes Kandidatenwort gegen die Route.
     * Liefert 0.0, wenn die Buchstabenfolge des Kandidaten keine Teilfolge der
     * Route ist (Reihenfolge erhalten) oder der Kandidat länger als die Route ist.
     */
    fun scoreWord(
        word: String,
        route: String,
        engine: SuggestionEngine,
        prev: String?
    ): Double {
        val w = word.lowercase()
        if (w.isEmpty() || w.length > route.length) return 0.0
        val (matched, gaps) = subsequenceMatch(w, route)
        if (!matched) return 0.0
        val hitRatio = w.length.toDouble() / route.length.coerceAtLeast(1)
        val compactness = 1.0 - (gaps.toDouble() / route.length.coerceAtLeast(1))
        val lenFit = if (w.length == route.length) 1.0
            else 1.0 - 0.1 * kotlin.math.abs(route.length - w.length)
        val freq = engine.baseScore(w) + (engine.userFreq[w] ?: 0.0) * 1.2
        val bigram = if (prev != null) engine.bigrams["${prev.lowercase()} $w"] ?: 0.0 else 0.0
        val bigramBonus = bigram * 3.0
        // Kürzere Wörter (2–3 Buchstaben) brauchen einen kleinen Bonus, damit sie
        // gegen lange Treffer konkurrenzfähig bleiben (Gesten tendieren zu kurzen).
        val shortBonus = if (w.length <= 3) 0.15 else 0.0
        return hitRatio * 1.5 + compactness * 1.0 + lenFit * 0.5 +
            freq * 1.2 + bigramBonus + shortBonus
    }

    /**
     * Teilfolge-Match: prüft, ob [word] als Teilfolge in [route] enthalten ist
     * (Reihenfolge erhalten, Lücken erlaubt). Liefert (matched, gaps), wobei
     * gaps = Anzahl übersprungener Route-Buchstaben zwischen den Treffern.
     */
    fun subsequenceMatch(word: String, route: String): Pair<Boolean, Int> {
        if (word.isEmpty()) return true to 0
        if (word.length > route.length) return false to 0
        var wi = 0
        var ri = 0
        var gaps = 0
        var lastMatchRi = -1
        while (wi < word.length && ri < route.length) {
            if (word[wi] == route[ri]) {
                if (lastMatchRi >= 0) gaps += ri - lastMatchRi - 1
                lastMatchRi = ri
                wi++
            }
            ri++
        }
        return (wi == word.length) to gaps
    }
}
