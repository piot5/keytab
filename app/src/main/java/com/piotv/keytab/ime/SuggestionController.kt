package com.piotv.keytab.ime

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.piotv.keytab.R
/**
 * Suggestion-Controller – Vorschlagsleiste, dynamische Tastengröße und
 * Likely-Highlights.
 *
 * Refactoring (docs/REFACTORING_PLAN.md Phase 4): aus [KeyTabImeService]
 * extrahiert. Kapselt:
 * - [setup]               – Vorschlag-Views binden, Engine laden, Skaler aufbauen
 * - [update]              – Vorschläge berechnen + Tasten skalieren + Likely-
 *                           Highlighting
 * - [applySuggestion]     – Vorschlag übernehmen (via Manager) + Shift reset
 * - Likely-Highlight-State ([likelyHighlighted])
 *
 * Der Emoji-Katalog-Browser (Seitennavigation über den Katalog, Katalog-Chrome
 * der Leiste, Raster-Popup) liegt in [SuggestionEmojiBrowser]; [update] hängt
 * die Katalog-Renderung über [emojiBrowser] ein.
 *
 * Verhalten bleibt bit-identisch („Umziehen statt Umschreiben").
 */
internal class SuggestionController(private val host: SuggestionHost) {

    /** Likely Highlighting: momentan hervorgehobene Tasten + deren Original-Background. */
    private val likelyHighlighted = mutableListOf<Pair<Button, android.graphics.drawable.Drawable>>()

    /** Emoji-Katalog-Browser (Seitennavigation, Katalog-Chrome, Raster-Popup). */
    private val emojiBrowser = SuggestionEmojiBrowser(host) { update() }

    /** Die 3 Vorschlags-Slots (gleiche Array-Referenz wie in [WordPredictionManager]). */
    private var slots: Array<TextView?> = arrayOfNulls(3)

    /** Likely-Highlights freigeben (beim Rebuild/Release der Tastatur). */
    fun clearLikelyHighlights() {
        likelyHighlighted.clear()
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
        slots = suggestionViews
        for (i in 0..2) {
            suggestionViews[i]?.setOnClickListener { v ->
                // Emoji-Katalog-Chip (Katalog-Browser aktiv)
                (v.getTag(SuggestionEngine.EMOJI_TAG) as? String)?.let { emoji ->
                    emojiBrowser.commitEmoji(emoji)
                    return@setOnClickListener
                }
                // Snippet-Chip (Satzanfang, ersetzt Wortvorschläge)
                (v.getTag(SuggestionEngine.SNIPPET_TAG) as? String)?.let { snip ->
                    commitSnippet(snip)
                    return@setOnClickListener
                }
                val word = v.tag as? String ?: return@setOnClickListener
                applySuggestion(word)
            }
        }
        // ☺-Katalog-Button (öffnet/blättert den Emoji-Katalog), ◀ (zurück zu den
        // Wortvorschlägen) und ▦ (ganzer Katalog als Tabelle) links in der Leiste.
        emojiBrowser.bind(root, suggestionViews)
        // ⌄/⇱ Tastatur ausblenden/maximieren (rechts in der Wortvorhersage-Zeile).
        // NUR im Editor-Tab sichtbar; Einblenden durch erneutes Tippen.
        // Symbol: ⇲ (nach außen-unten, dunkel) zum Maximieren, ⇱ (nach innen-oben)
        // zum Zurückblenden — wechselt je nach Hide-Zustand.
        val sugHide = root.findViewById<TextView>(R.id.sug_hide)
        sugHide?.setOnClickListener {
            host.haptic()
            host.hideKeyboard()
            // Symbol passend zum neuen Zustand setzen.
            val collapsed = root.getTag(R.id.sug_hide) as? Boolean ?: false
            sugHide.text = if (collapsed) "⇱" else "⇲"
        }
        sugHide?.visibility =
            if (host.isEditorTab()) View.VISIBLE else View.GONE
        // Initiales Symbol: ⇲ (nicht maximiert).
        sugHide?.text = "⇲"
        // Engine der aktiven Sprache laden (async, bei Wechsel: Reload)
        host.predictionManager?.loadEngine(language)
        // Nachhalten der Tasten-Nachbarschaft für den dynamischen Skaler
        host.keyScaler?.rebuildNeighbors()
        // Platzhalter: Leiste von Anfang an sichtbar (fixer Platz → kein Auf-/Zupoppen)
        val enabled = com.piotv.keytab.Prefs.of(host.context)
            .getBoolean(com.piotv.keytab.Prefs.KEY_SUGGESTIONS, true)
        root.findViewById<View>(R.id.suggestion_bar)?.visibility =
            if (enabled) View.VISIBLE else View.GONE
    }

