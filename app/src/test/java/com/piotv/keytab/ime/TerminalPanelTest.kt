package com.piotv.keytab.ime

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Terminal-Panel: Prompt-Aufbau (user@host, ~ für Home), cd-Verfolgung im
 * Verlauf, Insert/Delete am Cursor, Send leert das Feld. Die Shell selbst
 * läuft unter Robolectric (echter /system/bin/sh im JVM-Prozess).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TerminalPanelTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun panel(root: View) = TerminalPanel(app, root, mainHandler)
    private fun input(root: View) = root.findViewById<EditText>(R.id.terminal_input)
    private fun output(root: View) = root.findViewById<TextView>(R.id.term_output)

    @Test
    fun `Prompt folgt dem Home-Verzeichnis mit Tilde`() {
        val root = inflateKeyboardRoot(app)
        panel(root)
        val out = output(root).text.toString()
        assertTrue("Prompt sollte ~ für filesDir enthalten:\n$out",
            Regex(".+:~\\$ ").containsMatchIn(out))
    }

    @Test
    fun `cd verfolgt das Verzeichnis im Prompt`() {
        File(app.filesDir, "workdir").mkdirs()
        val root = inflateKeyboardRoot(app)
        val p = panel(root)
        input(root).setText("cd workdir")
        p.send()
        input(root).setText("pwd")
        p.send()
        val out = output(root).text.toString()
        assertTrue("Zweites Echo sollte den neuen Prompt mit workdir zeigen:\n$out",
            out.contains(":~/workdir$ "))
    }

    @Test
    fun `cd in unbekanntes Verzeichnis faellt auf Home zurueck`() {
        File(app.filesDir, "workdir").mkdirs()
        val root = inflateKeyboardRoot(app)
        val p = panel(root)
        input(root).setText("cd workdir")
        p.send()
        input(root).setText("cd /definitiv/nicht/vorhanden")
        p.send()
        input(root).setText("pwd")
        p.send()
        val out = output(root).text.toString()
        assertTrue("Zweites Echo sollte den Unterordner zeigen:\n$out",
            out.contains(":~/workdir$ "))
        assertTrue("Nach ungültigem cd sollte der Prompt wieder ~ zeigen:\n$out",
            out.contains(":~$ pwd"))
    }

    @Test
    fun `send leert das Eingabefeld und schreibt Zeile mit Prompt in den Verlauf`() {
        val root = inflateKeyboardRoot(app)
        val p = panel(root)
        input(root).setText("pwd")
        p.send()
        assertEquals("", input(root).text.toString())
        val out = output(root).text.toString()
        assertTrue("Echo-Zeile sollte Prompt + Befehl enthalten:\n$out", out.contains(":~$ pwd"))
    }

    @Test
    fun `leerer Befehl wird nicht ausgefuehrt`() {
        val root = inflateKeyboardRoot(app)
        val p = panel(root)
        val before = output(root).text.toString()
        input(root).setText("   ")
        p.send()
        assertEquals(before, output(root).text.toString())
    }

    @Test
    fun `insert fuegt am Cursor ein und setzt ihn ans Ende des Eingefuegten`() {
        val root = inflateKeyboardRoot(app)
        val p = panel(root)
        input(root).setText("ab")
        input(root).setSelection(1)
        p.insert("X")
        assertEquals("aXb", input(root).text.toString())
        assertEquals(2, input(root).selectionEnd)
    }

    @Test
    fun `delete loescht ein Zeichen vor dem Cursor`() {
        val root = inflateKeyboardRoot(app)
        val p = panel(root)
        input(root).setText("abc")
        input(root).setSelection(2)
        p.delete(false)
        assertEquals("ac", input(root).text.toString())
    }

    @Test
    fun `delete word loescht bis zum Wortanfang`() {
        val root = inflateKeyboardRoot(app)
        val p = panel(root)
        input(root).setText("cd files")
        input(root).setSelection(8)
        p.delete(true)
        assertEquals("cd ", input(root).text.toString())
    }

    @Test
    fun `deleteBefore loescht die angegebene Zeichenmenge vor dem Cursor`() {
        val root = inflateKeyboardRoot(app)
        val p = panel(root)
        input(root).setText("cd file")
        input(root).setSelection(7)
        p.deleteBefore(4)
        assertEquals("cd ", input(root).text.toString())
    }

    @Test
    fun `cursorContext liefert Text und Cursorposition`() {
        val root = inflateKeyboardRoot(app)
        val p = panel(root)
        input(root).setText("hi")
        input(root).setSelection(2)
        val ctx = p.cursorContext()
        assertEquals("hi", ctx?.first?.toString())
        assertEquals(2, ctx?.second)
    }
}
