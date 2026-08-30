package com.piotv.keytab.ime

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.R
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService

/**
 * Tab-Dateimanager des IME-Overlays: Navigation mit BackStack, Dateigrößen,
 * Einfügen des Dateipfads per Tipp. I/O auf [ioExecutor], UI über [mainHandler].
 */
class FileManagerPanel(
    private val context: Context,
    private val root: View,
    private val ioExecutor: ExecutorService,
    private val mainHandler: Handler,
    private val onCommit: (String) -> Unit
) {

    private data class FileEntry(val file: File, val label: String, val info: String)

    private val list = root.findViewById<ListView>(R.id.file_list)
    private val dirLabel = root.findViewById<TextView>(R.id.file_dir)
    private val hint = root.findViewById<TextView>(R.id.file_hint)
    private val back = root.findViewById<Button>(R.id.btn_back_dir)
    private val up = root.findViewById<Button>(R.id.btn_up_dir)

    private var currentDir: File? = null
    private val backStack = mutableListOf<File>()
    private var entries: List<FileEntry> = emptyList()

    init {
        if (currentDir == null) {
            val pub = Environment.getExternalStorageDirectory()
            currentDir = if (pub?.isDirectory == true && pub.canRead()) pub
            else context.getExternalFilesDir(null)?.parentFile?.let { File(it, "Download") }?.takeIf { it.isDirectory }
            ?: File("/")
        }

        back?.setOnClickListener {
            if (backStack.isNotEmpty()) {
                val previous = backStack.removeAt(backStack.lastIndex)
                currentDir = previous
                refresh()
            }
        }

        up?.setOnClickListener {
            currentDir?.parentFile?.let { navigate(it) }
        }

        list?.setOnItemClickListener { _, _, position, _ ->
            val e = entries.getOrNull(position) ?: return@setOnItemClickListener
            if (e.file.isDirectory) {
                navigate(e.file)
            } else {
                onCommit(e.file.absolutePath)
                root.findViewById<TabLayout>(R.id.ime_tabs)?.getTabAt(0)?.select()
            }
        }
        refresh()
    }

    /** Wird beim Wechsel auf den Files-Tab aufgerufen (aktualisiert die Liste). */
    fun show() {
        refresh()
    }

    private fun navigate(to: File) {
        if (to.absolutePath != currentDir?.absolutePath) {
            backStack.add(currentDir!!)
        }
        currentDir = to
        refresh()
    }

    private fun refresh() {
        val dir = currentDir ?: return
        dirLabel?.text = dir.absolutePath
        back?.visibility = if (backStack.isNotEmpty()) View.VISIBLE else View.GONE
        up?.visibility = if (dir.parentFile != null) View.VISIBLE else View.GONE
        hint?.text = context.getString(R.string.fm_loading)
        // listFiles() kann bei großen Verzeichnissen langsam sein → Hintergrund-Thread
        val target = dir
        ioExecutor.execute {
            val raw = try { target.listFiles() } catch (_: Exception) { null }
            val loaded: List<FileEntry> = raw
                ?.filter { !it.isHidden }
                ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
                ?.map { f ->
                    if (f.isDirectory) FileEntry(f, "\uD83D\uDCC1 ${f.name}/", context.getString(R.string.fm_entry_dir))
                    else FileEntry(f, "\uD83D\uDCC4 ${f.name}", TextEditLogic.formatSize(f.length()))
                }
                ?: emptyList()
            val dirs = raw?.count { it.isDirectory } ?: 0
            val files = raw?.count { it.isFile } ?: 0
            mainHandler.post {
                // Ergebnis verwerfen, wenn inzwischen navigiert wurde
                if (currentDir?.absolutePath != target.absolutePath) return@post
                entries = loaded
                hint?.text = if (raw == null) context.getString(R.string.fm_no_storage_access)
                else context.getString(R.string.fm_dir_summary, dirs, files)
                list?.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, entries.map { it.label })
            }
        }
    }
}