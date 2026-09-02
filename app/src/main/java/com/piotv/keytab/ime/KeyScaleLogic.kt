package com.piotv.keytab.ime

/**
 * Reine (Android-freie) Logik für die dynamische Tastenskalierung.
 *
 * Gestuftes Modell („Stufen“ analog zur Wahrscheinlichkeit):
 * - Stufe 3: rel ≥ 0.75 → 1.30× (größte Stufe)
 * - Stufe 2: rel ≥ 0.55 → 1.15×
 * - Verkleinerung ausschließlich im direkten Umfeld vergrößerter Tasten,
 *   ebenfalls gestuft nach Stärke des vergrößerten Nachbarn:
 *   Nachbar von Stufe 3 → 0.85×, Nachbar von Stufe 2 → 0.925×
 * - Alle übrigen Tasten neutral (1.0×; in der Map nicht enthalten).
 */
object KeyScaleLogic {

    /** Größte Vergrößerungsstufe (höchste Wahrscheinlichkeit). */
    const val MAX_SCALE = 1.30f

    /** Zweite Vergrößerungsstufe. */
    const val MID_SCALE = 1.15f

    /** Schwellwert für die zweite Stufe (relative Wahrscheinlichkeit). */
    const val MID_THRESHOLD = 0.55

    /** Schwellwert für die größte Stufe (relative Wahrscheinlichkeit). */
    const val HOT_THRESHOLD = 0.75

    /** Stärkste Verkleinerung direkt neben einer Stufe-3-Taste. */
    const val MIN_NEIGHBOR_SCALE = 0.85f

    /** Verkleinerung direkt neben einer Stufe-2-Taste. */
    const val MID_NEIGHBOR_SCALE = 0.925f

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

        // 1) Vergrößerung: gestuft proportional zur Wahrscheinlichkeit
        val out = HashMap<Char, Float>()
        for ((c, r) in rel) {
            if (r >= HOT_THRESHOLD) out[c] = MAX_SCALE
            else if (r >= MID_THRESHOLD) out[c] = MID_SCALE
        }

        // 2) Verkleinerung: nur direkte Nachbarn vergrößerter Tasten, gestuft
        //    nach der Stärke des stärksten vergrößerten Nachbarn
        //    (Nachbarn können selbst keinen Score haben — müssen trotzdem geprüft werden)
        val candidates = rel.keys + rel.keys.flatMap { neighborsOf(it) }
        for (c in candidates) {
            if (out.containsKey(c)) continue
            val hotNeighborRel = neighborsOf(c).mapNotNull { rel[it] }
                .filter { it >= MID_THRESHOLD }
                .maxOrNull() ?: continue
            out[c] = if (hotNeighborRel >= HOT_THRESHOLD) MIN_NEIGHBOR_SCALE
            else MID_NEIGHBOR_SCALE
        }
        return out
    }
}

