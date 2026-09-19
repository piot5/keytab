package com.piotv.keytab.ime

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * **Termux-Key-Matrix (automatisierter Teil).**
 *
 * Der Kern-Use-Case „Coding in Termux" hängt an wenigen Key-Verträgen des
 * [AppInputTarget]. Diese Suite fixiert sie — die vollständige manuelle
 * App-Matrix (Termux/neovim, AndroidIDE, VS Code proot: Cursor, IME-Wechsel)
 * bleibt Device-Verifikation (README Known gaps), aber diese Invarianten sind
 * hier maschinell beweisbar:
 *
 *  - **TAB = KEYCODE_TAB-Key-Event**, niemals `commitText("\t")`
 *    (Termux/vim/nano & Tab-Completion warten auf das Tastenereignis)
 *  - **Enter = KEYCODE_ENTER**, Backspace = KEYCODE_DEL
 *  - Normaler Text = `commitText`
 *  - `deleteBefore` = deleteSurroundingText; `deleteBeforeKeys` = Einzel-KEYCODE_DEL
 */
class TermuxKeyMatrixTest {

    /** Aufzeichnung: committed texts + gelöschte (before, after)-Paare. */
    private class Rec {
        val committed = mutableListOf<String>()
        val deleted = mutableListOf<Pair<Int, Int>>()
        var before = ""
    }

    /** InputConnection als dynamischer Proxy — nur die relevanten Methoden melden. */
    private fun fakeConnection(rec: Rec): InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader, arrayOf(InputConnection::class.java)
    ) { _, method, args ->
        when (method.name) {
            "commitText" -> rec.committed.add((args?.get(0) as? CharSequence)?.toString().orEmpty())
            "deleteSurroundingText" -> rec.deleted.add((args?.get(0) as Int) to (args?.get(1) as Int))
            "getTextBeforeCursor" -> rec.before.takeLast((args?.get(0) as Int).coerceAtLeast(0))
            else -> if (method.returnType == Boolean::class.javaPrimitiveType) true else null
        }
    } as InputConnection

    /** Target + Aufzeichnungen (Connection, gesendete Keycodes). */
    private fun target(rec: Rec): Pair<AppInputTarget, MutableList<Int>> {
        val keys = mutableListOf<Int>()
        return AppInputTarget({ fakeConnection(rec) }, { keys += it }) to keys
    }

    @Test
    fun `TAB ist ein KEYCODE_TAB-Key-Event - nie commitText`() {
        val rec = Rec(); val (t, keys) = target(rec)
        t.onTab()
        assertEquals(listOf(KeyEvent.KEYCODE_TAB), keys)
        assertTrue("Tab darf nicht als Text committed werden", rec.committed.isEmpty())
    }

    @Test
    fun `Enter ist KEYCODE_ENTER`() {
        val rec = Rec(); val (t, keys) = target(rec)
        t.onEnter()
        assertEquals(listOf(KeyEvent.KEYCODE_ENTER), keys)
        assertTrue(rec.committed.isEmpty())
    }

    @Test
    fun `Backspace ist KEYCODE_DEL`() {
        val rec = Rec(); val (t, keys) = target(rec)
        t.deleteBackspace()
        assertEquals(listOf(KeyEvent.KEYCODE_DEL), keys)
    }

    @Test
    fun `normaler Text läuft über commitText`() {
        val rec = Rec(); val (t, keys) = target(rec)
        t.insert("ls -la")
        assertEquals(listOf("ls -la"), rec.committed)
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `deleteBeforeKeys sendet n x KEYCODE_DEL (Termux-Fallback)`() {
        val rec = Rec(); val (t, keys) = target(rec)
        t.deleteBeforeKeys(3)
        assertEquals(
            List(3) { KeyEvent.KEYCODE_DEL }, keys)
    }

    @Test
    fun `deleteBefore 0 ist no-op`() {
        val rec = Rec(); val (t, keys) = target(rec)
        t.deleteBefore(0)
        assertTrue(rec.deleted.isEmpty())
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `Verbindung null - alle Operationen ohne Crash, Key-Events laufen weiter`() {
        val keys = mutableListOf<Int>()
        val t = AppInputTarget({ null }, { keys += it })
        t.insert("x"); t.onTab(); t.onEnter(); t.deleteBefore(2); t.deleteBackspace()
        assertTrue(keys.contains(KeyEvent.KEYCODE_TAB))
    }
}
