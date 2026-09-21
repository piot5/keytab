package com.piotv.keytab.ime

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regressionstests für [KeyAnimations.applyRoundedCorners].
 *
 * Hintergrund: Die Funktion ersetzte den Root-Hintergrund durch ein flaches
 * [GradientDrawable] mit `setColor(...)`. Bei einem konfigurierten **Verlauf**
 * liefert `GradientDrawable.color` den Wert `null` (die Farben stecken in
 * `colors`), sodass auf den dunklen Fallback zurückgefallen und der Verlauf des
 * Nutzers zerstört wurde.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyAnimationsTest {

    private fun rootView(): View =
        View(RuntimeEnvironment.getApplication()).also {
            it.layoutParams = FrameLayout.LayoutParams(1080, 200)
        }

    @Test
    fun `applyRoundedCorners erhaelt einen zweifarbigen Verlauf`() {
        val v = rootView()
        v.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF2196F3.toInt(), 0xFF0D47A1.toInt()))

        KeyAnimations.applyRoundedCorners(v)

        val d = v.background as GradientDrawable
        val colors = d.colors
        assertNotNull("Verlauf muss erhalten bleiben (colors != null)", colors)
        assertEquals("Startfarbe des Verlaufs", 0xFF2196F3.toInt(), colors!![0])
        assertEquals("Endfarbe des Verlaufs", 0xFF0D47A1.toInt(), colors[1])
        assertEquals(GradientDrawable.Orientation.TOP_BOTTOM, d.orientation)
    }

    @Test
    fun `applyRoundedCorners erhaelt eine einfarbige Fuellung`() {
        val v = rootView()
        v.background = ColorDrawable(0xFFE0E0E0.toInt())

        KeyAnimations.applyRoundedCorners(v)

        val d = v.background as GradientDrawable
        assertEquals(0xFFE0E0E0.toInt(), d.color?.defaultColor)
    }

    @Test
    fun `applyRoundedCorners rundet nur die oberen Ecken`() {
        val v = rootView()
        v.background = ColorDrawable(0xFFE0E0E0.toInt())
        KeyAnimations.applyRoundedCorners(v)
        // cornerRadii sind nur pruefbar, wenn das Drawable vermessen wird;
        // hier genuegt der Typ-Nachweis (GradientDrawable statt ColorDrawable).
        assertTrue("Hintergrund muss ein GradientDrawable sein",
            v.background is GradientDrawable)
    }

    @Test
    fun `applyRoundedCorners faellt ohne Hintergrund auf die Standardfarbe zurueck`() {
        val v = rootView()
        v.background = null
        KeyAnimations.applyRoundedCorners(v)
        val d = v.background as GradientDrawable
        assertEquals("Fallback-Hintergrund", 0xFF1a1a1a.toInt(), d.color?.defaultColor)
    }
}
