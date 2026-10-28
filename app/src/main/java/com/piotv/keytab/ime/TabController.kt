package com.piotv.keytab.ime

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.Prefs
import com.piotv.keytab.R

/**
 * Die Ebenen, die der `?123`-Schalter durchblättert — in Blätter-Reihenfolge.
 *
 * Ein Tipp geht immer **eine** Ebene weiter:
 * `LETTERS` (abc) → `SYMBOLS` (?123) → `LETTERS`.
 *
 * [labelRes] ist die Beschriftung des Schalters, *solange diese Ebene sichtbar
 * ist* — sie zeigt die **nächste** Ebene (auf der Buchstaben-Ebene „?123", auf
 * der Symbol-Ebene „abc"). [panelId] ist das Tastatur-Panel der Ebene;
 * die gemeinsame Funktionsleiste (`bottom_row`) gehört zu keiner Ebene und
 * bleibt in beiden sichtbar.
 */
internal enum class SymbolPlane(val labelRes: Int, val panelId: Int) {
    LETTERS(R.string.key_toggle_symbols, R.id.kb_panel),
    SYMBOLS(R.string.key_toggle_letters, R.id.sym_panel);

    /** Nächste Ebene beim Tippen auf den Schalter (zyklisch). */
    fun next(): SymbolPlane = values()[(ordinal + 1) % values().size]
}

/**
 * Tab-Controller für ABC, Editor, Files, Clipboard und Snippets.
 *
 * `@Suppress("TooManyFunctions")` ist bewusst und begründet: der Controller
 * bündelt Tab-Aufbau, Symbol-Ebenen, Panel-Routing und die zwei Editor-Tab-Menüs.
 * detekts Schwelle liegt bei 20 Funktionen, hier sind es 25 (28.10.2026).
 * Ein Aufteilen in zwei Klassen wäre ein eigener Umbau (mit Testanpassungen) —
 * die Baseline bleibt leer, deshalb steht die Ausnahme sichtbar im Code.
 */
@Suppress("TooManyFunctions")
internal class TabController(private val host: TabHost) {
    private companion object { const val TAB_TEXT_SIZE_SP = 8f }

    internal enum class TabKind { ABC, EDITOR, FILES, CLIP, SNIPPET }
    private var plane: SymbolPlane = SymbolPlane.LETTERS
    private var kinds: List<TabKind> = listOf(TabKind.ABC, TabKind.EDITOR, TabKind.FILES)
    private var currentKind: TabKind = TabKind.ABC
    private var currentTabs: TabLayout? = null
    private var currentRoot: View? = null

    /** Editor-Tab (Plakette + Long-Press-Kontextmenü). */
    private var editorTab: TabLayout.Tab? = null

    fun currentTabKind(): TabKind = currentKind
    fun select(kind: TabKind) {
        val index = kinds.indexOf(kind)
        if (index >= 0) currentTabs?.getTabAt(index)?.select()
    }
    fun resetSymbols() { plane = SymbolPlane.LETTERS }

    /** Aktuell sichtbare Tastatur-Ebene (Buchstaben oder eine Symbol-Ebene). */
    fun currentPlane(): SymbolPlane = plane

    /**
     * Setzt das ⇱/⇲-Symbol an den aktuellen Vollbild-Zustand (N1).
     *
     * Das Symbol wird auch vom `hideKeyboard()`-Toggle gebraucht — dort
     * wechselt der Zustand, ohne dass ein Tab-Wechsel stattfindet.
     */
    fun updateMaximizeSymbol(root: View) {
        val maximized = root.getTag(R.id.maximized_state) as? Boolean ?: false
        val symbol = if (maximized) "⇱" else "⇲"
        (root.findViewById<android.widget.TextView>(R.id.key_maximize))?.text = symbol
        (root.findViewById<android.widget.TextView>(R.id.sug_hide))?.text = symbol
    }

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
        editorTab = tabs.getTabAt(1)
        setupEditorTabView(editorTab)
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
        // *bevor* der Listener hing — sein `onTabSelected` läuft also nie. Nach
        // einem Rebuild zeigt die Leiste deshalb wieder abc; `currentKind` muss
        // das widerspiegeln, sonst zeigen Leiste und Panel verschiedene Tabs
        // (G2: Files-Tab öffnen → Theme toggeln → inkonsistenter Zustand).
        currentKind = TabKind.ABC
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
        // G3: Beim Verlassen des Vollbild-Modus per Tab-Wechsel die Normalhöhe
        // des aktuell maximierten Panels wiederherstellen, BEVOR die Sichtbarkeit
        // umschaltet — expand() greift das noch sichtbare (maximierte) Panel ab.
        // applyNormalHeights unten deckt nur die Inhalts-Panels ab, nicht das
        // Editor-Panel; ohne diesen Schritt behielte es seine Maximalhöhe.
        if (root.getTag(R.id.maximized_state) == true) {
            KeyboardCollapse.expand(root)
        }
        val kind = kinds.getOrNull(tab.position) ?: TabKind.ABC
        currentKind = kind
        root.setTag(R.id.maximized_state, false)
        applyTabVisibility(root, kind)
        activatePanel(root, kind)
        updateEditorBadge()
        // Symbol an den aktuellen Vollbild-Zustand anpassen (die
        // Sichtbarkeit von Zeile und Symbol setzt [applyTabVisibility]).
        updateMaximizeSymbol(root)
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

