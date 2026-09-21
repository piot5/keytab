package com.piotv.keytab.ime

import android.content.Context
import android.os.Handler
import android.view.View
import android.widget.TextView
import com.piotv.keytab.MainActivity
import com.piotv.keytab.R
import com.piotv.keytab.ime.KeyboardLanguage
import com.piotv.keytab.ime.Languages
import java.util.concurrent.Executor

/**
 * Modul für Wortvorhersage.
 *
 * Kapselt: SuggestionEngine-Laden (async, sprachabhängig), getippten Text-State,
 * Vorschlag-Ausgabe in der Leiste und Lernen abgeschlossener Wörter.
 * Eingabe-Operationen (Einfügen/Löschen) delegiert der Service via [inputOps].
 */
class WordPredictionManager(
    private val context: Context,
    private val ioExecutor: Executor,
    private val mainHandler: Handler,
    private val suggestionViews: Array<TextView?>,
    private val inputOps: InputOperations
) {
    /** Eingabe-Operationen, die der Service bereitstellt (kontextabhängig: App/Editor/Terminal). */
    interface InputOperations {
        fun deleteBefore(count: Int)
        /** Löscht via KEYCODE_DEL-Key-Events (Fallback für Felder ohne deleteSurroundingText). */
        fun deleteBeforeKeys(count: Int)
        /** Liest bis zu [count] Zeichen vor dem Cursor (für die Vorher-Prüfung). */
        fun textBefore(count: Int): String
        fun insert(text: String)
        fun commitToApp(text: String)
    }

    var engine: SuggestionEngine? = null
        private set
    private var engineLoading = false
    private var engineLanguage: String? = null

        var currentTypedWord = ""
        private set
    private var prevTypedWord: String? = null
    var currentSuggestions: List<SuggestionEngine.Suggestion> = emptyList()
        private set

    /** Text vor dem Cursor (für externe Module wie SuggestionController). */
    fun textBeforeForSuggestions(count: Int): String = inputOps.textBefore(count)

    /** Lädt die Engine für die aktive Sprache (async). Bei Sprachwechsel: Reload. */
    fun loadEngine(language: KeyboardLanguage, forceReload: Boolean = false) {
        if (engine != null && !forceReload && language.code == engineLanguage) return
        engine = null
        engineLoading = false
        engineLanguage = language.code
        engineLoading = true
        ioExecutor.execute {
            val words = mutableListOf<Pair<String, Int>>()
            try {
                context.assets.open(language.assetName).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val sp = line.trim().split(' ')
                        if (sp.size == 2) {
                            val f = sp[1].toIntOrNull() ?: continue
                            words.add(sp[0] to f)
                        }
                    }
                }
            } catch (_: Exception) { /* Asset fehlt: nur gelernte Wörter */ }
            val loaded = SuggestionEngine(words)
            val saved = com.piotv.keytab.Prefs.of(context)
                .getString(com.piotv.keytab.Prefs.KEY_USER_DICT, null)
            if (saved != null) loaded.restoreUserDict(saved)
            engine = loaded
            engineLoading = false
            mainHandler.post { onEngineReady?.invoke() }
        }
    }

    private var onEngineReady: (() -> Unit)? = null
    fun setOnEngineReady(callback: (() -> Unit)?) { onEngineReady = callback }

            /** Berechnet Vorschläge und aktualisiert die Leiste. */
    fun updateSuggestions(bar: View?, enabled: Boolean) {
        if (bar == null) return
        if (!enabled) { bar.visibility = View.GONE; return }
        val contextBefore = inputOps.textBefore(16)
        // Snippet-Leiste: nur ausblasen, wenn **kein** Wort getippt wird
        // (sentenceStart ist strenger: typedWord != null → false).
        if (SuggestionEngine.sentenceStart(contextBefore, currentTypedWord)) {
            val snips = SuggestionEngine.recentSnippets(
                com.piotv.keytab.Prefs.of(context)
                    .getString(com.piotv.keytab.Prefs.KEY_RECENT_SNIPPETS, null)
            )
            if (snips.isNotEmpty()) {
                renderSnippetBar(snips)
                return
            }
        }
        // Satz-Struktur-Berücksichtigung für Wortvorschläge: auch während des
        // Tippens (isSentenceStartContext entfernt das getippte Wort aus dem
        // Kontext) — am Satzanfang werden Satzanfangs­wörter bevorzugt und
        // Vorschläge großgeschrieben.
        val atSentenceStart = SuggestionEngine.isSentenceStartContext(
            contextBefore, currentTypedWord)
        val eng = engine
        eng?.emojiEnabled = com.piotv.keytab.Prefs.of(context)
            .getBoolean(com.piotv.keytab.Prefs.KEY_EMOJI_SUGGESTIONS, false)
        val list = if (eng == null) emptyList() else {
            try { eng.suggest(currentTypedWord, prevTypedWord, sentenceStart = atSentenceStart) }
            catch (_: Exception) { emptyList() }
        }
        if (list.isEmpty()) {
            currentSuggestions = emptyList()
            for (i in 0..2) {
                suggestionViews[i]?.apply {
                    visibility = View.INVISIBLE; tag = null
                    setTag(SuggestionEngine.SNIPPET_TAG, null)
                }
            }
            bar.visibility = View.VISIBLE
            return
        }
        for (i in 0..2) {
            val tv = suggestionViews[i] ?: continue
            val sug = list.getOrNull(i)
            if (sug == null) {
                tv.visibility = View.INVISIBLE; tv.tag = null
                tv.setTag(SuggestionEngine.SNIPPET_TAG, null)
            } else {
                tv.visibility = View.VISIBLE
                tv.text = (eng?.matchCase(sug.word, currentTypedWord, sentenceStart = atSentenceStart)).orEmpty()
                tv.tag = sug.word
                tv.setTag(SuggestionEngine.SNIPPET_TAG, null)
            }
        }
        currentSuggestions = list
        bar.visibility = View.VISIBLE
    }

    /**
     * Rendert bis zu 3 zuletzt eingefügte Snippets als wählbare Chips in die
     * Vorschlags-Leiste (ersetzt Wortvorschläge am Satzanfang). Der Klick-Listener
     * wird in [SuggestionController.setup] über [SuggestionEngine.SNIPPET_TAG] auf
     * Snippet‑Commit umgeleitet — kein Wort‑Lern‑Overhead, kein Trailing‑Space.
     */
    private fun renderSnippetBar(snippets: List<String>) {
        for (i in 0..2) {
            val tv = suggestionViews[i] ?: continue
            val snip = snippets.getOrNull(i)
            if (snip == null) {
                tv.visibility = View.INVISIBLE
                tv.tag = null
                tv.setTag(SuggestionEngine.SNIPPET_TAG, null)
            } else {
                tv.text = snip
                tv.tag = null                          // Snippet: Kennzeichnung via SNIPPET_TAG
                tv.setTag(SuggestionEngine.SNIPPET_TAG, snip)
                tv.visibility = View.VISIBLE
            }
        }
        currentSuggestions = emptyList()               // Key-Skalierung nicht an Top-3 knüpfen
    }

    /**
     * Setzt einen Vorschlag ein (Editor/Terminal/App via inputOps).
     *
     * **Bugfix Verdopplung/Zerstörung:** Die Ersetzung wird über
     * [SuggestionReplaceLogic] entschieden und das Löschen **verifiziert**.
     * Früher wurde der Vorschlag angehängt statt ersetzt, wenn der Vergleich
     * „steht das getippte Wort vor dem Cursor?“ nur scheinbar fehlschlug
     * (Unicode-Normalisierung/abweichende Feld-Repräsentation) → der Text
     * verdoppelte sich. Jetzt gilt: „anhängen ohne löschen“ nur, wenn der
     * gelesene Kontext exakt `typed.length` Zeichen lang **und** ein anderes
     * Wort ist; und es wird **nie eingefügt, wenn das Löschen nicht
     * nachweislich geklappt hat**.
     */
            fun applySuggestion(word: String) {
        val typed = currentTypedWord
        val atSentenceStart = SuggestionEngine.isSentenceStartContext(
            inputOps.textBefore(16), typed)
        val fullWord = engine?.matchCase(word, typed, sentenceStart = atSentenceStart) ?: word
        // Etwas weiter lesen als das Wort lang ist (NFC/NFD-Längen, siehe
        // SuggestionReplaceLogic.CONTEXT_PAD).
        val context = inputOps.textBefore(typed.length + SuggestionReplaceLogic.CONTEXT_PAD)
        when (SuggestionReplaceLogic.action(typed, context)) {
            SuggestionReplaceLogic.Action.INSERT_PLAIN ->
                inputOps.insert("$fullWord ")
            SuggestionReplaceLogic.Action.INSERT_WITH_SEPARATOR -> {
                val sep = if (context.isNotEmpty() && context.last().isLetter()) " " else ""
                inputOps.insert("$sep$fullWord ")
            }
            SuggestionReplaceLogic.Action.REPLACE_TYPED -> {
                // Lösch-Menge = Roh-Länge im Feld (nicht typed.length!), sonst
                // blieben bei NFD-Umlauten Zeichen stehen („verdoppelt“).
                val rawLen = SuggestionReplaceLogic.rawWordLength(context, typed)
                    ?: typed.length
                // Löschen zuerst VERIFIZIEREN; ohne nachweisliches Löschen NICHT
                // einfügen, sonst verdoppelt sich der Text.
                if (!replaceTypedWord(typed, context, rawLen)) return
                inputOps.insert("$fullWord ")
            }
        }
        engine?.learn(prevTypedWord, fullWord)
        prevTypedWord = fullWord.lowercase()
        currentTypedWord = ""
        persistUserDict()
    }

    /**
     * Löscht das getippte Wort vor dem Cursor — mit Verifikation.
     *
     * @param context der vor dem Löschen gelesene Kontext (für die Verifikation)
     * @param count Roh-Anzahl zu löschender Zeichen (siehe `rawWordLength`)
     * @return true, wenn das Wort danach nicht mehr dasteht; false, wenn es nicht
     *   entfernt werden konnte (der Aufrufer fügt dann bewusst NICHT ein).
     */
    private fun replaceTypedWord(typed: String, context: String, count: Int): Boolean {
        if (count <= 0) return true
        if (context.isEmpty()) {
            // Kein lesbarer Kontext (z. B. Termux/WebView): der bewährte
            // Key-Event-Weg ist dort zuverlässiger als deleteSurroundingText.
            inputOps.deleteBeforeKeys(count)
            return true
        }
        val width = context.length.coerceAtLeast(count)
        inputOps.deleteBefore(count)
        // Kontext unverändert → deleteSurroundingText hat nicht gewirkt.
        if (SuggestionReplaceLogic.deleteWorked(context, inputOps.textBefore(width))) return true
        inputOps.deleteBeforeKeys(count)
        return !SuggestionReplaceLogic.endsWith(inputOps.textBefore(width), typed)
    }

    /**
     * Emoji aus dem Vorschlags-Leisten-Katalog einfügen (v0.11): bewusst KEIN
     * Wort-Lernen und keine Autokorrektur — Emojis gehören nicht ins User-
     * Dictionary. Einfügen via [inputOps.insert] (App/Editor/Terminal-Routing,
     * wie bei den Wortvorschlägen) ohne Trailing-Space; danach Buffer-Reset,
     * damit Engine-State und Feld nicht desynchronisieren.
     */
    fun commitEmoji(emoji: String) {
        inputOps.insert(emoji)
        reset()
    }

    /** Zeichen hinzufügen/entfernen (Rückgabewert: Vorschläge neu berechnen?). */
    fun onCharacter(text: String) {
        currentTypedWord += text[0]
    }

    fun deleteLast() {
        currentTypedWord = currentTypedWord.dropLast(1).takeIf { it.isNotEmpty() }.orEmpty()
    }

    fun reset() {
        currentTypedWord = ""
        prevTypedWord = null
    }

    /** Wort abgeschlossen (Space/Punkt/Enter): lernen + State reset. */
    fun onWordCompleted() {
        if (currentTypedWord.isNotEmpty()) {
            engine?.learn(prevTypedWord, currentTypedWord)
            prevTypedWord = currentTypedWord.lowercase()
            currentTypedWord = ""
            persistUserDict()
        }
    }

    /**
     * Aktive Autokorrektur beim Space (v0.9.1): Ist das getippte Wort ein
     * offensichtlicher Tippfehler, wird es via [applySuggestion] durch den
     * besten Wörterbuch-Kandidaten ersetzt (inkl. nachfolgendem Space).
     *
     * @return true, wenn korrigiert wurde (der Aufrufer committet dann KEINEN
     *   zusätzlichen Space mehr), false wenn normal durchgelassen werden soll.
     */
    fun autoCorrectBeforeSpace(): Boolean {
        // Optional in den Einstellungen (Default: an)
        if (!com.piotv.keytab.Prefs.of(context)
                .getBoolean(com.piotv.keytab.Prefs.KEY_AUTOCORRECT, true)
        ) return false
        val typed = currentTypedWord
        if (typed.length < 3) return false
        val corrected = engine?.autoCorrect(typed, prevTypedWord) ?: return false
        applySuggestion(corrected)
        return true
    }

    private fun persistUserDict() {
        val eng = engine ?: return
        val raw = eng.serializeUserDict()
        ioExecutor.execute {
            com.piotv.keytab.Prefs.of(context)
                .edit().putString(com.piotv.keytab.Prefs.KEY_USER_DICT, raw).apply()
        }
    }
}
