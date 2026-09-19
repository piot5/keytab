package com.piotv.keytab.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Clipboard-Panel: Capture aus dem System-Clipboard, Persistenz über
 * Panel-Instanzen hinweg, Auto-Capture-Gate, Deduplizierung.
 * (Aus PanelsTest.kt getrennt — Name = Klasse.)
 */
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
