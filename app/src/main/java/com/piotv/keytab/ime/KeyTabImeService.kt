package com.piotv.keytab.ime

import android.annotation.SuppressLint
import android.inputmethodservice.InputMethodService
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.R
import java.io.File
import java.util.Locale

class KeyTabImeService : InputMethodService() {

    private companion object {
        const val LONG_PRESS_TIMEOUT = 400L
        const val WORD_DELETE_REPEAT_MS = 250L
        const val MAX_CLIP_ENTRIES = 50
    }

    private var shifted = false
    private var keyboardRoot: View? = null
    private var currentDir: File? = null
    private val backStack = mutableListOf<File>()
    private var showSymbols = false
    private var activePopup: PopupWindow? = null
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val clipHistory = mutableListOf<String>()
    private var editorActive = false
    private var editorInput: EditText? = null

    private data class FileEntry(val file: File, val label: String, val info: String)

    private val letterExtras = mapOf(
        'a' to listOf("ä", "á", "à", "â", "æ", "å"),
        'A' to listOf("Ä", "Á", "À", "Â", "Æ", "Å"),
        'e' to listOf("é", "è", "ê", "ë"),
        'E' to listOf("É", "È", "Ê", "Ë"),
        'i' to listOf("í", "ì", "î", "ï"),
        'I' to listOf("Í", "Ì", "Î", "Ï"),
        'o' to listOf("ö", "ó", "ò", "ô", "ø"),
        'O' to listOf("Ö", "Ó", "Ò", "Ô", "Ø"),
        'u' to listOf("ü", "ú", "ù", "û"),
        'U' to listOf("Ü", "Ú", "Ù", "Û"),
        's' to listOf("ß", "š"),
        'S' to listOf("Š"),
        'n' to listOf("ñ", "ń"),
        'N' to listOf("Ñ", "Ń"),
        'c' to listOf("ç", "č"),
        'C' to listOf("Ç", "Č"),
        'z' to listOf("ž", "ź"),
        'Z' to listOf("Ž", "Ź")
    )

