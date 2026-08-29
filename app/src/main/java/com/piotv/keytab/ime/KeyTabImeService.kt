package com.piotv.keytab.ime

import android.annotation.SuppressLint
import android.inputmethodservice.InputMethodService
import android.os.Environment
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.R
import java.io.File
import java.util.Locale

class KeyTabImeService : InputMethodService() {

    private var shifted = false
    private var keyboardRoot: View? = null
    private var currentDir: File? = null
    private val backStack = mutableListOf<File>()
    private var showSymbols = false
    private var activePopup: PopupWindow? = null

    private data class FileEntry(val file: File, val label: String, val info: String)

    // Long-Press Buchstaben → Sonderzeichen / Umlaute
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
        val fm = root.findViewById<View>(R.id.file_panel) ?: return
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val isKb = tab.position == 0
                kb.visibility = if (isKb) View.VISIBLE else View.GONE
                if (isKb) {
                    fm.visibility = View.GONE
                } else {
                    fm.visibility = View.VISIBLE
                    setupFileManager(root)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        kb.visibility = View.VISIBLE
        fm.visibility = View.GONE
    }

    private fun hookKeyboardButtons(root: View) {
        forEachView(root) { v ->
            val btn = v as? Button ?: return@forEachView
            when {
                btn.tag == "letter" -> {
                    btn.setOnClickListener {
                        activePopup?.dismiss()
                        commitText(btn.text.toString())
                    }
                    btn.isLongClickable = true
                    btn.setOnLongClickListener {
                        showLetterExtras(btn)
                        true
                    }
                }
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
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
                }
                btn.id == R.id.key_del -> {
                    btn.setOnClickListener {
                        activePopup?.dismiss()
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                    }
                    btn.isLongClickable = true
                    btn.setOnLongClickListener {
                        deleteLastWord()
                        true
                    }
                }
                btn.id == R.id.key_enter -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
                btn.id == R.id.key_space -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    currentInputConnection?.commitText(" ", 1)
                }
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
        currentInputConnection?.commitText(text, 1)
        if (shifted) {
            shifted = false
            applyLetterCase(keyboardRoot)
        }
    }

    private fun setupFileManager(root: View) {
        val list = root.findViewById<ListView>(R.id.file_list) ?: return
        val dirLabel = root.findViewById<TextView>(R.id.file_dir) ?: return
        val hint = root.findViewById<TextView>(R.id.file_hint) ?: return
        val back = root.findViewById<Button>(R.id.btn_back_dir) ?: return
        val up = root.findViewById<Button>(R.id.btn_up_dir) ?: return

        if (currentDir == null) {
            val pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
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

    private fun forEachView(root: View, action: (View) -> Unit) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) forEachView(root.getChildAt(i), action)
        } else {
            action(root)
        }
    }
}
