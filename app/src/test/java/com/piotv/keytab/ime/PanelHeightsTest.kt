package com.piotv.keytab.ime

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Panel-Hoehen: alle Inhalts-Tabs (Editor, Files, Clipboard, Snippets) muessen im
 * Normalzustand gleich hoch sein — so hoch wie der Editor-Tab, der als einziger
 * zusaetzlich die Buchstaben-Tastatur zeigt.
 *
 * Vorher standen die Panels auf unterschiedlichen Hoehen (Editor 180dp aus dem
 * Layout, die anderen auf der *natuerlichen* Editorhoehe plus Tastatur), und die
 * Tastatur blieb in den Inhalts-Tabs INVISIBLE stehen und zaehlte ihre Hoehe
 * doppelt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PanelHeightsTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    private fun nightContext(): Context {
        val conf = Configuration(app.resources.configuration)
        conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            Configuration.UI_MODE_NIGHT_YES
        return app.createConfigurationContext(conf)
    }

    private fun inflateRoot(ctx: Context): View =
        LayoutInflater.from(ContextThemeWrapper(ctx, R.style.Theme_KeyTab))
            .inflate(R.layout.keyboard_view, null)

    // ---------- Normalhoehe: Editor + Tastatur ----------

    @Test
    fun `Inhalts-Hoehe ist Editor-Hoehe plus Tastatur`() {
        val root = inflateRoot(nightContext())
        val ed = root.findViewById<View>(R.id.editor_panel)
        val kb = root.findViewById<View>(R.id.kb_panel)
        val width = app.resources.displayMetrics.widthPixels

        val height = PanelHeights.contentPanelHeight(ed, kb, width)
        assertTrue("Tastatur muss messbar sein", kb.measuredHeight > 0)
        // Der Editor hat eine feste Layout-Hoehe (180dp) — genau die wird
        // benutzt, nicht seine (kleinere) Naturalhoehe.
        assertEquals(ed.layoutParams.height + kb.measuredHeight, height)
    }

    @Test
    fun `Inhalts-Hoehe ist 0 bei fehlenden Panels oder ungueltiger Breite`() {
        val root = inflateRoot(nightContext())
        val ed = root.findViewById<View>(R.id.editor_panel)
        val kb = root.findViewById<View>(R.id.kb_panel)
        val width = app.resources.displayMetrics.widthPixels
        assertEquals(0, PanelHeights.contentPanelHeight(null, kb, width))
        assertEquals(0, PanelHeights.contentPanelHeight(ed, null, width))
        assertEquals(0, PanelHeights.contentPanelHeight(ed, kb, 0))
        assertEquals(0, PanelHeights.contentPanelHeight(ed, kb, -5))
    }

    @Test
    fun `applyNormalHeights setzt Files Clip und Snippet auf dieselbe Hoehe`() {
        val root = inflateRoot(nightContext())
        val width = app.resources.displayMetrics.widthPixels
        val editorHeightBefore =
            root.findViewById<View>(R.id.editor_panel).layoutParams.height

        val height = PanelHeights.applyNormalHeights(root, width)
        assertTrue("Hoehe muss ermittelbar sein", height > 0)

        val heights = PanelHeights.CONTENT_PANEL_IDS.map {
            root.findViewById<View>(it).layoutParams.height
        }
        assertEquals("Files/Clip/Snippet: $heights", 1, heights.distinct().size)
        assertEquals(height, heights.first())
        // Das Editor-Panel ist die Referenz und bleibt unangetastet.
        assertEquals(editorHeightBefore,
            root.findViewById<View>(R.id.editor_panel).layoutParams.height)
    }

    @Test
    fun `applyNormalHeights ist idempotent und lässt die Panels nicht wachsen`() {
        val root = inflateRoot(nightContext())
        val width = app.resources.displayMetrics.widthPixels
        val first = PanelHeights.applyNormalHeights(root, width)
        val heightsAfterFirst = PanelHeights.CONTENT_PANEL_IDS.map {
            root.findViewById<View>(it).layoutParams.height
        }
        // Ein zweiter Aufruf (Tab-Wechsel) darf die Panels nicht veraendern —
        // genau daran ist die alte Logik gescheitert.
        val second = PanelHeights.applyNormalHeights(root, width)
        val heightsAfterSecond = PanelHeights.CONTENT_PANEL_IDS.map {
            root.findViewById<View>(it).layoutParams.height
        }
        assertEquals(first, second)
        assertEquals(heightsAfterFirst, heightsAfterSecond)
    }

    @Test
    fun `abc-Tab ist nicht Teil der Panels mit gleicher Hoehe`() {
        // Der Buchstaben-Tab zeigt die Tastatur selbst; seine Hoehe kommt aus
        // den Tasten und darf nicht auf die Editor-Hoehe gezwungen werden.
        assertNotEquals(R.id.kb_panel, PanelHeights.ALL_CONTENT_TAB_IDS[0])
        assertEquals(4, PanelHeights.ALL_CONTENT_TAB_IDS.size)
        assertEquals(3, PanelHeights.CONTENT_PANEL_IDS.size)
        for (id in intArrayOf(R.id.editor_panel, R.id.file_panel, R.id.clip_panel,
                R.id.snippet_panel)) {
            assertTrue("Tab-Panel fehlt: $id",
                PanelHeights.ALL_CONTENT_TAB_IDS.contains(id))
        }
    }

    // ---------- Messen robuster machen ----------

    @Test
    fun `measureHeight misst auch GONE-Views und schuetzt vor Unsinn`() {
        val root = inflateRoot(nightContext())
        val ed = root.findViewById<View>(R.id.editor_panel)
        val width = app.resources.displayMetrics.widthPixels
        // Beim Tab-Wechsel sind Editor-/Buchstaben-Panel bereits GONE —
        // measure() muss trotzdem eine Hoehe liefern.
        ed.visibility = View.GONE
        assertTrue("GONE-View wird trotzdem gemessen",
            PanelHeights.measureHeight(ed, width) > 0)
        assertEquals(0, PanelHeights.measureHeight(null, width))
        assertEquals(0, PanelHeights.measureHeight(ed, 0))
        assertEquals(0, PanelHeights.measureHeight(ed, -10))
    }

    @Test
    fun `editorPanelHeight entspricht der Editor-Hoehe`() {
        val root = inflateRoot(nightContext())
        val ed = root.findViewById<View>(R.id.editor_panel)
        val width = app.resources.displayMetrics.widthPixels
        // Erst messen lassen, dann vergleichen — measuredHeight ist vor dem
        // allerersten measure() noch 0.
        val term = PanelHeights.editorPanelHeight(ed, width)
        assertTrue("Editor-Hoehe muss messbar sein", term > 0)
        // Zweimal messen muss denselben Wert liefern (deterministisch).
        assertEquals(term, PanelHeights.editorPanelHeight(ed, width))
        assertEquals(0, PanelHeights.editorPanelHeight(null, width))
        assertEquals(0, PanelHeights.editorPanelHeight(ed, 0))
    }

    @Test
    fun `Inhalts-Hoehe bleibt bei grosser Schrift und schmaler Breite positiv`() {
        val conf = Configuration(app.resources.configuration)
        conf.fontScale = 1.3f
        val root = inflateRoot(app.createConfigurationContext(conf))
        val ed = root.findViewById<View>(R.id.editor_panel)
        val kb = root.findViewById<View>(R.id.kb_panel)
        val narrow = (app.resources.displayMetrics.widthPixels / 2).coerceAtLeast(1)
        assertTrue(PanelHeights.contentPanelHeight(ed, kb, narrow) > 0)
    }

    // ---------- Begrenzung auf das IME-Fenster ----------

    @Test
    fun `Panel-Hoehe passt in das IME-Fenster und laesst die Chrome-Zeilen frei`() {
        val root = inflateRoot(nightContext())
        val width = app.resources.displayMetrics.widthPixels
        // Fenster simulieren: kleiner als Editor + Tastatur (das System gibt der
        // IME nur ~70 % des Bildschirms).
        val window = PanelHeights.maximizeAvailableHeight(root) / 2
        root.layout(0, 0, width, window)
        val height = PanelHeights.applyNormalHeights(root, width)
        val chrome = PanelHeights.chromeHeight(root, width)

        assertTrue("Hoehe muss positiv bleiben: $height", height > 0)
        assertTrue("Inhalt + Chrome muss ins Fenster passen: " +
            "$height + $chrome > $window", height + chrome <= window)
    }

    @Test
    fun `chromeHeight zaehlt nur sichtbare Zeilen`() {
        val root = inflateRoot(nightContext())
        val width = app.resources.displayMetrics.widthPixels
        val tabs = root.findViewById<View>(R.id.ime_tabs)
        val maximize = root.findViewById<View>(R.id.maximize_row)

        maximize.visibility = View.GONE
        val withoutMax = PanelHeights.chromeHeight(root, width)
        assertTrue("Tab-Leiste zaehlt mit: $withoutMax", withoutMax > 0)

        maximize.visibility = View.VISIBLE
        assertTrue("Maximieren-Zeile zaehlt jetzt mit",
            PanelHeights.chromeHeight(root, width) > withoutMax)

        tabs.visibility = View.GONE
        assertEquals("nur noch die Max-Zeile sichtbar",
            PanelHeights.maximizeRowHeight(root),
            PanelHeights.chromeHeight(root, width))
        maximize.visibility = View.GONE
        assertEquals("beide GONE -> 0", 0, PanelHeights.chromeHeight(root, width))
    }

    @Test
    fun `availableHeight nutzt die Fensterhoehe, sonst die 70-Prozent-Naeherung`() {
        val root = inflateRoot(nightContext())
        val display = app.resources.displayMetrics.heightPixels
        assertEquals("vor dem Layout: Naeherung",
            (display * PanelHeights.IME_WINDOW_FRACTION).toInt(),
            PanelHeights.availableHeight(root))
        root.layout(0, 0, 100, 1915)
        assertEquals("nach dem Layout: echte Fensterhoehe",
            1915, PanelHeights.availableHeight(root))
    }

    @Test
    fun `IME-Fenster bekommt hoechstens rund 70 Prozent des Bildschirms`() {
        // Auf dem Geraet gemessen: 1915 px Fenster bei 2712 px Bildschirm.
        // Deshalb ist "95 %" nur als Anteil des Fensters erreichbar.
        assertEquals(0.7f, PanelHeights.IME_WINDOW_FRACTION, 0.05f)
        val display = 2712
        val window = (display * PanelHeights.IME_WINDOW_FRACTION).toInt()
        assertTrue("Fenster muss kleiner als der Bildschirm sein", window < display)
        // Und es muss mehr sein, als die Tastatur allein braucht (~1060 px).
        assertTrue("Fenster muss die Tastatur tragen: $window", window > 1060)
    }

    // ---------- Maximieren: das ganze IME-Fenster ----------

    @Test
    fun `maximizeAvailableHeight nimmt die Fenstergrenze, nie die normale Hoehe`() {
        val root = inflateRoot(nightContext())
        val display = app.resources.displayMetrics.heightPixels
        val cap = (display * PanelHeights.IME_WINDOW_FRACTION).toInt()
        // Vor dem Layout: die Systemgrenze.
        assertEquals(cap, PanelHeights.maximizeAvailableHeight(root))
        // Ein gelayoutetes, *groesseres* Fenster gewinnt.
        root.layout(0, 0, 100, 1915)
        assertEquals(1915, PanelHeights.maximizeAvailableHeight(root))
        // Ein kleines Fenster (normale Tastatur, wrap_content-Wurzel) darf das
        // Maximum nicht nach unten ziehen — sonst waere "maximiert" genau der
        // Normalzustand. Das war der Fehler: das Panel wuchs nie.
        root.layout(0, 0, 100, 200)
        assertEquals("Fenstergrenze gewinnt gegen ein kleineres Fenster", cap,
            PanelHeights.maximizeAvailableHeight(root))
    }

    @Test
    fun `maximizedPanelHeight fuellt das Fenster und laesst die Chrome-Zeilen frei`() {
        val root = inflateRoot(nightContext())
        val width = app.resources.displayMetrics.widthPixels
        val window = PanelHeights.maximizeAvailableHeight(root)
        val height = PanelHeights.maximizedPanelHeight(root, width, keyboardVisible = false)
        assertTrue("Panel muss positiv sein: $height", height > 0)
        // Inhalts-Tab: Panel + obere Tab-Leiste + Maximieren-Zeile = Fenster.
        assertEquals("maximiert muss das Fenster fuellen",
            window, height + PanelHeights.chromeHeight(root, width))
    }

    @Test
    fun `maximizedPanelHeight laesst im Editor-Tab die Tastatur stehen`() {
        val root = inflateRoot(nightContext())
        val width = app.resources.displayMetrics.widthPixels
        root.findViewById<View>(R.id.kb_panel).visibility = View.VISIBLE
        root.findViewById<View>(R.id.bottom_row).visibility = View.VISIBLE
        val withKeyboard = PanelHeights.maximizedPanelHeight(root, width, keyboardVisible = true)
        val withoutKeyboard = PanelHeights.maximizedPanelHeight(root, width, keyboardVisible = false)
        val block = PanelHeights.keyboardBlockHeight(root, width)
        assertTrue("Tastaturblock muss messbar sein: $block", block > 0)
        assertEquals("Tastatur wird abgezogen, nicht ignoriert",
            withoutKeyboard - block, withKeyboard)
    }

    // ---------- Maximieren-Zeile: niedriger als eine Tastenreihe ----------

    @Test
    fun `Maximieren-Zeile ist niedriger als eine Tastenzeile`() {
        val root = inflateRoot(nightContext())
        val density = root.resources.displayMetrics.density
        val row = PanelHeights.maximizeRowHeight(root)
        // KeyDark (Tastenzeile) ist 42dp hoch; die Maximieren-Zeile ist nur eine
        // Bedienzeile und kostet in jedem Inhalts-Tab vertikalen Platz.
        assertTrue("Zeile muss positiv sein: $row", row > 0)
        assertTrue("Zeile ($row px) muss unter 42dp (${42 * density} px) liegen",
            row < 42 * density)
        // Und das Layout muss dieselbe Hoehe benutzen — PanelHeights rechnet mit
        // der Ressource, nicht mit einer Schaetzung aus einer Messung.
        assertEquals(row, root.findViewById<View>(R.id.maximize_row).layoutParams.height)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `Inhalts-Panels sind um die Maximieren-Zeile niedriger als der Editor-Tab`() {
        // Echtes Geraeteprofil (411x891dp, xhdpi): auf Robolectrics Standard-
        // Display (470px hoch) ist das IME-Fenster kleiner als Tastatur + Panel,
        // dann greift die Fensterbegrenzung und die Gleichheit ist rechnerisch
        // nicht mehr erreichbar.
        val root = inflateRoot(nightContext())
        val width = app.resources.displayMetrics.widthPixels
        val panel = PanelHeights.applyNormalHeights(root, width)
        val editor = root.findViewById<View>(R.id.editor_panel)
        val keyboard = root.findViewById<View>(R.id.kb_panel)
        // Editor-Tab = Editor-Panel + Tastatur (er hat keine Max-Zeile).
        val editorTab = PanelHeights.editorNormalHeight(editor, width) +
            (keyboard.height.takeIf { it > 0 } ?: PanelHeights.measureHeight(keyboard, width))
        // Inhalts-Tab = Panel + Max-Zeile. Beides gleich hoch.
        assertEquals("alle Tabs gleich hoch",
            editorTab, panel + PanelHeights.maximizeRowHeight(root))
        // Und die Fensterbegrenzung greift hier nicht (sonst waere der Test
        // wertlos, weil beide Seiten geklemmt waeren).
        assertTrue("Testfenster muss gross genug sein",
            PanelHeights.availableHeight(root) - PanelHeights.chromeHeight(root, width) >= editorTab)
    }

    @Test
    fun `maximizeRowHeight ist tab-unabhaengig`() {
        val root = inflateRoot(nightContext())
        val row = root.findViewById<View>(R.id.maximize_row)
        val visible = PanelHeights.maximizeRowHeight(root)
        row.visibility = View.GONE
        // Der Wert muss konstant bleiben: er geht als fester Abzug in die
        // gecachte Panel-Hoehe ein, unabhaengig vom gerade sichtbaren Tab.
        assertEquals(visible, PanelHeights.maximizeRowHeight(root))
    }

    // ---------- Vorgaben aus der Config-Datei (TabHeights) ----------

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `Config-Vorgabe gewinnt gegen die berechnete Normalhoehe`() {
        val root = inflateRoot(nightContext())
        val width = app.resources.displayMetrics.widthPixels
        val override = 300 * root.resources.displayMetrics.density.toInt()
        val height = PanelHeights.applyNormalHeights(root, width, override)
        assertEquals(override, height)
        for (id in PanelHeights.CONTENT_PANEL_IDS) {
            assertEquals(override, root.findViewById<View>(id).layoutParams.height)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `zu grosse Normal-Vorgabe wird auf das Fenster begrenzt`() {
        val root = inflateRoot(nightContext())
        val width = app.resources.displayMetrics.widthPixels
        val fenster = PanelHeights.availableHeight(root) - PanelHeights.chromeHeight(root, width)
        assertEquals("Vorgabe passt nicht ins Fenster -> Fenster gewinnt", fenster,
            PanelHeights.applyNormalHeights(root, width, overridePx = 99_999))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `maximierte Config-Vorgabe gilt, aber nie ueber das Fenster hinaus`() {
        val root = inflateRoot(nightContext())
        val width = app.resources.displayMetrics.widthPixels
        val frei = PanelHeights.maximizeAvailableHeight(root) -
            PanelHeights.chromeHeight(root, width)
        val kleiner = frei / 2
        assertEquals(kleiner, PanelHeights.maximizedPanelHeight(root, width, false, kleiner))
        assertEquals("zu gross -> freier Platz", frei,
            PanelHeights.maximizedPanelHeight(root, width, false, 99_999))
    }

    // ---------- Maximieren-Zeile im Layout ----------

    @Test
    fun `Maximieren-Zeile liegt unter allen Panels und hat einen Knopf`() {
        val root = inflateRoot(nightContext())
        val row = root.findViewById<View>(R.id.maximize_row)
        val button = root.findViewById<View>(R.id.key_maximize)
        assertTrue("maximize_row muss im Layout sein", row != null)
        assertTrue("key_maximize muss im Layout sein", button != null)
        assertTrue("Knopf ist in der Zeile", button.parent === row)
        // Unten im Baum: die Zeile ist das letzte Kind der Wurzel.
        val parent = row.parent as ViewGroup
        assertEquals("maximize_row muss die unterste Zeile sein",
            parent.childCount - 1, parent.indexOfChild(row))
    }
}
