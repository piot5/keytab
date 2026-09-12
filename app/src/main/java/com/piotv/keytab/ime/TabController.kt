package com.piotv.keytab.ime

import android.view.View
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.R

/**
 * Tab-Controller – Tab-Umschaltung (ABC/Notes/Files/Terminal), Panel-Visibility,
 * PanelHeights-Berechnung und Symbol-Layer-Toggle.
 *
 * Refactoring (docs/REFACTORING_PLAN.md Phase 4): aus [KeyTabImeService]
 * extrahiert. Der Service ruft [setup] beim View-Aufbau und [toggleSymbols]
 * beim Drücken der ?123-Taste.
 *
 * Die toten `editorActive`/`terminalActive`-Felder des Service entfallen hier
 * komplett – das Eingabe-Routing läuft seit Phase 2 über [InputRouter].
 * `showSymbols` lebt nun hier (statt als Service-Feld).
 *
 * Verhalten bleibt bit-identisch („Umziehen statt Umschreiben").
 */
internal class TabController(private val host: KeyboardHost) {

    /** Symbol-Layer sichtbar? (Toggle über ?123-Taste). */
    private var showSymbols = false

    /** Symbol-Status zurücksetzen (beim Rebuild der Tastatur). */
    fun resetSymbols() {
        showSymbols = false
    }

    /**
     * Tab-Leiste einrichten: Beschriftungen, Terminal-Tab ggf. entfernen,
     * Tab-Selection-Listener mit Panel-Visibility + PanelHeights.
     *
     * Tab 0=abc, 1=Notes (Editor + Ablage), 2=Files, 3=Terminal.
     */
    fun setup(root: View) {
        val tabs = root.findViewById<TabLayout>(R.id.ime_tabs) ?: return
        // Beschriftungen explizit setzen (TabItem-Texte können beim
        // Inflaten mit eigenem LayoutInflater verloren gehen)
        tabs.getTabAt(0)?.text = host.context.getString(R.string.ime_tab_letters)
        tabs.getTabAt(1)?.text = host.context.getString(R.string.ime_tab_editor)
        tabs.getTabAt(2)?.text = host.context.getString(R.string.ime_tab_files)
        tabs.getTabAt(3)?.text = host.context.getString(R.string.ime_tab_term_short)
        val kb = root.findViewById<View>(R.id.kb_panel) ?: return
        val sym = root.findViewById<View>(R.id.sym_panel) ?: return
        val fm = root.findViewById<View>(R.id.file_panel) ?: return
        val ed = root.findViewById<View>(R.id.editor_panel) ?: return
        val term = root.findViewById<View>(R.id.term_panel) ?: return
        val bottom = root.findViewById<View>(R.id.bottom_row) ?: return
        // Terminal-Tab ist optional (Einstellungen-App): aus -> Tab entfernen
        val termEnabled = host.context.getSharedPreferences(
            com.piotv.keytab.Prefs.FILE, android.content.Context.MODE_PRIVATE)
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                host.letterPopup.dismiss()
                val pos = tab.position
                // Tab 0=abc, 1=Notes, 2=Files, 3=Terminal
                // Notes/Terminal zeigen die Tastatur + Eingabezeile über der Tastatur
                val keyboardVisible = pos == 0 || pos == 1 || pos == 3
                // Eingabe-Routing: aktives Ziel an den Tab koppeln
                host.inputRouter?.kind = when (pos) {
                    1 -> InputKind.EDITOR
                    3 -> InputKind.TERMINAL
                    else -> InputKind.APP
                }
                kb.visibility = if (keyboardVisible && !showSymbols) View.VISIBLE else View.GONE
                sym.visibility = if (keyboardVisible && showSymbols) View.VISIBLE else View.GONE
                ed.visibility = if (pos == 1) View.VISIBLE else View.GONE
                term.visibility = if (pos == 3) View.VISIBLE else View.GONE
                fm.visibility = if (pos == 2) View.VISIBLE else View.GONE
                // In ALLEN Tabs die ENTER-Taste erreichbar lassen – mit konstanter Größe und
                // Position. Dafür werden die übrigen Tasten auf INVISIBLE (Platz bleibt)
                // statt GONE gesetzt, damit Enter rechtsbündig und identisch bleibt.
                bottom.visibility = View.VISIBLE
                root.findViewById<View>(R.id.key_toggle)?.visibility =
                    if (keyboardVisible) View.VISIBLE else View.INVISIBLE
                root.findViewById<View>(R.id.key_tab)?.visibility =
                    if (keyboardVisible) View.VISIBLE else View.INVISIBLE
                root.findViewById<View>(R.id.key_space)?.visibility =
                    if (keyboardVisible) View.VISIBLE else View.INVISIBLE
                root.findViewById<View>(R.id.key_dot)?.visibility =
                    if (keyboardVisible) View.VISIBLE else View.INVISIBLE
                // Enter-Taste ist in ALLEN Tabs erreichbar — explizit VISIBLE
                // setzen (liegt außerhalb kb_panel → von kb_panel-GONE nicht
                // verdeckt; wird sonst durch XML-Default erst sichtbar).
                root.findViewById<View>(R.id.key_enter)?.visibility = View.VISIBLE
                // Del-Taste ist Teil der Buchstaben-Tastatur (in kb_panel).
                // Wird sichtbar, wenn die Tastatur angezeigt wird.
                root.findViewById<View>(R.id.key_del)?.visibility =
                    if (keyboardVisible && !showSymbols) View.VISIBLE else View.INVISIBLE
                if (pos == 2) {
                    // Files-Tab genauso hoch wie Notes-Tab: Das Datei-Panel nimmt die
                    // Höhe von Editor-Panel + Buchstaben-Panel ein (gemessen, nicht
                    // hartkodiert) → gleiche Gesamthöhe beim Tab-Wechsel.
                    val h = PanelHeights.filesPanelHeight(ed, kb,
                        root.resources.displayMetrics.widthPixels)
                    if (h > 0 && fm.layoutParams.height != h) {
                        fm.layoutParams = fm.layoutParams.apply { height = h }
                    }
                    host.fileManagerPanel?.show()
                }
                if (pos == 1) host.clipboardPanel?.onSelected()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        kb.visibility = View.VISIBLE
        sym.visibility = View.GONE
        ed.visibility = View.GONE
        fm.visibility = View.GONE
        term.visibility = View.GONE
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
            .getBoolean(com.piotv.keytab.Prefs.KEY_TERM_TAB, true)
        if (!termEnabled) {
            tabs.getTabAt(3)?.let { tabs.removeTab(it) }
            term.visibility = View.GONE
        }
