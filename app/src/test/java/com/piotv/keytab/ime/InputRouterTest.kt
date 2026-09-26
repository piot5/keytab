package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputRouterTest {
    private class Target : InputTarget {
        val ops = mutableListOf<String>()
        var before = ""
        override fun insert(text: String) { ops += "ins:$text" }
        override fun deleteBackspace() { ops += "del" }
        override fun deleteWord() { ops += "delWord" }
        override fun deleteBefore(count: Int) { ops += "delB:$count" }
        override fun deleteBeforeKeys(count: Int) { ops += "delK:$count" }
        override fun textBefore(count: Int) = before
        override fun onEnter() { ops += "enter" }
        override fun onTab() { ops += "tab" }
    }

    @Test fun `default und Routing zwischen App und Editor`() {
        val app = Target(); val editor = Target(); val router = InputRouter(app, editor)
        assertTrue(router.isApp); router.insert("x")
        assertEquals(listOf("ins:x"), app.ops)
        router.kind = InputKind.EDITOR
        assertFalse(router.isApp); router.deleteBackspace(); router.deleteWord(); router.onTab()
        assertEquals(listOf("del", "delWord", "tab"), editor.ops)
    }

    @Test fun `alle Operationen gehen nur an das aktive Ziel`() {
        val app = Target(); val editor = Target(); val router = InputRouter(app, editor)
        router.kind = InputKind.EDITOR
        router.insert("x")
        router.deleteBackspace()
        router.deleteWord()
        router.deleteBefore(3)
        router.deleteBeforeKeys(2)
        router.onEnter()
        router.onTab()
        assertEquals(listOf("ins:x", "del", "delWord", "delB:3", "delK:2", "enter", "tab"), editor.ops)
        assertTrue(app.ops.isEmpty())
    }

    @Test fun `textBefore und isApp werden korrekt delegiert`() {
        val app = Target(); val editor = Target(); val router = InputRouter(app, editor)
        app.before = "Hallo"; assertEquals("Hallo", router.textBefore(3))
        router.kind = InputKind.EDITOR; editor.before = "Welt"
        assertEquals("Welt", router.textBefore(2)); assertFalse(router.isApp)
    }
}
