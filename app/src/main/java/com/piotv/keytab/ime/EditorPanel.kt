package com.piotv.keytab.ime

import android.app.AlertDialog
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.piotv.keytab.Prefs
import com.piotv.keytab.R
import com.piotv.keytab.ime.ClineFileWatcher
import java.io.File
import java.util.concurrent.Executor

/**
 * Editor-Panel der IME (Editor-Tab): Eingabefeld + Speichern/Laden + Funktionen.
 * "Load" öffnet einen Ordner-Browser (Dialog mit ListView): Ordner antippen =
 * hineinnavigieren, "▲ …" = nach oben, Datei antippen = laden. Save schreibt
 * in die zuletzt geladene/gewählte Datei (Default: keytab_editor.txt).
 * Datei-I/O läuft auf [ioExecutor], UI-Updates kehren über [mainHandler] zurück.
 * Toolbar und Editor-Chrome siehe [setupToolbar] und [setupEditorChrome].
 *
 * `@Suppress("TooManyFunctions")` ist bewusst und begründet: das Panel bündelt
 * I/O, Gutter/Highlighting, Speichern/Laden und den optionalen Cline-Watcher.
 * detekts Schwelle liegt bei 20 Funktionen, hier sind es 23 (28.10.2026).
 * Die Baseline bleibt leer — die Ausnahme steht sichtbar im Code.
 */
