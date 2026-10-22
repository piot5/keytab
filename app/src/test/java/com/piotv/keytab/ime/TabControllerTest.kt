package com.piotv.keytab.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.Prefs
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TabControllerTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()
    private open class TestEditorPanel(ctx: Context, root: View) : EditorPanel(
        ctx, root, directExecutor, Handler(Looper.getMainLooper())
    ) {
        override var openFiles: Int = 0
    }

    private open class TestHost(
        val ctx: Context,
        val panel: TestEditorPanel
    ) : TabHost {
        override val context get() = ctx
        override fun hideKeyboard() = Unit
        override val inputRouter = InputRouter(Target(), Target())
        override val letterPopup = LetterPopup(Robolectric.buildService(KeyTabImeService::class.java).get())
        override val fileManagerPanel: FileManagerPanel? = null
        override val clipboardPanel: ClipboardPanel? = null
        override val snippetPanel: SnippetPanel? = null
        override val editorPanel: EditorPanel? = panel
        override fun refreshEditorBadge() = Unit
    }

    private class Target : InputTarget {
        override fun insert(text: String) = Unit
        override fun deleteBackspace() = Unit
        override fun deleteWord() = Unit
        override fun deleteBefore(count: Int) = Unit
        override fun deleteBeforeKeys(count: Int) = Unit
        override fun textBefore(count: Int) = ""
        override fun onEnter() = Unit
        override fun onTab() = Unit
    }
    private lateinit var host: TestHost
    private lateinit var controller: TabController
    @Before fun setUp() {
        Prefs.of(app).edit().clear().commit()
        host = TestHost(app, TestEditorPanel(app, root()))
        controller = TabController(host)
    }
    private fun root(): View = LayoutInflater.from(ContextThemeWrapper(app, R.style.Theme_KeyTab)).inflate(R.layout.keyboard_view, null)
    private fun setup(clip: Boolean, snip: Boolean): View {
        Prefs.of(app).edit().putBoolean(Prefs.KEY_CLIP_TAB, clip).putBoolean(Prefs.KEY_SNIPPET_TAB, snip).commit()
        return root().also { controller.setup(it) }
    }
    @Test fun `Pflicht und optionale Tabs werden ohne Terminal aufgebaut`() {
        for (mask in 0..3) {
            val r = setup(mask and 1 != 0, mask and 2 != 0)
            assertEquals(3 + Integer.bitCount(mask), r.findViewById<TabLayout>(R.id.ime_tabs).tabCount)
        }
    }
    @Test fun `Editor und Files routen korrekt`() {
        val r = setup(false, false); val tabs = r.findViewById<TabLayout>(R.id.ime_tabs)
        tabs.getTabAt(1)!!.select()
        assertEquals(TabController.TabKind.EDITOR, controller.currentTabKind())
        assertEquals(InputKind.EDITOR, host.inputRouter.kind)
        tabs.getTabAt(2)!!.select()
        assertEquals(TabController.TabKind.FILES, controller.currentTabKind())
        assertEquals(InputKind.APP, host.inputRouter.kind)
    }

    @Test
    fun `Editor-Tab-Plakette zählt offene Dateien und ist bei deaktiviertem Mode ausgeblendet` () {
        val root = root()
        val panel = TestEditorPanel(app, root)
        panel.openFiles = 2
        val testHost = TestHost(app, panel)
        controller = TabController(testHost)
        controller.setup(root)

        // Die Plakette haengt an der Custom-View des Editor-Tabs, nicht am Root.
        val tabs = root.findViewById<TabLayout>(R.id.ime_tabs)
        val badge = tabs.getTabAt(1)!!.customView!!.findViewById<TextView>(R.id.editor_tab_badge)

        // Standard: Plakette ausgeblendet (Companion-Mode deaktiviert)
        controller.refreshEditorBadge()
        assertTrue(badge.visibility == View.GONE || badge.text.toString().isEmpty())

        // Companion-Mode + Datei-Plakette aktivieren
        Prefs.of(app).edit()
            .putBoolean(Prefs.KEY_CLINE_COMPANION_MODE, true)
            .putBoolean(Prefs.KEY_CLINE_FILE_BADGE, true)
            .commit()
        controller.refreshEditorBadge()
        assertEquals(View.VISIBLE, badge.visibility)
        assertEquals("2", badge.text.toString())

        // Weitere geoeffnete Datei → Plakette steigt
        panel.openFiles = 5
        controller.refreshEditorBadge()
        assertEquals("5", badge.text.toString())

        // Companion-Mode aus → Plakette wieder aus, Zaehler bleibt erhalten
        Prefs.of(app).edit()
            .putBoolean(Prefs.KEY_CLINE_COMPANION_MODE, false)
            .commit()
        controller.refreshEditorBadge()
        assertEquals(View.GONE, badge.visibility)
        assertEquals(5, panel.openFiles)
    }

    @Test
    fun `Editor-Tab-Kontextmenue erscheint nur im Companion-Mode` () {
        val root = root()
        val panel = TestEditorPanel(app, root)
        val testHost = TestHost(app, panel)
        controller = TabController(testHost)
        controller.setup(root)

        val tabs = root.findViewById<TabLayout>(R.id.ime_tabs)
        val custom = tabs.getTabAt(1)!!.customView!!

        // Ohne Companion-Mode: Long-Press wird abgefangen, aber es kommt
        // KEIN Menue (kein Dialog, kein Absturz).
        assertFalse(
            "Ohne Companion-Mode darf kein Menue erscheinen",
            controller.showEditorTabMenu(custom)
        )

        // Companion-Mode an, aber Tab-Kontextmenue noch aus -> weiterhin kein Menue
        Prefs.of(app).edit()
            .putBoolean(Prefs.KEY_CLINE_COMPANION_MODE, true)
            .putBoolean(Prefs.KEY_CLINE_TAB_CONTEXT_MENU, false)
            .commit()
        assertFalse(
            "Ohne Tab-Kontextmenue darf kein Menue erscheinen",
            controller.showEditorTabMenu(custom)
        )

        // Beide aktiv -> Menue (Dialog) wird gezeigt
        Prefs.of(app).edit()
            .putBoolean(Prefs.KEY_CLINE_TAB_CONTEXT_MENU, true)
            .commit()
        assertTrue(
            "Im Companion-Mode mit Tab-Kontextmenue erscheint das Menue",
            controller.showEditorTabMenu(custom)
        )
    }

    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
    @Test fun `Tab-Wechsel aus dem Vollbild stellt die Editor-Normalhoehe wieder her`() {
        val r = setup(true, true)
        val tabs = r.findViewById<TabLayout>(R.id.ime_tabs)
        tabs.getTabAt(1)!!.select() // Editor
        val editor = r.findViewById<View>(R.id.editor_panel)
        val normal = editor.layoutParams.height
        assertTrue("Editor-Normalhoehe vor dem Vollbild positiv", normal > 0)

        // Vollbild wie hideKeyboard(): Tag setzen + collapse. Der Editor behaelt
        // die Tastatur, sein Panel waechst auf die Fensterhoehe.
        r.setTag(R.id.maximized_state, true)
        KeyboardCollapse.collapse(r, keyboardVisible = true, maximizeRowVisible = false)
        val maximized = editor.layoutParams.height
        assertTrue("Vollbild muss hoeher als normal sein ($normal -> $maximized)", maximized > normal)

        // G3: Tab-Wechsel raus aus dem Vollbild — expand() stellt die Normalhoehe
        // wieder her, statt das Editor-Panel maximiert zu lassen.
        tabs.getTabAt(2)!!.select()
        assertEquals("Vollbild-Tag zurueckgesetzt", false, r.getTag(R.id.maximized_state))
        assertEquals("Editor-Normalhoehe wiederhergestellt", normal, editor.layoutParams.height)
    }
    @Test fun `Snippet ist optional und Maximieren sitzt je nach Tab`() {
        val r = setup(true, true); val tabs = r.findViewById<TabLayout>(R.id.ime_tabs)
        tabs.getTabAt(3)!!.select(); assertEquals(TabController.TabKind.CLIP, controller.currentTabKind())
        tabs.getTabAt(4)!!.select(); assertEquals(TabController.TabKind.SNIPPET, controller.currentTabKind())
        // Files/Clip/Snippet: Maximieren-Zeile am unteren Rand (diese Tabs haben
        // keine Prediction-Zeile).
        for (i in 2..4) {
            tabs.getTabAt(i)!!.select()
            assertEquals("Tab $i: Max-Zeile sichtbar", View.VISIBLE,
                r.findViewById<View>(R.id.maximize_row).visibility)
            assertEquals("Tab $i: kein Prediction-Symbol", View.GONE,
                r.findViewById<View>(R.id.sug_hide).visibility)
        }
        // Editor: das Symbol sitzt rechts in der Prediction-Zeile, keine Max-Zeile.
        tabs.getTabAt(1)!!.select()
        assertEquals("Editor: Prediction-Symbol sichtbar", View.VISIBLE,
            r.findViewById<View>(R.id.sug_hide).visibility)
        assertEquals("Editor: keine Max-Zeile", View.GONE,
            r.findViewById<View>(R.id.maximize_row).visibility)
        // abc: gar kein Maximieren (nichts zu vergroessern).
        tabs.getTabAt(0)!!.select()
        assertEquals("abc: keine Max-Zeile", View.GONE,
            r.findViewById<View>(R.id.maximize_row).visibility)
        assertEquals("abc: kein Prediction-Symbol", View.GONE,
            r.findViewById<View>(R.id.sug_hide).visibility)
    }

    @Test fun `Alle Inhalts-Tabs bekommen dieselbe Normalhoehe`() {
        val r = setup(true, true); val tabs = r.findViewById<TabLayout>(R.id.ime_tabs)
        // Im abc-Tab laeuft noch keine Tastatur, deshalb dort nicht pruefen.
        for (i in 1..4) {
            tabs.getTabAt(i)!!.select()
            shadowOf(Looper.getMainLooper()).idle()
        }
        // Files/Clip/Snippet bekommen dieselbe Panel-Hoehe ...
        val heights = PanelHeights.CONTENT_PANEL_IDS.map {
            r.findViewById<View>(it).layoutParams.height
        }
        assertTrue("Panel-Hoehen muessen positiv sein: $heights", heights.all { it > 0 })
        assertEquals("Files/Clip/Snippet gleich hoch: $heights", 1, heights.distinct().size)
        // ... und die entspricht der Gesamthoehe des Editor-Tabs (Editor +
        // Tastatur) minus Maximieren-Zeile — diese Zeile haben nur die
        // Inhalts-Tabs, der Editor-Tab hat dort das Prediction-Symbol. So sind
        // alle vier Tabs gleich hoch — oder das Panel weicht auf das Fenster
        // aus, wenn Editor+Tastatur zu klein sind. Auf dem Geraet gibt das
        // System das Band zwischen Status- und Navigationsleiste frei, deshalb
        // die Begrenzung; ohne sie passte der Inhalt nicht ins Fenster.
        val editorTotal = r.findViewById<View>(R.id.editor_panel).layoutParams.height +
            r.findViewById<View>(R.id.kb_panel).measuredHeight
        val width = r.resources.displayMetrics.widthPixels
        val window = PanelHeights.availableHeight(r)
        // Chrome (Tab-Leiste + Maximieren-Zeile) liegt ausserhalb des Panels und
        // muss im Fenster Platz haben — deshalb erst davon abziehen, dann kippen.
        val expected = minOf(editorTotal - PanelHeights.maximizeRowHeight(r),
            window - PanelHeights.chromeHeight(r, width))
        assertEquals("Panel-Hoehe = min(Editor+Tastatur-Max-Zeile, Band)",
            expected, heights.first())
    }

    @Test fun `Maximieren-Zeile nur in den Inhalts-Tabs`() {
        val r = setup(true, true); val tabs = r.findViewById<TabLayout>(R.id.ime_tabs)
        // abc, Editor: nein (Editor hat das Prediction-Symbol), Files/Clip/Snip: ja.
        val expected = listOf(false, false, true, true, true)
        for ((i, shows) in expected.withIndex()) {
            tabs.getTabAt(i)!!.select()
            assertEquals("showsMaximizeRow in Tab $i", shows, controller.showsMaximizeRow())
            assertEquals("Maximieren-Zeile in Tab $i",
                if (shows) View.VISIBLE else View.GONE,
                r.findViewById<View>(R.id.maximize_row).visibility)
        }
    }

    @Test fun `Erster Start zeigt den abc-Zustand ohne Maximieren-Zeile`() {
        // Regression: `addTab()` waehlt Tab 0 (abc) aus, *bevor* der Listener
        // haengt — sein onTabSelected laeuft also nie. Ohne den Startzustand in
        // setup() blieb die Maximieren-Zeile stehen, obwohl es im abc-Tab
        // nichts zu maximieren gibt.
        val r = setup(true, true)
        assertEquals("abc: keine Maximieren-Zeile beim ersten Start", View.GONE,
            r.findViewById<View>(R.id.maximize_row).visibility)
        assertEquals("abc: kein Maximieren-Symbol in der Vorschlagsleiste", View.GONE,
            r.findViewById<View>(R.id.sug_hide).visibility)
        assertEquals("abc: Tastatur sichtbar", View.VISIBLE,
            r.findViewById<View>(R.id.kb_panel).visibility)
        assertEquals("abc: Symbol-Ebene aus", View.GONE,
            r.findViewById<View>(R.id.sym_panel).visibility)
    }

    @Test fun `In Inhalts-Tabs ist die Tastatur GONE und im Editor-Tab sichtbar`() {
        val r = setup(true, true); val tabs = r.findViewById<TabLayout>(R.id.ime_tabs)
        tabs.getTabAt(1)!!.select()
        assertEquals("Editor-Tab: Tastatur sichtbar", View.VISIBLE,
            r.findViewById<View>(R.id.kb_panel).visibility)
        for (i in 2..4) {
            tabs.getTabAt(i)!!.select()
            assertEquals("Tab $i: Tastatur GONE (nicht INVISIBLE — sonst doppelte Hoehe)",
                View.GONE, r.findViewById<View>(R.id.kb_panel).visibility)
        }
    }
    @Test fun `Symbol Toggle schaltet Ebenen um`() {
        val r = setup(false, false)
        // Ein Tipp = eine Ebene weiter: abc -> ?123 -> abc
        controller.toggleSymbols(r)
        assertEquals(View.GONE, r.findViewById<View>(R.id.kb_panel).visibility)
        assertEquals(View.VISIBLE, r.findViewById<View>(R.id.sym_panel).visibility)
        controller.toggleSymbols(r)
        assertEquals(View.VISIBLE, r.findViewById<View>(R.id.kb_panel).visibility)
        assertEquals(View.GONE, r.findViewById<View>(R.id.sym_panel).visibility)
    }

    @Test fun `Schalter blaettert durch zwei Ebenen und laesst die Funktionsleiste stehen`() {
        val r = setup(false, false)
        val toggle = r.findViewById<Button>(R.id.key_toggle)
        val labels = mutableListOf<String>()
        for (step in 0 until 2) {
            labels += toggle.text.toString()
            controller.toggleSymbols(r)
            assertEquals("Schritt $step: Funktionsleiste bleibt stehen", View.VISIBLE,
                r.findViewById<View>(R.id.bottom_row).visibility)
            assertEquals("Schritt $step: Schalter bleibt bedienbar", View.VISIBLE,
                toggle.visibility)
            assertEquals("Schritt $step: genau eine Tastatur-Ebene sichtbar", 1,
                listOf(R.id.kb_panel, R.id.sym_panel)
                    .count { r.findViewById<View>(it).visibility == View.VISIBLE })
        }
        // Der Schalter zeigt immer die *naechste* Ebene: abc -> ?123 -> abc
        assertEquals(
            listOf(
                app.getString(R.string.key_toggle_symbols),
                app.getString(R.string.key_toggle_letters)
            ),
            labels
        )
        assertEquals("nach zwei Tipps wieder Buchstaben", SymbolPlane.LETTERS,
            controller.currentPlane())
        assertEquals(View.VISIBLE, r.findViewById<View>(R.id.kb_panel).visibility)
        assertEquals(View.GONE, r.findViewById<View>(R.id.sym_panel).visibility)
    }

    @Test fun `Vorschlags- und Smiley-Zeile bleibt beim Blaettern stehen`() {
        val r = setup(false, false)
        val bar = r.findViewById<View>(R.id.suggestion_bar)
        // So laesst SuggestionController die Zeile stehen (Vorschlaege an).
        bar.visibility = View.VISIBLE
        controller.toggleSymbols(r)   // ?123
        assertEquals("Smiley-Zeile bleibt in ?123", View.VISIBLE, bar.visibility)
        controller.toggleSymbols(r)   // abc
        assertEquals("Smiley-Zeile bleibt in abc", View.VISIBLE, bar.visibility)
    }

    @Test fun `Vorschlagszeile ist in den Inhalts-Tabs weg`() {
        val r = setup(true, true)
        val tabs = r.findViewById<TabLayout>(R.id.ime_tabs)
        val bar = r.findViewById<View>(R.id.suggestion_bar)
        // abc (0) und Editor (1) haben die Tastatur — dort bleibt die Zeile
        // (im Editor traegt sie das ⇲-Symbol). Inhalts-Tabs (ab 2) nicht.
        assertEquals("abc: Zeile sichtbar", View.VISIBLE, bar.visibility)
        for (i in 1 until tabs.tabCount) {
            tabs.getTabAt(i)!!.select()
            val expected = if (i == 1) View.VISIBLE else View.GONE
            assertEquals("Tab $i: Zeile wie die Tastatur", expected, bar.visibility)
        }
        tabs.getTabAt(0)!!.select()
        assertEquals("zurueck im abc-Tab", View.VISIBLE, bar.visibility)
    }
}
