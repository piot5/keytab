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
    }

    private fun router(): Triple<InputRouter, FakeTarget, FakeTarget> {
        val app = FakeTarget(); val ed = FakeTarget(); val term = FakeTarget()
        return Triple(InputRouter(app, ed, term), app, ed)
    }

    @Test
    fun `default-Ziel ist APP`() {
        val (r, app, ed) = router()
        assertTrue(r.isApp)
        r.insert("x")
        assertEquals(listOf("ins:x"), app.ops)
        assertEquals(0, ed.ops.size)
    }

        @Test
    fun `setKind EDITOR leitet an Editor-Target um`() {
        val (r, app, ed) = router()
        r.kind = InputKind.EDITOR
        assertFalse(r.isApp)
        r.insert("x"); r.deleteBackspace(); r.deleteBackspace(); r.deleteWord()
        assertEquals(0, app.ops.size)
        assertEquals(listOf("ins:x", "del", "del", "delWord"), ed.ops)
    }

    @Test
    fun `TERMINAL-Ziel erhält Operationen`() {
        val (r, app) = router()
        r.kind = InputKind.TERMINAL
        r.deleteWord()
        assertEquals(0, app.ops.size)
        assertTrue(r.active !== app)
    }

    @Test
    fun `onEnter delegiert an aktives Ziel`() {
        val (r, app, ed) = router()
        r.onEnter()
        assertEquals(listOf("enter"), app.ops)
        r.kind = InputKind.EDITOR
        r.onEnter()
        assertEquals(1, ed.ops.size)
    }

        @Test
    fun `textBefore delegiert an aktives Ziel (String)`() {
        val (r, app, ed) = router()
        app.before = "Hallo"
        // FakeTarget liefert das Feld unverändert zurück (kein substring).
        assertEquals("Hallo", r.textBefore(3))
        r.kind = InputKind.EDITOR
        ed.before = "Welt"
        assertEquals("Welt", r.textBefore(2))
    }

    @Test
    fun `isApp gilt nur für APP`() {
        val (r, _, _) = router()
        assertTrue(r.isApp)
        r.kind = InputKind.EDITOR
        assertTrue(!r.isApp)
        r.kind = InputKind.TERMINAL
        assertTrue(!r.isApp)
        r.kind = InputKind.APP
        assertTrue(r.isApp)
    }
}