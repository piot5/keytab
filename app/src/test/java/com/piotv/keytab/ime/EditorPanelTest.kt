package com.piotv.keytab.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/** Direkter Executor: Runnables laufen synchron → deterministische Tests. */
internal val directExecutor: Executor = Executor { it.run() }

internal fun inflateKeyboardRoot(context: Context): View =
    LayoutInflater.from(ContextThemeWrapper(context, R.style.Theme_KeyTab))
        .inflate(R.layout.keyboard_view, null)

/**
 * Editor-Panel: Einfügen/Löschen am Cursor, Wort-Löschen, Gutter-Nummerierung,
 * Highlight-Spans. (Aus PanelsTest.kt getrennt — Name = Klasse.)
 */
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

    @Test
    fun `Pfeil nach oben sendet ohne Auswahl den gesamten Editorinhalt`() {
        val root = inflateKeyboardRoot(app)
        val sent = mutableListOf<String>()
        EditorPanel(app, root, directExecutor, Handler(Looper.getMainLooper()), sent::add)
        val et = root.findViewById<EditText>(R.id.editor_input)
        et.setText("ganzer Text")
        et.setSelection(et.length())
        root.findViewById<View>(R.id.btn_editor_send_up).performClick()
        assertEquals(listOf("ganzer Text"), sent)
    }

    @Test
    fun `Pfeil nach oben sendet nur die markierte Stelle`() {
        val root = inflateKeyboardRoot(app)
        val sent = mutableListOf<String>()
        EditorPanel(app, root, directExecutor, Handler(Looper.getMainLooper()), sent::add)
        val et = root.findViewById<EditText>(R.id.editor_input)
        et.setText("vorher markiert nachher")
        et.setSelection(7, 15)
        root.findViewById<View>(R.id.btn_editor_send_up).performClick()
        assertEquals(listOf("markiert"), sent)
    }

    @Test
    fun `Gutter nummeriert logische Zeilen vor dem ersten Layout`() {
        val root = inflateKeyboardRoot(app)
        EditorPanel(app, root, directExecutor, Handler(Looper.getMainLooper()))
        val et = root.findViewById<EditText>(R.id.editor_input)
        val gutter = root.findViewById<TextView>(R.id.editor_gutter)
        et.setText("a\nb\nc")
        // Ohne Breite (Robolectric layoutet nicht) zählt die Logik logische Zeilen
        assertEquals("1\n2\n3", gutter.text.toString())
    }

    @Test
    fun `Highlight-Spans werden gesetzt und beim Leeren entfernt`() {
        val root = inflateKeyboardRoot(app)
        EditorPanel(app, root, directExecutor, Handler(Looper.getMainLooper()))
        val et = root.findViewById<EditText>(R.id.editor_input)
        et.setText("# Kommentar")
        assertTrue(et.editableText.getSpans(0, et.length(), android.text.style.ForegroundColorSpan::class.java).isNotEmpty())
        et.setText("")
        assertTrue(et.editableText.getSpans(0, 0, android.text.style.ForegroundColorSpan::class.java).isEmpty())
    }

    @Test
    fun `zu grosse Datei wird nicht geladen - Editor bleibt unveraendert`() {
        // OOM-Schutz (v0.18): ohne Groessen-Gate wuerde die Datei komplett
        // gelesen und in den EditText gesetzt.
        val root = inflateKeyboardRoot(app)
        val panel = EditorPanel(app, root, directExecutor, Handler(Looper.getMainLooper()))
        val et = root.findViewById<EditText>(R.id.editor_input)
        et.setText("unveraendert")
        val big = java.io.File(app.cacheDir, "zu_gross.txt")
        big.writeText("x".repeat((EditorTextState.MAX_EDITOR_BYTES + 1).toInt()))
        try {
            panel.openFile(big)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Editor-Inhalt bleibt unangetastet", "unveraendert", et.text.toString())
        } finally {
            big.delete()
        }
    }

    @Test
    fun `kleine Datei wird geladen - Gegenprobe zum Groessen-Gate`() {
        val root = inflateKeyboardRoot(app)
        val panel = EditorPanel(app, root, directExecutor, Handler(Looper.getMainLooper()))
        val et = root.findViewById<EditText>(R.id.editor_input)
        val small = java.io.File(app.cacheDir, "klein.txt")
        small.writeText("hallo welt")
        try {
            panel.openFile(small)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("hallo welt", et.text.toString())
        } finally {
            small.delete()
        }
    }

    @Test
    fun `clearEditor leert das Eingabefeld synchron` () {
        val root = inflateKeyboardRoot(app)
        val panel = EditorPanel(app, root, directExecutor, Handler(Looper.getMainLooper()))
        val et = root.findViewById<EditText>(R.id.editor_input)
        et.setText("hallo welt")
        panel.clearEditor()
        assertEquals("", et.text.toString())
    }

    @Test
    fun `openFile erhöht den Zähler offener Dateien` () {
        val root = inflateKeyboardRoot(app)
        val panel = object : EditorPanel(app, root, directExecutor, Handler(Looper.getMainLooper())) {
            @Volatile
            var openFilesCounter = 0
                get() = field
            override var openFiles: Int = 0
                set(value) {
                    field = value
                    openFilesCounter = value
                }
        }
        val et = root.findViewById<EditText>(R.id.editor_input)
        et.setText("eins")
        val small = java.io.File(app.cacheDir, "klein.txt")
        small.writeText("zwei")
        try {
            panel.openFile(small)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("zwei", et.text.toString())
            assertEquals(1, panel.openFilesCounter)
        } finally {
            small.delete()
        }
    }
}