    /** Aktiviert das zum Tab gehörige Panel und setzt den Eingabe-Router. */
    private fun activatePanel(root: View, kind: TabKind) {
        host.inputRouter?.kind = if (kind == TabKind.EDITOR) InputKind.EDITOR else InputKind.APP
        if (kind == TabKind.FILES) host.fileManagerPanel?.show()
        if (kind == TabKind.CLIP) { host.clipboardPanel?.onSelected(); host.clipboardPanel?.refreshList(root) }
        if (kind == TabKind.SNIPPET) host.snippetPanel?.onSelected(root)
        if (kind == TabKind.EDITOR) host.clipboardPanel?.onSelected()
    }

    /** Plakette am Editor-Tab neu zeichnen (wird vom Service nach Datei-Öffnen gerufen). */
    internal fun refreshEditorBadge() = updateEditorBadge()

    /**
     * Plakette am Editor-Tab anzeigen: sichtbar, wenn Dateien geöffnet sind.
     *
     * Der Zähler steht im [EditorPanel] — er wird bei jedem geöffneten Datei
     * erhöht (siehe `EditorPanel.onFileOpened`). Bei 0 offenen Dateien bleibt
     * die Plakette ausgeblendet.
     */
    private fun updateEditorBadge() {
        editorTab?.let { tab ->
            val badge = tab.customView?.findViewById<TextView>(R.id.editor_tab_badge)
            val count = host.editorPanel?.openFiles ?: 0
            badge?.apply {
                visibility = if (count > 0) View.VISIBLE else View.GONE
                text = count.toString()
            }
        }
    }

    /**
     * Baut die eigene Ansicht des Editor-Tabs: Label + Plakette.
     *
     * Eigene Ansicht statt `setText()`, weil die Plakette daneben stehen
     * soll. Der Long-Press-Handler hängt an der **Custom-View** — ein
     * `TabLayout.Tab` kennt keinen eigenen Long-Press-Listener.
     */
    private fun setupEditorTabView(tab: TabLayout.Tab?) {
        val custom = LayoutInflater.from(host.context)
            .inflate(R.layout.tab_editor_badge, null, false)
        custom.findViewById<TextView>(R.id.editor_tab_label)?.text =
            host.context.getString(R.string.ime_tab_editor)
        tab?.customView = custom
        custom.setOnLongClickListener { showEditorTabMenu() }
        updateEditorBadge()
    }

    /**
     * Long-Press-Kontextmenü am Editor-Tab-Button.
     *
     * Zeigt die Aktionen "Alle schließen", "Neue Datei", "Aktuelle speichern"
     * und "Neu laden". Seit v0.20 hängt das Menü **nicht** mehr am Companion-Mode
     * (die vier Companion-Schalter sind aus den Einstellungen entfernt): es
     * erscheint, sobald ein Editor-Panel existiert. Ohne Panel hat der Long-Press
     * keine Wirkung (der normale Tab-Wechsel bleibt unberührt).
     *
     * @return `true`, wenn das Menü gezeigt wurde; `false`, wenn kein
     *         Editor-Panel existiert.
     */
    internal fun showEditorTabMenu(): Boolean {
        val editor = host.editorPanel ?: return false
        // Beschriftung und Aktion werden **paarweise** aufgebaut und über den
        // Listenindex aufgelöst — ein Label-Vergleich wäre fehleranfällig.
        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        items += host.context.getString(R.string.editor_tab_close_all)
        actions += { editor.closeAllFiles() }
        items += host.context.getString(R.string.editor_tab_new_file)
        actions += { editor.clearEditor() }
        items += host.context.getString(R.string.editor_tab_save_current)
        actions += { editor.saveCurrent() }
        items += host.context.getString(R.string.editor_tab_reload)
        actions += { editor.reloadCurrent() }
        val dialog = AlertDialog.Builder(host.context)
            .setTitle(host.context.getString(R.string.editor_tab_menu_title))
            .setItems(items.toTypedArray()) { _, which -> actions.getOrNull(which)?.invoke() }
            .create()
        showEditorTabDialog(dialog)
        return true
    }

