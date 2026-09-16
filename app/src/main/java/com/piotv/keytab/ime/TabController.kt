package com.piotv.keytab.ime

import android.view.View
import android.widget.Button
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.R

/**
 * Tab-Controller – Tab-Umschaltung (ABC/Editor/Files/Clip/Terminal),
 * Panel-Visibility, PanelHeights-Berechnung und Symbol-Layer-Toggle.
 *
 * Clip und Terminal sind optionale Tabs (Einstellungen-App). Da optionale Tabs
 * entfernt werden, können Positionen nicht hartkodiert werden — stattdessen
 * wird eine [kinds]-Liste nach dem Entfernen aufgebaut.
 */
internal class TabController(private val host: KeyboardHost) {

    /** Schriftgröße der Tab-Beschriftungen (muss zu `KeyTabSmallTabText` passen). */
    private companion object {
        const val TAB_TEXT_SIZE_SP = 9f
    }

    /** Art eines Tabs (für Position→Verhalten-Mapping nach optionalem Entfernen). */
    private enum class TabKind { ABC, EDITOR, FILES, CLIP, TERMINAL, SNIPPET }

    /** Symbol-Layer sichtbar? (Toggle über ?123-Taste). */
    private var showSymbols = false

    /** Position → TabKind (nach optionalem Entfernen von Clip/Terminal). */
    private var kinds: List<TabKind> = listOf(TabKind.ABC, TabKind.EDITOR, TabKind.FILES)

    /** Symbol-Status zurücksetzen (beim Rebuild der Tastatur). */
    fun resetSymbols() {
        showSymbols = false
    }

