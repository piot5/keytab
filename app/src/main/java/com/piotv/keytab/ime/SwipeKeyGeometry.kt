package com.piotv.keytab.ime

import android.widget.Button
import com.piotv.keytab.ime.SwipePathLogic.KeyCenter

/**
 * Geometrie-Helfer der Swipe-Eingabe: Tasten-Zentren, Treffer-Abfrage und
 * dp-basierte Schwellwerte.
 *
 * Ausgelagert aus [SwipeManager], damit die reine Koordinaten-Rechnung ohne
 * den Android-gebundenen Preview-Zustand (Knoten, Overlays, Prefs) lesbar und
 * separat nutzbar bleibt. Die Aufrufer in [SwipeManager] delegieren nur noch.
 */
internal object SwipeKeyGeometry {

    /**
     * Tasten-Zentren in **Fenster**-Koordinaten (für Sampling & Routen-Ableitung).
     *
     * **Konsistenz-Fix (Koordinaten-Mismatch):** Die Touch-Koordinaten aus
     * [KeyboardBinder] (`btn.getLocationInWindow(loc) + event.x/y`) sind
     * fenster-relativ. Früher wurden die Zentren hier auf den Container
     * (`kb_container`) bezogen (Subtraktion der Container-Position), während der
     * Touch fenster-relativ blieb — dadurch lag die Buchstaben-Erkennung um die
     * Position der Tab-Leiste daneben (falsche Buchstaben beim Wischen).
     * Zeichnen ([SwipeManager.drawEdges]) rechnet die Container-Relation separat um.
     */
    fun centersFor(baseLetters: Map<Button, Char>): List<KeyCenter> =
        baseLetters.mapNotNull { (btn, letter) ->
            val loc = IntArray(2)
            btn.getLocationInWindow(loc)
            KeyCenter(
                letter.lowercaseChar(),
                loc[0] + btn.width / 2f,
                loc[1] + btn.height / 2f
            )
        }

    /** Buchstabe an der fenster-relativen Position, oder null zwischen Tasten. */
    fun charAt(x: Float, y: Float, baseLetters: Map<Button, Char>): Char? =
        SwipePathLogic.charAt(x, y, centersFor(baseLetters))

    /** Display-Density der Tastatur (Fallback 1f, falls kein Button vorhanden). */
    fun baseDip(baseLetters: Map<Button, Char>): Float {
        val ctx = baseLetters.keys.firstOrNull()?.context ?: return 1f
        return ctx.resources?.displayMetrics?.density ?: 1f
    }

    /** Mindest-Wischstrecke in Pixeln (Schwelle in dp × Density). */
    fun minSwipeDistPx(baseLetters: Map<Button, Char>): Float =
        SwipePathLogic.SWIPE_THRESHOLD_DP * baseDip(baseLetters)
}
