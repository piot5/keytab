package com.piotv.keytab.ime

import android.app.AlertDialog
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.piotv.keytab.R
import java.io.File
import java.util.concurrent.Executor

/**
 * Editor-Panel der IME (Notes-Tab): Eingabefeld + Speichern/Laden.
 * "Load" öffnet einen Ordner-Browser (Dialog mit ListView): Ordner antippen =
 * hineinnavigieren, "▲ …" = nach oben, Datei antippen = laden. Save schreibt
 * in die zuletzt geladene/gewählte Datei (Default: keytab_editor.txt).
 * Datei-I/O läuft auf [ioExecutor], UI-Updates kehren über [mainHandler] zurück.
 */
class EditorPanel(
    private val context: Context,
    private val rootView: View,
    private val ioExecutor: Executor,
    private val mainHandler: Handler
) {

    private val input: EditText? = rootView.findViewById(R.id.editor_input)
    private val fileLabel: TextView? = rootView.findViewById(R.id.editor_file)
    private var editorFile: File = defaultFile()

    init {
        fileLabel?.text = editorFile.name
        setupSave(rootView)
        setupLoad(rootView)
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
            val f = editorFile
            val text = input?.text?.toString() ?: return@setOnClickListener
            ioExecutor.execute {
                try {
                    f.parentFile?.mkdirs()
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
            showFilePicker()
        }
    }

    /** Ordneransicht zur Datei-Auswahl (Load): Dialog mit navigierbarer ListView. */
    private fun showFilePicker() {
        var dir: File? = editorFile.takeIf { it.parentFile?.isDirectory == true }?.parentFile
            ?: Environment.getExternalStorageDirectory()
            ?: context.filesDir
        var dialog: AlertDialog? = null
        val listView = ListView(context)
        listView.background = android.graphics.drawable.ColorDrawable(
            androidx.core.content.ContextCompat.getColor(context, R.color.list_bg))
        fun label(f: File) = if (f.isDirectory) "📁 ${f.name}/" else "📄 ${f.name}"
        fun refreshList(d: File) {
            dir = d
            ioExecutor.execute {
                val raw = try { d.listFiles() } catch (_: Exception) { null }
                val visible = raw?.filter { !it.isHidden }
                    ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
                    ?: emptyList()
                val items = visible.map { label(it) }
                mainHandler.post {
                    // Veraltetes Ergebnis verwerfen, falls weiter navigiert wurde
                    if (dir?.absolutePath != d.absolutePath) return@post
                    val withUp = if (d.parentFile != null)
                        listOf(context.getString(R.string.editor_pick_parent)) + items
                    else if (items.isEmpty())
                        listOf(context.getString(R.string.editor_pick_empty))
                    else items
                    listView.adapter = themedAdapter(context, withUp)
                    dialog?.setTitle("${context.getString(R.string.editor_load_title)} · ${d.name}")
                }
            }
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = (listView.adapter?.getItem(position) as? String)
                ?: return@setOnItemClickListener
            if (item == context.getString(R.string.editor_pick_parent)) {
                dir?.parentFile?.let { refreshList(it) }
                return@setOnItemClickListener
            }
            val target = dir?.listFiles()?.firstOrNull { label(it) == item }
                ?: return@setOnItemClickListener
            if (target.isDirectory) {
                refreshList(target)
            } else {
                dialog?.dismiss()
                loadFile(target)
            }
        }
        dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.editor_load_title))
            .setView(listView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        // WICHTIG (IME ohne Activity): Dialogfenster an das IME-Fenster anhängen,
        // sonst BadTokenException ("token null is not valid")
        dialog.window?.apply {
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
            rootView.windowToken?.let { attributes.token = it }
        }
        dialog.show()
        dir?.let { refreshList(it) }
    }

    /** Lädt [f] asynchron in den Editor und merkt sie als Save-Ziel. */
    private fun loadFile(f: File) {
        ioExecutor.execute {
            try {
                val content = if (f.exists()) f.readText() else ""
                mainHandler.post {
                    editorFile = f
                    fileLabel?.text = f.name
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


    private fun defaultFile(): File {
        val pub = Environment.getExternalStorageDirectory()
        val dir = if (pub != null && pub.isDirectory && pub.canWrite()) pub
        else context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, "keytab_editor.txt")
    }
}