    /** Dialog im IME-Fenster anzeigen (BadTokenException-Schutz). */
    private fun showEditorTabDialog(dialog: AlertDialog) {
        if (!ImeDialog.show(dialog, currentRoot?.windowToken)) {
            Toast.makeText(host.context, R.string.dialog_unavailable, Toast.LENGTH_SHORT).show()
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
     * Zeilen der Buchstaben-Ebene zurücksetzen: nach dem Vollbild-Modus können
     * dort Zeilen GONE geblieben sein ([KeyAnimations.collapseRow]).
     *
     * Die optionale Zahlenreihe folgt dabei der Einstellung: sie wird nicht
     * blind eingeblendet, sonst käme sie nach jedem Tab-Wechsel zurück, obwohl
     * sie in den Einstellungen aus ist.
     */
    private fun resetLettersRows(root: View) {
        val rows = root.findViewById<View>(R.id.letters_rows) as? android.view.ViewGroup ?: return
        val numRow = com.piotv.keytab.Prefs.of(host.context)
            .getBoolean(com.piotv.keytab.Prefs.KEY_NUM_ROW, true)
        for (i in 0 until rows.childCount) {
            val child = rows.getChildAt(i)
            child.visibility =
                if (child.id == R.id.num_row && !numRow) View.GONE else View.VISIBLE
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
        resetLettersRows(root)
        root.findViewById<View>(R.id.editor_panel).applyVisibility(kind == TabKind.EDITOR)
        root.findViewById<View>(R.id.file_panel).applyVisibility(kind == TabKind.FILES)
        root.findViewById<View>(R.id.clip_panel).applyVisibility(kind == TabKind.CLIP)
        root.findViewById<View>(R.id.snippet_panel).applyVisibility(kind == TabKind.SNIPPET)
        applyKeyboardRowVisibility(root, kind == TabKind.ABC || kind == TabKind.EDITOR)
        applyMaximizeVisibility(root, kind)
    }

    /** Tastatur-Ebene und die gemeinsame Funktionsleiste. */
    private fun applyKeyboardRowVisibility(root: View, keyboardVisible: Boolean) {
        applyKeyboardPlane(root, keyboardVisible)
        // Die Funktionsleiste (Schalter, TAB, Leertaste, Punkt, Enter) bleibt in
        // *jeder* Tastatur-Ebene sichtbar — auch beim Blättern zwischen abc,
        // ?123 und <v: sie ist der einzige Weg zurück.
        root.findViewById<View>(R.id.bottom_row).applyVisibility(true)
        // Die Vorschlags-/Emoji-Zeile (☺, ‹, ▦, Vorschläge, ⇲) gehört zur
        // Tastatur: sie bleibt beim Blättern abc -> ?123 -> <v stehen — der
        // ☺-Katalog-Zugang darf nicht verschwinden. In den Inhalts-Tabs ist sie
        // weg, weil dort die Tastatur weg ist (GONE, damit kein Leerraum bleibt).
        root.findViewById<View>(R.id.suggestion_bar).applyVisibility(keyboardVisible)
        // Die Tasten der Funktionsleiste bleiben als Platzhalter stehen
        // (INVISIBLE), Enter ist immer nutzbar.
        root.findViewById<View>(R.id.key_toggle).applyVisibility(keyboardVisible, View.INVISIBLE)
        root.findViewById<View>(R.id.key_tab).applyVisibility(keyboardVisible, View.INVISIBLE)
        root.findViewById<View>(R.id.key_space).applyVisibility(keyboardVisible, View.INVISIBLE)
        root.findViewById<View>(R.id.key_dot).applyVisibility(keyboardVisible, View.INVISIBLE)
        root.findViewById<View>(R.id.key_enter).applyVisibility(true)
        root.findViewById<View>(R.id.key_del)
            .applyVisibility(keyboardVisible && plane == SymbolPlane.LETTERS, View.INVISIBLE)
    }

    /**
     * Genau **eine** Tastatur-Ebene zeigen und den Schalter auf die nächste Ebene
     * beschriften.
     *
     * GONE statt INVISIBLE für die nicht gewählten Ebenen: in den Inhalts-Tabs
     * (und in den jeweils anderen Ebenen) ist die Tastatur wirklich weg —
     * INVISIBLE liesse sie ihren Platz stehen und verdoppelte so die Hoehe.
     */
    private fun applyKeyboardPlane(root: View, keyboardVisible: Boolean) {
        for (p in SymbolPlane.values()) {
            root.findViewById<View>(p.panelId).applyVisibility(keyboardVisible && p == plane)
        }
        root.findViewById<Button>(R.id.key_toggle)?.text = host.context.getString(plane.labelRes)
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

    /**
     * Ein Tipp auf den `?123`-Schalter: eine Ebene weiter
     * (abc → ?123 → abc). Die Beschriftung zeigt dabei immer die
     * *nächste* Ebene.
     *
     * `bottom_row` wird hier bewusst nicht angefasst: die Funktionsleiste mit
     * dem Schalter, der Leertaste und Enter bleibt in allen Ebenen stehen —
     * sonst käme man aus der Symbol-Ebene nicht mehr heraus.
     */
    fun toggleSymbols(root: View) {
        plane = plane.next()
        applyKeyboardPlane(root, keyboardVisible = true)
    }
}
