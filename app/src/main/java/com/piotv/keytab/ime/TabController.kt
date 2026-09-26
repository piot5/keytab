package com.piotv.keytab.ime

import android.view.View
import android.widget.Button
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.R

/** Tab-Controller für ABC, Editor, Files, Clipboard und Snippets. */
internal class TabController(private val host: TabHost) {
    private companion object { const val TAB_TEXT_SIZE_SP = 8f }

    internal enum class TabKind { ABC, EDITOR, FILES, CLIP, SNIPPET }
    private var showSymbols = false
    private var kinds: List<TabKind> = listOf(TabKind.ABC, TabKind.EDITOR, TabKind.FILES)
    private var currentKind: TabKind = TabKind.ABC
    private var currentTabs: TabLayout? = null

    fun currentTabKind(): TabKind = currentKind
    fun select(kind: TabKind) {
        val index = kinds.indexOf(kind)
        if (index >= 0) currentTabs?.getTabAt(index)?.select()
    }
    fun resetSymbols() { showSymbols = false }

    private fun applyUniformTabTextSize(tabs: TabLayout) {
        val group = tabs.getChildAt(0) as? android.view.ViewGroup ?: return
        for (i in 0 until group.childCount) {
            val cell = group.getChildAt(i) as? android.view.ViewGroup ?: continue
            for (j in 0 until cell.childCount) {
                val tv = cell.getChildAt(j) as? android.widget.TextView ?: continue
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, TAB_TEXT_SIZE_SP)
                tv.maxLines = 1
            }
        }
    }

    fun setup(root: View) {
        val tabs = root.findViewById<TabLayout>(R.id.ime_tabs) ?: return
        currentTabs = tabs
        val kb = root.findViewById<View>(R.id.kb_panel) ?: return
        val sym = root.findViewById<View>(R.id.sym_panel) ?: return
        val fm = root.findViewById<View>(R.id.file_panel) ?: return
        val ed = root.findViewById<View>(R.id.editor_panel) ?: return
        val clip = root.findViewById<View>(R.id.clip_panel) ?: return
        val snip = root.findViewById<View>(R.id.snippet_panel) ?: return
        val bottom = root.findViewById<View>(R.id.bottom_row) ?: return
        val prefs = com.piotv.keytab.Prefs.of(host.context)
        val clipEnabled = prefs.getBoolean(com.piotv.keytab.Prefs.KEY_CLIP_TAB, true)
        val snipEnabled = prefs.getBoolean(com.piotv.keytab.Prefs.KEY_SNIPPET_TAB, true)

        tabs.clearOnTabSelectedListeners()
        host.inputRouter?.kind = InputKind.APP
        tabs.removeAllTabs()
        tabs.addTab(tabs.newTab().setText(host.context.getString(R.string.ime_tab_letters)))
        tabs.addTab(tabs.newTab().setText(host.context.getString(R.string.ime_tab_editor)))
        tabs.addTab(tabs.newTab().setText(host.context.getString(R.string.ime_tab_files)))
        if (clipEnabled) tabs.addTab(tabs.newTab().setText(host.context.getString(R.string.ime_tab_clip_short)))
        if (snipEnabled) tabs.addTab(tabs.newTab().setText(host.context.getString(R.string.ime_tab_snip_short)))
        applyUniformTabTextSize(tabs)
        kinds = buildList {
            add(TabKind.ABC); add(TabKind.EDITOR); add(TabKind.FILES)
            if (clipEnabled) add(TabKind.CLIP)
            if (snipEnabled) add(TabKind.SNIPPET)
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                host.letterPopup.dismiss()
                val kind = kinds.getOrNull(tab.position) ?: TabKind.ABC
                currentKind = kind
                root.setTag(R.id.sug_hide, false)
                val sugBar = root.findViewById<View>(R.id.suggestion_bar)
                val lettersRoot = sugBar?.parent as? android.view.ViewGroup
                if (lettersRoot != null) for (i in 0 until lettersRoot.childCount)
                    lettersRoot.getChildAt(i).visibility = View.VISIBLE
                bottom.visibility = View.VISIBLE
                val keyboardVisible = kind == TabKind.ABC || kind == TabKind.EDITOR
                host.inputRouter?.kind = if (kind == TabKind.EDITOR) InputKind.EDITOR else InputKind.APP
                ed.visibility = if (kind == TabKind.EDITOR) View.VISIBLE else View.GONE
                fm.visibility = if (kind == TabKind.FILES) View.VISIBLE else View.GONE
                clip.visibility = if (kind == TabKind.CLIP) View.VISIBLE else View.GONE
                snip.visibility = if (kind == TabKind.SNIPPET) View.VISIBLE else View.GONE
                kb.visibility = if (keyboardVisible) View.VISIBLE else View.INVISIBLE
                sym.visibility = if (showSymbols && keyboardVisible) View.VISIBLE else View.GONE
                root.findViewById<View>(R.id.key_toggle)?.visibility =
                    if (keyboardVisible) View.VISIBLE else View.INVISIBLE
                root.findViewById<View>(R.id.key_tab)?.visibility =
                    if (keyboardVisible) View.VISIBLE else View.INVISIBLE
                root.findViewById<View>(R.id.key_space)?.visibility =
                    if (keyboardVisible) View.VISIBLE else View.INVISIBLE
                root.findViewById<View>(R.id.key_dot)?.visibility =
                    if (keyboardVisible) View.VISIBLE else View.INVISIBLE
                root.findViewById<View>(R.id.key_enter)?.visibility = View.VISIBLE
                root.findViewById<View>(R.id.key_del)?.visibility =
                    if (keyboardVisible && !showSymbols) View.VISIBLE else View.INVISIBLE
                if (kind == TabKind.FILES || kind == TabKind.CLIP || kind == TabKind.SNIPPET) {
                    val h = PanelHeights.filesPanelHeight(ed, kb, root.resources.displayMetrics.widthPixels)
                    for (p in listOf(fm, clip, snip)) if (h > 0 && p.layoutParams.height != h)
                        p.layoutParams = p.layoutParams.apply { height = h }
                }
                if (kind == TabKind.FILES) host.fileManagerPanel?.show()
                if (kind == TabKind.CLIP) { host.clipboardPanel?.onSelected(); host.clipboardPanel?.refreshList(root) }
                if (kind == TabKind.SNIPPET) host.snippetPanel?.onSelected(root)
                if (kind == TabKind.EDITOR) host.clipboardPanel?.onSelected()
                val sugHideBtn = root.findViewById<android.widget.TextView>(R.id.sug_hide)
                sugHideBtn?.visibility = if (kind == TabKind.EDITOR) View.VISIBLE else View.GONE
                sugHideBtn?.text = "⇲"
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        kb.visibility = View.VISIBLE; sym.visibility = View.GONE; ed.visibility = View.GONE
        fm.visibility = View.GONE; clip.visibility = View.GONE; snip.visibility = View.GONE; bottom.visibility = View.VISIBLE
    }

    fun toggleSymbols(root: View) {
        showSymbols = !showSymbols
        root.findViewById<View>(R.id.kb_panel)?.visibility = if (showSymbols) View.GONE else View.VISIBLE
        root.findViewById<View>(R.id.sym_panel)?.visibility = if (showSymbols) View.VISIBLE else View.GONE
        root.findViewById<Button>(R.id.key_toggle)?.text =
            if (showSymbols) host.context.getString(R.string.key_toggle_letters) else "?123"
    }
}
