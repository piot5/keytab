package com.piotv.keytab.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // Seit v0.18 wird erst *nach* dem ersten Load geschrieben: ein Capture
        // davor würde die Datei überschreiben, bevor sie gelesen ist (G4). Der
        // Looper holt Load und das gemerkte Schreiben nach.
        ShadowLooper.runUiThreadTasks()
        // Zweite Instanz: lädt beim Init die persistierte Historie — das Datei-
        // Lesen läuft seit v0.18 auf dem ioDispatcher (Main-Thread im IME-Aufbau
        // wird nicht mehr blockiert), das Anwenden über den Main-Handler.
        val p2 = panel()
        ShadowLooper.runUiThreadTasks()
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

    // ---------- Race: Schreiben vor dem ersten Load (v0.18) ----------
    //
    // v0.18 hat das Lesen der Historie vom Konstruktor auf den ioDispatcher
    // verschoben (der Main-Thread im IME-Aufbau soll nicht blockieren). Ohne
    // Sperre schrieb ein früher Capture die Datei, *bevor* der Load sie gelesen
    // hatte — die gespeicherte Historie war dann dauerhaft weg. Diese beiden
    // Tests halten die Reihenfolge fest (Datei lesen → mergen → schreiben).

    @Test
    fun `Capture vor dem Laden verliert die gespeicherte Historie nicht`() {
        setClipboard("alt")
        panel().onSelected()
        ShadowLooper.runUiThreadTasks() // Datei enthält jetzt "alt"
        setClipboard("neu")
        val p = panel()                 // Load dieser Instanz hängt noch am Looper
        p.onSelected()                  // Capture VOR dem Load
        assertEquals(listOf("neu"), p.entries())
        ShadowLooper.runUiThreadTasks() // Load + Nachschreiben
        assertEquals("geladene Historie bleibt erhalten", listOf("neu", "alt"), p.entries())
        val file = java.io.File(app.filesDir, "clipboard_history.txt").readText()
        assertTrue("Datei enthält den alten Eintrag: '$file'", file.contains("alt"))
        assertTrue("Datei enthält den neuen Eintrag: '$file'", file.contains("neu"))
    }

    @Test
    fun `Clear vor dem Laden holt die alte Historie nicht zurueck`() {
        setClipboard("alt")
        panel().onSelected()
        ShadowLooper.runUiThreadTasks()
        val p = panel()                 // Load dieser Instanz hängt noch am Looper
        p.clear()                       // Nutzer leert, bevor der Load ankommt
        ShadowLooper.runUiThreadTasks()
        assertTrue("Load darf die geleerte Liste nicht wieder füllen", p.entries().isEmpty())
        val file = java.io.File(app.filesDir, "clipboard_history.txt")
        if (file.exists()) {
            assertFalse("Datei darf den gelöschten Eintrag nicht mehr enthalten",
                file.readText().contains("alt"))
        }
    }


    @org.junit.Before
    fun clearHistoryFile() {
        // Eigene History-Datei pro Test: persistAsync schreibt synchron
        // (directExecutor), das Panel liest sie beim Init (asynchron über den
        // ioDispatcher) — ohne Clear würden Tests derselben Klasse die Datei teilen.
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
        val p2 = panel()
        ShadowLooper.runUiThreadTasks()
        assertTrue("Datei ist leer, also lädt auch die neue Instanz nichts", p2.entries().isEmpty())
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

    @Test
    fun `zu grosser Clipboard-Inhalt wird nicht aufgenommen`() {
        val errors = mutableListOf<String>()
        val p = panel(onError = { errors += it })

        setClipboard("x".repeat(TextEditLogic.MAX_STORED_TEXT_CHARS + 1))
        p.capture()
        assertTrue("Historie bleibt leer", p.entries().isEmpty())
        assertEquals(
            "Fehler wird gemeldet statt still zu schlucken",
            listOf(app.getString(com.piotv.keytab.R.string.clip_entry_too_large)),
            errors
        )

        // Gegenprobe: genau auf der Grenze wird aufgenommen.
        setClipboard("y".repeat(TextEditLogic.MAX_STORED_TEXT_CHARS))
        p.capture()
        assertEquals("Gegenprobe: an der Grenze wird gespeichert", 1, p.entries().size)
    }
}
