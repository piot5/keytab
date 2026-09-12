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
        tp.baselineShift += (tp.textSize * shift).toInt()
    }
    override fun updateMeasureState(tp: android.text.TextPaint) = apply(tp)
    override fun updateDrawState(tp: android.text.TextPaint) = apply(tp)
}