    override fun onCreateInputView(): View {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_KeyTab)
        val root = layoutInflater.cloneInContext(themedContext)
            .inflate(R.layout.keyboard_view, null)
        keyboardRoot = root
        setupTabs(root)
        hookKeyboardButtons(root)
        setupFileManager(root)
        applyLetterCase(root)
        return root
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        shifted = false
        applyLetterCase(keyboardRoot)
    }

    private fun setupTabs(root: View) {
        val tabs = root.findViewById<TabLayout>(R.id.ime_tabs) ?: return
        val kb = root.findViewById<View>(R.id.kb_panel) ?: return
        val sym = root.findViewById<View>(R.id.sym_panel) ?: return
        val fm = root.findViewById<View>(R.id.file_panel) ?: return
        val ed = root.findViewById<View>(R.id.editor_panel) ?: return
        val cp = root.findViewById<View>(R.id.clip_panel) ?: return
        val bottom = root.findViewById<View>(R.id.bottom_row) ?: return
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                activePopup?.dismiss()
                val pos = tab.position
                // Tab 0=abc, 1=Editor (Tastatur bleibt sichtbar), 2=Files, 3=Ablage
                val keyboardVisible = pos == 0 || pos == 1
                editorActive = pos == 1
                kb.visibility = if (keyboardVisible && !showSymbols) View.VISIBLE else View.GONE
                sym.visibility = if (keyboardVisible && showSymbols) View.VISIBLE else View.GONE
                ed.visibility = if (pos == 1) View.VISIBLE else View.GONE
                fm.visibility = if (pos == 2) View.VISIBLE else View.GONE
                cp.visibility = if (pos == 3) View.VISIBLE else View.GONE
                bottom.visibility = if (keyboardVisible) View.VISIBLE else View.GONE
                if (pos == 2) setupFileManager(root)
                if (pos == 3) captureClipboard(auto = true)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        kb.visibility = View.VISIBLE
        sym.visibility = View.GONE
        ed.visibility = View.GONE
        fm.visibility = View.GONE
        cp.visibility = View.GONE
        bottom.visibility = View.VISIBLE
        setupEditor(root)
        setupClipboard(root)
    }

    private fun hookKeyboardButtons(root: View) {
        forEachView(root) { v ->
            val btn = v as? Button ?: return@forEachView
            when {
                btn.tag == "letter" -> setupLetterButton(btn)
                btn.tag == "sym" -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    commitText(btn.text.toString())
                }
                btn.id == R.id.key_oe -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    commitText(if (shifted) "Ö" else "ö")
                }
                btn.id == R.id.key_toggle -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    toggleSymbols(root)
                }
                btn.id == R.id.key_shift -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    shifted = !shifted
                    btn.alpha = if (shifted) 1f else 0.6f
                    applyLetterCase(root)
                }
                btn.id == R.id.key_tab -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    if (editorActive) insertIntoEditor("\t")
                    else sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
                }
                btn.id == R.id.key_del -> setupDelButton(btn)
                btn.id == R.id.key_enter -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    if (editorActive) insertIntoEditor("\n")
                    else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
                btn.id == R.id.key_space -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    commitText(" ")
                }
            }
        }
    }

    private fun setupLetterButton(btn: Button) {
        var pendingLongPress: Runnable? = null
        var longPressFired = false
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressFired = false
                    btn.isPressed = true
                    pendingLongPress = Runnable {
                        longPressFired = true
                        showLetterExtras(btn)
                    }
                    longPressHandler.postDelayed(pendingLongPress!!, LONG_PRESS_TIMEOUT)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    btn.isPressed = false
                    pendingLongPress?.let { longPressHandler.removeCallbacks(it) }
                    if (!longPressFired) {
                        commitText(btn.text.toString())
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    btn.isPressed = false
                    pendingLongPress?.let { longPressHandler.removeCallbacks(it) }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupDelButton(btn: Button) {
        var pendingLongPress: Runnable? = null
        var repeater: Runnable? = null
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    btn.isPressed = true
                    if (editorActive) deleteFromEditor(word = false)
                    else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                    pendingLongPress = Runnable {
                        deleteLastWord()
                        repeater = object : Runnable {
                            override fun run() {
                                deleteLastWord()
                                longPressHandler.postDelayed(this, WORD_DELETE_REPEAT_MS)
                            }
                        }
                        longPressHandler.postDelayed(repeater!!, WORD_DELETE_REPEAT_MS)
                    }
                    longPressHandler.postDelayed(pendingLongPress!!, LONG_PRESS_TIMEOUT)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    btn.isPressed = false
                    pendingLongPress?.let { longPressHandler.removeCallbacks(it) }
                    repeater?.let { longPressHandler.removeCallbacks(it) }
                    if (event.action == MotionEvent.ACTION_UP) {
                        // falls Wort-Löschung schon lief, nichts mehr tun; kurzer Tap = einzelnes DEL (oben schon gesendet)
                    }
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showLetterExtras(anchor: Button) {
        activePopup?.dismiss()
        val letter = anchor.text?.firstOrNull() ?: return
        val extras = letterExtras[letter] ?: letterExtras[letter.lowercaseChar()] ?: return

        val ctx = anchor.context
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF37474F.toInt())
            setPadding(8, 8, 8, 8)
        }

        for (ch in extras) {
            val tv = TextView(ctx).apply {
                text = ch
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(20, 16, 20, 16)
                setOnClickListener {
                    commitText(ch)
                    activePopup?.dismiss()
                }
            }
            container.addView(tv)
        }

        val popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
        }
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, location[0], location[1] - 120)
        activePopup = popup
    }

    private fun toggleSymbols(root: View) {
        showSymbols = !showSymbols
        root.findViewById<View>(R.id.kb_panel)?.visibility =
            if (showSymbols) View.GONE else View.VISIBLE
        root.findViewById<View>(R.id.sym_panel)?.visibility =
            if (showSymbols) View.VISIBLE else View.GONE
        root.findViewById<Button>(R.id.key_toggle)?.text =
            if (showSymbols) getString(R.string.key_toggle_letters) else "?123"
    }

    private fun applyLetterCase(view: View?) {
        if (view == null) return
        forEachView(view) { v ->
            val btn = v as? Button ?: return@forEachView
            if (btn.tag == "letter") {
                val base = btn.text.toString()
                btn.text = if (shifted) base.uppercase() else base.lowercase()
            }
        }
    }

    private fun deleteLastWord() {
        if (editorActive) {
            deleteFromEditor(word = true)
            return
        }
        val ic = currentInputConnection ?: return
        val text = ic.getTextBeforeCursor(200, 0)?.toString() ?: ""
        var i = text.length
        while (i > 0 && text[i - 1].isWhitespace()) i--
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        if (i > 0) i--
        val toDelete = text.length - i
        if (toDelete > 0) {
            ic.deleteSurroundingText(toDelete, 0)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun commitText(text: String) {
        if (editorActive) {
            insertIntoEditor(text)
        } else {
            currentInputConnection?.commitText(text, 1)
        }
        if (shifted) {
            shifted = false
            applyLetterCase(keyboardRoot)
        }
    }

    /** Fügt Text intern in den Editor ein (an Cursorposition). */
    private fun insertIntoEditor(text: String) {
        val et = editorInput ?: return
        val editable = et.text ?: return
        var start = et.selectionStart.coerceIn(0, editable.length)
        val end = et.selectionEnd.coerceIn(start, editable.length)
        editable.replace(start, end, text)
        start += text.length
        et.setSelection(start)
    }

    /** Löscht intern im Editor (ein Zeichen oder bis Wortanfang). */
    private fun deleteFromEditor(word: Boolean) {
        val et = editorInput ?: return
        val editable = et.text ?: return
        val cursor = et.selectionEnd.coerceIn(0, editable.length)
        if (cursor == 0) return
        if (word) {
            var i = cursor
            while (i > 0 && editable[i - 1].isWhitespace()) i--
            while (i > 0 && !editable[i - 1].isWhitespace()) i--
            if (i < cursor) {
                editable.delete(i, cursor)
                et.setSelection(i)
            } else {
                editable.delete(cursor - 1, cursor)
                et.setSelection(cursor - 1)
            }
        } else {
            editable.delete(cursor - 1, cursor)
            et.setSelection(cursor - 1)
        }
    }

    private fun setupFileManager(root: View) {
        val list = root.findViewById<ListView>(R.id.file_list) ?: return
        val dirLabel = root.findViewById<TextView>(R.id.file_dir) ?: return
        val hint = root.findViewById<TextView>(R.id.file_hint) ?: return
        val back = root.findViewById<Button>(R.id.btn_back_dir) ?: return
        val up = root.findViewById<Button>(R.id.btn_up_dir) ?: return

        if (currentDir == null) {
            val pub = Environment.getExternalStorageDirectory()
            currentDir = if (pub?.isDirectory == true && pub.canRead()) pub
            else getExternalFilesDir(null)?.parentFile?.let { File(it, "Download") }?.takeIf { it.isDirectory }
            ?: File("/")
        }

        var entries: List<FileEntry> = emptyList()

        fun refresh() {
            val dir = currentDir ?: return
            dirLabel.text = dir.absolutePath
            back.visibility = if (backStack.isNotEmpty()) View.VISIBLE else View.GONE
            up.visibility = if (dir.parentFile != null) View.VISIBLE else View.GONE
            val raw = dir.listFiles()
            if (raw == null) {
                hint.text = "Speicherzugriff fehlt – öffne KeyTab App für Berechtigung."
                entries = emptyList()
            } else {
                hint.text = "${raw.count { it.isDirectory }} Ordner · ${raw.count { it.isFile }} Dateien"
                entries = raw
                    .filter { !it.isHidden }
                    .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
                    .map { f ->
                        if (f.isDirectory) FileEntry(f, "\uD83D\uDCC1 ${f.name}/", "Ordner")
                        else FileEntry(f, "\uD83D\uDCC4 ${f.name}", formatSize(f.length()))
                    }
            }
            list.adapter = ArrayAdapter(root.context, android.R.layout.simple_list_item_1, entries.map { it.label })
        }

        fun navigate(to: File) {
            if (to.absolutePath != currentDir?.absolutePath) {
                backStack.add(currentDir!!)
            }
            currentDir = to
            refresh()
        }

        back.setOnClickListener {
            if (backStack.isNotEmpty()) {
                val previous = backStack.removeAt(backStack.lastIndex)
                currentDir = previous
                refresh()
            }
        }

        up.setOnClickListener {
            currentDir?.parentFile?.let { navigate(it) }
        }

        list.setOnItemClickListener { _, _, position, _ ->
            val e = entries.getOrNull(position) ?: return@setOnItemClickListener
            if (e.file.isDirectory) {
                navigate(e.file)
            } else {
                commitText(e.file.absolutePath)
                root.findViewById<TabLayout>(R.id.ime_tabs)?.getTabAt(0)?.select()
            }
        }
        refresh()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.ROOT, "%.0f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.ROOT, "%.1f MB", mb)
    }

    private fun setupEditor(root: View) {
        val input = root.findViewById<EditText>(R.id.editor_input) ?: return
        editorInput = input
        val fileLabel = root.findViewById<TextView>(R.id.editor_file) ?: return
        fileLabel.text = editorFile().name
        root.findViewById<Button>(R.id.btn_editor_save)?.setOnClickListener {
            try {
                val f = editorFile()
                f.writeText(input.text.toString())
                Toast.makeText(this, "Gespeichert: ${f.name}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Speichern fehlgeschlagen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        root.findViewById<Button>(R.id.btn_editor_load)?.setOnClickListener {
            try {
                val f = editorFile()
                input.setText(if (f.exists()) f.readText() else "")
                Toast.makeText(this, if (f.exists()) "Geladen: ${f.name}" else "Datei ist leer", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Laden fehlgeschlagen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun editorFile(): File {
        val pub = Environment.getExternalStorageDirectory()
        val dir = if (pub != null && pub.isDirectory && pub.canWrite()) pub
        else getExternalFilesDir(null) ?: filesDir
        return File(dir, "keytab_editor.txt")
    }

    private fun currentClipboardText(): String? {
        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return null
        return cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun captureClipboard(auto: Boolean) {
        val text = currentClipboardText() ?: return
        if (clipHistory.firstOrNull() == text) return
        clipHistory.removeAll { it == text }
        clipHistory.add(0, text)
        if (clipHistory.size > MAX_CLIP_ENTRIES) clipHistory.removeAt(clipHistory.lastIndex)
        saveClipHistory()
        if (auto) refreshClipList()
    }

    private fun setupClipboard(root: View) {
        loadClipHistory()
        root.findViewById<Button>(R.id.btn_clip_add)?.setOnClickListener {
            val text = currentClipboardText()
            if (text == null) {
                Toast.makeText(this, "Zwischenablage ist leer", Toast.LENGTH_SHORT).show()
            } else {
                captureClipboard(auto = false)
                refreshClipList()
            }
        }
        root.findViewById<Button>(R.id.btn_clip_clear)?.setOnClickListener {
            clipHistory.clear()
            saveClipHistory()
            refreshClipList()
        }
        root.findViewById<ListView>(R.id.clip_list)?.setOnItemClickListener { _, _, position, _ ->
            commitText(clipHistory.getOrNull(position) ?: return@setOnItemClickListener)
        }
        refreshClipList()
    }

    private fun refreshClipList() {
        val root = keyboardRoot ?: return
        val list = root.findViewById<ListView>(R.id.clip_list) ?: return
        val hint = root.findViewById<TextView>(R.id.clip_hint) ?: return
        hint.text = if (clipHistory.isEmpty()) getString(R.string.clip_hint_empty)
        else "${clipHistory.size} Einträge · Tippen = einfügen"
        list.adapter = ArrayAdapter(root.context, android.R.layout.simple_list_item_1,
            clipHistory.map { s -> if (s.length > 80) s.take(80) + "…" else s.replace("\n", " ") })
    }

    private fun clipHistoryFile() = File(filesDir, "clipboard_history.txt")

    private fun saveClipHistory() {
        try { clipHistoryFile().writeText(clipHistory.joinToString("\u0000")) } catch (_: Exception) {}
    }

    private fun loadClipHistory() {
        try {
            val f = clipHistoryFile()
            if (f.exists()) {
                clipHistory.clear()
                clipHistory.addAll(f.readText().split('\u0000').filter { it.isNotEmpty() })
            }
        } catch (_: Exception) {}
    }

    private fun forEachView(root: View, action: (View) -> Unit) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) forEachView(root.getChildAt(i), action)
        } else {
            action(root)
        }
    }
}
