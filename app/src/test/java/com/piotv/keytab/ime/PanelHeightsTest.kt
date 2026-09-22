package com.piotv.keytab.ime

import android.content.Context
import android.content.res.Configuration
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
 * Files-Tab-Höhe: Der Files-Tab soll genauso hoch sein wie der Notes-Tab.
 * Notes zeigt Editor-Panel + Buchstaben-Tastatur, Files nur das Datei-Panel →
 * das Datei-Panel muss die kombinierte Höhe beider Panels einnehmen.
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

    @Test
    fun `Files-Panel bekommt die Notes-Hoehe (Editor- plus Buchstaben-Panel)`() {
        val root = inflateRoot(nightContext())
        val ed = root.findViewById<View>(R.id.editor_panel)
        val kb = root.findViewById<View>(R.id.kb_panel)
        val width = app.resources.displayMetrics.widthPixels

        val height = PanelHeights.filesPanelHeight(ed, kb, width)
        assertTrue("Editor-Panel muss gemessen worden sein", ed.measuredHeight > 0)
        assertTrue("Buchstaben-Panel muss gemessen worden sein", kb.measuredHeight > 0)
        assertEquals("Höhe = Editor-Panel + Buchstaben-Panel",
            ed.measuredHeight + kb.measuredHeight, height)
    }

    @Test
    fun `Files-Hoehe ist 0 bei fehlenden Panels oder ungueltiger Breite`() {
        val root = inflateRoot(nightContext())
        val ed = root.findViewById<View>(R.id.editor_panel)
        val kb = root.findViewById<View>(R.id.kb_panel)
        val width = app.resources.displayMetrics.widthPixels
        assertEquals(0, PanelHeights.filesPanelHeight(null, kb, width))
        assertEquals(0, PanelHeights.filesPanelHeight(ed, null, width))
        assertEquals(0, PanelHeights.filesPanelHeight(ed, kb, 0))
        assertEquals(0, PanelHeights.filesPanelHeight(ed, kb, -5))
    }

    // ---------- P0-Nachträge (measureHeight, Terminal, Robustheit) ----------

    @Test
    fun `measureHeight misst auch GONE-Views und schuetzt vor Unsinn`() {
        val root = inflateRoot(nightContext())
        val ed = root.findViewById<View>(R.id.editor_panel)
        val width = app.resources.displayMetrics.widthPixels
        // Beim Tab-Wechsel sind Editor-/Buchstaben-Panel bereits GONE —
        // measure() muss trotzdem eine Höhe liefern.
        ed.visibility = View.GONE
        assertTrue("GONE-View wird trotzdem gemessen",
            PanelHeights.measureHeight(ed, width) > 0)
        assertEquals(0, PanelHeights.measureHeight(null, width))
        assertEquals(0, PanelHeights.measureHeight(ed, 0))
        assertEquals(0, PanelHeights.measureHeight(ed, -10))
    }

    @Test
    fun `terminalPanelHeight entspricht der Editor-Hoehe`() {
        // Im Terminal-Tab bleibt die Buchstaben-Tastatur sichtbar —
        // Terminal-Panel + Tastatur = gleiche Gesamthöhe wie Notes/Files.
        val root = inflateRoot(nightContext())
        val ed = root.findViewById<View>(R.id.editor_panel)
        val width = app.resources.displayMetrics.widthPixels
        val term = PanelHeights.terminalPanelHeight(ed, width)
        assertTrue("Terminal-Panel muss gemessen worden sein", term > 0)
        assertEquals("Terminal-Höhe = Editor-Höhe",
            ed.measuredHeight, term)
        assertEquals(0, PanelHeights.terminalPanelHeight(null, width))
        assertEquals(0, PanelHeights.terminalPanelHeight(ed, 0))
    }

    @Test
    fun `Files-Hoehe bleibt bei grosser Schrift und schmaler Breite positiv`() {
        val conf = Configuration(app.resources.configuration)
        conf.fontScale = 1.3f
        val ctx = app.createConfigurationContext(conf)
        val root = inflateRoot(ctx)
        val ed = root.findViewById<View>(R.id.editor_panel)
        val kb = root.findViewById<View>(R.id.kb_panel)
        // Schmale Breite (Landscape-Split/Multi-Window-Näherung)
        val narrow = (app.resources.displayMetrics.widthPixels / 2).coerceAtLeast(1)
        val height = PanelHeights.filesPanelHeight(ed, kb, narrow)
        assertTrue("auch schmal + grosse Schrift: Höhe > 0", height > 0)
        assertEquals("Höhe = Editor-Panel + Buchstaben-Panel",
            ed.measuredHeight + kb.measuredHeight, height)
    }
}
