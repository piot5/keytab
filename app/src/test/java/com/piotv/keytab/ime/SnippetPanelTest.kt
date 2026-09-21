package com.piotv.keytab.ime

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ListView
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Snippet-Panel: Datei-Erzeugung mit Defaults, Parser-Regeln (Kommentare,
 * Leerzeilen, Fehlformat, escaped newline), Tap → onCommit + Recent-History.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnippetPanelTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun snippetsFile(): File {
        val dir = app.getExternalFilesDir(null) ?: app.filesDir
        return File(dir, "keytab_snippets.txt")
    }

    private fun names(root: View): List<String> {
        val list = root.findViewById<ListView>(R.id.snippet_list)
        return (0 until list.adapter.count).map { list.adapter.getItem(it).toString() }
    }

    private fun commit(list: ListView, position: Int) {
        list.performItemClick(list, position, list.getItemIdAtPosition(position))
    }

    @Test
    fun `onSelected erzeugt Standarddatei bei Fehlen und listet die Defaults`() {
        snippetsFile().delete()
        val root = inflateKeyboardRoot(app)
        val panel = SnippetPanel(app, directExecutor, mainHandler) {}
        panel.onSelected(root)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Defaults-Datei wurde erzeugt", snippetsFile().isFile)
        assertEquals(5, names(root).size)
        assertEquals("git status", names(root).first())
    }

    @Test
    fun `Parser ignoriert Kommentare Leerzeilen und Zeilen ohneassignment`() {
        snippetsFile().writeText(
            """
            # Kommentarzeile
            ok = echo hallo

            keinezuweisung
            = ohnename
            mehrzeilig = erste\nzweite
            """.trimIndent()
        )
        val root = inflateKeyboardRoot(app)
        val panel = SnippetPanel(app, directExecutor, mainHandler) {}
        panel.onSelected(root)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("ok", "mehrzeilig"), names(root))
    }

    @Test
    fun `Tap auf Eintrag commitet den Text und entschaedelt escaped newline`() {
        val committed = mutableListOf<String>()
        snippetsFile().writeText("greet = hallo welt\nmulti = a\\nb\n")
        val root = inflateKeyboardRoot(app)
        val panel = SnippetPanel(app, directExecutor, mainHandler) { committed.add(it) }
        panel.onSelected(root)
        shadowOf(Looper.getMainLooper()).idle()
        val list = root.findViewById<ListView>(R.id.snippet_list)
        commit(list, 0)
        assertEquals(1, committed.size)
        assertEquals("hallo welt", committed[0])
        commit(list, 1)
        assertEquals(2, committed.size)
        assertEquals("a\nb", committed[1])
    }

    @Test
    fun `Tap uebernimmt den Text in die Recent-History der Preferences ein`() {
        snippetsFile().writeText("rec = merk mich\n")
        val root = inflateKeyboardRoot(app)
        val panel = SnippetPanel(app, directExecutor, mainHandler) {}
        panel.onSelected(root)
        shadowOf(Looper.getMainLooper()).idle()
        val list = root.findViewById<ListView>(R.id.snippet_list)
        list.performItemClick(list, 0, list.getItemIdAtPosition(0))
        val prefs = com.piotv.keytab.Prefs.of(app)
        val recent = prefs.getString(com.piotv.keytab.Prefs.KEY_RECENT_SNIPPETS, null)
        assertTrue("Recent-History sollte den Snippet-Text enthalten", recent?.contains("merk mich") == true)
    }

    @Test
    fun `leere Snippet-Datei listet nichts`() {
        snippetsFile().writeText("# nur ein Kommentar\n")
        val root = inflateKeyboardRoot(app)
        val panel = SnippetPanel(app, directExecutor, mainHandler) {}
        panel.onSelected(root)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, names(root).size)
    }
}
