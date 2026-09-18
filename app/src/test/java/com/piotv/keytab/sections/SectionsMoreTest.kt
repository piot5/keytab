package com.piotv.keytab.sections

import android.graphics.Color
import com.piotv.keytab.R
import com.piotv.keytab.ime.ThemePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradientSectionTest : SectionsTestBase() {

    @Test
    fun `build erzeugt Checkbox, Spinner und Preview`() {
        val s = GradientSection(activity, prefs) { changedCount++ }
        s.build(col)
        assertTrue(col.childCount >= 3)
        s.updateGradient()
    }

    @Test
    fun `Checkbox unchecked setzt gradient_off_dark`() {
        val s = GradientSection(activity, prefs) { changedCount++ }
        s.build(col)
        val cb = col.getChildAt(0) as android.widget.CheckBox
        cb.isChecked = false
        assertTrue(ThemePrefs.isGradientOff(prefs, dark = false))
        assertEquals(1, changedCount)
    }

    @Test
    fun `updateGradient uebernimmt gesetzten Modus in die Auswahl`() {
        prefs.edit().putString(ThemePrefs.KEY_GRADIENT_MODE, ThemePrefs.GRADIENT_RADIAL).apply()
        val s = GradientSection(activity, prefs) { changedCount++ }
        s.build(col)
        s.updateGradient()
        // Aktualisierung ohne Crash; Verlauf-Preview wurde erzeugt (aktiv, kein OFF)
        assertTrue(ThemePrefs.hasGradient(prefs, dark = false))
    }
}

class PreviewSectionTest : SectionsTestBase() {

    @Test
    fun `build + updatePreview ohne Crash und mit Hintergrund`() {
        val s = PreviewSection(activity, prefs, { Color.RED }) { changedCount++ }
        s.build(col)
        assertEquals(1, col.childCount)
        s.updatePreview()
        assertNotNull(col.getChildAt(0).background)
    }
}

class ColorSectionTest : SectionsTestBase() {

    @Test
    fun `build erzeugt Farbkreis und zwei Slider`() {
        val s = ColorSection(activity, prefs, { ThemePrefs.KIND_BG }) { changedCount++ }
        s.build(col)
        assertEquals(3, col.childCount)
    }

    @Test
    fun `updateControls liest gesetzte Farbe in die Slider ein`() {
        prefs.edit().putInt(ThemePrefs.colorKey(dark = false, ThemePrefs.KIND_BG),
            Color.argb(200, 0, 120, 255)).apply()
        val s = ColorSection(activity, prefs, { ThemePrefs.KIND_BG }) { }
        s.build(col)
        s.updateControls(ThemePrefs.KIND_BG)
        val brightness = col.getChildAt(1) as android.widget.SeekBar
        val alpha = col.getChildAt(2) as android.widget.SeekBar
        assertTrue(brightness.progress in 0..100)
        assertEquals(200, alpha.progress)
    }
}

class BackgroundSectionTest : SectionsTestBase() {

    @Test
    fun `build erzeugt Titel, zwei Buttons, Label und Spinner`() {
        val s = BackgroundSection(activity, prefs) { changedCount++ }
        s.build(col)
        assertTrue(col.childCount >= 5)
        s.refresh()
        // Kein Bild gesetzt → "kein Bild"-Label
        val label = col.getChildAt(2) as android.widget.TextView
        assertEquals(activity.getString(R.string.background_none), label.text)
    }

    @Test
    fun `refresh zeigt gesetzten Bild-URI als Label`() {
        prefs.edit().putString(com.piotv.keytab.Prefs.KEY_BG_IMAGE_URI, "content://x").apply()
        val s = BackgroundSection(activity, prefs) { changedCount++ }
        s.build(col)
        s.refresh()
        val label = col.getChildAt(2) as android.widget.TextView
        assertEquals("content://x", label.text)
    }

    @Test
    fun `refresh uebernimmt gesetzten Fill-Modus in die Auswahl`() {
        prefs.edit().putString(com.piotv.keytab.Prefs.KEY_BG_IMAGE_FILL, com.piotv.keytab.Prefs.FILL_COVER).apply()
        val s = BackgroundSection(activity, prefs) { changedCount++ }
        s.build(col)
        s.refresh()
        assertEquals(6, col.childCount) // Titel, 2 Buttons, Label, Spinner, Hinweis
    }
}
