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
    fun scaling_isProportionalToScore() {
        val out = KeyScaleLogic.scales(mapOf('a' to 1.0, 'b' to 0.8, 'c' to 0.4), noNeighbors)
        val sa = out['a']!!
        val sb = out['b']!!
        // Nicht-hot-Tasten ohne Nachbarn fehlen in der Map (= neutral 1.0)
        val sc = out['c'] ?: 1f
        // b liegt proportional zwischen a und neutral
        assertTrue(sb < sa && sb > 1f)
        // c unterhalb des Hot-Thresholds bleibt neutral (keine Nachbarn)
        assertTrue(sc >= 1f)
        // Proportionalität: (sb-1)/(sa-1) == 0.8
        assertEquals(0.8, ((sb - 1f) / (sa - 1f)).toDouble(), 0.01)
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
    fun neighborShrink_proportionalToHotStrength() {
        val neighborsOf: (Char) -> Set<Char> = { c ->
            when (c) {
                's' -> setOf('a', 'b')
                'a', 'b' -> setOf('s')
                else -> emptySet()
            }
        }
        val out = KeyScaleLogic.scales(mapOf('a' to 1.0, 'b' to 0.6), neighborsOf)
        val sNearStrong = out['s']!!
        assertTrue(sNearStrong < 1f && sNearStrong >= KeyScaleLogic.MIN_NEIGHBOR_SCALE)
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
