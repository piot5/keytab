package com.piotv.keytab.ime

/**
 * Reine Logik für den Gaming-Modus (Android-frei, unit-testbar):
 *
 * - [nextChar]: das wahrscheinlichste nächste Zeichen (Top-Vorschlag an der
 *   Position des bereits getippten Worts) – genau das Zeichen bekommt die
 *   Gaming-Färbung.
 * - [completed]: „Vervollständigung erreicht" – das getippte Wort entspricht
 *   exakt dem Top-Vorschlag (case-insensitiv) → Trigger für den
 *   Zufriedenstellungs-Effekt (Puls + Haptik).
 */
object GamingLogic {

    /** Wahrscheinlichstes nächstes Zeichen oder null (kein Vorschlag/Ende). */
    fun nextChar(suggestions: List<SuggestionEngine.Suggestion>, typedLength: Int): Char? =
        suggestions.firstOrNull()?.word?.getOrNull(typedLength)?.lowercaseChar()

    /** Wahrscheinlichkeit erreicht: getipptes Wort == Top-Vorschlag. */
    fun completed(suggestions: List<SuggestionEngine.Suggestion>, typed: String): Boolean {
        val top = suggestions.firstOrNull() ?: return false
        return typed.isNotEmpty() && top.word.equals(typed, ignoreCase = true)
    }
}