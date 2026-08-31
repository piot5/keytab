package com.piotv.keytab.ime

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Tab-Dateimanager des IME-Overlays: Navigation mit BackStack, Dateigrößen,
 * Einfügen des Dateipfads per Tipp. I/O auf [ioExecutor], UI über [mainHandler].
 */
class FileManagerPanel(
    private val context: Context,
    private val root: View,
    private val ioExecutor: Executor,
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
    // Datei für Kopieren→Einfügen zwischen Verzeichnissen
    private var pendingCopy: File? = null
    private val paste = root.findViewById<Button>(R.id.btn_paste_dir)

    private companion object {
        const val MAX_CLIP_CONTENT_BYTES = 1_000_000
        const val PREFS = "keytab_prefs"
        const val KEY_DIR = "fm_dir"
        const val KEY_BACKSTACK = "fm_backstack"
    }

    init {
        restoreSaved()
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
        // Long-Press: Kontextmenü (Eigenschaften, Kopieren, Einfügen …)
        list?.setOnItemLongClickListener { _, _, position, _ ->
            entries.getOrNull(position)?.let { showFileMenu(it) }
            true
        }
        paste?.setOnClickListener { pasteIntoCurrent() }
        paste?.visibility = if (pendingCopy != null) View.VISIBLE else View.GONE
        refresh()
    }

    private fun showFileMenu(e: FileEntry) {
        val f = e.file
        val items = mutableListOf(
            context.getString(R.string.fm_properties),
            context.getString(R.string.fm_copy_path),
            context.getString(R.string.fm_copy_file)
        )
        if (f.isFile) items.add(context.getString(R.string.fm_copy_content))
        val actions: List<Pair<String, () -> Unit>> = listOf(
            context.getString(R.string.fm_properties) to { showProperties(f) },
            context.getString(R.string.fm_copy_path) to { copyTextToClipboard(f.absolutePath) },
            context.getString(R.string.fm_copy_file) to {
                pendingCopy = f
                paste?.visibility = View.VISIBLE
                toast(context.getString(R.string.fm_copied, f.name))
            },
            context.getString(R.string.fm_copy_content) to { copyFileContent(f) }
        )
        AlertDialog.Builder(context)
            .setTitle(e.label)
            .setItems(items.toTypedArray()) { _, which -> actions.firstOrNull { it.first == items[which] }?.second?.invoke() }
            .show()
    }

    private fun showProperties(f: File) {
        val type = context.getString(if (f.isDirectory) R.string.fm_type_dir else R.string.fm_type_file)
        val modified = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(f.lastModified()))
        AlertDialog.Builder(context)
            .setTitle(f.name)
            .setMessage(context.getString(R.string.fm_props_fmt, f.absolutePath, type,
                TextEditLogic.formatSize(f.length()), modified))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun copyTextToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("KeyTab", text))
        toast(context.getString(R.string.fm_copied, text))
    }

    private fun copyFileContent(f: File) {
        ioExecutor.execute {
            val text = try {
                // Bugfix: Größenlimit, sonst OOM bei großen Dateien (Logs, DBs)
                if (f.length() > MAX_CLIP_CONTENT_BYTES) null else f.readText()
            } catch (_: Exception) { null }
            mainHandler.post {
                if (text == null) toast(context.getString(R.string.fm_paste_failed, f.name))
                else {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText(f.name, text))
                    toast(context.getString(R.string.fm_copied, f.name))
                }
            }
        }
    }

    private fun pasteIntoCurrent() {
        val src = pendingCopy ?: return
        val dir = currentDir ?: return
        val dst = File(dir, src.name)
        // Bugfix: Eigene Verzeichniskopie würde die Quelle zerstören (copyTo trunciert bei src==dst)
        if (dst.absolutePath == src.absolutePath) {
            toast(context.getString(R.string.fm_paste_failed, src.name))
            return
        }
        ioExecutor.execute {
            val ok = try {
                if (src.isDirectory) {
                    // Rekursives Kopieren für Ordner (copyTo kopiert nicht rekursiv)
                    src.copyRecursively(dst, overwrite = true)
                } else {
                    src.copyTo(dst, overwrite = true)
                }
                dst.exists()
            } catch (_: Exception) { false }
            mainHandler.post {
                pendingCopy = if (ok) null else pendingCopy
                paste?.visibility = if (ok) View.GONE else View.VISIBLE
                if (ok) refresh()
                toast(context.getString(if (ok) R.string.fm_pasted else R.string.fm_paste_failed,
                    if (ok) dst.name else src.name))
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    /** Persistiert das aktuelle Verzeichnis + BackStack, damit der Zustand
     *  über Tastatur-Neustarts bzw. App-Neustarts hinweg erhalten bleibt. */
    private fun persistDir() {
        val dir = currentDir ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DIR, dir.absolutePath)
            .putString(KEY_BACKSTACK, backStack.joinToString("\n") { it.absolutePath })
            .apply()
    }

    /** Stellt das zuletzt besuchte Verzeichnis + BackStack wieder her (falls vorhanden/lesbar). */
    private fun restoreSaved() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DIR, null)?.let { p ->
            File(p).takeIf { it.isDirectory && it.canRead() }?.let { currentDir = it }
        }
        val stack = prefs.getString(KEY_BACKSTACK, null)
            ?.split("\n")
            ?.mapNotNull { File(it).takeIf { f -> f.isDirectory && f.canRead() } }
        if (!stack.isNullOrEmpty()) {
            backStack.clear()
            backStack.addAll(stack)
        }
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
        persistDir()
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
                list?.adapter = themedAdapter(context, entries.map { it.label })
            }
        }
    }
}