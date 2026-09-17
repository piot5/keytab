package com.piotv.keytab.ime

import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.widget.Button

/**
 * Tippspur-Effekt (Trail): die zuletzt geklickten Buchstaben werden farblich
 * markiert und mit jeder neuen Eingabe stufenweise weniger sichtbar, bis sie
 * ganz verschwinden ([maxSteps] Schritte).
 *
 * Implementierung als **Foreground-Overlay** ([Button.setForeground]):
 * Der Button-Hintergrund (Theme, Rundung, Press-State) bleibt UNBERÜHRT –
 * damit kann der Trail die Taste weder unsichtbar machen noch dauerhaft
 * einfärben (früherer Bug: SRC_IN-Tint auf dem geteilten Hintergrund-Drawable).
 *
 * Die Farbe kommt aus den Theme-Einstellungen (Standard = Blau #2196F3),
 * der Alpha-Wert liefert das stufenweise Verblassen. Optional (Pref `trail_enabled`).
 */
class TrailManager(
    private val prefs: SharedPreferences,
    private val baseLetters: Map<Button, Char>
) {
    /** Zuletzt geklickter Buchstabe (lowercase). null = kein aktiver Trail. */
    private var lastChar: Char? = null
    /** Maximale Schritte bis zur vollständigen Entfernung. */
    private var maxSteps: Int = 5
    /** Decay-Schritt pro markiertem Buchstaben (0 = voll sichtbar, maxSteps = weg).
     *  Bei jedem neuen Buchstaben werden alle bestehenden Schritte +1 (verblassen). */
    private val steps = mutableMapOf<Char, Int>()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            ThemePrefs.KEY_TRAIL -> {
                if (!trailEnabled()) clear()
                refresh()
            }
            ThemePrefs.KEY_TRAIL_COLOR, ThemePrefs.KEY_TRAIL_STEPS -> {
                refresh()
                applyToKeyboard()
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    /** Wird aufgerufen, wenn ein Buchstabe geklickt wurde.
     * Decay des vorherigen Trails (alle Schritte +1), dann neuer Buchstabe mit Schritt 0.
     */
    fun snap(char: Char) {
        if (! trailEnabled()) return
        // Decay: über Snapshot iterieren (ConcurrentModificationException-Schutz),
        // Einträge über maxSteps werden entfernt.
        for (c in steps.keys.toList()) {
            val newStep = (steps[c] ?: 0) + 1
            if (newStep > maxSteps) steps.remove(c) else steps[c] = newStep
        }
        // Neuen Buchstaben mit Schritt 0 hinzufügen
        val c = char.lowercaseChar()
        steps[c] = 0
        lastChar = c
        applyToKeyboard()
    }

    /** Trail vollständig zurücksetzen (z.B. beim Theme-Wechsel oder View-Neuaufbau). */
    fun clear() {
        steps.clear()
        lastChar = null
        clearKeyboard()
    }

    /** Aktualisiert die Trail-Einstellungen aus den Prefs (z.B. nach Theme-Änderung). */
    fun refresh() {
        maxSteps = trailSteps()
    }

    /** Aktualisiert die BaseLetters-Map (z.B. nach Language-Wechsel oder View-Neuaufbau).
     *  Da der Trail nur Foreground-Overlays nutzt, müssen keine Hintergründe
     *  verwaltet werden; alte Overlays auf entfernten Buttons sind unbeobachtbar. */
    fun updateBaseLetters(newBaseLetters: Map<Button, Char>) {
        // Nichts zu tun – Overlays hängen an den Buttons selbst.
    }

    // ---------- Interne Hilfsmethoden ----------

    private fun trailEnabled(): Boolean = ThemePrefs.trailEnabled(prefs)
    private fun trailSteps(): Int = ThemePrefs.trailSteps(prefs)
    /** Farbe für einen spezifischen Decay-Schritt (0 = am deutlichsten, maxSteps = weg).
     *  Das Overlay liegt ÜBER dem Text → Alpha wird auf ~55 % gedeckelt,
     *  damit die Beschriftung immer lesbar bleibt. */
    private fun colorForStep(stepForChar: Int): Int? {
        if (stepForChar > maxSteps) return null
        val raw = 255f * (maxSteps - stepForChar) / maxSteps
        val alpha = (raw * 0.55f).toInt().coerceIn(0, 255)
        return ThemePrefs.withAlpha(ThemePrefs.trailColor(prefs), alpha)
    }

    /** Wendet den Trail auf die Tastatur-Buttons an. */
    fun applyToKeyboard() {
        clearKeyboard()
        // Trail als FOREGROUND-Overlay: der Button-Hintergrund (Theme, Rundung,
        // Press-State) bleibt unberührt → Taste wird nie unsichtbar/tintiert.
        for ((btn, letter) in baseLetters) {
            val stepForChar = steps[letter.lowercaseChar()] ?: continue
            val color = colorForStep(stepForChar) ?: continue
            applyTrailToButton(btn, color)
        }
    }

    private fun applyTrailToButton(btn: Button, color: Int) {
        // Halbtransparente, abgerundete Overlay-Fläche ÜBER der Taste.
        // Alpha steckt in der Trail-Farbe (step 0 = voll, maxSteps = weg);
        // Text bleibt voll sichtbar, da nur das Overlay halbtransparent ist.
        btn.foreground = GradientDrawable().apply {
            cornerRadius = 8f * btn.context.resources.displayMetrics.density
            setColor(color)
        }
    }

    /** Entfernt alle Trail-Overlays (Original-Hintergründe bleiben unberührt). */
    private fun clearKeyboard() {
        for ((btn, _) in baseLetters) {
            btn.foreground = null
        }
    }

    companion object {
        /** Standard-Max-Steps (v0.9.7). */
        const val DEFAULT_STEPS = 5
    }
}