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
            val saved = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getString(MainActivity.KEY_USER_DICT, null)
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
        val eng = engine
        val list = if (eng == null) emptyList() else {
            try { eng.suggest(currentTypedWord, prevTypedWord) }
            catch (_: Exception) { emptyList() }
        }
        if (list.isEmpty()) {
            currentSuggestions = emptyList()
            for (i in 0..2) { suggestionViews[i]?.apply { visibility = View.INVISIBLE; tag = null } }
            bar.visibility = View.VISIBLE
            return
        }
        for (i in 0..2) {
            val tv = suggestionViews[i] ?: continue
            val sug = list.getOrNull(i)
            if (sug == null) { tv.visibility = View.INVISIBLE; tv.tag = null }
            else { tv.visibility = View.VISIBLE; tv.text = eng!!.matchCase(sug.word, currentTypedWord); tv.tag = sug.word }
        }
        currentSuggestions = list
        bar.visibility = View.VISIBLE
    }

    /**
     * Setzt einen Vorschlag ein (Editor/Terminal/App via inputOps).
     *
     * Bugfix: Vor dem Löschen wird geprüft, ob das getippte Teilwort wirklich
     * direkt vor dem Cursor steht. Steht der Cursor woanders (z. B. nach einem
     * Tap mitten in den Text), würde das alte Verhalten [deleteBefore] falsche
     * Zeichen entfernen und das Wort am falschen Ort einfügen. In dem Fall wird
     * nichts gelöscht, sondern das Wort sauber mit Trenner an der Cursor-
     * position eingefügt.
     */
    fun applySuggestion(word: String) {
        val typed = currentTypedWord
        val typedLen = typed.length
        val fullWord = engine?.matchCase(word, typed) ?: word
        if (typedLen > 0) {
            val before = inputOps.textBefore(typedLen)
            val readable = before.isNotEmpty()
            val tailMatches = readable && before.takeLast(typedLen)
                .equals(typed, ignoreCase = true)
            when {
                // Verifiziert ANDERER Inhalt (Cursor steht mitten im Text):
                // nichts löschen, Wort mit Trenner an der Cursorposition.
                readable && before.length >= typedLen && !tailMatches -> {
                    val sep = if (before.last().isLetter()) " " else ""
                    inputOps.insert("$sep$fullWord ")
                    engine?.learn(prevTypedWord, fullWord)
                    prevTypedWord = fullWord.lowercase()
                    currentTypedWord = ""
                    persistUserDict()
                    return
                }
                // Lesbar + Wort steht davor → normal löschen, dann VERIFIZIEREN
                readable -> {
                    inputOps.deleteBefore(typedLen)
                    val after = inputOps.textBefore(typedLen)
                    if (after.isNotEmpty() && after.length >= typedLen &&
                        after.takeLast(typedLen).equals(typed, ignoreCase = true)
                    ) {
                        // deleteSurroundingText wirkte nicht (z. B. Termux/WebView)
                        // → über KEYCODE_DEL-Key-Events löschen
                        inputOps.deleteBeforeKeys(typedLen)
                    }
                }
                // Feld liefert nichts Lesbares → direkt den zuverlässigen
                // Key-Event-Weg nehmen (statt blind deleteSurroundingText)
                else -> inputOps.deleteBeforeKeys(typedLen)
            }
        }
        inputOps.insert("$fullWord ")
        engine?.learn(prevTypedWord, fullWord)
        prevTypedWord = fullWord.lowercase()
        currentTypedWord = ""
        persistUserDict()
    }

    /** Zeichen hinzufügen/entfernen (Rückgabewert: Vorschläge neu berechnen?). */
    fun onCharacter(text: String) {
        currentTypedWord += text[0]
    }

    fun deleteLast() {
        currentTypedWord = currentTypedWord.dropLast(1).takeIf { it.isNotEmpty() } ?: ""
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

    private fun persistUserDict() {
        val eng = engine ?: return
        val raw = eng.serializeUserDict()
        ioExecutor.execute {
            context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .edit().putString(MainActivity.KEY_USER_DICT, raw).apply()
        }
    }
}