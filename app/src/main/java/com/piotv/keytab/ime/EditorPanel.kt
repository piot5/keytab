package com.piotv.keytab.ime

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.piotv.keytab.R
import java.io.File
import java.util.concurrent.Executor

/**
 * Editor-Panel der IME: Eingabefeld + Speichern/Laden in eine feste Datei.
 * Datei-I/O läuft auf [ioExecutor], UI-Updates kehren über [mainHandler] zurück.
 */
class EditorPanel(
    private val context: Context,
    root: View,
    private val ioExecutor: Executor,
    private val mainHandler: Handler
) {

    private val input: EditText? = root.findViewById(R.id.editor_input)
    private val fileLabel: TextView? = root.findViewById(R.id.editor_file)

    init {
        fileLabel?.text = editorFile().name
        setupSave(root)
        setupLoad(root)
    }

    /** Fügt Text an der Cursorposition ein (ersetzt eine Selektion). */
    fun insert(text: String) {
        val et = input ?: return
        val editable = et.text ?: return
        var start = et.selectionStart.coerceIn(0, editable.length)
        val end = et.selectionEnd.coerceIn(start, editable.length)
        editable.replace(start, end, text)
        start += text.length
        et.setSelection(start)
    }

    /** Löscht im Editor: ein Zeichen oder bis zum Wortanfang ([word] = true). */
    fun delete(word: Boolean) {
        val et = input ?: return
        val editable = et.text ?: return
        val cursor = et.selectionEnd.coerceIn(0, editable.length)
        if (cursor == 0) return
        if (word) {
            val start = TextEditLogic.wordStartIndex(editable, cursor)
            if (start < cursor) {
                editable.delete(start, cursor)
                et.setSelection(start)
            } else {
                editable.delete(cursor - 1, cursor)
                et.setSelection(cursor - 1)
            }
        } else {
            editable.delete(cursor - 1, cursor)
            et.setSelection(cursor - 1)
        }
    }

    private fun setupSave(root: View) {
        root.findViewById<Button>(R.id.btn_editor_save)?.setOnClickListener {
            val f = editorFile()
            val text = input?.text?.toString() ?: return@setOnClickListener
            ioExecutor.execute {
                try {
                    f.writeText(text)
                    mainHandler.post {
                        Toast.makeText(context, context.getString(R.string.editor_saved, f.name), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        Toast.makeText(context, context.getString(R.string.editor_save_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupLoad(root: View) {
        root.findViewById<Button>(R.id.btn_editor_load)?.setOnClickListener {
            val f = editorFile()
            ioExecutor.execute {
                try {
                    val content = if (f.exists()) f.readText() else ""
                    mainHandler.post {
                        input?.setText(content)
                        Toast.makeText(context, if (f.exists()) context.getString(R.string.editor_loaded, f.name)
                        else context.getString(R.string.editor_file_empty), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        Toast.makeText(context, context.getString(R.string.editor_load_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun editorFile(): File {
        val pub = Environment.getExternalStorageDirectory()
        val dir = if (pub != null && pub.isDirectory && pub.canWrite()) pub
        else context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, "keytab_editor.txt")
    }
}