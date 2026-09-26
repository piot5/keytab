package com.piotv.keytab.ime

import android.app.AlertDialog
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
 * Editor-Panel der IME (Editor-Tab): Eingabefeld + Speichern/Laden + Funktionen.
 * "Load" öffnet einen Ordner-Browser (Dialog mit ListView): Ordner antippen =
 * hineinnavigieren, "▲ …" = nach oben, Datei antippen = laden. Save schreibt
 * in die zuletzt geladene/gewählte Datei (Default: keytab_editor.txt).
 * Datei-I/O läuft auf [ioExecutor], UI-Updates kehren über [mainHandler] zurück.
 * Toolbar und Editor-Chrome siehe [setupToolbar] und [setupEditorChrome].
 */
class EditorPanel(
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
        onClick(root, R.id.btn_editor_save) {
            val f = editorFile
            val text = input?.text?.toString() ?: return@onClick
            ioExecutor.execute {
                try {
                    EditorTextState.writeFile(f, text)
                    mainHandler.post { toast(R.string.editor_saved, f.name) }
                } catch (e: Exception) {
                    mainHandler.post { toast(R.string.editor_save_failed, e.message.orEmpty()) }
                }
            }
        }
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
        onClick(root, R.id.btn_editor_clear) {
            input?.setText("")
            toast(R.string.editor_cleared)
        }
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
            val target = dir?.listFiles()?.firstOrNull { EditorTextState.entryLabel(it) == item }
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

    /** Lädt eine Datei in den Editor; der Tab bleibt beim Editor. */
    fun openFile(f: File) { loadFile(f) }

    /** Lädt [f] asynchron in den Editor und merkt sie als Save-Ziel. */
    private fun loadFile(f: File) {
        ioExecutor.execute {
            try {
                val content = EditorTextState.readFile(f)
                mainHandler.post {
                    editorFile = f
                    fileLabel?.text = f.name
                    input?.setText(content)
                    if (f.exists()) toast(R.string.editor_loaded, f.name)
                    else toast(R.string.editor_file_empty)
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
        val numbers = if (width > 0 && text.isNotEmpty()) {
            val layout = android.text.StaticLayout.Builder
                .obtain(text, 0, text.length, et.paint, width).build()
            val starts = IntArray(layout.lineCount) { layout.getLineStart(it) }
            EditorTextState.gutterForLineStarts(text, starts)
        } else {
            EditorTextState.gutterLogical(text)
        }
        g.text = numbers
        if (et.width == 0 && text.isNotEmpty()) et.post { refreshGutter() }
    }

    /** Highlight-Spans neu anwenden; vorherige werden entfernt (kein Stau). */
    private fun applyHighlight() {
        val et = input ?: return
        val editable = et.editableText ?: return
        if (highlightSpans.isNotEmpty()) {
            for (s in highlightSpans) editable.removeSpan(s)
            highlightSpans.clear()
        }
        val text = editable.toString()
        // Performance-Guard: große Dateien bleiben einfarbig statt UI zu blockieren
        if (text.length > EditorHighlightLogic.MAX_SCAN_CHARS) return
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
