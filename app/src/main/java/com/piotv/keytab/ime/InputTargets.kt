package com.piotv.keytab.ime

import android.view.inputmethod.InputConnection

/**
 * Ziel, in das die Tastatur Text schreibt / löscht.
 *
 * Drei Implementierungen: [AppInputTarget] (Fremd-App via InputConnection),
 * [EditorInputTarget] (Notes-Editor), [TerminalInputTarget] (Terminal-Shell).
 * Ersetzt die `editorActive`/`terminalActive`-Verzweigungsketten des Service
 * (Refactoring, docs/REFACTORING_PLAN.md Phase 2).
 */
interface InputTarget {
    /** Text einfügen (an Cursorposition). */
    fun insert(text: String)

    /** Einzelschritt-Löschen (Del-Taste; App-Feld via KEYCODE_DEL). */
    fun deleteBackspace()

    /** Wort vor dem Cursor löschen (Del-LongPress). */
    fun deleteWord()

    /** [count] Zeichen vor dem Cursor löschen (Wortvorhersage-Buchführung). */
    fun deleteBefore(count: Int)

        /** [count] × Del als KEYCODE-Event (Felder, die deleteSurroundingText ignorieren). */
    fun deleteBeforeKeys(count: Int)

    /** Text vor dem Cursor (max. [count] Zeichen). */
    fun textBefore(count: Int): String

    /** Eingabe-Taste: Terminal=führe Zeile aus, Editor= Neue Zeile, App=KEYCODE_ENTER. */
    fun onEnter()
}

/** Aktives Ziel des Routers (App-Feld, Notes-Editor oder Terminal). */
enum class InputKind { APP, EDITOR, TERMINAL }

/**
 * Routing zwischen den Eingabezielen. Der Service setzt [kind] beim Tab-Wechsel;
 * alle Schreib-/Löschoperationen laufen über [active].
 */
class InputRouter(
    app: InputTarget,
    editor: InputTarget,
    terminal: InputTarget
) {
    private val targets = mapOf(
        InputKind.APP to app,
        InputKind.EDITOR to editor,
        InputKind.TERMINAL to terminal
    )

    /** Aktives Ziel (Default: App-Feld). */
    var kind: InputKind = InputKind.APP

    /** Das aktuell aktive Ziel. */
    val active: InputTarget get() = targets.getValue(kind)

    /** true, wenn das Ziel ein Fremd-App-Feld ist (Auto-Korrektur nur dort). */
    val isApp: Boolean get() = kind == InputKind.APP

    fun insert(text: String) = active.insert(text)
    fun deleteBackspace() = active.deleteBackspace()
    fun deleteWord() = active.deleteWord()
    fun deleteBefore(count: Int) = active.deleteBefore(count)
            fun deleteBeforeKeys(count: Int) = active.deleteBeforeKeys(count)
    fun textBefore(count: Int): String = active.textBefore(count)
    fun onEnter() = active.onEnter()
}

/**
 * Ziel: Fremd-App-Feld über [InputConnection].
 *
 * Einzelschritt-Löschen läuft über KEYCODE_DEL ([sendKey]) statt
 * deleteSurroundingText – KEYCODE_DEL funktioniert auch in Feldern, die
 * surrounding-text nicht unterstützen (Termux, WebView).
 */
class AppInputTarget(
    private val connection: () -> InputConnection?,
    private val sendKey: (Int) -> Unit
) : InputTarget {
    override fun insert(text: String) {
        runCatching { connection()?.commitText(text, 1) }
    }
    override fun deleteBackspace() = sendKey(android.view.KeyEvent.KEYCODE_DEL)
    override fun deleteBefore(count: Int) {
        if (count > 0) connection()?.deleteSurroundingText(count, 0)
    }
    override fun deleteBeforeKeys(count: Int) =
        repeat(count.coerceAtLeast(0)) { sendKey(android.view.KeyEvent.KEYCODE_DEL) }

    override fun deleteWord() {
        val ic = connection() ?: return
        val text = ic.getTextBeforeCursor(200, 0)?.toString() ?: ""
        val toDelete = TextEditLogic.wordDeleteCount(text, text.length)
        // Wortgrenze löschen, min. ein Zeichen (wie im Original-Service).
        ic.deleteSurroundingText(maxOf(toDelete, 1), 0)
    }
    override fun textBefore(count: Int): String =
        connection()?.getTextBeforeCursor(count, 0)?.toString() ?: ""
    override fun onEnter() = sendKey(android.view.KeyEvent.KEYCODE_ENTER)
}

/** Ziel: Notes-Editor ([EditorPanel]). */
class EditorInputTarget(private val panel: EditorPanel) : InputTarget {
    override fun insert(text: String) = panel.insert(text)
    override fun deleteBackspace() = panel.delete(word = false)
    override fun deleteWord() = panel.delete(word = true)
    override fun deleteBefore(count: Int) = panel.deleteBefore(count)
    override fun deleteBeforeKeys(count: Int) = panel.deleteBefore(count)
    override fun textBefore(count: Int): String {
        val ctx = panel.cursorContext() ?: return ""
        val (t, cursor) = ctx
        val start = (cursor - count).coerceAtLeast(0)
        return t.substring(start, cursor).toString()
    }
    override fun onEnter() = panel.insert("\n")
}

/** Ziel: Terminal-Shell ([TerminalPanel]). */
class TerminalInputTarget(private val panel: TerminalPanel) : InputTarget {
    override fun insert(text: String) = panel.insert(text)
    override fun deleteBackspace() = panel.delete(word = false)
    override fun deleteWord() = panel.delete(word = true)
    override fun deleteBefore(count: Int) = panel.deleteBefore(count)
    override fun deleteBeforeKeys(count: Int) = panel.deleteBefore(count)
        override fun textBefore(count: Int): String {
        val ctx = panel.cursorContext() ?: return ""
        val (t, cursor) = ctx
        val start = (cursor - count).coerceAtLeast(0)
        return t.substring(start, cursor).toString()
    }
    override fun onEnter() = panel.send()
}
