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
    private var currentRoot: View? = null

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
        // Die Panel-Views werden in [applyTabVisibility] gesucht (und dort
        // null-sicher behandelt): der Startzustand läuft durch dieselbe
        // Funktion wie jeder Tab-Wechsel, damit es nur *einen* Ort gibt, der
        // Sichtbarkeiten kennt.
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
        currentRoot = root
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                applySelected(tab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            // Bei bereits aktivem Tab (z. B. nach KeyboardCollapse.expand(),
            // das bottom_row auf GONE gestellt hat) feuert TabLayout nur hier —
            // die Sichtbarkeiten müssen trotzdem neu gesetzt werden, sonst fehlt
            // nach dem Vollbild die Key-Leiste und die Tastatur ist unbedienbar.
            override fun onTabReselected(tab: TabLayout.Tab) {
                applySelected(tab)
            }
        })
        // Startzustand: `addTab()` hat den ersten Tab (abc) bereits ausgewählt,
        // *bevor* der Listener hing — sein `onTabSelected` läuft also nie. Bis
        // hierher blieb deshalb die Maximieren-Zeile stehen, obwohl sie im
        // abc-Tab nichts zu suchen hat. Der Startzustand läuft jetzt durch
        // dieselbe Funktion wie jeder Tab-Wechsel.
        applyTabVisibility(root, currentKind)
    }

    /**
     * Ein Tab-Wechsel (oder Re-Apply desselben Tabs) — einziger Ort, der die
     * Sichtbarkeiten und Hoehen eines Tabs setzt (siehe [applyTabVisibility]).
     */
    private fun applySelected(tab: TabLayout.Tab) {
        val root = currentRoot
        if (root == null) return
        host.letterPopup.dismiss()
        val kind = kinds.getOrNull(tab.position) ?: TabKind.ABC
        currentKind = kind
        root.setTag(R.id.maximized_state, false)
        applyTabVisibility(root, kind)
        host.inputRouter?.kind = if (kind == TabKind.EDITOR) InputKind.EDITOR else InputKind.APP
        if (kind == TabKind.FILES) host.fileManagerPanel?.show()
        if (kind == TabKind.CLIP) { host.clipboardPanel?.onSelected(); host.clipboardPanel?.refreshList(root) }
        if (kind == TabKind.SNIPPET) host.snippetPanel?.onSelected(root)
        if (kind == TabKind.EDITOR) host.clipboardPanel?.onSelected()
        // Symbol an den aktuellen Vollbild-Zustand anpassen (die
        // Sichtbarkeit von Zeile und Symbol setzt [applyTabVisibility]).
        val maximized = root.getTag(R.id.maximized_state) as? Boolean ?: false
        val symbol = if (maximized) "⇱" else "⇲"
        (root.findViewById<android.widget.TextView>(R.id.key_maximize))?.text = symbol
        (root.findViewById<android.widget.TextView>(R.id.sug_hide))?.text = symbol
        // Normalzustand aller Inhalts-Tabs:
        //  * Editor: sein Panel behaelt seine feste Hoehe, darunter
        //    steht die Tastatur (er ist die Hoehenreferenz),
        //  * Files/Clip/Snippets: Panel so hoch wie Editor + Tastatur
        //    **minus Maximieren-Zeile** — diese Zeile haben nur die
        //    Inhalts-Tabs, deshalb sind so alle Tabs gleich hoch.
        // Bewusst *nach* den Sichtbarkeits-Wechseln: dann ist die
        // Tastaturhoehe gemessen und nicht geschaetzt. Je Tab kann eine
        // Hoehe in der Config-Datei stehen (TabHeights); 0 = rechnen.
        val width = root.resources.displayMetrics.widthPixels
        PanelHeights.applyNormalHeights(
            root, width, TabHeights.overridePx(host.context, kind, maximized = false)
        )
        // Im maximierten Zustand darf der Tab-Wechsler die Hoehe nicht
        // zuruecksetzen — der Zustand gehoert zum Tab.
        if (root.getTag(R.id.maximized_state) == true) {
            PanelHeights.maximizedPanelHeight(
                root,
                width,
                isKeyboardAlwaysVisible(),
                TabHeights.overridePx(host.context, kind, maximized = true)
            ).takeIf { it > 0 }?.let { h ->
                val panel = contentPanelOf(root, kind) ?: return@let
                panel.layoutParams = panel.layoutParams.apply { height = h }
            }
        }
    }

    /**
     * Sichtbarkeit in einer Zeile setzen: sichtbar oder `hidden`.
     *
     * `hidden` ist absichtlich parametrisierbar: [View.GONE] nimmt den Platz weg
     * (Tastatur in Inhalts-Tabs), [View.INVISIBLE] lässt ihn stehen (die
     * Funktionsleiste, damit die Zeilenhöhe in allen Tabs gleich bleibt).
     */
    private fun View?.applyVisibility(visible: Boolean, hidden: Int = View.GONE) {
        this?.visibility = if (visible) View.VISIBLE else hidden
    }

    /**
     * Geschwister der Vorschlagsleiste zurücksetzen: nach dem Vollbild-Modus
     * können dort Zeilen GONE geblieben sein.
     */
    private fun resetSuggestionSiblings(root: View) {
        val lettersRoot =
            root.findViewById<View>(R.id.suggestion_bar)?.parent as? android.view.ViewGroup
            ?: return
        for (i in 0 until lettersRoot.childCount) {
            lettersRoot.getChildAt(i).visibility = View.VISIBLE
        }
    }

    /**
     * Sichtbarkeiten eines Tabs setzen — ohne Panel-Nachladen und ohne
     * Höhenrechnung.
     *
     * Der eine Ort für „was ist in diesem Tab sichtbar": beim Tab-Wechsel und
     * beim ersten Aufbau ([setup]). Höhen und Nachladen bleiben beim Aufrufer,
     * weil sie beim Start noch nicht messbar sind.
     */
    private fun applyTabVisibility(root: View, kind: TabKind) {
        resetSuggestionSiblings(root)
        root.findViewById<View>(R.id.editor_panel).applyVisibility(kind == TabKind.EDITOR)
        root.findViewById<View>(R.id.file_panel).applyVisibility(kind == TabKind.FILES)
        root.findViewById<View>(R.id.clip_panel).applyVisibility(kind == TabKind.CLIP)
        root.findViewById<View>(R.id.snippet_panel).applyVisibility(kind == TabKind.SNIPPET)
        applyKeyboardRowVisibility(root, kind == TabKind.ABC || kind == TabKind.EDITOR)
        applyMaximizeVisibility(root, kind)
    }

    /** Tastatur, Symbol-Ebene und die gemeinsame Funktionsleiste. */
    private fun applyKeyboardRowVisibility(root: View, keyboardVisible: Boolean) {
        // GONE statt INVISIBLE für die Tastatur: in den Inhalts-Tabs ist sie
        // wirklich weg. INVISIBLE liess sie ihren Platz stehen und verdoppelte
        // so die Hoehe (Panel + unsichtbare Tastatur).
        root.findViewById<View>(R.id.kb_panel).applyVisibility(keyboardVisible)
        root.findViewById<View>(R.id.sym_panel).applyVisibility(showSymbols && keyboardVisible)
        root.findViewById<View>(R.id.bottom_row).applyVisibility(true)
        // Die Tasten der Funktionsleiste bleiben als Platzhalter stehen
        // (INVISIBLE), Enter ist immer nutzbar.
        root.findViewById<View>(R.id.key_toggle).applyVisibility(keyboardVisible, View.INVISIBLE)
        root.findViewById<View>(R.id.key_tab).applyVisibility(keyboardVisible, View.INVISIBLE)
        root.findViewById<View>(R.id.key_space).applyVisibility(keyboardVisible, View.INVISIBLE)
        root.findViewById<View>(R.id.key_dot).applyVisibility(keyboardVisible, View.INVISIBLE)
        root.findViewById<View>(R.id.key_enter).applyVisibility(true)
        root.findViewById<View>(R.id.key_del)
            .applyVisibility(keyboardVisible && !showSymbols, View.INVISIBLE)
    }

    /**
     * Maximieren-Bedienung, abhängig vom Tab:
     *  * Editor: das Symbol sitzt rechts in der Prediction-Zeile (dort ist die
     *    Leiste ohnehin sichtbar) — die Max-Zeile entfällt, damit der Editor
     *    nicht doppelt bedient wird.
     *  * Files/Clip/Snippets: die Max-Zeile am unteren Rand, weil diese Tabs
     *    keine Prediction-Zeile haben.
     *  * abc: **keine** Maximieren-Zeile. Im Buchstaben-Tab gibt es nichts zu
     *    vergrößern — dort ist die Tastatur selbst das größte Element.
     */
    private fun applyMaximizeVisibility(root: View, kind: TabKind) {
        root.findViewById<View>(R.id.maximize_row)?.visibility =
            if (showsMaximizeRow(kind)) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.sug_hide)?.visibility =
            if (kind == TabKind.EDITOR) View.VISIBLE else View.GONE
    }

    /** Zeigt der aktuelle Tab die Maximieren-Zeile am unteren Rand? */
    fun showsMaximizeRow(): Boolean = showsMaximizeRow(currentKind)

    /**
     * Maximieren-Zeile nur in den Inhalts-Tabs: sie haben keine Prediction-Zeile,
     * in der das Symbol sitzen könnte. Editor (Symbol in der Prediction-Zeile) und
     * abc (gar kein Maximieren) haben keine eigene Zeile — auch nicht im
     * Vollbild-Modus ([KeyboardCollapse.collapse]).
     */
    private fun showsMaximizeRow(kind: TabKind): Boolean =
        kind == TabKind.FILES || kind == TabKind.CLIP || kind == TabKind.SNIPPET

    /** Das Inhalts-Panel, das zu diesem Tab gehoert (nie die Tastatur). */
    private fun contentPanelOf(root: View, kind: TabKind): View? = when (kind) {
        TabKind.EDITOR -> root.findViewById(R.id.editor_panel)
        TabKind.FILES -> root.findViewById(R.id.file_panel)
        TabKind.CLIP -> root.findViewById(R.id.clip_panel)
        TabKind.SNIPPET -> root.findViewById(R.id.snippet_panel)
        TabKind.ABC -> null
    }

    /**
     * Muss die Buchstaben-Tastatur in diesem Tab dauerhaft sichtbar sein?
     *
     * Wahr im abc- und im Editor-Tab: im Editor ist die Tastatur das einzige
     * Eingabewerkzeug — im maximierten Zustand wegzunehmen hieße, im Text nichts
     * mehr tippen zu können. In Files/Clip/Snippets nie: dort ersetzt das
     * Inhalts-Panel die Tastatur.
     */
    fun isKeyboardAlwaysVisible(): Boolean =
        currentKind == TabKind.ABC || currentKind == TabKind.EDITOR

    fun toggleSymbols(root: View) {
        showSymbols = !showSymbols
        root.findViewById<View>(R.id.kb_panel)?.visibility = if (showSymbols) View.GONE else View.VISIBLE
        root.findViewById<View>(R.id.sym_panel)?.visibility = if (showSymbols) View.VISIBLE else View.GONE
        root.findViewById<Button>(R.id.key_toggle)?.text =
            if (showSymbols) host.context.getString(R.string.key_toggle_letters) else "?123"
    }
}
