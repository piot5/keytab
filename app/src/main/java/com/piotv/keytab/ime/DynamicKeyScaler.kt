package com.piotv.keytab.ime

import android.view.ViewGroup
import android.widget.Button
import com.piotv.keytab.ime.KeyScaleLogic

/**
 * Modul für die dynamische Tastenskalierung.
 *
 * Wandelt aktuelle Vorschlags-Scores in Tastengrößen um (vergrößert wahrscheinliche
 * nächste Buchstaben, verkleinert ausschließlich deren direkte Nachbarn). Reine
 * Skalierungslogik steckt in [KeyScaleLogic]; hier nur die Layout-Anbindung.
 */
class DynamicKeyScaler(
    private val baseLetters: Map<Button, Char>
) {
    private var neighborLookup: (Char) -> Set<Char> = { emptySet() }

    /** Muss nach Layout-Änderungen neu berechnet werden (z. B. beim View-Build). */
    fun rebuildNeighbors() {
        neighborLookup = computeNeighbors()
    }

    /**
     * Skaliert alle Buchstaben-Tasten anhand der aktuellen Vorschläge.
     *
     * @param suggestions aktuelle Vorschläge (nächster Buchstabe + Score)
     * @param typedLength Länge des bereits getippten Teilworts
     * @param enabled Feature-Flag (Einstellungen)
     */
    fun apply(
        suggestions: List<SuggestionEngine.Suggestion>,
        typedLength: Int,
        enabled: Boolean
    ) {
        if (!enabled) {
            for ((btn, _) in baseLetters) {
                btn.scaleX = 1f
                btn.scaleY = 1f
            }
            return
        }
        val charScore = HashMap<Char, Double>()
        for (sug in suggestions) {
            val nextChar = sug.word.getOrNull(typedLength)?.lowercaseChar() ?: continue
            charScore[nextChar] = (charScore[nextChar] ?: 0.0) + sug.score
        }
        val scaleMap = KeyScaleLogic.scales(charScore, neighborLookup)
        for ((btn, letter) in baseLetters) {
            val s = scaleMap[letter.lowercaseChar()] ?: 1f
            btn.scaleX = s
            btn.scaleY = s
        }
    }

    /** Direkte Nachbarschaft: gleiche Zeile ±1 Spalte, angrenzende Zeile ±1 Spalte. */
    private fun computeNeighbors(): (Char) -> Set<Char> {
        val byRow = baseLetters.keys.groupBy { it.parent as? ViewGroup }
        val rows = byRow.keys.filterNotNull()
            .sortedBy { row -> (row.parent as? ViewGroup)?.indexOfChild(row) ?: 0 }
        val cellOf = HashMap<Button, Pair<Int, Int>>()
        rows.forEachIndexed { r, row ->
            byRow[row]?.forEach { btn -> cellOf[btn] = r to row.indexOfChild(btn) }
        }
        val neighborButtons = HashMap<Char, MutableSet<Char>>()
        for ((btn, letter) in baseLetters) {
            val (r, c) = cellOf[btn] ?: continue
            val key = letter.lowercaseChar()
            val set = neighborButtons.getOrPut(key) { mutableSetOf() }
            for ((other, otherLetter) in baseLetters) {
                if (other === btn) continue
                val (r2, c2) = cellOf[other] ?: continue
                val sameRow = r2 == r && kotlin.math.abs(c2 - c) == 1
                val adjacentRow = kotlin.math.abs(r2 - r) == 1 && kotlin.math.abs(c2 - c) <= 1
                if (sameRow || adjacentRow) set.add(otherLetter.lowercaseChar())
            }
        }
        return { c -> neighborButtons[c] ?: emptySet() }
    }
}