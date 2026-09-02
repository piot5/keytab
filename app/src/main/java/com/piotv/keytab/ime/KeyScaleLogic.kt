package com.piotv.keytab.ime

/**
 * Reine (Android-freie) Logik für die dynamische Tastenskalierung.
 *
 * Regeln:
 * - Tasten mit hoher Wahrscheinlichkeit (rel > HOT_THRESHOLD) werden skaliert,
 *   proportional zu ihrer gewichteten Wahrscheinlichkeit: 1.0 … MAX_SCALE (1.30×).
 * - Verkleinerung (bis MIN_NEIGHBOR_SCALE = 0.85×) gibt es ausschließlich im
 *   direkten Umfeld (Nachbartasten) vergrößerter Tasten, proportional zur
 *   Stärke des vergrößerten Nachbarn.
 * - Alle übrigen Tasten bleiben neutral bei 1.0×.
 */
object KeyScaleLogic {

    /** Größte Vergrößerungsstufe (höchste Wahrscheinlichkeit). */
    const val MAX_SCALE = 1.30f

    /** Stärkste Verkleinerung direkt neben einer maximal vergrößerten Taste. */
    const val MIN_NEIGHBOR_SCALE = 0.85f

    /** Ab dieser relativen Wahrscheinlichkeit gilt eine Taste als „hot“ (vergrößert). */
    const val HOT_THRESHOLD = 0.55

    /**
     * Berechnet die Skalierung pro Buchstabe.
     *
     * @param scores akkumulierte, gewichtete Vorschlags-Scores pro nächstem Buchstaben
     * @param neighborsOf direkte Nachbarbuchstaben einer Taste (Zeile ±1, Zeile darüber/darunter ±1 Spalte)
     * @return Map Buchstabe → Scale; nicht enthaltene Buchstaben bleiben bei 1.0×
     */
    fun scales(
        scores: Map<Char, Double>,
        neighborsOf: (Char) -> Set<Char>
    ): Map<Char, Float> {
        val maxScore = scores.values.maxOrNull() ?: return emptyMap()
        if (maxScore <= 0.0) return emptyMap()

        val rel = scores.mapValues { it.value / maxScore }

        // 1) Vergrößerung: proportional zur Wahrscheinlichkeit
        val out = HashMap<Char, Float>()
        for ((c, r) in rel) {
            if (r > HOT_THRESHOLD) {
                out[c] = 1f + r.toFloat() * (MAX_SCALE - 1f)
            }
        }

        // 2) Verkleinerung: nur direkte Nachbarn von „hot“-Tasten, proportional zu deren Stärke
        //    (Nachbarn können selbst keinen Score haben — müssen trotzdem geprüft werden)
        val candidates = rel.keys + rel.keys.flatMap { neighborsOf(it) }
        for (c in candidates) {
            if (out.containsKey(c)) continue
            val hotNeighbor = neighborsOf(c).mapNotNull { rel[it] }
                .filter { it > HOT_THRESHOLD }
                .maxOrNull() ?: continue
            out[c] = 1f - hotNeighbor.toFloat() * (1f - MIN_NEIGHBOR_SCALE)
        }
        return out
    }
}