@Suppress("TooManyFunctions")
open class EditorPanel(
    private val context: Context,
    private val rootView: View,
    private val ioExecutor: Executor,
    private val mainHandler: Handler,
    private val sendToApp: (String) -> Unit = {}
) {

    private val input: EditText? = rootView.findViewById(R.id.editor_input)
    private val fileLabel: TextView? = rootView.findViewById(R.id.editor_file)
    // Zeilennummern-Gutter + Highlight-Spans (Roadmap: Editor-Features)
    private val gutter: TextView? = rootView.findViewById(R.id.editor_gutter)
    private val highlightSpans = mutableListOf<ForegroundColorSpan>()
    private var editorFile: File = defaultFile()

    /**
     * Im Editor während der Sitzung geöffnete Dateien (Plaketten-Zähler).
     *
     * Gezählt werden **unterschiedliche** Dateien: ein erneutes Laden derselben
     * Datei (Toolbar-↻, Kontextmenü "Neu laden") erhöht den Zähler nicht.
     * Gesteuert wird die Zahl über [openFiles]; die Pfad-Historie steht in
     * [openedPaths].
     */
    open var openFiles: Int = 0

    /** Bindung zum Tab-Host (Plakette am Editor-Tab aktualisieren). */
    internal var tabHost: TabHost? = null
        internal set

    /** Optionaler Cline-Watcher (Auto-Load im Companion-Mode). */
    private var clineWatcher: ClineFileWatcher? = null

    /** Verzeichnis, das der laufende Watcher überwacht (für Restart-Vermeidung). */
    private var watchedDirectory: File? = null

    /** Inkrementiert den Zähler und informiert den Editor-Tab (nur im Companion-Mode sichtbar). */
    private fun onFileOpened(file: File) {
        // Nur unterschiedliche Dateien zählen — ein Reload derselben Datei
        // (Toolbar-↻) soll die Plakette nicht hochzählen.
        if (openedPaths.add(file.absolutePath)) {
            openFiles = openedPaths.size
        }
        tabHost?.refreshEditorBadge()
        startClineWatch(file.parentFile)
    }

    /** Pfade der in dieser Sitzung geöffneten Dateien (Plaketten-Zähler). */
    private val openedPaths = mutableSetOf<String>()

    /**
     * Startet/abort den Cline-Watcher für das übergebene Verzeichnis.
     *
     * Der Watcher wird **nicht** bei jedem Datei-Öffnen neu erzeugt: sonst
     * wäre sein interner Zustand (`knownPaths`) jedes Mal leer, jedes bereits
     * bekannte File gälte als "neu" und würde erneut geladen — eine
     * Endlos-Schleife aus "Datei öffnen → Watcher neu → Datei öffnen".
     * Deshalb wird nur bei einem **Verzeichniswechsel** neu gestartet.
     */
    private fun startClineWatch(directory: File?) {
        if (directory == null) return
        if (!Prefs.of(context).getBoolean(Prefs.KEY_CLINE_COMPANION_MODE, false) ||
            !Prefs.of(context).getBoolean(Prefs.KEY_CLINE_AUTO_LOAD, false)) {
            stopClineWatch()
            return
        }
        if (clineWatcher != null && watchedDirectory == directory) return
        stopClineWatch()
        watchedDirectory = directory
        clineWatcher = ClineFileWatcher(
            directory, CLINE_POLL_MS,
            { openFile(it) }
        )
        // Vorhandene Dateien als "bekannt" markieren — sonst würde der erste
        // Poll den ganzen Verzeichnisinhalt in den Editor laden.
        clineWatcher?.seedExistingFiles()
        clineWatcher?.start()
    }

    /** Stoppt den Watcher (z. B. beim Schließen aller Dateien oder Modus-Aus). */
    private fun stopClineWatch() {
        clineWatcher?.stop()
        clineWatcher = null
        watchedDirectory = null
    }

    /**
     * "Alle schließen": Editor leeren, Zähler zurücksetzen, Watcher stoppen.
     *
     * Das ist die Aktion aus dem Long-Press-Kontextmenü am Editor-Tab
     * (siehe [TabController]); sie beendet auch die Auto-Load-Überwachung,
     * damit keine Datei sofort wieder in den (leeren) Editor geladen wird.
     */
    fun closeAllFiles() {
        stopClineWatch()
        openedPaths.clear()
        openFiles = 0
        clearEditor()
        tabHost?.refreshEditorBadge()
    }

    /** Lädt die aktuelle Datei neu (Toolbar-↻ und Kontextmenü). */
    fun reloadCurrent() = loadFile(editorFile)

    private companion object {
        /** Max. Zahl der Nachpost-Versuche fuer das Gutter (N7). */
        const val GUTTER_REPOST_LIMIT = 5
        /** Poll-Intervall für Cline-Datei-Hinweis-Watcher (ms). */
        private const val CLINE_POLL_MS = 1000L
    }

    /**
     * Anzahl der Nachpost-Versuche fuer das Gutter (N7).
     *
     * Das Gutter braucht die Feldbreite, um die Zeilennummern korrekt um
     * Soft-Wraps zu setzen. Solange das Feld noch keine Breite hat, wird
     * neu gepostet — das war aber unbegrenzt (Endlos-Schleife auf der
     * Haupt-CPU). Der Zaehler begrenzt das auf wenige Versuche; danach
     * bleibt die logische Zeilennummerung (ohne Wrap) stehen.
     */
    private var gutterRepostCount = 0

    init {
        fileLabel?.text = editorFile.name
        setupToolbar(rootView)
        setupEditorChrome()
    }

    /** Text + Cursorposition (für die Wortvorhersage), null wenn nicht bereit. */
    fun cursorContext(): Pair<CharSequence, Int>? {
        val et = input ?: return null
        val t = et.text ?: return null
        return t to et.selectionEnd.coerceIn(0, t.length)
    }

    /** Löscht [count] Zeichen vor dem Cursor (Vorschlag ersetzt Teilwort). */
    fun deleteBefore(count: Int) {
        val et = input ?: return
        val editable = et.text ?: return
        val r = EditorTextState.deleteBeforeRange(editable.length, et.selectionEnd, count)
        if (r.start < r.end) editable.delete(r.start, r.end)
    }

    /** Fügt Text an der Cursorposition ein (ersetzt eine Selektion). */
    fun insert(text: String) {
        val et = input ?: return
        val editable = et.text ?: return
        val r = EditorTextState.replaceRange(editable.length, et.selectionStart, et.selectionEnd)
        editable.replace(r.start, r.end, text)
        et.setSelection(r.start + text.length)
    }

    /** Löscht im Editor: ein Zeichen oder bis zum Wortanfang ([word] = true). */
    fun delete(word: Boolean) {
        val et = input ?: return
        val editable = et.text ?: return
        val r = EditorTextState.deleteRange(editable, et.selectionEnd, word)
        if (r.start < r.end) {
            editable.delete(r.start, r.end)
            et.setSelection(r.start)
        }
    }

    /** Klick-Handler auf eine optional vorhandene Toolbar-Schaltfläche. */
    private fun onClick(root: View, id: Int, action: () -> Unit) {
        root.findViewById<Button>(id)?.setOnClickListener { action() }
    }

    private fun toast(resId: Int) {
        Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
    }

    private fun toast(resId: Int, arg: String) {
        Toast.makeText(context, context.getString(resId, arg), Toast.LENGTH_SHORT).show()
    }

    /**
     * Toolbar verdrahten: ⤓Save · ⤒Load · ↑Send-to-App · ✕Clear · ⎘Copy · ↻Reload
     * (alle Schaltflächen optional). ↑ sendet Auswahl bzw. den gesamten Inhalt
     * an die App darüber, ✕ leert das Feld, ⎘ kopiert ins Clipboard, ↻ lädt neu.
     */
    private fun setupToolbar(root: View) {
        onClick(root, R.id.btn_editor_save) { saveCurrent() }
        onClick(root, R.id.btn_editor_load) { showFilePicker() }
        onClick(root, R.id.btn_editor_send_up) {
            val text = input?.let { et ->
                et.text?.let { e -> EditorTextState.sendUpText(e, et.selectionStart, et.selectionEnd) }
            }.orEmpty()
            if (text.isEmpty()) toast(R.string.editor_empty_nothing)
            else {
                sendToApp(text)
                toast(R.string.editor_sent_to_app)
            }
        }
        onClick(root, R.id.btn_editor_clear) { clearEditor() }
        onClick(root, R.id.btn_editor_copy) {
            val text = (input?.text?.toString()).orEmpty()
            if (text.isEmpty()) toast(R.string.editor_empty_nothing)
            else {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("KeyTab", text))
                toast(R.string.editor_copied)
            }
        }
        onClick(root, R.id.btn_editor_reload) { loadFile(editorFile) }
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
        fun refreshList(d: File) {
            dir = d
            ioExecutor.execute {
                val items = EditorTextState.visibleEntries(d).map { EditorTextState.entryLabel(it) }
                mainHandler.post {
                    // Veraltetes Ergebnis verwerfen, falls weiter navigiert wurde
                    if (dir?.absolutePath != d.absolutePath) return@post
                    val labels = EditorTextState.pickerItems(
                        d.parentFile != null, items, context.getString(R.string.editor_pick_parent),
                        context.getString(R.string.editor_pick_empty)
                    )
                    listView.adapter = themedAdapter(context, labels)
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
            // Verzeichnis-Listing nicht auf dem Main-Thread (N21).
            val d = dir ?: return@setOnItemClickListener
            ioExecutor.execute {
                val target = EditorTextState.visibleEntries(d)
                    .firstOrNull { EditorTextState.entryLabel(it) == item }
                mainHandler.post {
                    if (target == null) return@post
                    if (target.isDirectory) {
                        refreshList(target)
                    } else {
                        dialog?.dismiss()
                        loadFile(target)
                    }
                }
            }
        }
        val picker = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.editor_load_title))
            .setView(listView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog = picker
        // WICHTIG (IME ohne Activity): Dialogfenster an das IME-Fenster anhängen,
        // sonst BadTokenException ("token null is not valid") — [ImeDialog] macht
        // das zentral und meldet statt eines Absturzes `false`.
        if (!ImeDialog.show(picker, rootView.windowToken)) {
            toast(R.string.dialog_unavailable)
            return
        }
        dir?.let { refreshList(it) }
    }

    /** Lädt eine Datei in den Editor; der Tab bleibt beim Editor. */
    fun openFile(f: File) { loadFile(f) }

    /** Editor-Inhalt leeren (für die Aktion 'Neue Datei'). */
    fun clearEditor() {
        input?.setText("")
    }

    /** Aktuelle Datei speichern (Toolbar-⇨Save). */
    fun saveCurrent() {
        val f = editorFile
        val text = input?.text?.toString() ?: return
        ioExecutor.execute {
            try {
                EditorTextState.writeFile(f, text)
                mainHandler.post { toast(R.string.editor_saved, f.name) }
            } catch (e: Exception) {
                mainHandler.post { toast(R.string.editor_save_failed, e.message.orEmpty()) }
            }
        }
    }

    /** Lädt [f] asynchron in den Editor und merkt sie als Save-Ziel. */
    private fun loadFile(f: File) {
        // Größen-Gate VOR dem Lesen: eine riesige Datei würde sonst den
        // IME-Prozess per OOM beenden (siehe [EditorTextState.MAX_EDITOR_BYTES]).
        if (EditorTextState.isTooLargeForEditor(f)) {
            toast(R.string.editor_file_too_large, f.name)
            return
        }
        ioExecutor.execute {
            try {
                val content = EditorTextState.readFile(f)
                mainHandler.post {
                    editorFile = f
                    fileLabel?.text = f.name
                    input?.setText(content)
                    if (f.exists()) toast(R.string.editor_loaded, f.name)
                    else toast(R.string.editor_file_empty)
                    onFileOpened(f)
                }
            } catch (e: Exception) {
                mainHandler.post { toast(R.string.editor_load_failed, e.message.orEmpty()) }
            }
        }
    }

    private fun defaultFile(): File {
        // Default-Startverzeichnis = externes Files-Dir der App (immer beschreibbar,
        // keine Storage-Berechtigung nötig). Fallback: internes Files-Dir.
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, "keytab_editor.txt")
    }

    // ---------- Editor-Chrome: Zeilennummern + Syntax-Highlighting ----------

    /**
     * Zeilennummern-Gutter ([R.id.editor_gutter]) und Highlighting verdrahten.
     * Der Gutter nutzt dieselbe Schrift/Polsterung wie das Feld; der vertikale
     * Scroll des Felds wird per OnScrollChangeListener auf den Gutter gespiegelt
     * (translationY), damit beide Zeilen zusammenlaufen.
     */
    private fun setupEditorChrome() {
        val et = input ?: return
        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                refreshGutter()
                applyHighlight()
            }
        })
        et.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            gutter?.translationY = -scrollY.toFloat()
        }
        refreshGutter()
        applyHighlight()
    }

    /**
     * Zeilennummern neu schreiben. Wrap-bewusst, sobald das Feld Breite hat:
     * Ein StaticLayout mit denselben Paint-/Breiten-Parametern wie das Feld
     * liefert die visuellen Zeilen (umbrochene Fortsetzungslinien erhalten
     * eine Leerzeile im Gutter, die Zahl bleibt an der logischen Zeile).
     * Ohne Breite (Tests, vor erstem Layout) zählen logische Zeilen.
     */
    private fun refreshGutter() {
        val g = gutter ?: return
        val et = input ?: return
        val text = et.text ?: return
        val width = et.width - et.paddingLeft - et.paddingRight
        // Performance-Guard: bei sehr großen Texten kein StaticLayout je
        // Tastendruck (N21) — dann nur logische Zeilennummern.
        val numbers = if (width > 0 && text.isNotEmpty() &&
            text.length <= EditorHighlightLogic.MAX_SCAN_CHARS
        ) {
            val layout = android.text.StaticLayout.Builder
                .obtain(text, 0, text.length, et.paint, width).build()
            val starts = IntArray(layout.lineCount) { layout.getLineStart(it) }
            EditorTextState.gutterForLineStarts(text, starts)
        } else {
            EditorTextState.gutterLogical(text)
        }
        g.text = numbers
        // N7: Das Nachposten war eine Endlos-Schleife: ohne Breite wurde
        // endlos neu gepostet (nie gemessenes Feld → CPU-Last). Jetzt
        // begrenzt: hoechstens [GUTTER_REPOST_LIMIT] Versuche, und nur wenn
        // das Panel wirklich noch am Fenster haengt.
        if (needsGutterRepost(et, text) && gutterRepostCount < GUTTER_REPOST_LIMIT) {
            gutterRepostCount++
            et.post { refreshGutter() }
        }
    }

    /**
     * N7: Nachposten lohnt nur, wenn das Feld noch ungemessen ist und wirklich am
     * Fenster hängt. Ausgelagert, weil detekt vier Bedingungen in einem `if` schon
     * als `ComplexCondition` wertet (Schwelle 4).
     */
    private fun needsGutterRepost(et: EditText, text: CharSequence): Boolean =
        et.width == 0 && text.isNotEmpty() && et.isAttachedToWindow

    /** Highlight-Spans neu anwenden; vorherige werden entfernt (kein Stau). */
    private fun applyHighlight() {
        val et = input ?: return
        val editable = et.editableText ?: return
        if (highlightSpans.isNotEmpty()) {
            for (s in highlightSpans) editable.removeSpan(s)
            highlightSpans.clear()
        }
        // Performance-Guard VOR dem Kopieren des Textes (N21): große Dateien
        // bleiben einfarbig, statt bei jedem Tastendruck den ganzen Text zu kopieren.
        if (editable.length > EditorHighlightLogic.MAX_SCAN_CHARS) return
        val text = editable.toString()
        for (span in EditorHighlightLogic.spans(text)) {
            val color = androidx.core.content.ContextCompat.getColor(
                context, EditorTextState.colorRes(span.kind)
            )
            val fg = ForegroundColorSpan(color)
            editable.setSpan(fg, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            highlightSpans.add(fg)
        }
    }

}
