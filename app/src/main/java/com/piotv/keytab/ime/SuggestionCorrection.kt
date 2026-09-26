package com.piotv.keytab.ime

/**
 * Reine, Android- und engine-freie Korrektur-/Vergleichslogik der Wortvorhersage
 * (aus [SuggestionEngine] ausgelagert). Enthält ausschließlich Funktionen, die
 * keinen Zugriff auf `baseFreq`, `userFreq`, `bigrams` oder die Char-Indizes
 * brauchen — dadurch bleiben sie ohne Dictionary-Instanz testbar.
 *
 * [SuggestionEngine] delegiert diese Logik eins zu eins hierher, damit alle
 * Aufrufer (inkl. der bestehenden Tests) unverändert weiter funktionieren.
 */
internal object SuggestionCorrection {

    /** Boost-Faktor für typische Satzanfangs­wörter. */
    private const val START_BOOST = 1.15

    /** Abwertungsfaktor für reine Funktions­wörter am Satzanfang. */
    private const val WEAK_START_PENALTY = 0.75

    /**
     * Groß-/Kleinschreibung des Getippten auf den Vorschlag übertragen.
     * @param sentenceStart true am Satzanfang → Vorschlag großschreiben,
     *   auch wenn der Nutzer noch ein Kleinbuchstabe getippt hat
     */
    fun matchCase(suggestion: String, typed: String, sentenceStart: Boolean = false): String =
        if (typed.isEmpty() || typed[0].isUpperCase() || sentenceStart) {
            suggestion.replaceFirstChar { it.uppercase() }
        } else suggestion

    /**
     * Satzanfang-Boost: Am Satzanfang werden typische Satzanfangs­wörter
     * (Nomen, Verben, häufige Einleitungen) bevorzugt, reine Funktions­wörter
     * (Kurzpräpositionen/Konjunktionen), die selten allein einen Satz
     * eröffnen, leicht abgewertet. Der Faktor ist absichtlich moderat,
     * damit seltene, aber wirklich passende Bigramme nicht ausgeblendet werden.
     */
    fun sentenceStartBoost(word: String): Double =
        if (WEAK_STARTERS.contains(word.lowercase())) WEAK_START_PENALTY else START_BOOST

    /**
     * Reine Funktionswörter, die typischerweise nicht als Satzanfang dienen.
     * Statisch, weil der Set pro Aufruf sonst neu allokiert würde
     * (wird pro Vorschlagsrunde mehrfach aufgerufen).
     */
    private val WEAK_STARTERS = setOf(
        // Deutsch: Kurzpräpositionen / -konjunktionen
        "in", "auf", "von", "zu", "mit", "bei", "nach", "vor", "um",
        "aus", "seit", "durch", "für", "gegen", "ohne", "per",
        // Englisch: Kurzpräpositionen / Artikeln / Konjunktionen
        "of", "to", "in", "on", "at", "by", "for", "and", "or", "but", "the"
    )
}
