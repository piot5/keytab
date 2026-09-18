package com.piotv.keytab.ime

import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.widget.Button

/**
 * Tippspur + Korrektur-Trace (Trail): die zuletzt geklickten Buchstaben werden
 * farblich markiert und mit jeder neuen Eingabe stufenweise weniger sichtbar,
 * bis sie verschwinden ([TrailLogic.alphaForStep]).
 *
 * Implementierung als **Foreground-Overlay** ([Button.setForeground]):
 * Der Button-Hintergrund (Theme, Rundung, Press-State) bleibt UNBERÜHRT –
 * damit kann der Trail die Taste weder unsichtbar machen noch dauerhaft
 * einfärben (früherer Bug: SRC_IN-Tint auf dem geteilten Hintergrund-Drawable).
 *
 * **Zwei Betriebsarten** (Details in [TrailLogic.TrailKind]):
 *  - Tippspur: Standardfarbe Blau #2196F3, pro Buchstabe verblassend.
 *  - Korrektur-Trace: grün = Wörterbuch kennt das Wort, rot = Fuzzy-Korrektur
 *    würde es ersetzen. Damit lässt sich die [SuggestionEngine] beim Tippen
 *    live beobachten. Optional (Pref `trail_trace`).
 *
 * **Sicherheit:** In Passwort-Feldern erscheint nichts, weil Android-IME-
 * Verträge (`inputType`-Variation, IME_FLAG_NO_PERSONALIZED_LEARNING) das
 * verbieten – siehe [TrailLogic.isTrailAllowed]. Der [editorInfo] wird über
 * [setEditorInfo] vom Service bei jedem Feldwechsel gesetzt.
 */
