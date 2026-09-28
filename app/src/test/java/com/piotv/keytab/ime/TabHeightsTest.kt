package com.piotv.keytab.ime

import android.content.Context
import com.piotv.keytab.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Höhen-Vorgaben je Tab und Zustand ([TabHeights]).
 *
 * Geprüft wird, was eine kaputte `keytab_config.txt` anrichten darf: Nichts.
 * Schlüsselnamen, dp→px-Umrechnung und der Schutz gegen unbrauchbare Werte.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class TabHeightsTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = Prefs.of(app)

    @Before fun setUp() { prefs.edit().clear().commit() }

    @Test
    fun `Config-Schluessel heissen normal_height_ und max_height_ je Tab`() {
        assertEquals("normal_height_files",
            TabHeights.configKey(TabController.TabKind.FILES, false))
        assertEquals("max_height_files",
            TabHeights.configKey(TabController.TabKind.FILES, true))
        assertEquals("normal_height_editor",
            TabHeights.configKey(TabController.TabKind.EDITOR, false))
        assertEquals("max_height_snippet",
            TabHeights.configKey(TabController.TabKind.SNIPPET, true))
        for (tab in TabHeights.kinds()) {
            assertTrue(TabHeights.slug(tab) in TabHeights.configKey(tab, false))
            assertTrue(TabHeights.slug(tab) in TabHeights.configKey(tab, true))
        }
    }

    @Test
    fun `nur Tabs mit Inhalts-Panel haben Vorgaben`() {
        assertEquals(4, TabHeights.kinds().size)
        assertTrue("abc-Tab: Hoehe kommt aus den Tasten",
            TabHeights.kinds().none { it == TabController.TabKind.ABC })
    }

    @Test
    fun `Vorgabe wird von dp nach px umgerechnet und bleibt je Tab getrennt`() {
        val density = app.resources.displayMetrics.density
        prefs.edit().putInt(TabHeights.prefKey(TabController.TabKind.FILES, false), 220).commit()
        assertEquals((220 * density).toInt(),
            TabHeights.overridePx(app, TabController.TabKind.FILES, false))
        assertEquals("anderer Tab unberuehrt", 0,
            TabHeights.overridePx(app, TabController.TabKind.CLIP, false))
        assertEquals("anderer Zustand unberuehrt", 0,
            TabHeights.overridePx(app, TabController.TabKind.FILES, true))
    }

    @Test
    fun `null negativ und unmoeglich gross gelten als keine Vorgabe`() {
        val key = TabHeights.prefKey(TabController.TabKind.EDITOR, true)
        for (dp in listOf(TabHeights.AUTO, -50, 999_999)) {
            prefs.edit().putInt(key, dp).commit()
            assertEquals("dp=$dp darf keine Vorgabe sein", 0,
                TabHeights.overridePx(app, TabController.TabKind.EDITOR, true))
        }
    }
}