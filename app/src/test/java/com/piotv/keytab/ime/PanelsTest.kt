package com.piotv.keytab.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ListView
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/** Direkter Executor: Runnables laufen synchron → deterministische Tests. */
private val directExecutor: Executor = Executor { it.run() }

private fun inflateKeyboardRoot(context: Context): View =
    LayoutInflater.from(ContextThemeWrapper(context, R.style.Theme_KeyTab))
        .inflate(R.layout.keyboard_view, null)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorPanelTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `insert fügt am Ende ein`() {
        val root = inflateKeyboardRoot(app)
        val panel = EditorPanel(app, root, directExecutor, Handler(Looper.getMainLooper()))
        val et = root.findViewById<EditText>(R.id.editor_input)
        panel.insert("abc")
        assertEquals("abc", et.text.toString())
    }

    @Test
    fun `insert respektiert Cursorposition und Selektion`() {
        val root = inflateKeyboardRoot(app)
        val panel = EditorPanel(app, root, directExecutor, Handler(Looper.getMainLooper()))
        val et = root.findViewById<EditText>(R.id.editor_input)
        et.setText("abc")
        et.setSelection(1)
        panel.insert("X")
        assertEquals("aXbc", et.text.toString())
        et.setSelection(0, 2)
        panel.insert("Y")
        assertEquals("Ybc", et.text.toString())
    }

    @Test
    fun `delete einzelnes Zeichen`() {
        val root = inflateKeyboardRoot(app)
        val panel = EditorPanel(app, root, directExecutor, Handler(Looper.getMainLooper()))
        val et = root.findViewById<EditText>(R.id.editor_input)
        et.setText("abc")
        et.setSelection(3)
        panel.delete(word = false)
        assertEquals("ab", et.text.toString())
        assertEquals(2, et.selectionEnd)
    }

    @Test
    fun `delete Wort löscht bis Wortanfang ohne Extra-Zeichen`() {
        val root = inflateKeyboardRoot(app)
        val panel = EditorPanel(app, root, directExecutor, Handler(Looper.getMainLooper()))
        val et = root.findViewById<EditText>(R.id.editor_input)
        et.setText("hello world")
        et.setSelection(11)
        panel.delete(word = true)
        assertEquals("hello ", et.text.toString())
    }

    @Test
    fun `delete Wort überspringt vorangehenden Whitespace`() {
        val root = inflateKeyboardRoot(app)
        val panel = EditorPanel(app, root, directExecutor, Handler(Looper.getMainLooper()))
        val et = root.findViewById<EditText>(R.id.editor_input)
        et.setText("hello   world")
        et.setSelection(13)
        panel.delete(word = true)
        assertEquals("hello", et.text.toString())
    }

    @Test
    fun `delete am Stringanfang ist unkritisch`() {
        val root = inflateKeyboardRoot(app)
        val panel = EditorPanel(app, root, directExecutor, Handler(Looper.getMainLooper()))
        val et = root.findViewById<EditText>(R.id.editor_input)
        et.setText("abc")
        et.setSelection(0)
        panel.delete(word = false)
        assertEquals("abc", et.text.toString())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipboardPanelTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    private fun setClipboard(text: String) {
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("test", text))
    }

    private fun panel(canAutoCapture: Boolean = true) =
        ClipboardPanel(app, directExecutor, Handler(Looper.getMainLooper()),
            onCommit = {}, canAutoCapture = { canAutoCapture })

    @Test
    fun `capture nimmt Clipboard auf und Liste zeigt es`() {
        setClipboard("hallo welt")
        val p = panel()
        p.onSelected()
        assertEquals(1, p.entries().size)
        assertEquals("hallo welt", p.entries()[0])
    }

    @Test
    fun `Persistenz - zweite Panel-Instanz laedt Historie`() {
        setClipboard("eintrag eins")
        panel().onSelected()
        // zweite Instanz: lädt beim Init die persistierte Historie
        val p2 = panel()
        assertEquals(1, p2.entries().size)
        assertEquals("eintrag eins", p2.entries()[0])
    }

    @Test
    fun `Auto-Capture wird blockiert wenn IME nicht fokussiert`() {
        setClipboard("geheim")
        val p = panel(canAutoCapture = false)
        p.onSelected()
        assertEquals(0, p.entries().size)
    }

    @Test
    fun `Duplikate werden nicht doppelt aufgenommen`() {
        setClipboard("x")
        val p = panel()
        p.onSelected()
        p.onSelected()
        assertEquals(1, p.entries().size)
    }

    @Test
    fun `leeres Clipboard fuehrt zu leerer Liste ohne Crash`() {
        val p = panel()
        p.onSelected()
        assertTrue(p.entries().isEmpty())
    }
}