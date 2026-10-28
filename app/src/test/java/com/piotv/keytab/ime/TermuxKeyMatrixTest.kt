package com.piotv.keytab.ime

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

private class Rec {
    val committed = mutableListOf<String>()
    val deleted = mutableListOf<Pair<Int, Int>>()
    var before = ""
}

private fun fakeConnection(rec: Rec): InputConnection = Proxy.newProxyInstance(
    InputConnection::class.java.classLoader,
    arrayOf(InputConnection::class.java)
) { _, method, args ->
    when (method.name) {
        "commitText" -> rec.committed.add((args?.get(0) as? CharSequence)?.toString().orEmpty())
        "deleteSurroundingText" -> rec.deleted.add((args?.get(0) as Int) to (args?.get(1) as Int))
        "getTextBeforeCursor" -> rec.before.takeLast((args?.get(0) as Int).coerceAtLeast(0))
        else -> if (method.returnType == Boolean::class.javaPrimitiveType) true else null
    }
} as InputConnection

private fun target(rec: Rec): Pair<AppInputTarget, MutableList<Int>> {
    val keys = mutableListOf<Int>()
    return AppInputTarget({ fakeConnection(rec) }, { keys += it }) to keys
}

@Test
fun `TAB ist KEYCODE_TAB - nie commitText`() {
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
