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
    private val onEdit: (File) -> Unit = {},
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

    /** Snippet in die Vorschlags-History (Preferences) eintragen. */
    private fun recordRecent(text: String) {
        val p = com.piotv.keytab.Prefs.of(context)
        p.edit().putString(
            com.piotv.keytab.Prefs.KEY_RECENT_SNIPPETS,
            SuggestionEngine.recordRecent(
                p.getString(com.piotv.keytab.Prefs.KEY_RECENT_SNIPPETS, null), text)
        ).apply()
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
            snippets.getOrNull(position)?.let { snip ->
                recordRecent(snip.text)
                onCommit(snip.text)
            }
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            snippets.getOrNull(position)?.let {
                Toast.makeText(context, "${it.name}: ${it.text}", Toast.LENGTH_LONG).show()
            }
            true
        }
        setupHeader(root)
    }

    /** ✎-Button: Datei im Editor-Tab öffnen. */
    private fun setupHeader(root: View) {
        root.findViewById<View>(R.id.snip_edit)?.setOnClickListener { onEdit(file()) }
    }

    /** Fügt einen Clipboard-Eintrag als benanntes Snippet hinzu. */
    fun addFromClipboard(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val normalized = clean.replace("\\n", "\n")
        val firstPart = normalized.lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
            .take(40)
            .replace('=', '-')
            .replace('#', '-')
            .trim()
        val name = firstPart.ifEmpty { "clip_${System.currentTimeMillis()}" }
        val escaped = clean.replace("\\", "\\\\").replace("\n", "\\n")
        appendAsync("$name = $escaped")
        Toast.makeText(context, R.string.snip_added_from_clip, Toast.LENGTH_SHORT).show()
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
