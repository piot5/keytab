package com.piotv.keytab.ime

import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.widget.Button

/**
 * Reine **View-/Farb-Application** des Trails: übersetzt den Decay-Zustand aus
 * [TrailManager] in Overlays auf den Tasten-Buttons.
 *
 * Hierher ausgelagert, weil das die Android-View-nahe Haelfte des Trails ist
 * (Button/GradientDrawable/DisplayMetrics) – [TrailManager] bleibt der reine
 * ZUSTANDSAUTOMAT (Decay-Schritte, Betriebsarten, Fade-Timer).
 *
 * **Warum Foreground statt Background:** Der Button-Hintergrund (Theme,
 * Rundung, Press-State) bleibt UNBERÜHRT – die Taste kann dadurch weder
 * unsichtbar werden noch dauerhaft eingefaerbt werden. Deshalb gibt es hier
 * auch keine Liste gesicherter Original-Hintergruende: es wird nichts
 * ueberschrieben, was man zuruecksetzen muesste.
 *
 * Der Painter ist zustandslos – Stufen und Betriebsarten werden pro Aufruf
 * uebergeben, damit [TrailManager] die einzige Quelle der Wahrheit bleibt.
 */
class TrailKeyboardPainter(
    private val prefs: SharedPreferences,
    private val baseLetters: Map<Button, Char>
) {

    private companion object {
        /** Eckenradius des Trail-Overlays in dp (umgesetzt ueber die Display-Density). */
        const val OVERLAY_CORNER_RADIUS_DP = 8f
    }

    /** Farbe fuer einen spezifischen Decay-Schritt (0 = am deutlichsten, maxSteps = weg).
     *  Das Overlay liegt UEBER dem Text → Alpha ist gedeckelt
     *  ([TrailLogic.ALPHA_LIMIT]), damit die Beschriftung immer lesbar bleibt.
     *  Die Grundfarbe haengt an der Betriebsart: Tippspur = Theme-Farbe,
     *  Treffer-Markierung = gruen. */
    private fun colorForStep(
        stepForChar: Int,
        kind: TrailLogic.TrailKind,
        maxSteps: Int
    ): Int? {
        val alpha = TrailLogic.alphaForStep(stepForChar, maxSteps)
        if (alpha <= 0) return null
        val base = when (kind) {
            TrailLogic.TrailKind.ACCEPTED -> ThemePrefs.trailAcceptedColor(prefs)
            TrailLogic.TrailKind.TYPED -> ThemePrefs.trailColor(prefs)
        }
        return ThemePrefs.withAlpha(base, alpha)
    }

    /**
     * Wendet die gespeicherten Decay-Stufen auf die Tastatur-Buttons an:
     * erst alle alten Overlays entfernen, dann je Buchstabe das Overlay
     * mit seiner Farbe setzen. Buchstaben ohne Eintrag bleiben unberuehrt.
     */
    fun apply(
        steps: Map<Char, Int>,
        kinds: Map<Char, TrailLogic.TrailKind>,
        maxSteps: Int
    ) {
        clear()
        for ((btn, letter) in baseLetters) {
            val color = overlayColorFor(letter, steps, kinds, maxSteps) ?: continue
            applyTrailToButton(btn, color)
        }
    }

    /** Overlay-Farbe fuer einen Buchstaben, oder `null`, wenn die Taste
     *  unberuehrt bleiben soll (kein Trail-Eintrag oder Alpha bereits weg). */
    private fun overlayColorFor(
        letter: Char,
        steps: Map<Char, Int>,
        kinds: Map<Char, TrailLogic.TrailKind>,
        maxSteps: Int
    ): Int? {
        val key = letter.lowercaseChar()
        val stepForChar = steps[key] ?: return null
        val kind = kinds[key] ?: TrailLogic.TrailKind.TYPED
        return colorForStep(stepForChar, kind, maxSteps)
    }

    private fun applyTrailToButton(btn: Button, color: Int) {
        // Halbtransparente, abgerundete Overlay-Flaeche UEBER der Taste.
        // Alpha steckt in der Trail-Farbe (step 0 = voll, maxSteps = weg);
        // Text bleibt voll sichtbar, da nur das Overlay halbtransparent ist.
        btn.foreground = GradientDrawable().apply {
            cornerRadius = OVERLAY_CORNER_RADIUS_DP * btn.context.resources.displayMetrics.density
            setColor(color)
        }
    }

    /** Entfernt alle Trail-Overlays (Original-Hintergruende bleiben unberuehrt). */
    fun clear() {
        for ((btn, _) in baseLetters) {
            btn.foreground = null
        }
    }
}
