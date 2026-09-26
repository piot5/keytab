package com.piotv.keytab.ime

import android.graphics.Path
import android.view.ViewGroup
import android.widget.Button

/**
 * Reiner Pfad-Aufbau der Schaltplan-Kanten: verbindet die [nodes] zu einem
 * Linienzug in **Container**-Koordinaten.
 *
 * Ausgelagert aus [SwipeManager.drawEdges], damit die reine Geometrie
 * (Tasten-Zentren umrechnen + [Path] aufbauen) getrennt vom Android-gebundenen
 * Overlay-Zustand (View-Erzeugung, Prefs, Restore) lesbar bleibt. Die
 * Zeichen-Logik selbst steckt in [SwipeEdgeOverlay].
 *
 * **Koordinaten:** [SwipeKeyGeometry.centersFor] liefert fenster-relative
 * Zentren; hier wird die Container-Position abgezogen, damit das Overlay
 * (MATCH_PARENT im Container) den Linienzug an der richtigen Stelle zeigt.
 */
internal object SwipeEdgeDrawing {

    /**
     * Pfad durch die Buchstaben in [nodes] (Reihenfolge = Zeichenreihenfolge).
     * Buchstaben ohne Tasten-Treffer werden übersprungen.
     *
     * @return der [Path] oder `null`, wenn kein einziger Knoten positioniert
     *   werden konnte (dann gibt es keine Kante zu zeichnen).
     */
    fun buildEdgePath(
        nodes: List<Char>,
        container: ViewGroup,
        baseLetters: Map<Button, Char>
    ): Path? {
        val rootLoc = IntArray(2)
        container.getLocationInWindow(rootLoc)
        val centers = LinkedHashMap<Char, SwipePathLogic.KeyCenter>()
        for ((btn, letter) in baseLetters) {
            val loc = IntArray(2)
            btn.getLocationInWindow(loc)
            centers[letter.lowercaseChar()] = SwipePathLogic.KeyCenter(
                letter.lowercaseChar(),
                loc[0] + btn.width / 2f - rootLoc[0],
                loc[1] + btn.height / 2f - rootLoc[1]
            )
        }
        val path = Path()
        var moved = false
        for (ch in nodes) {
            val c = centers[ch] ?: continue
            if (!moved) { path.moveTo(c.x, c.y); moved = true }
            else path.lineTo(c.x, c.y)
        }
        return if (moved) path else null
    }
}
