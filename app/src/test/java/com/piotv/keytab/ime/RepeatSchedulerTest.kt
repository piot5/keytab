package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RepeatScheduler: beschleunigender Del-Repeat (reine Zeitlogik).
 * Start 250 ms → Faktor → Minimum 30 ms (Werte aus KeyTabImeService.setupDelButton).
 */
class RepeatSchedulerTest {

    private fun scheduler() = RepeatScheduler(250L, 0.9f, 30L)

    @Test
    fun `Startintervall ist der Konstruktorwert`() {
        assertEquals(250L, scheduler().interval)
    }

    @Test
    fun `next beschleunigt multiplikativ`() {
        val s = scheduler()
        s.next()
        assertEquals((250 * 0.9).toLong(), s.interval)
        s.next()
        assertEquals(((250 * 0.9) * 0.9).toLong(), s.interval)
    }

    @Test
    fun `Intervall faellt nie unter das Minimum`() {
        val s = scheduler()
        repeat(100) { s.next() }
        assertEquals(30L, s.interval)
        s.next()
        assertEquals("Minimum klebt", 30L, s.interval)
    }

    @Test
    fun `reset stellt den Startwert wieder her`() {
        val s = scheduler()
        repeat(10) { s.next() }
        assertTrue(s.interval < 250L)
        s.reset()
        assertEquals(250L, s.interval)
    }

    @Test
    fun `Faktor 1 haelt das Intervall konstant`() {
        val s = RepeatScheduler(100L, 1.0f, 10L)
        repeat(5) { s.next() }
        assertEquals(100L, s.interval)
    }

    @Test
    fun `Minimum ueber Start wird sofort geklemmt`() {
        val s = RepeatScheduler(20L, 0.9f, 30L)
        s.next()
        assertEquals(30L, s.interval)
    }
}
