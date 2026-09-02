package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyScaleLogicTest {

    private val noNeighbors: (Char) -> Set<Char> = { emptySet() }

    @Test
    fun emptyScores_returnsEmptyMap() {
        assertTrue(KeyScaleLogic.scales(emptyMap(), noNeighbors).isEmpty())
    }

    @Test
    fun allZeroScores_returnsEmptyMap() {
        assertTrue(KeyScaleLogic.scales(mapOf('a' to 0.0, 'b' to 0.0), noNeighbors).isEmpty())
    }

    @Test
    fun topKey_scalesToMax() {
        val out = KeyScaleLogic.scales(mapOf('a' to 5.0, 'b' to 0.0), noNeighbors)
        assertEquals(KeyScaleLogic.MAX_SCALE, out['a']!!, 0.001f)
    }

    @Test
    fun scaling_isSteppedByProbability() {
        val out = KeyScaleLogic.scales(mapOf('a' to 1.0, 'b' to 0.6, 'c' to 0.4), noNeighbors)
        // Stufe 3 (rel >= 0.75)
        assertEquals(KeyScaleLogic.MAX_SCALE, out['a']!!, 0.001f)
        // Stufe 2 (rel >= 0.55)
        assertEquals(KeyScaleLogic.MID_SCALE, out['b']!!, 0.001f)
        // Unterhalb der Stufe-2-Schwelle: neutral (fehlt in der Map)
        assertFalse(out.containsKey('c'))
    }

    @Test
    fun lowLikelihoodKeys_withoutHotNeighbors_stayNeutral() {
        val out = KeyScaleLogic.scales(mapOf('a' to 1.0, 'm' to 0.1), noNeighbors)
        assertFalse(out.containsKey('m'))
    }

    @Test
    fun shrinking_limitedToDirectNeighbors() {
        // symmetrische Nachbarschaft: s <-> a, s <-> d
        val neighborsOf: (Char) -> Set<Char> = { c ->
            when (c) {
                's' -> setOf('a', 'd')
                'a', 'd' -> setOf('s')
                else -> emptySet()
            }
        }
        val out = KeyScaleLogic.scales(mapOf('a' to 1.0, 'x' to 0.2), neighborsOf)
        assertEquals(KeyScaleLogic.MIN_NEIGHBOR_SCALE, out['s']!!, 0.001f)
        assertFalse(out.containsKey('x'))
    }

    @Test
    fun neighborShrink_isSteppedByHotStrength() {
        val neighborsOf: (Char) -> Set<Char> = { c ->
            when (c) {
                's' -> setOf('a', 'b', 't')   // s grenzt an Stufe3 (a), Stufe2 (b), neutral (t)
                'a', 't' -> setOf('s')
                'b' -> setOf('s')
                else -> emptySet()
            }
        }
        val out = KeyScaleLogic.scales(mapOf('a' to 1.0, 'b' to 0.6), neighborsOf)
        // Nachbar einer Stufe-3-Taste → stärkste Verkleinerung
        assertEquals(KeyScaleLogic.MIN_NEIGHBOR_SCALE, out['s']!!, 0.001f)
        // 't' hat nur neutrale Nachbarn → bleibt neutral
        assertFalse(out.containsKey('t'))
    }

    @Test
    fun neighborOfMidStepKey_getsMilderShrink() {
        val neighborsOf: (Char) -> Set<Char> = { c ->
            when (c) {
                's' -> setOf('b')   // s grenzt NUR an eine Stufe-2-Taste
                'b' -> setOf('s')
                else -> emptySet()
            }
        }
        // a = Stufe 3 (rel 1.0), b = Stufe 2 (rel 0.6); s grenzt nur an b
        val out = KeyScaleLogic.scales(mapOf('a' to 1.0, 'b' to 0.6), neighborsOf)
        assertEquals(KeyScaleLogic.MID_NEIGHBOR_SCALE, out['s']!!, 0.001f)
    }

    @Test
    fun hotKey_isNeverShrunk() {
        val neighborsOf: (Char) -> Set<Char> = { setOf('a') }
        val out = KeyScaleLogic.scales(mapOf('a' to 1.0, 's' to 0.9), neighborsOf)
        assertTrue(out['s']!! >= 1f)
    }

    @Test
    fun constants_inExpectedRange() {
        assertTrue(KeyScaleLogic.MAX_SCALE > 1.2f)
        assertTrue(KeyScaleLogic.MIN_NEIGHBOR_SCALE in 0.8f..0.9f)
    }
}
