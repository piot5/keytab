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

/**
 * KeyTab IME – Tastatur mit TAB-Taste + Tab-Leiste über der Tastatur:
 * Tab „q“  = Buchstaben-Tastatur
 * Tab „FM“ = Dateimanager (ersetzt die Buchstaben, fügt Pfade ein)
 */
class KeyTabImeService : InputMethodService() {

    private var shifted = false
    private var keyboardRoot: View? = null
    private var currentDir: File? = null

    override fun onCreateInputView(): View {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_KeyTab)
        val root = layoutInflater.cloneInContext(themedContext)
            .inflate(R.layout.keyboard_view, null)
        keyboardRoot = root
        setupTabs(root)
        hookKeyboardButtons(root)
        setupFileManager(root)
        applyCase(root)
        return root
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        shifted = false
        applyCase(keyboardRoot)
    }

    // ---------- Tab-Leiste q | FM ----------
    private fun setupTabs(root: View) {
        val tabs = root.findViewById<TabLayout>(R.id.ime_tabs) ?: return
        val kb = root.findViewById<View>(R.id.kb_panel) ?: return
        val fm = root.findViewById<View>(R.id.file_panel) ?: return
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                kb.visibility = if (tab.position == 0) View.VISIBLE else View.GONE
                fm.visibility = if (tab.position == 1) View.VISIBLE else View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        kb.visibility = View.VISIBLE
        fm.visibility = View.GONE
    }

    // ---------- Buchstaben- und Funktionstasten ----------
    private fun hookKeyboardButtons(root: View) {
        forEachView(root) { v ->
            val btn = v as? Button ?: return@forEachView
            when {
                btn.tag == "letter" -> btn.setOnClickListener {
                    commitText(btn.text.toString())
                }
                btn.id == R.id.key_shift -> btn.setOnClickListener {
                    shifted = !shifted
                    btn.alpha = if (shifted) 1f else 0.6f
                    applyCase(root)
                }
                btn.id == R.id.key_tab -> btn.setOnClickListener {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
                }
                btn.id == R.id.key_del -> btn.setOnClickListener {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
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

    private fun applyCase(view: View?) {
        if (view == null) return
        forEachView(view) { v ->
            val btn = v as? Button ?: return@forEachView
            if (btn.tag == "letter") {
                val base = btn.text.toString()
                btn.text = if (shifted) base.uppercase() else base.lowercase()
            }
        }
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
        if (shifted) {
            shifted = false
            applyCase(keyboardRoot)
        }
    }

    // ---------- Dateimanager im IME ----------
    private fun setupFileManager(root: View) {
        val list = root.findViewById<ListView>(R.id.file_list) ?: return
        val dirLabel = root.findViewById<TextView>(R.id.file_dir) ?: return
        val back = root.findViewById<View>(R.id.btn_back_dir) ?: return

        if (currentDir == null) {
            currentDir = Environment.getExternalStorageDirectory()
                ?.takeIf { it.canRead() }
                ?: File("/")
        }

        var entries: List<File> = emptyList()

        fun refresh() {
            val dir = currentDir ?: return
            dirLabel.text = dir.absolutePath
            entries = dir.listFiles()
                ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
                ?: emptyList()
            val labels = ArrayList<String>()
            if (dir.parentFile != null) labels.add("..  " + (dir.parentFile?.name ?: ""))
            labels += entries.map { f ->
                if (f.isDirectory) "\uD83D\uDCC1 ${f.name}/" else "\uD83D\uDCC4 ${f.name}"
            }
            list.adapter = ArrayAdapter(root.context, android.R.layout.simple_list_item_1, labels)
        }

        fun navigate(to: File) {
            currentDir = to
            refresh()
        }

        back.setOnClickListener { currentDir?.parentFile?.let { navigate(it) } }
        list.setOnItemClickListener { _, _, position, _ ->
            if (position == 0 && currentDir?.parentFile != null) {
                navigate(currentDir!!.parentFile!!)
                return@setOnItemClickListener
            }
            val offset = if (currentDir?.parentFile != null) 1 else 0
            val f = entries.getOrNull(position - offset) ?: return@setOnItemClickListener
            if (f.isDirectory) {
                navigate(f)
            } else {
                commitText(f.absolutePath)
                root.findViewById<TabLayout>(R.id.ime_tabs)?.getTabAt(0)?.select()
            }
        }
        refresh()
    }

    private fun forEachView(root: View, action: (View) -> Unit) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) forEachView(root.getChildAt(i), action)
        } else {
            action(root)
        }
    }
}
