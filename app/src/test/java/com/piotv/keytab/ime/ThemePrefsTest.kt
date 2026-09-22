package com.piotv.keytab.ime

import android.content.Context
import android.content.SharedPreferences
import com.piotv.keytab.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * ThemePrefs: Trail-Defaults, Key-Bildung, Verlauf je Modus,
 * Export/Import-Roundtrip, Reset. (Bisher nur indirekt getestet.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemePrefsTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var prefs: SharedPreferences

    @Before
    fun clear() {
        prefs = app.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun `Trail-Defaults sind aus-blau-5-aus-gruen`() {
        assertFalse(ThemePrefs.trailEnabled(prefs))
        assertEquals(0xFF2196F3.toInt(), ThemePrefs.trailColor(prefs))
        assertEquals(TrailLogic.DEFAULT_STEPS, ThemePrefs.trailSteps(prefs))
        assertFalse(ThemePrefs.trailTraceEnabled(prefs))
        assertEquals(0xFF4CAF50.toInt(), ThemePrefs.trailAcceptedColor(prefs))
    }

    @Test
    fun `trailSteps klemmt auf mindestens 1`() {
        prefs.edit().putInt(ThemePrefs.KEY_TRAIL_STEPS, 0).commit()
        assertEquals(1, ThemePrefs.trailSteps(prefs))
        prefs.edit().putInt(ThemePrefs.KEY_TRAIL_STEPS, 10).commit()
        assertEquals(10, ThemePrefs.trailSteps(prefs))
    }

    @Test
    fun `withAlpha blendet Alpha ein und klemmt`() {
        assertEquals(0x8C2196F3.toInt(), ThemePrefs.withAlpha(0xFF2196F3.toInt(), 140))
        assertEquals(0xFF2196F3.toInt(), ThemePrefs.withAlpha(0xFF2196F3.toInt(), 300))
        assertEquals(0x002196F3, ThemePrefs.withAlpha(0xFF2196F3.toInt(), -5))
    }

    @Test
    fun `trailColorWithAlpha nutzt die Decay-Formel`() {
        val expected = ThemePrefs.withAlpha(0xFF2196F3.toInt(),
            TrailLogic.alphaForStep(0, 5))
        assertEquals(expected, ThemePrefs.trailColorWithAlpha(prefs, 0, 5))
        assertEquals(140, TrailLogic.alphaForStep(0, 5))
    }

    @Test
    fun `colorKey und Gradient-Keys sind modusgetrennt`() {
        assertEquals("theme_dark_bg", ThemePrefs.colorKey(true, ThemePrefs.KIND_BG))
        assertEquals("theme_light_key", ThemePrefs.colorKey(false, ThemePrefs.KIND_KEY))
        assertEquals("theme_dark_gradient_mode", ThemePrefs.gradientModeKey(true))
        assertEquals("theme_light_gradient_mode", ThemePrefs.gradientModeKey(false))
        assertEquals("gradient_off_dark", ThemePrefs.gradientOffKey(true))
        assertEquals("gradient_off_light", ThemePrefs.gradientOffKey(false))
    }

    @Test
    fun `hasGradient ist default an, BG oder OFF schalten ab`() {
        assertTrue(ThemePrefs.hasGradient(prefs, true))
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_BG, 123)
        assertFalse(ThemePrefs.hasGradient(prefs, true))
        assertTrue("light bleibt aktiv", ThemePrefs.hasGradient(prefs, false))
        prefs.edit().clear().commit()
        ThemePrefs.setGradientOff(prefs, true, true)
        assertFalse(ThemePrefs.hasGradient(prefs, true))
    }

    @Test
    fun `Verlaufsfarben fallen per-mode zu legacy zu default`() {
        assertEquals(0xFF3A3A3A.toInt(), ThemePrefs.gradientColor1(prefs, true))
        assertEquals(0xFF121212.toInt(), ThemePrefs.gradientColor2(prefs, true))
        assertEquals(0xFFFFFFFF.toInt(), ThemePrefs.gradientColor1(prefs, false))
        prefs.edit().putInt(ThemePrefs.KEY_GRADIENT_COLOR1, 111).commit()
        assertEquals("legacy greift", 111, ThemePrefs.gradientColor1(prefs, true))
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_GRADIENT1, 222)
        assertEquals("per-mode gewinnt", 222, ThemePrefs.gradientColor1(prefs, true))
    }

    @Test
    fun `gradientMode faellt auf top_down zurueck`() {
        assertEquals(ThemePrefs.GRADIENT_TOP_DOWN, ThemePrefs.gradientMode(prefs, true))
        prefs.edit().putString(ThemePrefs.KEY_GRADIENT_MODE,
            ThemePrefs.GRADIENT_RADIAL).commit()
        assertEquals(ThemePrefs.GRADIENT_RADIAL, ThemePrefs.gradientMode(prefs, true))
    }

    @Test
    fun `gradientDrawable ist null bei OFF, sonst zwei Farben`() {
        ThemePrefs.setGradientOff(prefs, true, true)
        assertNull(ThemePrefs.gradientDrawable(prefs, true, 400))
        prefs.edit().clear().commit()
        val d = ThemePrefs.gradientDrawable(prefs, true, 400)!!
        assertEquals(2, d.colors!!.size)
    }

    @Test
    fun `setColor bei Verlauf aktiviert ihn und raeumt BG ab`() {
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_BG, 1)
        ThemePrefs.setGradientOff(prefs, true, true)
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_GRADIENT1, 2)
        assertFalse(ThemePrefs.isGradientOff(prefs, true))
        assertFalse(prefs.contains(ThemePrefs.colorKey(true, ThemePrefs.KIND_BG)))
    }

    @Test
    fun `export-import-Roundtrip stellt Farben und Trail wieder her`() {
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_BG, 123)
        ThemePrefs.setColor(prefs, false, ThemePrefs.KIND_KEY, 456)
        prefs.edit().putBoolean(ThemePrefs.KEY_TRAIL, true)
            .putInt(ThemePrefs.KEY_TRAIL_STEPS, 7).commit()
        val json = ThemePrefs.exportColors(prefs)
        prefs.edit().clear().commit()
        assertTrue(ThemePrefs.importColors(prefs, json))
        assertEquals(123, ThemePrefs.getColor(prefs, true, ThemePrefs.KIND_BG, 0))
        assertEquals(456, ThemePrefs.getColor(prefs, false, ThemePrefs.KIND_KEY, 0))
        assertTrue(ThemePrefs.trailEnabled(prefs))
        assertEquals(7, ThemePrefs.trailSteps(prefs))
        assertEquals("import bumpt die Theme-Version", 1, ThemePrefs.themeVersion(prefs))
    }

    @Test
    fun `importColors lehnt ungueltiges JSON ab`() {
        assertFalse(ThemePrefs.importColors(prefs, "kein json{"))
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun `resetAll entfernt alle Overrides`() {
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_BG, 1)
        ThemePrefs.setColor(prefs, false, ThemePrefs.KIND_KEY, 2)
        prefs.edit().putBoolean(ThemePrefs.KEY_LIKELY, true)
            .putBoolean(ThemePrefs.KEY_TRAIL, true).commit()
        ThemePrefs.resetAll(prefs)
        assertFalse(prefs.contains(ThemePrefs.colorKey(true, ThemePrefs.KIND_BG)))
        assertFalse(prefs.contains(ThemePrefs.colorKey(false, ThemePrefs.KIND_KEY)))
        assertFalse(prefs.contains(ThemePrefs.KEY_LIKELY))
        assertFalse(prefs.contains(ThemePrefs.KEY_TRAIL))
    }

    @Test
    fun `Likely-Defaults sind aus-an`() {
        assertFalse(ThemePrefs.likelyHighlighting(prefs))
        assertTrue(ThemePrefs.likelyEffect(prefs))
    }
}
