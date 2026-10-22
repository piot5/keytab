package com.piotv.keytab

/**
 * Was im jeweiligen Test passieren soll — Text für die Anzeige im Test-Runner.
 *
 * Bewusst gebündelt statt im Test verstreut: eine Quelle für den Beobachter,
 * die sich zusammen mit dem Testnamen pflegen lässt. Fehlt ein Eintrag, zeigt
 * der Runner das sichtbar an (kein stiller Fallback).
 */
object TestExpectations {
    private val texts = mapOf(
        "kompletterTippdurchlauf" to
            "Ein Durchlauf über alle Tippfunktionen: langer Standardtext, Shift " +
                "(Großbuchstabe), Zahlenreihe, Sonderzeichen-Ebene (?123), " +
                "Long-Press-Auswahl am Buchstaben, Backspace, Leertaste, Enter, " +
                "TAB und das Passwortfeld (tippt normal, keine Vorschläge).",
        "tabWechselUndVollbildRendertUndTipptWeiter" to
            "Inhalts-Tabs (Files/Editor) rendern, Vollbild im Editor wird per " +
                "Tab-Wechsel verlassen (Normalhöhe wiederhergestellt), Tastatur tippt weiter.",
        "swipeGesteCrashtNichtUndTipptWeiter" to
            "Wisch-Geste über die Buchstaben-Reihe (Swipe aktiviert) crasht nicht, " +
                "die Tastatur tippt danach weiter.",
        "trailRendertOhneCrash" to
            "Mit aktivem Trail mehrere Buchstaben tippen — kein Crash im Trail-Pfad."
    )

    /** Erwartungstext zum Testnamen (sichtbarer Hinweis, wenn keiner hinterlegt ist). */
    fun of(testName: String): String = texts[testName]
        ?: "Keine Beschreibung hinterlegt — bitte in TestExpectations.of ergaenzen."
}
