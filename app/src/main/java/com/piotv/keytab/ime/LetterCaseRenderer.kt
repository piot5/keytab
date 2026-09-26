package com.piotv.keytab.ime

/**
 * Pure Text-Aufbereitung der Buchstaben-Tasten: Groß-/Kleinschreibung und die
 * Liste der Sonderzeichen-Hinweise (Android-frei, JUnit-testbar).
 *
 * Refactoring (docs/REFACTORING_PLAN.md Phase 3): die reine Zeichen-Logik aus
 * [KeyboardBinder.applyLetterCase] und [KeyboardBinder.tapLetter] extrahiert.
 * Die Spans (Größe/Lift/Farbe) bleiben im Binder – sie brauchen den sekundären
 * Farbwert aus dem Theme.
 *
 * Verhalten bleibt bit-identisch („Umziehen statt Umschreiben").
 */
internal object LetterCaseRenderer {

    /**
     * Anzuzeigender Buchstabe für [base] unter [upper].
     * ß hat kein echtes Großbuchstaben per [Char.uppercaseChar] → ẞ als Sonderfall.
     */
    internal fun letterFor(base: Char, upper: Boolean): Char = when {
        upper && base == 'ß' -> 'ẞ'
        upper -> base.uppercaseChar()
        else -> base.lowercaseChar()
    }

    /**
     * Sonderzeichen-Hinweise für [base] unter [upper] aus [extras].
     * Interpunktion-Extras gibt es nur am lowercase-Key → Shift fällt darauf zurück;
     * bei ß gibt es keine Hinweise.
     */
    internal fun extrasFor(base: Char, upper: Boolean, extras: Map<Char, List<String>>): List<String> = when {
        upper && base == 'ß' -> emptyList()
        upper -> (extras[base.uppercaseChar()] ?: extras[base]).orEmpty()
        else -> extras[base].orEmpty()
    }
}
