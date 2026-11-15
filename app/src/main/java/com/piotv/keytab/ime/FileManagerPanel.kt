package com.piotv.keytab.ime

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
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
 *
 * Zwei Zugriffs-Modi (ab v0.24 — Ersatz für „Alle Dateien“/MANAGE_EXTERNAL_STORAGE):
 * - **SAF-Modus**: der über „Ordner wählen“ (Einstellungen) freigegebene Baum —
 *   alle Dateitypen via DocumentsContract ([SafFiles]); Einträge im Primär-Volume
 *   liefern echte Pfade ([SafPaths]) — das Kernfeature „Pfad in Termux einfügen“.
 * - **File-Modus** (Fallback ohne Freigabe): bisheriges Verhalten — Ordner und
 *   Bilder über `java.io.File`, Start im Download-Ordner.
 *
 * `@Suppress("TooManyFunctions")` ist bewusst und begründet: das Panel bündelt
 * beide Modi inkl. Navigation, Kontextmenü und Kopieren/Einfügen. detekts
 * Schwelle liegt bei 20 Funktionen, hier sind es 24 (09.11.2026). Die Baseline
 * bleibt leer — die Ausnahme steht sichtbar im Code (Muster: [EditorPanel]).
 */
@Suppress("TooManyFunctions")
class FileManagerPanel(
    private val context: Context,
    private val root: View,
    private val ioExecutor: Executor,
    private val mainHandler: Handler,
    private val callbacks: FileManagerCallbacks
) {
    data class FileManagerCallbacks(
        val onOpenInEditor: (String) -> Unit = {},
        val onCommit: (String) -> Unit
    )

    /**
     * Ein Eintrag der Liste — File-Modus ([file]) oder SAF-Modus ([doc]);
     * genauestens einer von beiden ist gesetzt.
     */
    private data class FileEntry(
        val label: String,
        val info: String,
        val file: File? = null,
        val doc: SafEntry? = null
    ) {
        val isDirectory: Boolean get() = file?.isDirectory == true || doc?.isDir == true
        val name: String get() = doc?.name ?: file?.name.orEmpty()
        val size: Long get() = doc?.size ?: file?.length() ?: 0L
        val lastModified: Long get() = doc?.lastModified ?: file?.lastModified() ?: 0L
    }

    private val list = root.findViewById<ListView>(R.id.file_list)
    private val dirLabel = root.findViewById<TextView>(R.id.file_dir)
    private val hint = root.findViewById<TextView>(R.id.file_hint)
    private val back = root.findViewById<Button>(R.id.btn_back_dir)
    private val up = root.findViewById<Button>(R.id.btn_up_dir)

    // File-Modus (Fallback): java.io.File-Navigation
    private var currentDir: File? = null
    private val backStack = mutableListOf<File>()
    // SAF-Modus: freigegebener Baum + Dokument-ID des aktuellen Verzeichnisses
    private var tree: Uri? = null
    private var currentDocId: String? = null
    private val docBackStack = mutableListOf<String>()

    private var entries: List<FileEntry> = emptyList()
    // Eintrag für Kopieren→Einfügen zwischen Verzeichnissen (nur im eigenen Modus)
    private var pendingCopy: FileEntry? = null
    private val paste = root.findViewById<Button>(R.id.btn_paste_dir)

    private companion object {
        const val MAX_CLIP_CONTENT_BYTES = 1_000_000
        const val KEY_DIR = "fm_dir"
        const val KEY_BACKSTACK = "fm_backstack"

        /** Präfix SAF-persistierter Verzeichnisse: `saf|<treeUri>|<docId>`. */
        const val SAF_PREFIX = "saf|"

        /** Schriftgröße der Dateiliste (sp) — bewusst kleiner als der Plattform-Default. */
        const val LIST_TEXT_SP = 11f
    }

    init {
        restoreSaved()
        if (currentDir == null && currentDocId == null) {
            val activeTree = tree
            if (activeTree != null) {
                currentDocId = SafFiles.rootDocId(activeTree)
            } else {
                val pub = Environment.getExternalStorageDirectory()
                currentDir = if (pub?.isDirectory == true && pub.canRead()) pub
                else context.getExternalFilesDir(null)?.parentFile?.let { File(it, "Download") }?.takeIf { it.isDirectory }
                ?: File("/")
            }
        }

        back?.setOnClickListener {
            if (tree != null) {
                if (docBackStack.isNotEmpty()) {
                    currentDocId = docBackStack.removeAt(docBackStack.lastIndex)
                    refresh()
                }
            } else if (backStack.isNotEmpty()) {
                val previous = backStack.removeAt(backStack.lastIndex)
                currentDir = previous
                refresh()
            }
        }

        up?.setOnClickListener {
            val activeTree = tree
            if (activeTree != null) {
                SafPaths.parentDocId(currentDocId ?: return@setOnClickListener)
                    ?.let { parent -> navigateDoc(parent) }
            } else {
                currentDir?.parentFile?.let { parent -> navigate(parent) }
            }
        }

        list?.setOnItemClickListener { _, _, position, _ ->
            val e = entries.getOrNull(position) ?: return@setOnItemClickListener
            if (e.isDirectory) {
                val doc = e.doc
                if (doc != null) navigateDoc(doc.docId) else e.file?.let { navigate(it) }
            } else {
                callbacks.onCommit(pathFor(e))
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
        // Beschriftungen und Aktionen werden **paarweise** aufgebaut und über den
        // Listenindex aufgelöst: vorher wurde die Aktion per Label-Vergleich
        // gesucht — zwei gleiche Beschriftungen (künftige Übersetzung) hätten die
        // falsche Aktion ausgelöst.
        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        items += context.getString(R.string.fm_properties)
        actions += { showProperties(e) }
        items += context.getString(R.string.fm_copy_path)
        actions += { copyTextToClipboard(displayPath(e)) }
        items += context.getString(R.string.fm_copy_file)
        actions += {
            pendingCopy = e
            paste?.visibility = View.VISIBLE
            toast(context.getString(R.string.fm_copied, e.name))
        }
        if (!e.isDirectory) {
            items += context.getString(R.string.fm_copy_content)
            actions += { copyFileContent(e) }
            items += context.getString(R.string.fm_open_in_editor)
            actions += { callbacks.onOpenInEditor(openTargetForEditor(e)) }
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

    private fun showProperties(e: FileEntry) {
        val type = context.getString(if (e.isDirectory) R.string.fm_type_dir else R.string.fm_type_file)
        val modified = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(e.lastModified))
        AlertDialog.Builder(context)
            .setTitle(e.name)
            .setMessage(context.getString(R.string.fm_props_fmt, displayPath(e), type,
                TextEditLogic.formatSize(e.size), modified))
            .setPositiveButton(android.R.string.ok, null)
            .create()
            .let(::showImeDialog)
    }

    private fun copyTextToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("KeyTab", text))
        toast(context.getString(R.string.fm_copied, text))
    }

    private fun copyFileContent(e: FileEntry) {
        ioExecutor.execute {
            val text = try {
                // Bugfix: Größenlimit, sonst OOM bei großen Dateien (Logs, DBs).
                // SAF: unbekannte Größe (Provider nennt SIZE nicht) bleibt sicherheitshalber
                // unkopiert — readText liest komplett und würde sonst das RAM riskieren.
                when {
                    e.file != null ->
                        if (e.file.length() > MAX_CLIP_CONTENT_BYTES) null else e.file.readText()
                    e.doc != null ->
                        if (e.doc.size > MAX_CLIP_CONTENT_BYTES || e.doc.size < 0L) null
                        else SafFiles.readText(context, e.doc.docUri)
                    else -> null
                }
            } catch (_: Exception) { null }
            mainHandler.post {
                if (text == null) toast(context.getString(R.string.fm_copy_content_failed, e.name))
                else {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText(e.name, text))
                    toast(context.getString(R.string.fm_copied, e.name))
                }
            }
        }
    }

    private fun pasteIntoCurrent() {
        val src = pendingCopy ?: return
        val activeTree = tree
        if (activeTree != null) pasteDoc(activeTree, src) else pasteFile(src)
    }

    /** Einfügen im SAF-Modus: Quelle muss ein Dokument desselben Baums sein. */
    private fun pasteDoc(treeUri: Uri, src: FileEntry) {
        val doc = src.doc
        val dir = currentDocId
        if (doc == null || dir == null) {
            // Quelle stammt aus dem File-Modus (z. B. nach Moduswechsel) — es gibt
            // kein Mapping zwischen File-Pfad und Baum; sauber abbrechen.
            toast(context.getString(R.string.fm_paste_failed, src.name))
            return
        }
        val dstId = SafPaths.childDocId(dir, src.name)
        // Eigene Verzeichniskopie würde die Quelle zerstören — dieselbe Regel wie
        // im File-Modus (siehe FileCopyRules.isSameOrInside).
        if (SafPaths.isSameOrInside(doc.docId, dstId)) {
            toast(context.getString(R.string.fm_paste_into_self, src.name))
            return
        }
        // Vorhandenes Ziel nicht mehr still überschreiben (Datenverlust).
        ioExecutor.execute {
            val existing = SafFiles.findChild(context, treeUri, dir, src.name)
            mainHandler.post {
                if (existing != null) {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.fm_overwrite_title)
                        .setMessage(context.getString(R.string.fm_overwrite_message, src.name))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            copyDocInto(treeUri, dir, doc, existing)
                        }
                        .create()
                        .let(::showImeDialog)
                } else {
                    copyDocInto(treeUri, dir, doc, null)
                }
            }
        }
    }

    /** SAF-Kopie ausführen (I/O im Hintergrund) und Liste aktualisieren. */
    private fun copyDocInto(treeUri: Uri, dir: String, doc: SafEntry, replaceDocId: String?) {
        ioExecutor.execute {
            val ok = try {
                SafFiles.copyInto(context, treeUri, dir, doc, replaceDocId)
            } catch (_: Exception) {
                false
            }
            mainHandler.post {
                pendingCopy = if (ok) null else pendingCopy
                paste?.visibility = if (ok) View.GONE else View.VISIBLE
                if (ok) refresh()
                toast(context.getString(
                    if (ok) R.string.fm_pasted else R.string.fm_paste_failed, doc.name
                ))
            }
        }
    }

    /** Einfügen im File-Modus (bisheriges Verhalten). */
    private fun pasteFile(src: FileEntry) {
        val file = src.file ?: return
        val dir = currentDir ?: return
        val dst = FileCopyRules.pasteTarget(file, dir)
        // Eigene Verzeichniskopie würde die Quelle zerstören (copyTo trunciert bei
        // src==dst); ein Ordner in einem eigenen Unterordner ließe copyRecursively
        // in den gerade erzeugten Teilbaum laufen.
        if (FileCopyRules.isSameOrInside(file, dst)) {
            toast(context.getString(R.string.fm_paste_into_self, file.name))
            return
        }
        // Vorhandenes Ziel nicht mehr still überschreiben (Datenverlust).
        if (FileCopyRules.needsOverwriteConfirm(dst)) {
            AlertDialog.Builder(context)
                .setTitle(R.string.fm_overwrite_title)
                .setMessage(context.getString(R.string.fm_overwrite_message, dst.name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ -> copyInto(file, dst) }
                .create()
                .let(::showImeDialog)
            return
        }
        copyInto(file, dst)
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
     *  über Tastatur-Neustarts bzw. App-Neustarts hinweg erhalten bleibt.
     *  SAF-Modus speichert `saf|<treeUri>|<docId>`, File-Modus den Pfad —
     *  alte File-Einträge bleiben damit lesbar (Abwärtskompatibilität). */
    private fun persistDir() {
        val prefs = com.piotv.keytab.Prefs.of(context).edit()
        val activeTree = tree
        when {
            activeTree != null -> currentDocId?.let { id ->
                prefs.putString(KEY_DIR, SAF_PREFIX + activeTree + "|" + id)
                prefs.putString(
                    KEY_BACKSTACK,
                    docBackStack.joinToString("\n") { SAF_PREFIX + activeTree + "|" + it }
                )
            }
            else -> currentDir?.let { dir ->
                prefs.putString(KEY_DIR, dir.absolutePath)
                prefs.putString(KEY_BACKSTACK, backStack.joinToString("\n") { it.absolutePath })
            }
        }
        prefs.apply()
    }

    /** Stellt das zuletzt besuchte Verzeichnis + BackStack wieder her (falls vorhanden/lesbar). */
    private fun restoreSaved() {
        tree = SafAccess.treeUri(context)
        val prefs = com.piotv.keytab.Prefs.of(context)
        restoreDir(prefs, tree)
        restoreStack(prefs, tree)
        val activeTree = tree
        if (activeTree != null && currentDocId == null) {
            // Frisch erteilter Zugriff: Start an der Baumwurzel.
            currentDocId = SafFiles.rootDocId(activeTree)
        }
    }

    /** Verzeichnis wiederherstellen: SAF-Eintrag nur bei passendem/lesbarem Baum. */
    private fun restoreDir(prefs: SharedPreferences, activeTree: Uri?) {
        val saved = prefs.getString(KEY_DIR, null) ?: return
        val saf = SafPref.of(saved)
        if (saf != null) {
            if (activeTree != null && saf.tree == activeTree.toString() &&
                SafFiles.isDir(context, activeTree, saf.docId)
            ) {
                currentDocId = saf.docId
            }
        } else if (activeTree == null) {
            File(saved).takeIf { it.isDirectory && it.canRead() }?.let { currentDir = it }
        }
    }

    /** BackStack wiederherstellen — im jeweiligen Format des aktiven Modus. */
    private fun restoreStack(prefs: SharedPreferences, activeTree: Uri?) {
        val lines = prefs.getString(KEY_BACKSTACK, null)?.split("\n") ?: return
        val restored = lines.mapNotNull { line ->
            val saf = SafPref.of(line)
            when {
                saf != null && activeTree != null && saf.tree == activeTree.toString() -> saf.docId
                saf == null && activeTree == null ->
                    File(line).takeIf { it.isDirectory && it.canRead() }?.absolutePath
                else -> null
            }
        }
        if (restored.isEmpty()) return
        if (activeTree != null) {
            docBackStack.clear()
            docBackStack.addAll(restored)
        } else {
            backStack.clear()
            backStack.addAll(restored.map { File(it) })
        }
    }

    /** Zerlegung eines SAF-persistierten Eintrags `saf|<treeUri>|<docId>`. */
    private data class SafPref(val tree: String, val docId: String) {
        companion object {
            fun of(line: String): SafPref? {
                if (!line.startsWith(SAF_PREFIX)) return null
                val parts = line.removePrefix(SAF_PREFIX).split("|", limit = 2)
                val tree = parts.getOrNull(0) ?: return null
                val docId = parts.getOrNull(1) ?: return null
                return SafPref(tree, docId)
            }
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

    /** SAF-Navigation in ein Verzeichnis des Baums (Dokument-ID). */
    private fun navigateDoc(toDocId: String) {
        val current = currentDocId ?: return
        if (toDocId != current) {
            docBackStack.add(current)
        }
        currentDocId = toDocId
        refresh()
    }

    /** Text, den ein Tipp auf [e] einfügt: echter Pfad (Primär-Volume), sonst Name. */
    private fun pathFor(e: FileEntry): String = when {
        e.file != null -> e.file.absolutePath
        else -> e.doc?.realPath ?: e.name
    }

    /** Anzeige-/Clipboard-Pfad: echter Pfad, sonst Dokument-ID (ehrlich). */
    private fun displayPath(e: FileEntry): String = when {
        e.file != null -> e.file.absolutePath
        e.doc != null -> e.doc.realPath ?: e.doc.docId
        else -> e.name
    }

    /** Ziel für „Im Editor öffnen“: Pfad (File-Modus) oder content-URI (SAF-Modus). */
    private fun openTargetForEditor(e: FileEntry): String =
        e.file?.absolutePath ?: e.doc?.docUri?.toString().orEmpty()

    private fun refresh() {
        persistDir()
        if (tree != null) refreshDocs() else refreshFiles()
    }

    /** Listen-Aktualisierung im SAF-Modus: alle Dateitypen des Baums. */
    private fun refreshDocs() {
        val activeTree = tree ?: return
        val docId = currentDocId ?: return
        val treeId = SafFiles.treeId(activeTree)
        dirLabel?.text = SafPaths.pseudoPath(treeId, docId)
        back?.visibility = if (docBackStack.isNotEmpty()) View.VISIBLE else View.GONE
        up?.visibility = if (SafPaths.parentDocId(docId) != null) View.VISIBLE else View.GONE
        hint?.text = context.getString(R.string.fm_loading)
        // Listing kann bei großen Ordnern langsam sein → Hintergrund-Thread
        ioExecutor.execute {
            val loaded = SafFiles.listChildren(context, activeTree, docId)
            mainHandler.post {
                // Ergebnis verwerfen, wenn inzwischen navigiert wurde
                if (currentDocId != docId) return@post
                entries = loaded.orEmpty().map { doc ->
                    FileEntry(
                        label = SafFiles.label(doc),
                        info = if (doc.isDir) context.getString(R.string.fm_entry_dir)
                        else TextEditLogic.formatSize(doc.size),
                        doc = doc
                    )
                }
                hint?.text = if (loaded == null) {
                    context.getString(R.string.fm_no_storage_access)
                } else {
                    context.getString(
                        R.string.fm_dir_summary,
                        loaded.count { it.isDir }, loaded.count { !it.isDir }
                    )
                }
                list?.adapter = themedAdapter(
                    context, entries.map { it.label },
                    textSizeSp = LIST_TEXT_SP, compactRows = true
                )
            }
        }
    }

    /** Listen-Aktualisierung im File-Modus (Fallback ohne SAF-Freigabe). */
    private fun refreshFiles() {
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
                    if (f.isDirectory) {
                        FileEntry("\uD83D\uDCC1 ${f.name}/", context.getString(R.string.fm_entry_dir), file = f)
                    } else {
                        FileEntry("\uD83D\uDCC4 ${f.name}", TextEditLogic.formatSize(f.length()), file = f)
                    }
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
     * Gilt nur für den File-Modus — im SAF-Modus sind alle Dateitypen sichtbar.
     */
    private fun mediaOnlyAccess(): Boolean = listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    ).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
