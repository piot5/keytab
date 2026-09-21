package com.piotv.keytab.sections

import android.content.Context
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.piotv.keytab.ime.ThemePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Robolectric-Tests für die Theme-Einstellungs-Sektionen (`sections`).
 * Die Activity wird nur bis `onCreate` gebracht (create()), damit
 * `registerForActivityResult` (BackgroundSection) erlaubt bleibt und die
 * Sektionen anschließend manuell in ein LinearLayout gebaut werden.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class SectionsTestBase {

    protected lateinit var activity: AppCompatActivity
    protected lateinit var prefs: android.content.SharedPreferences
    protected lateinit var col: LinearLayout
    protected var changedCount = 0

    @Before
    fun setUpBase() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get()
        prefs = activity.getSharedPreferences(ThemePrefs.PREFS, Context.MODE_PRIVATE)
        col = LinearLayout(activity)
        changedCount = 0
    }

    protected fun idleMain() { ShadowLooper.idleMainLooper() }
}

class LikelyHighlightSectionTest : SectionsTestBase() {

    private fun section(): LikelyHighlightSection =
        LikelyHighlightSection(activity, prefs) { changedCount++ }

    private fun row(s: LikelyHighlightSection): LinearLayout {
        s.build(col)
        return col.getChildAt(0) as LinearLayout
    }

    @Test
    fun `build zeigt beiden Schalter in einer Reihe und Defaults`() {
        val s = section()
        val r = row(s)
        assertEquals(1, col.childCount)
        assertEquals(2, r.childCount)
        assertFalse(ThemePrefs.likelyHighlighting(prefs))
    }

    @Test
    fun `Klick auf Likely-Toggle schaltet Pref um und ruft onChange`() {
        val s = section()
        val r = row(s)
        r.getChildAt(0).performClick()
        assertTrue(ThemePrefs.likelyHighlighting(prefs))
        assertEquals(1, changedCount)
        r.getChildAt(0).performClick()
        assertFalse(ThemePrefs.likelyHighlighting(prefs))
        assertEquals(2, changedCount)
    }

    @Test
    fun `Klick auf Effect-Toggle schaltet Pref um`() {
        val s = section()
        val r = row(s)
        r.getChildAt(1).performClick()
        assertFalse(ThemePrefs.likelyEffect(prefs))
        assertEquals(1, changedCount)
    }
}

class TrailSectionTest : SectionsTestBase() {

    private fun buttons(s: TrailSection): LinearLayout {
        s.build(col)
        return col.getChildAt(0) as LinearLayout
    }

    @Test
    fun `build erzeugt drei Schalter in einer Reihe plus Hinweis-Texte`() {
        val s = TrailSection(activity, prefs) { changedCount++ }
        val r = buttons(s)
        assertEquals(3, col.childCount) // Reihe + 2 Hinweis-Texte
        assertEquals(3, r.childCount)
        assertFalse(ThemePrefs.trailEnabled(prefs))
    }

    @Test
    fun `Trail-Toggle schaltet trail_enabled um`() {
        val s = TrailSection(activity, prefs) { changedCount++ }
        val r = buttons(s)
        r.getChildAt(0).performClick()
        assertTrue(ThemePrefs.trailEnabled(prefs))
        assertEquals(1, changedCount)
        s.updateButton() // Farb-Update darf nicht crashen
    }

    @Test
    fun `Steps-Toggle zykl durch 3-5-7-10`() {
        val s = TrailSection(activity, prefs) { changedCount++ }
        val r = buttons(s)
        r.getChildAt(1).performClick() // Default 5 → 7
        assertEquals(7, ThemePrefs.trailSteps(prefs))
        r.getChildAt(1).performClick() // 7 → 10
        assertEquals(10, ThemePrefs.trailSteps(prefs))
        r.getChildAt(1).performClick() // 10 → 3
        assertEquals(3, ThemePrefs.trailSteps(prefs))
    }

    @Test
    fun `Trace-Toggle schaltet trail_trace um`() {
        val s = TrailSection(activity, prefs) { changedCount++ }
        val r = buttons(s)
        r.getChildAt(2).performClick()
        assertTrue(ThemePrefs.trailTraceEnabled(prefs))
        assertEquals(1, changedCount)
    }
}

class TopSectionTest : SectionsTestBase() {

    @Test
    fun `Mond-Icon setzt dark_mode true, Sonne false`() {
        val s = TopSection(activity, prefs) { changedCount++ }
        s.build(col)
        val row = col.getChildAt(0) as LinearLayout
        row.getChildAt(0).performClick() // Mond
        assertTrue(prefs.getBoolean(ThemePrefs.KEY_DARK, false))
        assertEquals(1, changedCount)
        row.getChildAt(1).performClick() // Sonne
        assertFalse(prefs.getBoolean(ThemePrefs.KEY_DARK, true))
        assertEquals(2, changedCount)
    }

    @Test
    fun `Preset-Klick schreibt Verlaufs-Farben und Modus`() {
        val s = TopSection(activity, prefs) { changedCount++ }
        s.build(col)
        val presetRow = col.getChildAt(1) as LinearLayout
        presetRow.getChildAt(2).performClick() // "Ozean"
        val dark = ThemePrefs.isDarkMode(activity)
        assertEquals(
            ThemePrefs.GRADIENTS[2].c1,
            prefs.getInt(ThemePrefs.colorKey(dark, ThemePrefs.KIND_GRADIENT1), 0))
        assertEquals(
            ThemePrefs.GRADIENTS[2].mode,
            prefs.getString(ThemePrefs.gradientModeKey(dark), null))
        assertEquals(1, changedCount)
    }

    /** Nur das aktive Icon wird hervorgehoben (Hintergrund statt transparent). */
    @Test
    fun `updateThemeIcons hebt genau das aktive Icon hervor`() {
        val s = TopSection(activity, prefs) { }
        s.build(col)
        val row = col.getChildAt(0) as LinearLayout
        val moon = row.getChildAt(0)
        val sun = row.getChildAt(1)
        val active = android.graphics.Color.parseColor("#33000000")
        val transparent = android.graphics.Color.TRANSPARENT
        fun background(v: android.view.View): Int =
            (v.background as? android.graphics.drawable.ColorDrawable)?.color ?: transparent

        s.updateThemeIcons(editingDark = true)
        assertEquals("Mond ist hervorgehoben", active, background(moon))
        assertEquals("Sonne ist transparent", transparent, background(sun))

        s.updateThemeIcons(editingDark = false)
        assertEquals("Mond ist transparent", transparent, background(moon))
        assertEquals("Sonne ist hervorgehoben", active, background(sun))
    }
}
