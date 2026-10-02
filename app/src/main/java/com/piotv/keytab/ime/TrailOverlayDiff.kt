package com.piotv.keytab.ime

/**
 * Reine (Android-freie) Differenzlogik für Overlay-Repaints des Trails.
 *
 * V0.16 (Performance): [TrailKeyboardPainter] strich bisher bei jedem
 * [TrailManager.applyToKeyboard] das Foreground-Overlay ALLER Tasten und
 * setzte es neu — pro Tastendruck ~30 View-Schreibzugriffe, auch für
 * unveränderte Tasten. Die Deadline-Misses beim heißen Tippen entstehen
 * draw-seitig (vgl. docs/DEVICE_PERF.md), deshalb berührt der Painter jetzt
 * nur noch Tasten, deren Overlay-Zustand sich tatsächlich ändert.
 *
 * Diese Klasse liefert die reine Entscheidung (entfernen / setzen) getrennt
 * von der View-Application, damit sie JUnit-testbar bleibt.
 */
object TrailOverlayDiff {

    /** Ergebnis der Differenz zwischen zwei Overlay-Zuständen. */
    data class Diff<K>(
        /** Schlüssel, deren Overlay entfernt werden muss (nicht mehr im Ziel). */
        val remove: Set<K>,
        /** Schlüssel → Farbe, deren Overlay gesetzt/aktualisiert werden muss. */
        val set: Map<K, Int>
    )

    /**
     * Berechnet, welche Overlays entfernt bzw. gesetzt werden müssen, um vom
     * Zustand [previous] (aktuell angezeigt) zum Zustand [target] (gewünscht)
     * zu kommen. Nur Schlüssel, deren Farbe sich ändert, landen in [Diff.set];
     * verschwundene Schlüssel landen in [Diff.remove]. Unveränderte Schlüssel
     * werden in keiner der beiden Mengen berührt.
     */
    fun <K> compute(previous: Map<K, Int>, target: Map<K, Int>): Diff<K> {
        val remove = LinkedHashSet<K>()
        for (k in previous.keys) {
            if (k !in target) remove.add(k)
        }
        val set = LinkedHashMap<K, Int>()
        for ((k, color) in target) {
            if (previous[k] != color) set[k] = color
        }
        return Diff(remove, set)
    }
}
