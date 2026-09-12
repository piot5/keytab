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
        val fm = root.findViewById<View>(R.id.file_panel)
        val width = app.resources.displayMetrics.widthPixels
        val density = app.resources.displayMetrics.density

        val height = PanelHeights.filesPanelHeight(ed, kb, width)
        val fixedOld = (164 * density).toInt()
        println("filesPanelHeight=${height}px alt-fix=${fixedOld}px " +
            "fm.layoutParams.height=${fm.layoutParams.height}")
        assertTrue("Höhe muss ermittelt werden", height > 0)
        assertTrue(
            "Files-Panel ($height px) muss höher sein als die alte fixe Höhe ($fixedOld px)",
            height > fixedOld)
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
}