class TrailManager(
    private val prefs: SharedPreferences,
    private val baseLetters: Map<Button, Char>
) {
    /** Zuletzt geklickter Buchstabe (lowercase). null = kein aktiver Trail. */
    private var lastChar: Char? = null
    /** Maximale Schritte bis zur vollständigen Entfernung. */
    private var maxSteps: Int = TrailLogic.DEFAULT_STEPS
    /** Decay-Schritt pro markiertem Buchstaben (0 = voll sichtbar, maxSteps = weg).
     *  Bei jedem neuen Buchstaben werden alle bestehenden Schritte +1 (verblassen). */
    private val steps = mutableMapOf<Char, Int>()
    /** Betriebsart je Buchstabe (Tippspur oder Korrektur-Trace). */
    private val kinds = mutableMapOf<Char, TrailLogic.TrailKind>()
    /** Aktuelles Feld (Passwort-Schutz). null = unbekannt → Trail erlaubt. */
    private var editorInfo: android.view.inputmethod.EditorInfo? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            ThemePrefs.KEY_TRAIL -> {
                if (!trailEnabled()) clear()
                refresh()
            }
            ThemePrefs.KEY_TRAIL_COLOR, ThemePrefs.KEY_TRAIL_STEPS,
            ThemePrefs.KEY_TRAIL_TRACE -> {
                refresh()
                applyToKeyboard()
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    /**
     * Aktuelles Eingabefeld melden (aus `onStartInput`/`onStartInputView`).
     * Wechselt das Feld, wird die Spur geleert – damit ein Trace aus einem
     * harmlosen Feld nicht in ein Passwortfeld überläuft.
     */
    fun setEditorInfo(info: android.view.inputmethod.EditorInfo?) {
        if (editorInfo?.inputType != info?.inputType ||
            editorInfo?.imeOptions != info?.imeOptions
        ) {
            editorInfo = info
            clear()
        }
    }

    /** Wird aufgerufen, wenn ein Buchstabe geklickt wurde.
     * Decay des vorherigen Trails (alle Schritte +1), dann neuer Buchstabe mit Schritt 0.
     */
    fun snap(char: Char) {
        snap(char, TrailLogic.TrailKind.TYPED)
    }

    /** Wie [snap], aber mit expliziter Betriebsart (Korrektur-Trace). */
    fun snap(char: Char, kind: TrailLogic.TrailKind) {
        if (! trailEnabled()) return
        if (! TrailLogic.isTrailAllowed(editorInfo)) {
            // Passwortfeld (oder NO_PERSONALIZED_LEARNING): nichts anzeigen.
            if (steps.isNotEmpty()) clear()
            return
        }
        // Decay: über Snapshot iterieren (ConcurrentModificationException-Schutz),
        // Einträge über maxSteps werden entfernt.
        for (c in steps.keys.toList()) {
            val next = TrailLogic.nextStep(steps[c] ?: 0, maxSteps)
            if (next == null) {
                steps.remove(c)
                kinds.remove(c)
            } else {
                steps[c] = next
            }
        }
        // Neuen Buchstaben mit Schritt 0 hinzufügen.
        // Achtung: `kinds` wird pro Buchstabe gehalten, nicht pro Vorkommen.
        // Läuft für diesen Buchstaben bereits ein Korrektur-Trace, bleibt der
        // bestehen – sonst würde ein zweiter Tap desselben Buchstabens die
        // rote Trace-Färbung eines Wortes (z.B. `hauss`) blau überschreiben.
        val c = char.lowercaseChar()
        steps[c] = 0
        val existingKind = kinds[c]
        if (existingKind == null || existingKind == TrailLogic.TrailKind.TYPED) {
            kinds[c] = kind
        }
        lastChar = c
        applyToKeyboard()
    }

    /**
     * Korrektur-Trace für eine ganze Eingabe: färbt **alle** Buchstaben des
     * zuletzt getippten Wortes ein (grün = akzeptiert, rot = Korrektur nötig).
     * Nur aktiv, wenn die Trace-Pref an ist – sonst No-Op.
     *
     * **Atomar pro Wort:** Vor dem Setzen werden *alle* bisherigen Trace-Einträge
     * entfernt. Ohne das entstanden widersprüchliche Zustände, weil [steps] und
     * [kinds] pro **Buchstabe** speichern, nicht pro Vorkommen: tippt man
     * `hauss`, so setzte `traceWord("haus")` alle vier Buchstaben auf
     * [TrailLogic.TrailKind.CORRECTED]; der zweite `s`-Tap lief danach durch
     * [snap] und überschrieb `kinds['s']` mit [TrailLogic.TrailKind.TYPED],
     * während `steps['s'] = 0` stehen blieb. Ergebnis: `s` blau, `haus` rot –
     * und weil alle Vorkommen eines Buchstabens auf dieselbe Taste zeigen,
     * überlagerte sich die Färbung sichtbar.
     *
     * **Tippspur bleibt erhalten:** [TrailLogic.TrailKind.TYPED]-Einträge werden
     * nicht angetastet – der Trace ist eine Zusatzinformation, kein Ersatz für
     * den zuletzt gedrückten Buchstaben.
     */
    fun traceWord(typed: String, engine: SuggestionEngine?) {
        if (! trailEnabled() || ! traceEnabled()) return
        if (! TrailLogic.isTrailAllowed(editorInfo)) return
        val kind = TrailLogic.classifyTypedWord(typed, engine) ?: return
        // Bisherigen Trace dieses Wortes verwerfen – atomarer Neuzustand.
        clearTrace()
        // Nur den Korrektur-Fall sichtbar machen: er ist die Information, die
        // beim Tippen sonst nirgends erscheint.
        if (kind == TrailLogic.TrailKind.ACCEPTED) {
            applyToKeyboard()
            return
        }
        for (ch in typed.lowercase()) {
            if (ch.isLetter()) {
                steps[ch] = 0
                kinds[ch] = kind
            }
        }
        applyToKeyboard()
    }

    /**
     * Entfernt alle Trace-Einträge, lässt die Tippspur
     * ([TrailLogic.TrailKind.TYPED]) unangetastet. Grundlage für den atomaren
     * Wort-Trace in [traceWord].
     */
    private fun clearTrace() {
        for (c in kinds.keys.toList()) {
            if (kinds[c] != TrailLogic.TrailKind.TYPED) {
                kinds.remove(c)
                steps.remove(c)
            }
        }
    }

    /** Trail vollständig zurücksetzen (z.B. beim Theme-Wechsel oder View-Neuaufbau). */
    fun clear() {
        steps.clear()
        kinds.clear()
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
    @Suppress("UNUSED_PARAMETER")
    fun updateBaseLetters(newBaseLetters: Map<Button, Char>) {
        // Nichts zu tun – Overlays hängen an den Buttons selbst.
    }

    // ---------- Interne Hilfsmethoden ----------

    private fun trailEnabled(): Boolean = ThemePrefs.trailEnabled(prefs)
    private fun traceEnabled(): Boolean = ThemePrefs.trailTraceEnabled(prefs)
    private fun trailSteps(): Int = ThemePrefs.trailSteps(prefs)

    /** Farbe für einen spezifischen Decay-Schritt (0 = am deutlichsten, maxSteps = weg).
     *  Das Overlay liegt ÜBER dem Text → Alpha ist gedeckelt
     *  ([TrailLogic.ALPHA_LIMIT]), damit die Beschriftung immer lesbar bleibt.
     *  Die Grundfarbe hängt an der Betriebsart: Tippspur = Theme-Farbe,
     *  Korrektur-Trace = grün (akzeptiert) bzw. rot (korrigiert). */
    private fun colorForStep(stepForChar: Int, kind: TrailLogic.TrailKind): Int? {
        val alpha = TrailLogic.alphaForStep(stepForChar, maxSteps)
        if (alpha <= 0) return null
        val base = when (kind) {
            TrailLogic.TrailKind.CORRECTED -> ThemePrefs.trailCorrectedColor(prefs)
            TrailLogic.TrailKind.ACCEPTED -> ThemePrefs.trailAcceptedColor(prefs)
            TrailLogic.TrailKind.TYPED -> ThemePrefs.trailColor(prefs)
        }
        return ThemePrefs.withAlpha(base, alpha)
    }

    /** Wendet den Trail auf die Tastatur-Buttons an. */
    fun applyToKeyboard() {
        clearKeyboard()
        // Trail als FOREGROUND-Overlay: der Button-Hintergrund (Theme, Rundung,
        // Press-State) bleibt unberührt → Taste wird nie unsichtbar/tintiert.
        for ((btn, letter) in baseLetters) {
            val key = letter.lowercaseChar()
            val stepForChar = steps[key] ?: continue
            val kind = kinds[key] ?: TrailLogic.TrailKind.TYPED
            val color = colorForStep(stepForChar, kind) ?: continue
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
        const val DEFAULT_STEPS = TrailLogic.DEFAULT_STEPS
        /** Ab dieser Wortlänge bewertet der Korrektur-Trace (Engine-Grenze). */
        const val MIN_TRACE_WORD = 3
    }
}