package com.piotv.keytab.ime

/**
 * Baseline-Versatz (Rebaseline) für Tasten-/Kandidaten-Labels.
 *
 * Positiver [shift] hebt die Basislinie an, negativer senkt sie ab
 * (Faktor relativ zur aktuellen Schriftgröße). Wirkung über
 * [android.text.style.MetricAffectingSpan] + DrawState: erlaubt FlorisBoard-
 * Style-Labels (Hauptbuchstabe leicht angehoben, Hinweis-Zeichen klein
 * rechts-UNTEN stark abgesenkt – vgl. Verwendung in [KeyTabImeService]).
 *
 * Refactoring (docs/REFACTORING_PLAN.md Phase 1): aus innerer Klasse des
 * [KeyTabImeService] herausgezogen, damit er unabhängig vom Service als
 * wiederverwendbarer Text-Span vorliegt.
 */
internal class LiftSpan(private val shift: Float) : android.text.style.MetricAffectingSpan() {
    private fun apply(tp: android.text.TextPaint) {
        // WICHTIG: `baselineShift` NICHT aufaddieren. Android reicht denselben
        // TextPaint durch mehrere Measure-/Draw-Passes (TextLine) — ein += würde
        // den Versatz mit jedem Pass weiter wachsen lassen, bis die Buchstaben
        // aus der Taste herauswandern (Symptom: „Buchstaben verschwinden").
        // Deshalb: den von diesem Span gesetzten Anteil genau einmal setzen.
        val target = (tp.textSize * shift).toInt()
        tp.baselineShift = target
    }
    override fun updateMeasureState(tp: android.text.TextPaint) = apply(tp)
    override fun updateDrawState(tp: android.text.TextPaint) = apply(tp)
}