package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Shift/CapsLock-State-Machine (pure Logik, kein Robolectric nötig –
 * läuft aber im selben Testmodus).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShiftControllerTest {

    private val DEBOUNCE = 300L

    private fun controller(doubleTap: Long = DEBOUNCE) = ShiftController(doubleTap)

    @Test
    fun `einzelner Shift-Tap toggelt shifted, kein CapsLock`() {
        val c = controller()
        c.resetForInput(false)
        val s = c.tapShift(100)
        assertTrue(s.shifted)
        assertFalse(s.capsLock)
        assertTrue(c.isUpper())
        // zweiter einzelner Tap nach Debounce (weit genug entfernt) → wieder aus
        val s2 = c.tapShift(100 + DEBOUNCE + 50)
        assertFalse(s2.shifted)
        assertFalse(s2.capsLock)
    }

    @Test
    fun `Doppel-Tap aktiviert CapsLock`() {
        val c = controller()
        c.resetForInput(false)
        c.tapShift(100)
        val s = c.tapShift(100 + 50)  // innerhalb Debounce
        assertTrue(s.capsLock)
        assertTrue(s.shifted)
        // consume() darf CapsLock NICHT beenden
        c.consume()
        assertTrue("consume darf CapsLock nicht aufheben", c.state().capsLock)
        assertTrue(c.isUpper())
    }

    @Test
    fun `einzelner Tap bei aktivem CapsLock deaktiviert`() {
        val c = controller()
        c.resetForInput(false)
        c.tapShift(100); c.tapShift(150)  // CapsLock an
        val s = c.tapShift(500)           // einzelner Tap
        assertFalse(s.capsLock)
        assertFalse(s.shifted)
    }

    @Test
    fun `consume beendet einzelnes Shift aber nicht CapsLock`() {
        val c = controller()
        c.resetForInput(false)
        c.tapShift(100)
        assertTrue(c.state().shifted)
        c.consume()
        assertFalse(c.state().shifted)
        assertFalse(c.state().capsLock)
    }

    @Test
    fun `resetForInput setzt AutoCaps und CapsLock aus`() {
        val c = controller()
        c.tapShift(100); c.tapShift(150)
        c.resetForInput(true)
        assertTrue(c.state().shifted)   // AutoCaps = true
        assertFalse(c.state().capsLock)
        c.resetForInput(false)
        assertFalse(c.state().shifted)
    }

    @Test
    fun `Doppel-Tap-Erkennung respektiert konfigurierbares Zeitfenster`() {
        val c = controller(100)
        c.resetForInput(false)
        c.tapShift(100)
        val slow = c.tapShift(100 + 90)   // 90 < 100 → Doppel-Tap
        assertTrue(slow.capsLock)
        c.resetForInput(false)
        c.tapShift(1000)
        val notFast = c.tapShift(1000 + 150)  // 150 > 100 → einzelner Toggle
        assertFalse(notFast.capsLock)
    }
}