package com.piotv.keytab.ime

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Handler
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
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
        const val KEY_DIR = "fm_dir"
        const val KEY_BACKSTACK = "fm_backstack"

        /** Schriftgröße der Dateiliste (sp) — bewusst kleiner als der Plattform-Default. */
        const val LIST_TEXT_SP = 11f
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
            currentDir?.parentFile?.let { parent -> navigate(parent) }
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
        // Beschriftungen und Aktionen werden **paarweise** aufgebaut und über den
        // Listenindex aufgelöst: vorher wurde die Aktion per Label-Vergleich
        // gesucht — zwei gleiche Beschriftungen (künftige Übersetzung) hätten die
        // falsche Aktion ausgelöst.
        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        items += context.getString(R.string.fm_properties)
        actions += { showProperties(f) }
        items += context.getString(R.string.fm_copy_path)
        actions += { copyTextToClipboard(f.absolutePath) }
        items += context.getString(R.string.fm_copy_file)
        actions += {
            pendingCopy = f
            paste?.visibility = View.VISIBLE
            toast(context.getString(R.string.fm_copied, f.name))
        }
        if (f.isFile) {
            items += context.getString(R.string.fm_copy_content)
            actions += { copyFileContent(f) }
        }
        AlertDialog.Builder(context)
            .setTitle(e.label)
            .setItems(items.toTypedArray()) { _, which -> actions.getOrNull(which)?.invoke() }
            .create()
            .let(::showImeDialog)
    }

    /**
     * Zeigt [dialog] im IME an. Ohne Fenster-Token (kein Input-View aktiv) bleibt
     * nur ein Hinweis — vorher stürzte der Long-Press hier mit einer
     * `BadTokenException` ab.
     */
    private fun showImeDialog(dialog: AlertDialog) {
        if (!ImeDialog.show(dialog, root.windowToken)) {
            toast(context.getString(R.string.dialog_unavailable))
        }
    }

    private fun showProperties(f: File) {
        val type = context.getString(if (f.isDirectory) R.string.fm_type_dir else R.string.fm_type_file)
        val modified = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(f.lastModified()))
        AlertDialog.Builder(context)
            .setTitle(f.name)
            .setMessage(context.getString(R.string.fm_props_fmt, f.absolutePath, type,
                TextEditLogic.formatSize(f.length()), modified))
            .setPositiveButton(android.R.string.ok, null)
            .create()
            .let(::showImeDialog)
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
                if (text == null) toast(context.getString(R.string.fm_copy_content_failed, f.name))
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
        val dst = FileCopyRules.pasteTarget(src, dir)
        // Eigene Verzeichniskopie würde die Quelle zerstören (copyTo trunciert bei
        // src==dst); ein Ordner in einem eigenen Unterordner ließe copyRecursively
        // in den gerade erzeugten Teilbaum laufen.
        if (FileCopyRules.isSameOrInside(src, dst)) {
            toast(context.getString(R.string.fm_paste_into_self, src.name))
            return
        }
        // Vorhandenes Ziel nicht mehr still überschreiben (Datenverlust).
        if (FileCopyRules.needsOverwriteConfirm(dst)) {
            AlertDialog.Builder(context)
                .setTitle(R.string.fm_overwrite_title)
                .setMessage(context.getString(R.string.fm_overwrite_message, dst.name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ -> copyInto(src, dst) }
                .create()
                .let(::showImeDialog)
            return
        }
        copyInto(src, dst)
    }

    /** Kopiert [src] nach [dst] (I/O im Hintergrund) und aktualisiert die Liste. */
    private fun copyInto(src: File, dst: File) {
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
        com.piotv.keytab.Prefs.of(context)
            .edit()
            .putString(KEY_DIR, dir.absolutePath)
            .putString(KEY_BACKSTACK, backStack.joinToString("\n") { it.absolutePath })
            .apply()
    }

    /** Stellt das zuletzt besuchte Verzeichnis + BackStack wieder her (falls vorhanden/lesbar). */
    private fun restoreSaved() {
        val prefs = com.piotv.keytab.Prefs.of(context)
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
        val dir = currentDir ?: return
        if (to.absolutePath != dir.absolutePath) {
            backStack.add(dir)
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
                .orEmpty()
            val dirs = raw?.count { it.isDirectory } ?: 0
            val files = raw?.count { it.isFile } ?: 0
            mainHandler.post {
                // Ergebnis verwerfen, wenn inzwischen navigiert wurde
                if (currentDir?.absolutePath != target.absolutePath) return@post
                entries = loaded
                hint?.text = when {
                    raw == null -> context.getString(R.string.fm_no_storage_access)
                    mediaOnlyAccess() -> context.getString(R.string.fm_dir_summary_media, dirs, files)
                    else -> context.getString(R.string.fm_dir_summary, dirs, files)
                }
                list?.adapter = themedAdapter(
                    context, entries.map { it.label },
                    textSizeSp = LIST_TEXT_SP, compactRows = true
                )
            }
        }
    }

    /**
     * True, wenn nur Medien-Zugriff besteht: ab Android 13 gibt
     * `READ_MEDIA_IMAGES` (bzw. ab Android 14 „nur ausgewählte Bilder“) nur Bilder
     * frei — sichtbar sind Ordner und Bilder, alles andere (PDF, ZIP, XLSX …)
     * nicht. Der Hinweis über der Liste sagt das dann ausdrücklich, sonst wirkt ein
     * Ordner ohne Bilder wie ein Fehler (F-Droid-Testerbericht 2026-10-13).
     * Bis Android 12 deckt `READ_EXTERNAL_STORAGE` den ganzen Speicher ab → false.
     */
    private fun mediaOnlyAccess(): Boolean = listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    ).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
