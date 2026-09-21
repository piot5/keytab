package com.piotv.keytab.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Reine Trail-Logik (Android-frei bis auf die [EditorInfo]-Ableitung, JUnit-testbar):
 * Decay-Berechnung der Tippspur und die Klassifikation der Treffer-Markierung.
 *
 * Der Trail hat zwei Betriebsarten:
 *  - **Tippspur** ([TrailKind.TYPED]): der zuletzt gedrückte Buchstabe, in der
 *    Theme-Farbe, stufenweise verblassend (v0.9.7, unverändertes Verhalten).
 *  - **Treffer-Markierung** ([TrailKind.ACCEPTED]): wird grün, wenn das getippte
 *    Wort genau dem obersten Vorschlag entspricht — eine reine Bestätigung.
 *    **Kein Rot:** die frühere Warnfärbung („Fuzzy-Korrektur würde greifen")
 *    wurde entfernt; Tippfehler bleiben unmarkiert.
 *
 * **Sicherheit:** In Passwort-Feldern darf der Trail nicht erscheinen. Er würde
 * die `•`-Maskierung durch einen visuellen Seitenkanal unterlaufen
 * ([isTrailAllowed]). Die Regel ist hart im Code verankert, nicht per Pref.
 */
object TrailLogic {

    /** Betriebsart eines Trail-Eintrags. */
    enum class TrailKind {
        /** Normale Tippspur: zuletzt gedrückter Buchstabe, Theme-Farbe. */
        TYPED,

        /** Treffer-Markierung: getipptes Wort entspricht dem Top-Vorschlag → grün. */
        ACCEPTED
    }

    /** Default-Anzahl Decay-Stufen (v0.9.7). */
    const val DEFAULT_STEPS = 5

    /** Alpha-Deckel des Overlays: es liegt ÜBER dem Text, ~55 % hält die Beschriftung lesbar. */
    const val ALPHA_LIMIT = 0.55f

    /**
     * Alpha für einen Decay-Schritt. `step == 0` → maximal sichtbar (mit
     * [ALPHA_LIMIT] gedeckelt), `step >= maxSteps` → 0 (verschwunden).
     * Einzige Alpha-Formel des Trails – [ThemePrefs.trailColorWithAlpha] wurde
     * darauf abgestimmt.
     */
    fun alphaForStep(step: Int, maxSteps: Int): Int {
        if (maxSteps <= 0) return 0
        if (step >= maxSteps) return 0
        val raw = 255f * (maxSteps - step) / maxSteps
        return (raw * ALPHA_LIMIT).toInt().coerceIn(0, 255)
    }

    /**
     * Nächster Decay-Schritt. `null` = der Eintrag ist nach [maxSteps] Schritten
     * vollständig verschwunden und wird entfernt.
     */
    fun nextStep(currentStep: Int, maxSteps: Int): Int? {
        val next = currentStep + 1
        return if (next >= maxSteps) null else next
    }

    /**
     * Klassifiziert das getippte Wort für die Treffer-Markierung.
     * Nur ganze Wörter ab [TrailManager.MIN_TRACE_WORD] Zeichen werden bewertet –
     * für kürzere Wörter greift die Engine-Autokorrektur ohnehin nicht
     * ([SuggestionEngine.autoCorrect] verlangt `length >= 3`).
     *
     * Liefert nur noch [TrailKind.ACCEPTED] (Wort ist der Engine bekannt) oder
     * `null`. Ein Fuzzy-Korrektur-Kandidat ergibt **keine** Markierung mehr —
     * die frühere rote Warnfarbe wurde entfernt.
     *
     * @param typed das getippte Wort (ohne Trennzeichen)
     * @param engine laufende Engine oder null (noch nicht geladen)
     * @return [TrailKind.ACCEPTED] oder null (keine Markierung)
     */
    fun classifyTypedWord(typed: String, engine: SuggestionEngine?): TrailKind? {
        if (engine == null) return null
        if (typed.length < TrailManager.MIN_TRACE_WORD) return null
        if (!typed.all { it.isLetter() }) return null
        return if (engine.knowsWord(typed)) TrailKind.ACCEPTED else null
    }

    /**
     * Darf der Trail in diesem Feld überhaupt angezeigt werden?
     *
     * **Harte Sicherheitsregel:** Passwort-Felder sind immer ausgeschlossen –
     * auch wenn die Pref an ist. Ein Trail über einem `•`-Feld würde Buchstaben
     * und (über die Verblass-Reihenfolge) Teile der Eingabereihenfolge
     * preisgeben. Zusätzlich respektieren wir
     * [EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING], mit dem Apps der IME
     * ausdrücklich verbieten, sensible Eingaben zu verarbeiten.
     */
    fun isTrailAllowed(attribute: EditorInfo?): Boolean {
        attribute ?: return true
        if (attribute.imeOptions and
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        ) return false
        if (isPasswordField(attribute)) return false
        // Numerische PIN/Passwort-Varianten (TYPE_NUMBER_VARIATION_PASSWORD)
        if (attribute.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER &&
            attribute.inputType and InputType.TYPE_MASK_VARIATION ==
            InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) return false
        return true
    }

    /**
     * Passwort-Feld? Deckt Text- und Web-Passwort-Varianten ab – dieselbe
     * Menge, die [CapsLogic.wantsCapitalization] ausschließt.
     */
    fun isPasswordField(attribute: EditorInfo?): Boolean {
        attribute ?: return false
        if (attribute.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) {
            return false
        }
        return when (attribute.inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> true
            else -> false
        }
    }
}
