package com.piotv.keytab.ime

/**
 * Reine Logik für Likely Highlighting (Android-frei, unit-testbar):
 *
 * - [nextChar]: das wahrscheinlichste nächste Zeichen (Top-Vorschlag an der
 *   Position des bereits getippten Worts) – genau dieses Zeichen bekommt die
 *   Hervorhebung.
 *
 * Der frühere „Zufriedenstellungs-Effekt" (Puls + Haptik bei „Vervollständigung
 * erreicht") ist in v0.18 entfernt worden: seine Bedingung war unerreichbar —
 * `completed` verlangte **gleiche** Wortlänge, wurde aber nur unter
 * `nextChar != null` (Vorschlag **länger**) geprüft. Siehe
 * `SuggestionController.updateLikelyKeys`.
 */
object LikelyHighlightLogic {

    /** Wahrscheinlichstes nächstes Zeichen oder null (kein Vorschlag/Ende). */
    fun nextChar(suggestions: List<SuggestionEngine.Suggestion>, typedLength: Int): Char? =
        suggestions.firstOrNull()?.word?.getOrNull(typedLength)?.lowercaseChar()
}