    /** Vorschläge berechnen (Manager) + Tasten skalieren + Likely-Highlights. */
    fun update() {
        val bar = host.keyboardRoot?.findViewById<View>(R.id.suggestion_bar) ?: return
        val suggestionEnabled = com.piotv.keytab.Prefs.of(host.context)
            .getBoolean(com.piotv.keytab.Prefs.KEY_SUGGESTIONS, true)
        // Harte Sicherheitsregel (dieselbe wie beim Trail): in Passwort-Feldern
        // bzw. bei IME_FLAG_NO_PERSONALIZED_LEARNING wird nichts gerendert. Der
        // Emoji-Katalog wird dort ebenfalls geschlossen — er rendert an
        // [WordPredictionManager.updateSuggestions] vorbei, bliebe sonst also
        // sichtbar, wenn er vor dem Feldwechsel geöffnet wurde.
        val fieldAllowed = host.predictionManager?.isFieldProcessingAllowed() ?: true
        // Katalog-Browser aktiv? Dann Katalog-Seite statt Wortvorschläge rendern
        // (siehe [SuggestionEmojiBrowser.renderIfActive]).
        if (emojiBrowser.renderIfActive(bar, suggestionEnabled, fieldAllowed)) return
        host.predictionManager?.updateSuggestions(bar, suggestionEnabled)
        emojiBrowser.setPageChrome(bar, catalogMode = false)
        updateDynamicKeys()
    }

    /** Dynamische Tastengröße (Skaler-Modul) + Likely-Highlights. */
    private fun updateDynamicKeys() {
        val prefs = com.piotv.keytab.Prefs.of(host.context)
        val enabled = prefs.getBoolean(
            com.piotv.keytab.Prefs.KEY_DYNAMIC_KEYS, true)
        val pm = host.predictionManager
        host.keyScaler?.apply(
            (pm?.currentSuggestions).orEmpty(),
            pm?.currentTypedWord?.length ?: 0,
            enabled
        )
        updateLikelyKeys()
    }

    /**
     * Likely Highlighting: die wahrscheinlichste nächste Taste bekommt die
     * Hervorhebungs-Farbe (Pref [ThemePrefs.KEY_LIKELY]); wenn das getippte Wort
     * dem Top-Vorschlag entspricht (Wahrscheinlichkeit erreicht), gibt es einen
     * Puls-Effekt ([ThemePrefs.KEY_LIKELY_EFFECT]). Ohne Modus werden die
     * Highlights zurückgesetzt.
     */
    private fun updateLikelyKeys() {
        val prefs = com.piotv.keytab.Prefs.of(host.context)
        if (!ThemePrefs.likelyHighlighting(prefs)) { restoreLikelyKeys(); return }
        val pm = host.predictionManager
        val sugs = (pm?.currentSuggestions).orEmpty()
        val typed = (pm?.currentTypedWord).orEmpty()
        val next = LikelyHighlightLogic.nextChar(sugs, typed.length)
        restoreLikelyKeys()
        if (next != null) {
            val likelyColor = ThemePrefs.getColor(prefs, host.isDarkMode(), ThemePrefs.KIND_LIKELY,
                ThemePrefs.defaultColor(host.context, host.isDarkMode(), ThemePrefs.KIND_LIKELY))
            val dip = host.context.resources.displayMetrics.density
            for ((btn, c) in host.baseLetters) {
                if (c.lowercaseChar() == next) {
                    likelyHighlighted.add(btn to btn.background)
                    btn.background = GradientDrawable().apply {
                        cornerRadius = 8f * dip
                        setColor(likelyColor)
                    }
                }
            }
            if (ThemePrefs.likelyEffect(prefs) && LikelyHighlightLogic.completed(sugs, typed)) {
                likelyCompletionEffect(
                    host.baseLetters.filter { it.value.lowercaseChar() == next }.keys.toList(),
                    likelyColor)
            }
        }
    }

