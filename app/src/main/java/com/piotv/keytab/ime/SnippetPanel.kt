package com.piotv.keytab.ime

import android.content.Context
import android.os.Handler
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import com.piotv.keytab.R
import java.io.File
import java.util.concurrent.Executor

/**
 * Snippet-Panel der IME (v0.9.7, ersetzt das ehemalige Swipe-Feature):
 * benannte Befehle/Snippets als eigener Tab. Quell-Datei:
 *   <externes Files-Dir>/keytab_snippets.txt
 * Format pro Zeile:   name = text   (Leerzeichen um '=' optional, '#' = Kommentar)
 * Tap auf einen Eintrag fügt den Text ins aktive Eingabefeld ein,
 * Long-Press zeigt den Text als Vorschau.
 *
 * Fehlt die Datei, wird sie mit Beispieleinträgen erzeugt (direkt im
 * Files-Tab editierbar). I/O auf [ioExecutor], UI über [mainHandler].
 */
class SnippetPanel(
    private val context: Context,
    private val ioExecutor: Executor,
    private val mainHandler: Handler,
    private val onCommit: (String) -> Unit
) {

    private companion object {
        const val FILE_NAME = "keytab_snippets.txt"
        val DEFAULTS = listOf(
            "git status" to "git status",
            "git log" to "git log --oneline -10",
            "gradle test" to "./gradlew test",
            "ls lang" to "ls -la",
            "pfeile" to "←↑↓→"
        )
    }

    private data class Snippet(val name: String, val text: String)

    private val snippets = mutableListOf<Snippet>()

    private var lastRoot: View? = null

    private fun file(): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, FILE_NAME)
    }

    /** Snippets (neu) laden — asynchron, UI-Refresh danach. */
    fun onSelected(root: View) {
        lastRoot = root
        ioExecutor.execute {
            val f = file()
            if (!f.isFile) {
                runCatching {
                    f.parentFile?.mkdirs()
                    f.writeText(DEFAULTS.joinToString("\n") { "${it.first} = ${it.second}" })
                }
            }
            val parsed = runCatching { parse(f.readText()) }.getOrDefault(emptyList())
            mainHandler.post {
                snippets.clear()
                snippets.addAll(parsed)
                refreshList(root)
            }
        }
    }

    /** Parst "name = text"-Zeilen; '#' Kommentare und Leerzeilen werden ignoriert. */
    private fun parse(text: String): List<Snippet> = buildList {
        for (raw in text.lines()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val idx = line.indexOf('=')
            if (idx <= 0) continue
            val name = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim().replace("\\n", "\n")
            if (name.isNotEmpty() && value.isNotEmpty()) add(Snippet(name, value))
        }
    }

    /** Snippets in den ListView des Snip-Tabs rendern. */
    fun refreshList(root: View) {
        val list = root.findViewById<ListView>(R.id.snippet_list) ?: return
        if (snippets.isEmpty()) {
            Toast.makeText(context, R.string.snippet_hint_empty, Toast.LENGTH_SHORT).show()
        }
        list.adapter = themedAdapter(context, snippets.map { it.name })
        list.setOnItemClickListener { _, _, position, _ ->
            snippets.getOrNull(position)?.let { onCommit(it.text) }
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            snippets.getOrNull(position)?.let {
                Toast.makeText(context, "${it.name}: ${it.text}", Toast.LENGTH_LONG).show()
            }
            true
        }
        setupHeader(root)
    }

    /** ＋/✎-Buttons: Snippet hinzufügen bzw. keytab_snippets.txt direkt bearbeiten. */
    private fun setupHeader(root: View) {
        root.findViewById<View>(R.id.snip_add)?.setOnClickListener { showAddDialog() }
        root.findViewById<View>(R.id.snip_edit)?.setOnClickListener { showEditDialog() }
    }

    /** Dialog: neues Snippet (Name + Text) ans Ende der Datei anhängen. */
    private fun showAddDialog() {
        val name = android.widget.EditText(context).apply {
            hint = context.getString(R.string.snip_name_hint)
            setSingleLine(true)
        }
        val text = android.widget.EditText(context).apply {
            hint = context.getString(R.string.snip_text_hint)
            minLines = 2
        }
        val box = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((16 * resources.displayMetrics.density).toInt(), 0,
                (16 * resources.displayMetrics.density).toInt(), 0)
            addView(name)
            addView(text)
        }
        android.app.AlertDialog.Builder(context)
            .setTitle(R.string.snip_add)
            .setView(box)
            .setPositiveButton(R.string.snip_save) { _, _ ->
                val n = name.text.toString().trim()
                val t = text.text.toString().trim().replace("\n", "\\n")
                if (n.isNotEmpty() && t.isNotEmpty()) {
                    appendAsync("$n = $t")
                    Toast.makeText(context, R.string.snip_added, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.snip_cancel, null)
            .show()
    }

    /** Dialog: komplette Datei als Text bearbeiten (mehrzeilig) und speichern. */
    private fun showEditDialog() {
        ioExecutor.execute {
            val f = file()
            val content = runCatching {
                if (f.isFile) f.readText() else DEFAULTS.joinToString("\n") { "${it.first} = ${it.second}" }
            }.getOrDefault("")
            mainHandler.post {
                val editor = android.widget.EditText(context).apply {
                    setText(content)
                    minLines = 8
                    setHorizontallyScrolling(false)
                    gravity = android.view.Gravity.TOP
                }
                android.app.AlertDialog.Builder(context)
                    .setTitle(R.string.snip_edit)
                    .setView(editor)
                    .setPositiveButton(R.string.snip_save) { _, _ ->
                        writeAsync(editor.text.toString())
                        Toast.makeText(context, R.string.snip_saved, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.snip_cancel, null)
                    .show()
            }
        }
    }

    /** Zeile anhängen und Liste neu laden. */
    private fun appendAsync(line: String) {
        ioExecutor.execute {
            runCatching {
                val f = file()
                f.parentFile?.mkdirs()
                f.appendText(if (f.isFile && f.length() > 0) "\n$line" else line)
            }
            lastRoot?.let { root -> mainHandler.post { onSelected(root) } }
        }
    }

    /** Datei komplett überschreiben und Liste neu laden. */
    private fun writeAsync(content: String) {
        ioExecutor.execute {
            runCatching {
                val f = file()
                f.parentFile?.mkdirs()
                f.writeText(content)
            }
            lastRoot?.let { root -> mainHandler.post { onSelected(root) } }
        }
    }
}
