package com.piotv.keytab.ime

import android.inputmethodservice.InputMethodService
import android.os.Environment
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.R
import java.io.File
import java.util.Locale

/**
 * KeyTab IME – Tastatur mit TAB-Taste, Tab-Leiste „abc | Files“,
 * Sonderzeichen-Ebene (?123) und eingebautem Dateimanager.
 */
class KeyTabImeService : InputMethodService() {

    private var shifted = false
    private var keyboardRoot: View? = null
    private var currentDir: File? = null
    private var showSymbols = false

    private data class FileEntry(val file: File, val label: String, val info: String)

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

    // ---------- Tab-Leiste abc | Files ----------
    private fun setupTabs(root: View) {
        val tabs = root.findViewById<TabLayout>(R.id.ime_tabs) ?: return
        val kb = root.findViewById<View>(R.id.kb_panel) ?: return
        val fm = root.findViewById<View>(R.id.file_panel) ?: return
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val isKb = tab.position == 0
                kb.visibility = if (isKb) View.VISIBLE else View.GONE
                fm.visibility = if (isKb) View.GONE else View.VISIBLE
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        kb.visibility = View.VISIBLE
        fm.visibility = View.GONE
    }

    // ---------- Tasten ----------
    private fun hookKeyboardButtons(root: View) {
        forEachView(root) { v ->
            val btn = v as? Button ?: return@forEachView
            when {
                btn.tag == "letter" -> btn.setOnClickListener {
                    commitText(btn.text.toString())
                }
                btn.tag == "sym" -> btn.setOnClickListener { commitText(btn.text.toString()) }
                btn.id == R.id.key_oe -> btn.setOnClickListener {
                    commitText(if (shifted) "Ö" else "ö")
                }
                btn.id == R.id.key_toggle -> btn.setOnClickListener { toggleSymbols(root) }
                btn.id == R.id.key_shift -> btn.setOnClickListener {
                    shifted = !shifted
                    btn.alpha = if (shifted) 1f else 0.6f
                    applyLetterCase(root)
                }
                btn.id == R.id.key_tab -> btn.setOnClickListener {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
                }
                btn.id == R.id.key_del -> {
                    btn.setOnClickListener { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
                    btn.setOnLongClickListener {
                        deleteLastWord()
                        true
                    }
                }
                btn.id == R.id.key_enter -> btn.setOnClickListener {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
                btn.id == R.id.key_space -> btn.setOnClickListener {
                    currentInputConnection?.commitText(" ", 1)
                }
            }
        }
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

        /** Langer Druck auf ⌫: löscht ein Wort (bis zum vorigen Leerzeichen) */
    private fun deleteLastWord() {
        val ic = currentInputConnection ?: return
        val text = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
        var i = text.length
        // führende Leerzeichen überspringen
        while (i > 0 && text[i - 1].isWhitespace()) i--
        var j = i
        while (j > 0 && !text[j - 1].isWhitespace()) j--
        val toDelete = i - j
        if (toDelete > 0) {
            ic.deleteSurroundingText(toDelete, 0)
        } else {
            // nichts vor der Cursorposition → ein Zeichen löschen
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
        if (shifted) {
            shifted = false
            applyLetterCase(keyboardRoot)
        }
    }

        // ---------- Dateimanager mit Dateien ----------
    private fun setupFileManager(root: View) {
        val list = root.findViewById<ListView>(R.id.file_list) ?: return
        val dirLabel = root.findViewById<TextView>(R.id.file_dir) ?: return
        val hint = root.findViewById<TextView>(R.id.file_hint) ?: return
        val back = root.findViewById<Button>(R.id.btn_back_dir) ?: return

        if (currentDir == null) {
            val d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            currentDir = if (d?.isDirectory == true) d else File("/")
        }

        var entries: List<FileEntry> = emptyList()

        fun refresh() {
            val dir = currentDir ?: return
            dirLabel.text = dir.absolutePath
            // "..."-Button nur sichtbar, wenn wirklich eine Parent-Ebene existiert
            back.visibility = if (dir.parentFile != null) View.VISIBLE else View.GONE
            back.text = ".."
            val raw = dir.listFiles()
            if (raw == null) {
                hint.text = "Zugriff verweigert – voller Dateizugriff nötig."
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
            val labels = ArrayList<String>()
            // KEIN ".."-Eintrag in der Liste mehr – Navigation ausschließlich über den Header-Button
            labels += entries.map { it.label }
            list.adapter = ArrayAdapter(root.context, android.R.layout.simple_list_item_1, labels)
        }

        fun navigate(to: File) {
            currentDir = to
            refresh()
        }

        back.setOnClickListener { currentDir?.parentFile?.let { navigate(it) } }
        list.setOnItemClickListener { _, _, position, _ ->
            // da kein ".."-Eintrag mehr in der Liste ist: Position = direkter Index
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

    private fun forEachView(root: View, action: (View) -> Unit) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) forEachView(root.getChildAt(i), action)
        } else {
            action(root)
        }
    }
}
