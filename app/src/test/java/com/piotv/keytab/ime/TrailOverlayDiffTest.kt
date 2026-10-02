package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine Differenzlogik des Trail-Repaints (v0.16 Performance).
 *
 * Der [TrailKeyboardPainter] soll pro Tastendruck nur noch Tasten berühren,
 * deren Overlay verschwindet oder seine Farbe ändert — [TrailOverlayDiff]
 * liefert genau diese Entscheidung, Android-frei und damit JUnit-testbar.
 */
class TrailOverlayDiffTest {

    @Test
    fun `leere Zustaende ergeben eine leere Differenz`() {
        val d = TrailOverlayDiff.compute(emptyMap<Char, Int>(), emptyMap())
        assertTrue(d.remove.isEmpty())
        assertTrue(d.set.isEmpty())
    }

    @Test
    fun `neuer Schluessel landet in set`() {
        val d = TrailOverlayDiff.compute(emptyMap<Char, Int>(), mapOf('a' to 0x00FF0000.toInt()))
        assertTrue(d.remove.isEmpty())
        assertEquals(mapOf('a' to 0x00FF0000.toInt()), d.set)
    }

    @Test
    fun `verschwundener Schluessel landet in remove`() {
        val d = TrailOverlayDiff.compute(mapOf('a' to 0x00FF0000.toInt()), emptyMap())
        assertEquals(setOf('a'), d.remove)
        assertTrue(d.set.isEmpty())
    }

    @Test
    fun `geaenderte Farbe landet in set und unveraenderte nicht`() {
        val prev = mapOf('a' to 0x00FF0000.toInt(), 'b' to 0x0000FF00.toInt())
        val target = mapOf('a' to 0x000000FF.toInt(), 'b' to 0x0000FF00.toInt())
        val d = TrailOverlayDiff.compute(prev, target)
        assertTrue("nichts verschwindet", d.remove.isEmpty())
        assertEquals("nur 'a' hat eine neue Farbe", mapOf('a' to 0x000000FF.toInt()), d.set)
    }

    @Test
    fun `gleicher Zustand beruehrt nichts`() {
        val state = mapOf('a' to 0x00FF0000.toInt(), 'b' to 0x0000FF00.toInt())
        val d = TrailOverlayDiff.compute(state, state)
        assertTrue(d.remove.isEmpty())
        assertTrue(d.set.isEmpty())
    }
}
