package com.piotv.keytab.ime

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Vollbild-Modus ([KeyboardCollapse]): was bleibt sichtbar, welche Höhe gilt und
 * was [KeyboardCollapse.expand] zurückstellt.
 *
 * Zwei Verträge, die leicht kaputtgehen und keine Service-Mocks brauchen:
 *  * **Vollbild füllt das IME-Fenster** — Panel + sichtbare Chrome-Zeilen (und im
 *    Editor die Tastatur) ergeben zusammen genau die Höhe, die das System gibt.
 *  * **Der Editor-Tab hat keine Maximieren-Zeile**, auch nicht maximiert: der
 *    Ausweg ist das ⇲-Symbol der Prediction-Zeile, und die Zeile nähme dem Editor
 *    Höhe weg. Seine Tastatur samt unterer Key-Leiste bleibt stehen — dort sitzen
 *    Leerzeichen, Tab und Enter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class KeyboardCollapseTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    private fun root(): View =
        LayoutInflater.from(ContextThemeWrapper(app, R.style.Theme_KeyTab))
            .inflate(R.layout.keyboard_view, null)

    private fun vis(root: View, id: Int) = root.findViewById<View>(id).visibility
    private fun width(root: View) = root.resources.displayMetrics.widthPixels

    /** Editor-Tab im Normalzustand: Editor-Panel über der Tastatur, kein Max-Knopf unten. */
    private fun editorTab(): View = root().apply {
        findViewById<View>(R.id.editor_panel).visibility = View.VISIBLE
        findViewById<View>(R.id.kb_panel).visibility = View.VISIBLE
        findViewById<View>(R.id.bottom_row).visibility = View.VISIBLE
        findViewById<View>(R.id.maximize_row).visibility = View.GONE
        findViewById<View>(R.id.sug_hide).visibility = View.VISIBLE
    }

    /** Files-Tab im Normalzustand: Datei-Panel statt Tastatur, Max-Zeile unten. */
    private fun filesTab(): View = root().apply {
        findViewById<View>(R.id.file_panel).visibility = View.VISIBLE
        findViewById<View>(R.id.kb_panel).visibility = View.GONE
        PanelHeights.applyNormalHeights(this, width(this))
        findViewById<View>(R.id.maximize_row).visibility = View.VISIBLE
    }

    @Test
    fun `Editor-Tab maximiert ohne Maximieren-Zeile und mit Tastatur`() {
        val r = editorTab()
        val window = PanelHeights.maximizeAvailableHeight(r)

        KeyboardCollapse.collapse(r, keyboardVisible = true, maximizeRowVisible = false)

        assertEquals("Editor: keine Maximieren-Zeile", View.GONE, vis(r, R.id.maximize_row))
        assertEquals("Editor: Tastatur bleibt", View.VISIBLE, vis(r, R.id.kb_panel))
        assertEquals("Editor: untere Key-Leiste bleibt (Leerzeichen/Enter)",
            View.VISIBLE, vis(r, R.id.bottom_row))
        assertEquals("Editor: Ausweg ⇲ in der Prediction-Zeile",
            View.VISIBLE, vis(r, R.id.sug_hide))
        val panel = r.findViewById<View>(R.id.editor_panel).layoutParams.height
        assertEquals("Editor: Vollbild füllt das IME-Fenster", window,
            panel + PanelHeights.chromeHeight(r, width(r)) +
                PanelHeights.keyboardBlockHeight(r, width(r)))
    }

    @Test
    fun `Inhalts-Tab maximiert ohne Tastatur und mit Maximieren-Zeile`() {
        val r = filesTab()
        val window = PanelHeights.maximizeAvailableHeight(r)

        KeyboardCollapse.collapse(r, keyboardVisible = false, maximizeRowVisible = true)

        assertEquals("Files: Maximieren-Zeile sichtbar", View.VISIBLE, vis(r, R.id.maximize_row))
        assertEquals("Files: keine Tastatur", View.GONE, vis(r, R.id.kb_panel))
        assertEquals("Files: keine untere Key-Leiste", View.GONE, vis(r, R.id.bottom_row))
        assertEquals("Files: Oberkante (Tab-Leiste) bleibt", View.VISIBLE, vis(r, R.id.ime_tabs))
        val panel = r.findViewById<View>(R.id.file_panel).layoutParams.height
        assertEquals("Files: Vollbild füllt das IME-Fenster", window,
            panel + PanelHeights.chromeHeight(r, width(r)))
        assertEquals("Files: Tastatur zaehlt hier nicht", 0,
            PanelHeights.keyboardBlockHeight(r, width(r)))
    }

    @Test
    fun `expand stellt die Normalhoehe verlustfrei wieder her`() {
        val r = filesTab()
        val normal = r.findViewById<View>(R.id.file_panel).layoutParams.height
        assertTrue("Normalhoehe muss gesetzt sein: $normal", normal > 0)

        KeyboardCollapse.collapse(r, keyboardVisible = false, maximizeRowVisible = true)
        assertTrue("Maximiert muss hoeher sein als normal",
            r.findViewById<View>(R.id.file_panel).layoutParams.height > normal)

        KeyboardCollapse.expand(r)
        assertEquals("Normalhoehe zurueck", normal,
            r.findViewById<View>(R.id.file_panel).layoutParams.height)
        assertEquals("Tab-Leiste bleibt", View.VISIBLE, vis(r, R.id.ime_tabs))
    }

    @Test
    fun `Vollbild mit Config-Vorgabe nutzt die angegebene Hoehe`() {
        val r = filesTab()
        val width = r.resources.displayMetrics.widthPixels
        val frei = PanelHeights.maximizeAvailableHeight(r) - PanelHeights.chromeHeight(r, width)
        val override = frei / 2
        KeyboardCollapse.collapse(
            r, keyboardVisible = false, maximizeRowVisible = true, heightPx = override
        )
        assertEquals(override, r.findViewById<View>(R.id.file_panel).layoutParams.height)
    }

    @Test
    fun `abc-Tab maximiert die Tastatur selbst`() {
        val r = root().apply {
            findViewById<View>(R.id.kb_panel).visibility = View.VISIBLE
            for (id in PanelHeights.CONTENT_PANEL_IDS) {
                findViewById<View>(id).visibility = View.GONE
            }
        }
        val target = KeyboardCollapse.targetPanel(r)
        assertEquals("Ziel im abc-Tab ist die Tastatur", R.id.kb_panel, target?.id)
    }
}
