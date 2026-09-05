package com.piotv.keytab.ime

/**
 * Reine (Android-freie) Logik für die dynamische Tastenskalierung.
 *
 * Gestuftes Modell („Stufen“ analog zur Wahrscheinlichkeit):
 * - Stufe 3: rel ≥ hotThreshold → maxScale (größte Stufe)
 * - Stufe 2: rel ≥ midThreshold → midScale
 * - Verkleinerung ausschließlich im direkten Umfeld vergrößerter Tasten,
 *   ebenfalls gestuft nach Stärke des vergrößerten Nachbarn:
 *   Nachbar von Stufe 3 → minNeighborScale, Nachbar von Stufe 2 → midNeighborScale
 * - Alle übrigen Tasten neutral (1.0×; in der Map nicht enthalten).
 *
 * Die Schwellen/Skalen sind über [params] zur Laufzeit konfigurierbar
 * (KeyTab-Konfigurationsdatei, siehe [KeyTabConfig]).
 */
object KeyScaleLogic {

    /** Größte Vergrößerungsstufe (höchste Wahrscheinlichkeit). Default. */
    const val MAX_SCALE = 1.30f

    /** Zweite Vergrößerungsstufe. Default. */
    const val MID_SCALE = 1.15f

    /** Schwellwert für die zweite Stufe (relative Wahrscheinlichkeit). Default. */
    const val MID_THRESHOLD = 0.55

    /** Schwellwert für die größte Stufe (relative Wahrscheinlichkeit). Default. */
    const val HOT_THRESHOLD = 0.75

    /** Stärkste Verkleinerung direkt neben einer Stufe-3-Taste. Default. */
    const val MIN_NEIGHBOR_SCALE = 0.85f

    /** Verkleinerung direkt neben einer Stufe-2-Taste. Default. */
    const val MID_NEIGHBOR_SCALE = 0.925f

    /** Konfigurierbare Parameter (Defaults = obige Konstanten). */
    data class Params(
        val maxScale: Float = MAX_SCALE,
        val midScale: Float = MID_SCALE,
        val hotThreshold: Double = HOT_THRESHOLD,
        val midThreshold: Double = MID_THRESHOLD,
        val minNeighborScale: Float = MIN_NEIGHBOR_SCALE,
        val midNeighborScale: Float = MID_NEIGHBOR_SCALE
    )

    /** Aktive Parameter; werden aus der KeyTab-Konfigurationsdatei gesetzt. */
    @Volatile
    var params: Params = Params()

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
        val p = params
        val maxScore = scores.values.maxOrNull() ?: return emptyMap()
        if (maxScore <= 0.0) return emptyMap()

        val rel = scores.mapValues { it.value / maxScore }

        // 1) Vergrößerung: gestuft proportional zur Wahrscheinlichkeit
        val out = HashMap<Char, Float>()
        for ((c, r) in rel) {
            if (r >= p.hotThreshold) out[c] = p.maxScale
            else if (r >= p.midThreshold) out[c] = p.midScale
        }

        // 2) Verkleinerung: nur direkte Nachbarn vergrößerter Tasten, gestuft
        //    nach der Stärke des stärksten vergrößerten Nachbarn
        //    (Nachbarn können selbst keinen Score haben — müssen trotzdem geprüft werden)
        val candidates = rel.keys + rel.keys.flatMap { neighborsOf(it) }
        for (c in candidates) {
            if (out.containsKey(c)) continue
            val hotNeighborRel = neighborsOf(c).mapNotNull { rel[it] }
                .filter { it >= p.midThreshold }
                .maxOrNull() ?: continue
            out[c] = if (hotNeighborRel >= p.hotThreshold) p.minNeighborScale
            else p.midNeighborScale
        }
        return out
    }
}

