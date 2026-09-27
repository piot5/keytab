package com.piotv.keytab.ime

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan

/**
 * Setzt den Text einer Buchstaben-Taste zusammen: Hauptbuchstabe plus – falls
 * vorhanden – das erste Sonderzeichen als klein abgedunkelter Hinweis.
 *
 * Refactoring (docs/REFACTORING_PLAN.md, P2): die Span-Komposition aus
 * [KeyboardBinder.applyLetterCase] herausgelöst. Vorher lagen die fünf
 * Lay-out-Werte als Magic Numbers im Binder; jetzt sind sie benannte Konstanten
 * an der einzigen Stelle, die sie verwendet, und der Binder orchestriert nur noch.
 *
 * Die Werte selbst sind unverändert – „Umziehen statt Umschreiben":
 * - Hauptbuchstabe: [MAIN_SIZE_SCALE] × Textgröße, um [MAIN_LIFT] angehoben
 * - Hinweis-Zeichen: [EXTRA_SIZE_SCALE] × Textgröße, um [EXTRA_LIFT] abgesenkt
 *   (FlorisBoard-Stil: rechts UNTEN, deshalb das starke Absenken)
 * - davor ein geschütztes Leerzeichen, damit der Hinweis nicht direkt klebt
 *
 * Die Farbe des Hinweises kommt von außen ([secondary]), weil sie aus dem Theme
 * stammt; diese Klasse kennt keine Themen-Mechanik.
 */
internal object LetterLabelComposer {

    /** Größenfaktor des Hauptbuchstabens. */
    const val MAIN_SIZE_SCALE = 0.85f

    /** Baseline-Versatz des Hauptbuchstabens (positiv = angehoben). */
    const val MAIN_LIFT = -0.2f

    /** Größenfaktor des Sonderzeichen-Hinweises. */
    const val EXTRA_SIZE_SCALE = 0.5f

    /** Baseline-Versatz des Hinweises (positiv = angehoben). */
    const val EXTRA_LIFT = 0.55f

    /** Geschütztes Leerzeichen als Trenner zwischen Buchstabe und Hinweis. */
    const val EXTRA_SEPARATOR = "\u00A0"

    /**
     * Baut das anzuzeigende Label.
     *
     * @param letter       anzuzeigender Hauptbuchstabe (Groß-/Kleinschreibung bereits entschieden)
     * @param letterExtras Sonderzeichen-Hinweise; nur [letterExtras].firstOrNull() wird angezeigt
     * @param secondary    Theme-Farbe (`text_secondary`) für den Hinweis
     * @return [Spanned]-Label mit den Spans – direkt als `Button.text` verwendbar
     */
    internal fun compose(
        letter: Char,
        letterExtras: List<String>,
        secondary: Int
    ): Spanned {
        val sb = SpannableStringBuilder(letter.toString())
        sb.setSpan(
            RelativeSizeSpan(MAIN_SIZE_SCALE), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        sb.setSpan(
            LiftSpan(MAIN_LIFT), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val firstExtra = letterExtras.firstOrNull()
        if (firstExtra != null) {
            val start = sb.length
            sb.append(EXTRA_SEPARATOR + firstExtra)
            sb.setSpan(
                RelativeSizeSpan(EXTRA_SIZE_SCALE), start, sb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.setSpan(
                ForegroundColorSpan(secondary), start, sb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.setSpan(
                LiftSpan(EXTRA_LIFT), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return sb
    }
}
