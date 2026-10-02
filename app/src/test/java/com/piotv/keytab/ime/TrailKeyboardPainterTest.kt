package com.piotv.keytab.ime

import android.content.Context
import android.widget.Button
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [TrailKeyboardPainter] (Android-bound): die v0.16-Differenz des Trail-Repaints.
 * Ergänzt den reinen [TrailOverlayDiffTest] um die View-Anbindung: Overlays
 * werden nur auf markierte Tasten gesetzt, entfernte Tasten werden abgeräumt
 * und [clear] entfernt alles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrailKeyboardPainterTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()
    private fun prefs() = app.getSharedPreferences(com.piotv.keytab.Prefs.FILE, Context.MODE_PRIVATE)

    @Before
    fun clearPrefs() { prefs().edit().clear().commit() }

    private fun board(vararg letters: Char): Map<Button, Char> {
        val map = linkedMapOf<Button, Char>()
        for (c in letters) map[Button(app).apply { text = c.toString() }] = c
        return map
    }

    private fun buttonFor(map: Map<Button, Char>, c: Char): Button =
        map.entries.first { it.value == c }.key

    @Test
    fun `apply setzt Overlays nur auf markierte Tasten`() {
        val map = board('a', 'b', 'c')
        val painter = TrailKeyboardPainter(prefs(), map)
        painter.apply(mapOf('a' to 0), mapOf('a' to TrailLogic.TrailKind.TYPED), 5)
        assertNotNull("markierte Taste hat Overlay", buttonFor(map, 'a').foreground)
        assertNull("unmarkierte Taste bleibt frei", buttonFor(map, 'b').foreground)
        assertNull(buttonFor(map, 'c').foreground)
    }

    @Test
    fun `zweites apply raeumt entfernte Taste ab (Differenz)`() {
        val map = board('a', 'b')
        val painter = TrailKeyboardPainter(prefs(), map)
        painter.apply(
            mapOf('a' to 0, 'b' to 0),
            mapOf('a' to TrailLogic.TrailKind.TYPED, 'b' to TrailLogic.TrailKind.TYPED),
            5
        )
        val a = buttonFor(map, 'a')
        val b = buttonFor(map, 'b')
        assertNotNull(a.foreground)
        assertNotNull(b.foreground)
        painter.apply(mapOf('a' to 1), mapOf('a' to TrailLogic.TrailKind.TYPED), 5)
        assertNotNull("'a' bleibt markiert", a.foreground)
        assertNull("'b' muss abgeraeumt sein", b.foreground)
    }

    @Test
    fun `clear entfernt alle Overlays`() {
        val map = board('a', 'b')
        val painter = TrailKeyboardPainter(prefs(), map)
        painter.apply(mapOf('a' to 0, 'b' to 0),
            mapOf('a' to TrailLogic.TrailKind.TYPED, 'b' to TrailLogic.TrailKind.TYPED), 5)
        painter.clear()
        assertTrue("alle Overlays muessen weg sein", map.keys.all { it.foreground == null })
    }
}