    /** Likely-Highlights zurücksetzen (Original-Backgrounds wiederherstellen). */
    private fun restoreLikelyKeys() {
        for ((btn, bg) in likelyHighlighted) {
            if (btn.isAttachedToWindow) btn.background = bg
        }
        likelyHighlighted.clear()
    }

    /** Zufriedenstellender Effekt: Farblitz + Scale-Puls + Haptik. */
    private fun likelyCompletionEffect(buttons: List<Button>, color: Int) {
        if (buttons.isEmpty()) return
        host.haptic()
        val dip = host.context.resources.displayMetrics.density
        for (b in buttons) {
            val saved = likelyHighlighted.firstOrNull { it.first === b }?.second ?: b.background
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

    /**
     * Snippet aus der Vorschlags-Leiste einfügen + in die History festhalten.
     * Läuft über [WordPredictionManager.applySuggestion] — dieselbe,
     * Auto-Korrektur-konsistente Route wie die Suggestion-Übernahme und der
     * Snippet-Tab (Editor/App-Routing via InputRouter; mehrzeilige
     * Snippets ohne Trailing-Space). [applySuggestion] löst danach [update]
     * aus, die dann wieder reguläre Wort-Vorschläge zeigt (Snippet beendet
     * das Satzanfangs-Fenster).
     */

    private fun commitSnippet(snippet: String) {
        val p = com.piotv.keytab.Prefs.of(host.context)
        val raw = p.getString(com.piotv.keytab.Prefs.KEY_RECENT_SNIPPETS, null)
        p.edit().putString(
            com.piotv.keytab.Prefs.KEY_RECENT_SNIPPETS,
            SuggestionEngine.recordRecent(raw, snippet)
        ).apply()
        host.predictionManager?.applySuggestion(snippet)
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

    /**
     * Swipe-Kandidaten in der Vorschlags-Leiste anzeigen (v0.11). Die Wörter
     * laufen als normale Tap-Vorschläge über die 3 Slots (kein neues Tag nötig –
     * `v.tag = word` wie bei Wortvorschlägen). Ein Tap übernimmt das Wort via
     * [applySuggestion] (Auto-Korrektur/Shift-Reset wie gehabt). Leerliste
     * versteckt die Slots nicht (Platzhalter bleibt).
     */
    fun showSwipeCandidates(words: List<String>) {
        val bar = host.keyboardRoot?.findViewById<View>(R.id.suggestion_bar) ?: return
        val enabled = com.piotv.keytab.Prefs.of(host.context)
            .getBoolean(com.piotv.keytab.Prefs.KEY_SUGGESTIONS, true)
        if (!enabled) return
        // Case-Matching: am Satzanfang (nach . ! ? oder Dokument-Anfang)
        // werden Swipe-Wörter großgeschrieben, auch wenn der Route-Kandidat
        // aus Kleinbuchstaben besteht.
        val pm = host.predictionManager
        val eng = pm?.engine
        val typed = pm?.currentTypedWord.orEmpty()
        val atSentenceStart = pm?.let {
            SuggestionEngine.isSentenceStartContext(
                it.textBeforeForSuggestions(16), typed)
        } ?: false
        emojiBrowser.close()
        emojiBrowser.setPageChrome(bar, catalogMode = false)
        for (i in 0..2) {
            val tv = slots[i] ?: continue
            val word = words.getOrNull(i)
            if (word != null) {
                tv.text = (eng?.matchCase(word, typed, sentenceStart = atSentenceStart)).orEmpty()
                tv.tag = word
                tv.setTag(SuggestionEngine.SNIPPET_TAG, null)
                tv.setTag(SuggestionEngine.EMOJI_TAG, null)
                tv.visibility = View.VISIBLE
            } else {
                tv.text = ""
                tv.tag = null
                tv.visibility = View.INVISIBLE
            }
        }
        // Likely-Highlights zurücksetzen (Swipe-Kandidaten haben keinen nächsten Buchstaben).
        restoreLikelyKeys()
    }
}
