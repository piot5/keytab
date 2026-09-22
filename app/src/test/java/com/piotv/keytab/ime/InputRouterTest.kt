package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Routing-Logik des InputRouter: Das aktive Ziel erhält die Operationen,
 * die anderen nicht. Fake-Targets zeichnen die Aufrufe auf (pure Kotlin).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InputRouterTest {

    /** Fake-Target: zeichnet Operationen auf. */
    private class FakeTarget : InputTarget {
        val ops = mutableListOf<String>()
        var before: String = ""
        override fun insert(text: String) { ops += "ins:$text" }
        override fun deleteBackspace() { ops += "del" }
        override fun deleteWord() { ops += "delWord" }
        override fun deleteBefore(count: Int) { ops += "delB:$count" }
        override fun deleteBeforeKeys(count: Int) { ops += "delK:$count" }
        override fun textBefore(count: Int): String = before
        override fun onEnter() { ops += "enter" }
        override fun onTab() { ops += "tab" }
    }

    private data class Rig(
        val router: InputRouter,
        val app: FakeTarget,
        val editor: FakeTarget,
        val terminal: FakeTarget
    )

    private fun rig(): Rig {
        val app = FakeTarget(); val ed = FakeTarget(); val term = FakeTarget()
        return Rig(InputRouter(app, ed, term), app, ed, term)
    }

    @Test
    fun `default-Ziel ist APP`() {
        val (r, app, ed, term) = rig()
        assertTrue(r.isApp)
        r.insert("x")
        assertEquals(listOf("ins:x"), app.ops)
        assertEquals(0, ed.ops.size)
        assertEquals(0, term.ops.size)
    }

    @Test
    fun `setKind EDITOR leitet an Editor-Target um`() {
        val (r, app, ed, term) = rig()
        r.kind = InputKind.EDITOR
        assertFalse(r.isApp)
        r.insert("x"); r.deleteBackspace(); r.deleteBackspace(); r.deleteWord()
        assertEquals(0, app.ops.size)
        assertEquals(0, term.ops.size)
        assertEquals(listOf("ins:x", "del", "del", "delWord"), ed.ops)
    }

    @Test
    fun `TERMINAL-Ziel erhaelt alle Operationen`() {
        val (r, app, ed, term) = rig()
        r.kind = InputKind.TERMINAL
        assertFalse(r.isApp)
        r.insert("ls"); r.deleteWord(); r.onEnter()
        assertEquals(0, app.ops.size)
        assertEquals(0, ed.ops.size)
        assertEquals(listOf("ins:ls", "delWord", "enter"), term.ops)
    }

    @Test
    fun `active entspricht dem gesetzten kind`() {
        val (r, app, ed, term) = rig()
        assertTrue(r.active === app)
        r.kind = InputKind.EDITOR
        assertTrue(r.active === ed)
        r.kind = InputKind.TERMINAL
        assertTrue(r.active === term)
        r.kind = InputKind.APP
        assertTrue(r.active === app)
    }

    @Test
    fun `deleteBefore und deleteBeforeKeys werden geroutet`() {
        val (r, app, ed, _) = rig()
        r.kind = InputKind.EDITOR
        r.deleteBefore(3); r.deleteBeforeKeys(2)
        assertEquals(listOf("delB:3", "delK:2"), ed.ops)
        assertEquals(0, app.ops.size)
    }

    @Test
    fun `onEnter delegiert an aktives Ziel`() {
        val (r, app, ed, _) = rig()
        r.onEnter()
        assertEquals(listOf("enter"), app.ops)
        r.kind = InputKind.EDITOR
        r.onEnter()
        assertEquals(1, ed.ops.size)
    }

    @Test
    fun `onTab delegiert an aktives Ziel`() {
        val (r, app, ed, _) = rig()
        r.onTab()
        assertEquals(listOf("tab"), app.ops)
        r.kind = InputKind.EDITOR
        r.onTab()
        assertEquals(1, ed.ops.size)
    }

    @Test
    fun `onTab nutzt nicht den insert-Pfad (Regression KEYCODE_TAB)`() {
        val (r, app, _, _) = rig()
        r.onTab()
        // Frueher: insert Tab-Zeichen - Termux/vim empfangen darauf kein Tastenereignis.
        assertEquals(0, app.ops.count { it.startsWith("ins:") })
        assertEquals(listOf("tab"), app.ops)
    }

    @Test
    fun `textBefore delegiert an aktives Ziel (String)`() {
        val (r, app, ed, _) = rig()
        app.before = "Hallo"
        // FakeTarget liefert das Feld unverändert zurück (kein substring).
        assertEquals("Hallo", r.textBefore(3))
        r.kind = InputKind.EDITOR
        ed.before = "Welt"
        assertEquals("Welt", r.textBefore(2))
    }

    @Test
    fun `isApp gilt nur fuer APP`() {
        val (r, _, _, _) = rig()
        assertTrue(r.isApp)
        r.kind = InputKind.EDITOR
        assertTrue(!r.isApp)
        r.kind = InputKind.TERMINAL
        assertTrue(!r.isApp)
        r.kind = InputKind.APP
        assertTrue(r.isApp)
    }
}
