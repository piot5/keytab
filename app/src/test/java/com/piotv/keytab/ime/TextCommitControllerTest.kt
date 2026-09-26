package com.piotv.keytab.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

/**
 * TextCommitController: commit/commitToApp/deleteLastWord-Routing
 * über einen Fake-Host. (Bisher keine eigene Suite.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextCommitControllerTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    private class FakeTarget : InputTarget {
        val ops = mutableListOf<String>()
        override fun insert(text: String) { ops += "ins:$text" }
        override fun deleteBackspace() { ops += "del" }
        override fun deleteWord() { ops += "delWord" }
        override fun deleteBefore(count: Int) { ops += "delB:$count" }
        override fun deleteBeforeKeys(count: Int) { ops += "delK:$count" }
        override fun textBefore(count: Int): String = ""
        override fun onEnter() { ops += "enter" }
        override fun onTab() { ops += "tab" }
    }

    private class FakeField : WordPredictionManager.InputOperations {
        var content = ""
        var cursor = 0
        override fun deleteBefore(count: Int) {
            val s = (cursor - count).coerceAtLeast(0)
            content = content.removeRange(s, cursor); cursor = s
        }
        override fun deleteBeforeKeys(count: Int) {
            repeat(count) {
                if (cursor > 0) { content = content.removeRange(cursor - 1, cursor); cursor-- }
            }
        }
        override fun textBefore(count: Int): String =
            content.substring((cursor - count).coerceAtLeast(0), cursor)
        override fun insert(text: String) {
            content = content.substring(0, cursor) + text + content.substring(cursor)
            cursor += text.length
        }
        override fun commitToApp(text: String) = insert(text)
    }

    private class FakeHost : TextCommitController.Host {
        var haptics = 0
        var shifts = 0
        var updates = 0
        var router: InputRouter? = null
        var pm: WordPredictionManager? = null
        override fun haptic() { haptics++ }
        override val inputRouter get() = router
        override val predictionManager get() = pm
        override fun consumeSingleShift() { shifts++ }
        override fun updateSuggestions() { updates++ }
    }

    private fun rig(withPm: Boolean = true): Triple<TextCommitController, FakeHost, FakeTarget> {
        val appT = FakeTarget()
        val host = FakeHost()
        host.router = InputRouter(appT, FakeTarget())
        if (withPm) {
            host.pm = WordPredictionManager(app, { it.run() },
                Handler(Looper.getMainLooper()), arrayOfNulls(3), FakeField())
        }
        return Triple(TextCommitController(host), host, appT)
    }

    @Test
    fun `commit Buchstabe routet an App und sammelt Vorhersage`() {
        val (c, host, appT) = rig()
        c.commit("a")
        assertEquals(listOf("ins:a"), appT.ops)
        assertEquals("a", host.pm!!.currentTypedWord)
        assertEquals(1, host.haptics)
        assertEquals(1, host.shifts)
        assertEquals(1, host.updates)
    }

    @Test
    fun `commit Space schliesst das Wort ab`() {
        val (c, host, appT) = rig()
        c.commit("x")
        c.commit(" ")
        assertEquals(listOf("ins:x", "ins: "), appT.ops)
        assertTrue("Wort abgeschlossen", host.pm!!.currentTypedWord.isEmpty())
        assertEquals(2, host.updates)
    }

    @Test
    fun `commit ohne Router und PM crasht nicht, Haptik laeuft`() {
        val (c, host, _) = rig(withPm = false)
        host.router = null
        c.commit("a")
        assertEquals(1, host.haptics)
        assertEquals(1, host.shifts)
        assertEquals(1, host.updates)
    }

    @Test
    fun `commitToApp schreibt direkt in die Connection`() {
        val (c, host, appT) = rig()
        val committed = mutableListOf<String>()
        val conn = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java)) { _, m, a ->
            if (m.name == "commitText") committed.add((a?.get(0) as CharSequence).toString())
            true
        } as InputConnection
        c.commitToApp("clip text", conn)
        assertEquals(listOf("clip text"), committed)
        assertTrue("Clipboard umgeht den Router", appT.ops.isEmpty())
        assertEquals(1, host.haptics)
        assertEquals(1, host.shifts)
    }

    @Test
    fun `commitToApp mit null-Connection crasht nicht`() {
        val (c, host, _) = rig()
        c.commitToApp("x", null)
        assertEquals(1, host.haptics)
    }

    @Test
    fun `deleteLastWord loescht Wort und resettet Vorhersage`() {
        val (c, host, appT) = rig()
        c.commit("h"); c.commit("i")
        c.deleteLastWord()
        assertEquals(listOf("ins:h", "ins:i", "delWord"), appT.ops)
        assertTrue(host.pm!!.currentTypedWord.isEmpty())
        assertEquals(3, host.haptics)
    }
}
