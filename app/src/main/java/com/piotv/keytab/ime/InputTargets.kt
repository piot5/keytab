package com.piotv.keytab.ime

import android.view.inputmethod.InputConnection

/** Ziele, in die die Tastatur Text schreibt oder löscht. */
interface InputTarget {
    fun insert(text: String)
    fun deleteBackspace()
    fun deleteWord()
    fun deleteBefore(count: Int)
    fun deleteBeforeKeys(count: Int)
    fun textBefore(count: Int): String
    fun onEnter()
    fun onTab()
}

enum class InputKind { APP, EDITOR }

/** Routing zwischen App-Feld und KeyTab-Editor. */
class InputRouter(app: InputTarget, editor: InputTarget) {
    private val targets = mapOf(InputKind.APP to app, InputKind.EDITOR to editor)
    var kind: InputKind = InputKind.APP
    val active: InputTarget get() = targets.getValue(kind)
    val isApp: Boolean get() = kind == InputKind.APP
    fun insert(text: String) = active.insert(text)
    fun deleteBackspace() = active.deleteBackspace()
    fun deleteWord() = active.deleteWord()
    fun deleteBefore(count: Int) = active.deleteBefore(count)
    fun deleteBeforeKeys(count: Int) = active.deleteBeforeKeys(count)
    fun textBefore(count: Int) = active.textBefore(count)
    fun onEnter() = active.onEnter()
    fun onTab() = active.onTab()
}

/** Ziel: Fremd-App-Feld über InputConnection. */
class AppInputTarget(
    private val connection: () -> InputConnection?,
    private val sendKey: (Int) -> Unit
) : InputTarget {
    override fun insert(text: String) { runCatching { connection()?.commitText(text, 1) } }
    override fun deleteBackspace() = sendKey(android.view.KeyEvent.KEYCODE_DEL)
    override fun deleteBefore(count: Int) { if (count > 0) connection()?.deleteSurroundingText(count, 0) }
    override fun deleteBeforeKeys(count: Int) = repeat(count.coerceAtLeast(0)) { sendKey(android.view.KeyEvent.KEYCODE_DEL) }
    override fun deleteWord() {
        val ic = connection() ?: return
        val text = ic.getTextBeforeCursor(200, 0)?.toString().orEmpty()
        ic.deleteSurroundingText(maxOf(TextEditLogic.wordDeleteCount(text, text.length), 1), 0)
    }
    override fun textBefore(count: Int): String = connection()?.getTextBeforeCursor(count, 0)?.toString().orEmpty()
    override fun onEnter() = sendKey(android.view.KeyEvent.KEYCODE_ENTER)
    override fun onTab() = sendKey(android.view.KeyEvent.KEYCODE_TAB)
}

/** Ziel: interner KeyTab-Editor. */
class EditorInputTarget(private val panel: EditorPanel) : InputTarget {
    override fun insert(text: String) = panel.insert(text)
    override fun deleteBackspace() = panel.delete(word = false)
    override fun deleteWord() = panel.delete(word = true)
    override fun deleteBefore(count: Int) = panel.deleteBefore(count)
    override fun deleteBeforeKeys(count: Int) = panel.deleteBefore(count)
    override fun textBefore(count: Int): String {
        val (text, cursor) = panel.cursorContext() ?: return ""
        return text.substring((cursor - count).coerceAtLeast(0), cursor).toString()
    }
    override fun onEnter() = panel.insert("\n")
    override fun onTab() = panel.insert("\t")
}