    /**
     * Tab-Leiste einrichten: Beschriftungen, Clip/Terminal-Tab ggf. entfernen,
     * Tab-Selection-Listener mit Panel-Visibility + PanelHeights.
     *
     * XML-Reihenfolge: 0=abc, 1=Editor, 2=Files, 3=Clip, 4=Terminal.
     * Optionale Tabs werden von hinten entfernt (Terminal vor Clip), damit die
     * Indizes der Pflicht-Tabs (0–2) stabil bleiben.
     */
    /**
     * Einheitliche Tab-Beschriftungsgröße erzwingen.
     *
     * Material's `TabLayout` verkleinert/vergrößert Labels je Zelle automatisch
     * (AppearanceHelper/autoSize im TabView), sodass das längste Label
     * („Terminal") optisch größer wirkte als „abc"/"Clip". Deshalb setzen wir
     * nach dem Text-Setzen die Größe für ALLE Zellen gemeinsam auf [sizeSp].
     */
    private fun applyUniformTabTextSize(tabs: TabLayout, sizeSp: Float) {
        val view = tabs.getChildAt(0) as? android.view.ViewGroup ?: return
        for (i in 0 until view.childCount) {
            val cell = view.getChildAt(i) as? android.view.ViewGroup ?: continue
            for (j in 0 until cell.childCount) {
                val tv = cell.getChildAt(j) as? android.widget.TextView ?: continue
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp)
            }
        }
    }

    fun setup(root: View) {
        val tabs = root.findViewById<TabLayout>(R.id.ime_tabs) ?: return
        tabs.getTabAt(0)?.text = host.context.getString(R.string.ime_tab_letters)
        tabs.getTabAt(1)?.text = host.context.getString(R.string.ime_tab_editor)
        tabs.getTabAt(2)?.text = host.context.getString(R.string.ime_tab_files)
        tabs.getTabAt(3)?.text = host.context.getString(R.string.ime_tab_clip_short)
        tabs.getTabAt(4)?.text = host.context.getString(R.string.ime_tab_term_short)
        tabs.getTabAt(5)?.text = host.context.getString(R.string.ime_tab_snip_short)
        // Alle Labels gleich groß (Material auto-sizt sonst je Zelle unterschiedlich)
        applyUniformTabTextSize(tabs, TAB_TEXT_SIZE_SP)
        val kb = root.findViewById<View>(R.id.kb_panel) ?: return
        val sym = root.findViewById<View>(R.id.sym_panel) ?: return
        val fm = root.findViewById<View>(R.id.file_panel) ?: return
        val ed = root.findViewById<View>(R.id.editor_panel) ?: return
        val clip = root.findViewById<View>(R.id.clip_panel) ?: return
        val term = root.findViewById<View>(R.id.term_panel) ?: return
        val snip = root.findViewById<View>(R.id.snippet_panel) ?: return
        val bottom = root.findViewById<View>(R.id.bottom_row) ?: return
        val prefs = host.context.getSharedPreferences(
            com.piotv.keytab.Prefs.FILE, android.content.Context.MODE_PRIVATE)
        val clipEnabled = prefs.getBoolean(com.piotv.keytab.Prefs.KEY_CLIP_TAB, true)
        val termEnabled = prefs.getBoolean(com.piotv.keytab.Prefs.KEY_TERM_TAB, true)
        val snipEnabled = prefs.getBoolean(com.piotv.keytab.Prefs.KEY_SNIPPET_TAB, true)
        // Von hinten entfernen (Snippet bei 5, Terminal bei 4, Clip bei 3) → Pflicht-Tabs stabil
        if (!snipEnabled) {
            tabs.getTabAt(5)?.let { tabs.removeTab(it) }
            snip.visibility = View.GONE
        }
        if (!termEnabled) {
            tabs.getTabAt(4)?.let { tabs.removeTab(it) }
            term.visibility = View.GONE
        }
        if (!clipEnabled) {
            tabs.getTabAt(3)?.let { tabs.removeTab(it) }
            clip.visibility = View.GONE
        }
        kinds = buildList {
            add(TabKind.ABC); add(TabKind.EDITOR); add(TabKind.FILES)
            if (clipEnabled) add(TabKind.CLIP)
            if (termEnabled) add(TabKind.TERMINAL)
            if (snipEnabled) add(TabKind.SNIPPET)
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                host.letterPopup.dismiss()
                val pos = tab.position
                val kind = kinds.getOrNull(pos) ?: TabKind.ABC
                val keyboardVisible = kind == TabKind.ABC ||
                    kind == TabKind.EDITOR || kind == TabKind.TERMINAL
                host.inputRouter?.kind = when (kind) {
                    TabKind.EDITOR -> InputKind.EDITOR
                    TabKind.TERMINAL -> InputKind.TERMINAL
                    else -> InputKind.APP
                }
                kb.visibility = if (keyboardVisible && !showSymbols) View.VISIBLE else View.GONE
                sym.visibility = if (keyboardVisible && showSymbols) View.VISIBLE else View.GONE
                ed.visibility = if (kind == TabKind.EDITOR) View.VISIBLE else View.GONE
                term.visibility = if (kind == TabKind.TERMINAL) View.VISIBLE else View.GONE
                fm.visibility = if (kind == TabKind.FILES) View.VISIBLE else View.GONE
                clip.visibility = if (kind == TabKind.CLIP) View.VISIBLE else View.GONE
                snip.visibility = if (kind == TabKind.SNIPPET) View.VISIBLE else View.GONE
                bottom.visibility = View.VISIBLE
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
                // Einheitliche Panel-Höhe: Files/Clip/Terminal/Snippet-Panel = Höhe von
                // Editor-Panel + Buchstaben-Panel (gemessen) → alle Tabs gleich hoch.
                if (kind == TabKind.FILES || kind == TabKind.CLIP ||
                    kind == TabKind.TERMINAL || kind == TabKind.SNIPPET) {
                    val h = PanelHeights.filesPanelHeight(ed, kb,
                        root.resources.displayMetrics.widthPixels)
                    for (p in listOf(fm, clip, term, snip)) {
                        if (h > 0 && p.layoutParams.height != h) {
                            p.layoutParams = p.layoutParams.apply { height = h }
                        }
                    }
                }
                if (kind == TabKind.FILES) host.fileManagerPanel?.show()
                if (kind == TabKind.CLIP) {
                    host.clipboardPanel?.onSelected()
                    host.clipboardPanel?.refreshList(root)
                }
                if (kind == TabKind.SNIPPET) host.snippetPanel?.onSelected(root)
                if (kind == TabKind.EDITOR) host.clipboardPanel?.onSelected()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        kb.visibility = View.VISIBLE
        sym.visibility = View.GONE
        ed.visibility = View.GONE
        fm.visibility = View.GONE
        clip.visibility = View.GONE
        term.visibility = View.GONE
        snip.visibility = View.GONE
        bottom.visibility = View.VISIBLE
    }

    /** Buchstaben-/Symbol-Layer umschalten (?123-Taste). */
    fun toggleSymbols(root: View) {
        showSymbols = !showSymbols
        root.findViewById<View>(R.id.kb_panel)?.visibility =
            if (showSymbols) View.GONE else View.VISIBLE
        root.findViewById<View>(R.id.sym_panel)?.visibility =
            if (showSymbols) View.VISIBLE else View.GONE
        root.findViewById<Button>(R.id.key_toggle)?.text =
            if (showSymbols) host.context.getString(R.string.key_toggle_letters) else "?123"
    }
}
