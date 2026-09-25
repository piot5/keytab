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
import org.robolectric.shadows.ShadowLooper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

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

    private fun panel(
        canAutoCapture: Boolean = true,
        onError: (String) -> Unit = {}
    ) =
        ClipboardPanel(
            app, Handler(Looper.getMainLooper()),
            CoroutineScope(Dispatchers.Unconfined),
            ClipboardPanel.Callbacks(
                onCommit = {}, canAutoCapture = { canAutoCapture }, onError = onError
            ),
            ioDispatcher = Dispatchers.Unconfined
        )

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

    // ---------- P0-Nachträge (Limit, Clear, Isolation) ----------

    @org.junit.Before
    fun clearHistoryFile() {
        // Eigene History-Datei pro Test: persistAsync schreibt synchron
        // (directExecutor), loadHistory liest im init — ohne Clear würden
        // Tests derselben Klasse die Datei teilen.
        val f = java.io.File(app.filesDir, "clipboard_history.txt")
        if (f.exists()) f.delete()
    }

    @Test
    fun `Verlauf ist auf 50 Eintraege begrenzt - aeltester faellt raus`() {
        val p = panel()
        for (i in 1..55) {
            setClipboard("eintrag $i")
            p.capture()
        }
        val entries = p.entries()
        assertEquals("MAX_ENTRIES aus ClipboardPanel", 50, entries.size)
        assertEquals("neuester zuerst", "eintrag 55", entries.first())
        assertEquals("aeltester ueberlebender", "eintrag 6", entries.last())
        assertTrue(entries.none { it == "eintrag 5" })
    }

    @Test
    fun `erneutes Capturen schiebt Eintrag nach vorn ohne Duplikat`() {
        val p = panel()
        setClipboard("eins"); p.capture()
        setClipboard("zwei"); p.capture()
        setClipboard("eins"); p.capture()
        assertEquals(listOf("eins", "zwei"), p.entries())
    }

    @Test
    fun `clear leert Verlauf und Persistenz`() {
        setClipboard("bleib"); val p = panel(); p.onSelected()
        assertEquals(1, p.entries().size)
        p.clear()
        assertTrue(p.entries().isEmpty())
        // zweite Instanz lädt aus der Datei → muss ebenfalls leer sein
        assertTrue(panel().entries().isEmpty())
    }

    @Test
    fun `Persistenzfehler meldet Fehler statt still zu scheitern`() {
        val errors = mutableListOf<String>()
        val p = panel(onError = { errors += it })
        val historyFile = java.io.File(app.filesDir, "clipboard_history.txt")
        historyFile.delete()
        historyFile.mkdirs()
        setClipboard("captured")
        p.capture()
        ShadowLooper.runUiThreadTasks()
        assertEquals(listOf(app.getString(com.piotv.keytab.R.string.clip_history_save_failed)), errors)
        historyFile.delete()
    }

    @Test
    fun `Blank-Clipboard wird nicht aufgenommen`() {
        val p = panel()
        setClipboard("   ")
        p.capture()
        assertTrue(p.entries().isEmpty())
    }
}
