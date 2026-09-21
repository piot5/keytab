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
 *
 * Toolbar: ⤓Save · ⤒Load · ↑Send-to-App · ✕Clear · ⎘Copy · ↻Reload
 * - ↑ sendet den Editor-Inhalt direkt ins Zielfeld der App darüber (InputConnection)
 * - ✕ leert das Editorfeld
 * - ⎘ kopiert den Editor-Inhalt ins System-Clipboard
 * - ↻ lädt die Datei neu in den Editor
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
        setupSave(rootView)
        setupLoad(rootView)
        setupSendUp(rootView)
        setupClear(rootView)
        setupCopy(rootView)
        setupReload(rootView)
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
        val cursor = et.selectionEnd.coerceIn(0, editable.length)
        val start = (cursor - count.coerceAtLeast(0)).coerceAtLeast(0)
        if (start < cursor) editable.delete(start, cursor)
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

    /** ↑: Editor-Inhalt direkt ins Zielfeld der App darüber einfügen. */
    private fun setupSendUp(root: View) {
        root.findViewById<Button>(R.id.btn_editor_send_up)?.setOnClickListener {
            val text = (input?.text?.toString()).orEmpty()
            if (text.isEmpty()) {
                Toast.makeText(context, R.string.editor_empty_nothing, Toast.LENGTH_SHORT).show()
            } else {
                sendToApp(text)
                Toast.makeText(context, R.string.editor_sent_to_app, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** ✕: Editorfeld leeren. */
    private fun setupClear(root: View) {
        root.findViewById<Button>(R.id.btn_editor_clear)?.setOnClickListener {
            input?.setText("")
            Toast.makeText(context, R.string.editor_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    /** ⎘: Editor-Inhalt ins System-Clipboard kopieren. */
    private fun setupCopy(root: View) {
        root.findViewById<Button>(R.id.btn_editor_copy)?.setOnClickListener {
            val text = (input?.text?.toString()).orEmpty()
            if (text.isEmpty()) {
                Toast.makeText(context, R.string.editor_empty_nothing, Toast.LENGTH_SHORT).show()
            } else {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("KeyTab", text))
                Toast.makeText(context, R.string.editor_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** ↻: Aktuelle Datei neu in den Editor laden. */
    private fun setupReload(root: View) {
        root.findViewById<Button>(R.id.btn_editor_reload)?.setOnClickListener {
            loadFile(editorFile)
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
                        Toast.makeText(context, context.getString(R.string.editor_save_failed, e.message.orEmpty()),
                        Toast.LENGTH_SHORT).show()
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
                    .orEmpty()
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
                    Toast.makeText(context, context.getString(R.string.editor_load_failed, e.message.orEmpty()), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun defaultFile(): File {
        // Default-Startverzeichnis = externes Files-Dir der App (keine Storage-
        // Berechtigung nötig, immer beschreibbar). Fallback: internes Files-Dir.
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
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
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
        val sb = StringBuilder()
        val width = et.width - et.paddingLeft - et.paddingRight
        if (width > 0 && text.isNotEmpty()) {
            val layout = android.text.StaticLayout.Builder
                .obtain(text, 0, text.length, et.paint, width).build()
            var logical = 1
            for (v in 0 until layout.lineCount) {
                val start = layout.getLineStart(v)
                val wrapped = start > 0 && text[start - 1] != '\n'
                if (wrapped) sb.append(' ') else { sb.append(logical); logical++ }
                sb.append('\n')
            }
        } else {
            var count = 1
            for (c in text) if (c == '\n') count++
            for (i in 1..count) sb.append(i).append('\n')
        }
        g.text = sb.toString().trimEnd('\n')
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
            val color = androidx.core.content.ContextCompat.getColor(context, when (span.kind) {
                EditorHighlightLogic.Kind.COMMENT -> R.color.editor_comment
                EditorHighlightLogic.Kind.STRING -> R.color.editor_string
                EditorHighlightLogic.Kind.NUMBER -> R.color.editor_number
                EditorHighlightLogic.Kind.KEYWORD -> R.color.editor_keyword
            })
            val fg = ForegroundColorSpan(color)
            editable.setSpan(fg, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            highlightSpans.add(fg)
        }
    }
}
