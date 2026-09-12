package com.piotv.keytab.ime

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.piotv.keytab.R

/**
 * Suggestion-Controller – Vorschlagsleiste, dynamische Tastengröße und
 * Gaming-Highlights.
 *
 * Refactoring (docs/REFACTORING_PLAN.md Phase 4): aus [KeyTabImeService]
 * extrahiert. Kapselt:
 * - [setup]               – Vorschlag-Views binden, Engine laden, Skaler aufbauen
 * - [update]              – Vorschläge berechnen + Tasten skalieren + Gaming
 * - [applySuggestion]     – Vorschlag übernehmen (via Manager) + Shift reset
 * - Gaming-Highlight-State ([gamingHighlighted])
 *
 * Verhalten bleibt bit-identisch („Umziehen statt Umschreiben").
 */
internal class SuggestionController(private val host: KeyboardHost) {

    /** Gaming-Modus: momentan hervorgehobene Tasten + deren Originale-Background. */
    private val gamingHighlighted = mutableListOf<Pair<Button, android.graphics.drawable.Drawable>>()

    /** Gaming-Highlights freigeben (beim Rebuild/Release der Tastatur). */
    fun clearGaming() {
        gamingHighlighted.clear()
    }

    /**
     * Vorschlag-Views binden, Click-Listener setzen, Engine laden und
     * Skaler-Nachbarschaft aufbauen. Leiste-Visibility aus den Einstellungen.
     *
     * @param root Inflated Tastatur-Root
     * @param suggestionViews Array (gleiche Referenz wie an [WordPredictionManager] übergeben)
     * @param language Aktive Sprache (für Engine-Laden)
     */
    fun setup(root: View, suggestionViews: Array<TextView?>, language: KeyboardLanguage) {
        suggestionViews[0] = root.findViewById(R.id.sug_1)
        suggestionViews[1] = root.findViewById(R.id.sug_2)
        suggestionViews[2] = root.findViewById(R.id.sug_3)
        for (i in 0..2) {
            suggestionViews[i]?.setOnClickListener {
                val word = it?.tag as? String ?: return@setOnClickListener
                applySuggestion(word)
            }
        }
        // Engine der aktiven Sprache laden (async, bei Wechsel: Reload)
        host.predictionManager?.loadEngine(language)
        // Nachhalten der Tasten-Nachbarschaft für den dynamischen Skaler
        host.keyScaler?.rebuildNeighbors()
        // Platzhalter: Leiste von Anfang an sichtbar (fixer Platz → kein Auf-/Zupoppen)
        val enabled = host.context.getSharedPreferences(
            com.piotv.keytab.Prefs.FILE, android.content.Context.MODE_PRIVATE)
            .getBoolean(com.piotv.keytab.Prefs.KEY_SUGGESTIONS, true)
        root.findViewById<View>(R.id.suggestion_bar)?.visibility =
            if (enabled) View.VISIBLE else View.GONE
    }

    /** Vorschläge berechnen (Manager) + Tasten skalieren + Gaming-Highlights. */
    fun update() {
        val bar = host.keyboardRoot?.findViewById<View>(R.id.suggestion_bar) ?: return
        val suggestionEnabled = host.context.getSharedPreferences(
            com.piotv.keytab.Prefs.FILE, android.content.Context.MODE_PRIVATE)
            .getBoolean(com.piotv.keytab.Prefs.KEY_SUGGESTIONS, true)
        host.predictionManager?.updateSuggestions(bar, suggestionEnabled)
        updateDynamicKeys()
    }


    /** Dynamische Tastengröße (Skaler-Modul) + Gaming-Highlights. */
    private fun updateDynamicKeys() {
        val prefs = host.context.getSharedPreferences(
            com.piotv.keytab.Prefs.FILE, android.content.Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(
            com.piotv.keytab.Prefs.KEY_DYNAMIC_KEYS, true)
        val pm = host.predictionManager
        host.keyScaler?.apply(
            pm?.currentSuggestions ?: emptyList(),
            pm?.currentTypedWord?.length ?: 0,
            enabled
        )
        updateGamingKeys()
    }

    /**
     * Gaming-Modus: die wahrscheinlichste nächste Taste bekommt die Gaming-Farbe
     * (Pref [ThemePrefs.KEY_GAMING]); wenn das getippte Wort dem Top-Vorschlag
     * entspricht (Wahrscheinlichkeit erreicht), gibt es einen Puls-Effekt
     * ([ThemePrefs.KEY_GAMING_EFFECT]). Ohne Modus werden Highlights zurückgesetzt.
     */
    private fun updateGamingKeys() {
        val prefs = host.context.getSharedPreferences(
            com.piotv.keytab.Prefs.FILE, android.content.Context.MODE_PRIVATE)
        if (!ThemePrefs.gamingMode(prefs)) { restoreGamingKeys(); return }
        val pm = host.predictionManager
        val sugs = pm?.currentSuggestions ?: emptyList()
        val typed = pm?.currentTypedWord ?: ""
        val next = GamingLogic.nextChar(sugs, typed.length)
        restoreGamingKeys()
        if (next != null) {
            val gamingColor = ThemePrefs.getColor(prefs, host.isDarkMode(), ThemePrefs.KIND_GAMING,
                ThemePrefs.defaultColor(host.context, host.isDarkMode(), ThemePrefs.KIND_GAMING))
            val dip = host.context.resources.displayMetrics.density
            for ((btn, c) in host.baseLetters) {
                if (c.lowercaseChar() == next) {
                    gamingHighlighted.add(btn to btn.background)
                    btn.background = GradientDrawable().apply {
                        cornerRadius = 8f * dip
                        setColor(gamingColor)
                    }
                }
            }
            if (ThemePrefs.gamingEffect(prefs) && GamingLogic.completed(sugs, typed)) {
                gamingCompletionEffect(
                    host.baseLetters.filter { it.value.lowercaseChar() == next }.keys.toList(),
                    gamingColor)
            }
        }
    }

    /** Gaming-Highlights zurücksetzen (Original-Backgrounds wiederherstellen). */
    private fun restoreGamingKeys() {
        for ((btn, bg) in gamingHighlighted) {
            if (btn.isAttachedToWindow) btn.background = bg
        }
        gamingHighlighted.clear()
    }

    /** Zufriedenstellender Effekt: Farblitz + Scale-Puls + Haptik. */
    private fun gamingCompletionEffect(buttons: List<Button>, color: Int) {
        if (buttons.isEmpty()) return
        host.haptic()
        val dip = host.context.resources.displayMetrics.density
        for (b in buttons) {
            val saved = gamingHighlighted.firstOrNull { it.first === b }?.second ?: b.background
            val flash = GradientDrawable().apply {
                cornerRadius = 8f * dip
                setColor(Color.argb(230,
                    Color.red(color), Color.green(color), Color.blue(color)))
            }
            b.background = flash
            b.animate().scaleX(1.3f).scaleY(1.3f).setDuration(130).withEndAction {
                b.animate().scaleX(1f).scaleY(1f).setDuration(170).start()
                b.background = saved
            }.start()
        }
    }

    /** Vorschlag übernehmen (delegiert an Manager) + Shift zurücksetzen. */
    fun applySuggestion(word: String) {
        host.haptic()
        host.predictionManager?.applySuggestion(word)
        if (host.isShifted() && !host.isCapsLock()) {
            host.consumeSingleShift()
        }
        update()
    }
}
