package com.piotv.keytab.ime

/**
 * Pure Text-Aufbereitung der Buchstaben-Tasten: Groß-/Kleinschreibung und die
 * Liste der Sonderzeichen-Hinweise (Android-frei, JUnit-testbar).
 *
 * Refactoring (Phase 3): die reine Zeichen-Logik aus
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
     * Sonderzeichen-Hinweise für [base] aus [extras] – **unabhängig von Shift/Caps**.
     *
     * v0.20: Vorher lieferte der Shift-Zustand die Großbuchstaben-Reihe
     * (`extras['A']`), während Tasten ohne Großbuchstaben-Eintrag auf die
     * Kleinbuchstaben-Reihe zurückfielen. Dieselbe Taste zeigte damit je nach
     * Shift etwas anderes („Ä …“ statt „ä …“, bei `m`/`r` aber weiter „&“/„.“).
     * Jetzt gilt überall dieselbe Reihe an der Basistaste; [extras] führt deshalb
     * nur noch Basis-Tasten (Kleinbuchstaben + Interpunktion).
     */
    internal fun extrasFor(base: Char, extras: Map<Char, List<String>>): List<String> =
        extras[base].orEmpty()
}
