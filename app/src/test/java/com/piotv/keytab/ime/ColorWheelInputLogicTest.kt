package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spezifikationen der Farb-Rad-Gestenlogik. Regressionsschutz für den
 * Multi-Touch-Fehler: `MotionEvent.x` ist immer Zeiger-Index 0, wodurch ein
 * zweiter Finger die gewählte Farbe mitzog.
 */
class ColorWheelInputLogicTest {

    // ---------------- Zeiger-Übergänge ----------------

    @Test
    fun `DOWN im Rad startet Tracking und merkt sich den Zeiger`() {
        val l = ColorWheelInputLogic()
        assertTrue(l.onDown(pointerId = 3, inside = true))
        assertTrue(l.isTracking())
        assertEquals(3, l.activePointer)
    }

    @Test
    fun `DOWN ausserhalb des Rads startet kein Tracking`() {
        val l = ColorWheelInputLogic()
        assertFalse(l.onDown(pointerId = 0, inside = false))
        assertFalse(l.isTracking())
        assertEquals(ColorWheelInputLogic.NO_POINTER, l.activePointer)
    }

    @Test
    fun `DOWN ausserhalb bricht ein laufendes Tracking ab`() {
        val l = ColorWheelInputLogic()
        l.onDown(0, inside = true)
        l.onDown(9, inside = false)
        assertFalse("zweiter Finger ausserhalb darf die Geste nicht uebernehmen", l.isTracking())
    }

    @Test
    fun `zweiter Finger wird konsumiert aber nicht zum aktiven Zeiger`() {
        val l = ColorWheelInputLogic()
        l.onDown(1, inside = true)
        assertTrue("Event muss konsumiert werden, Geste laeuft weiter", l.onSecondaryDown(2))
        assertEquals("der urspruengliche Finger bleibt verfolgt", 1, l.activePointer)
    }

    @Test
    fun `MOVE liefert nur die Position des aktiven Fingers`() {
        val l = ColorWheelInputLogic()
        l.onDown(1, inside = true)
        // Regression: mit zwei Fingern auf dem Rad wanderte die Farbe auf den
        // falschen Finger, weil die View immer Zeiger-Index 0 auslas.
        val own = l.trackedPosition(pointerId = 1, x = 40f, y = 50f)
        assertEquals(40f, own!!.first, 0.001f)
        assertEquals(50f, own.second, 0.001f)
        assertNull("fremder Finger liefert keine Position",
            l.trackedPosition(pointerId = 2, x = 999f, y = 999f))
    }

    @Test
    fun `MOVE ohne Tracking liefert null`() {
        val l = ColorWheelInputLogic()
        assertNull(l.trackedPosition(pointerId = 0, x = 1f, y = 1f))
    }

    @Test
    fun `POINTER_UP des aktiven Fingers beendet die Geste`() {
        val l = ColorWheelInputLogic()
        l.onDown(1, inside = true)
        assertTrue(l.onPointerUp(1))
        assertFalse(l.isTracking())
    }

    @Test
    fun `POINTER_UP eines anderen Fingers laesst die Geste weiterlaufen`() {
        val l = ColorWheelInputLogic()
        l.onDown(1, inside = true)
        assertFalse("nicht-aktiver Finger darf nicht beenden", l.onPointerUp(2))
        assertTrue(l.isTracking())
        assertEquals(1, l.activePointer)
        // Die Geste ist danach noch benutzbar — der Finger zieht weiter.
        assertEquals(50f, l.trackedPosition(1, 50f, 60f)!!.first, 0.001f)
    }

    @Test
    fun `POINTER_UP ohne Tracking meldet nichts zu beenden`() {
        assertFalse(ColorWheelInputLogic().onPointerUp(0))
    }

    @Test
    fun `onRelease beendet die Geste auch ohne Zeiger-ID`() {
        val l = ColorWheelInputLogic()
        l.onDown(4, inside = true)
        l.onRelease()
        assertFalse(l.isTracking())
        assertEquals(ColorWheelInputLogic.NO_POINTER, l.activePointer)
    }

    @Test
    fun `zwei Instanzen teilen keinen Zustand`() {
        val a = ColorWheelInputLogic()
        val b = ColorWheelInputLogic()
        a.onDown(1, inside = true)
        assertFalse(b.isTracking())
        assertNull(b.trackedPosition(1, 10f, 10f))
    }

    // ---------------- Hue/Saettigung ----------------

    @Test
    fun `Mitte ist Hue 0 und Saettigung 0`() {
        val out = FloatArray(2)
        assertTrue(ColorWheelInputLogic().hsvAt(100f, 100f, 80f, 100f, 100f, out))
        assertEquals(0f, out[0], 0.001f)
        assertEquals(0f, out[1], 0.001f)
    }

    @Test
    fun `rechts ist Hue 0 und max Saettigung`() {
        val out = FloatArray(2)
        assertTrue(ColorWheelInputLogic().hsvAt(100f, 100f, 80f, 180f, 100f, out))
        assertEquals(0f, out[0], 0.001f)
        assertEquals(1f, out[1], 0.001f)
    }

    @Test
    fun `unten ist Hue 90 weil der Bildschirm-y nach unten waechst`() {
        val out = FloatArray(2)
        assertTrue(ColorWheelInputLogic().hsvAt(100f, 100f, 80f, 100f, 180f, out))
        assertEquals(90f, out[0], 0.001f)
    }

    @Test
    fun `oben ist Hue 270 und nicht negativ`() {
        // SweepGradient startet bei 3 Uhr und laeuft im Uhrzeigersinn (Marker:
        // cos/sin mit Bildschirm-y nach unten) — oben liegt also 270, nicht 180.
        val out = FloatArray(2)
        assertTrue(ColorWheelInputLogic().hsvAt(100f, 100f, 80f, 100f, 20f, out))
        assertEquals(270f, out[0], 0.001f)
    }

    @Test
    fun `ausserhalb des Radius wird nichts geschrieben`() {
        val out = floatArrayOf(7f, 7f)
        assertFalse(ColorWheelInputLogic().hsvAt(100f, 100f, 80f, 200f, 100f, out))
        assertEquals("Farbe darf sich beim Ziehen ueber den Rand nicht aendern", 7f, out[0], 0.001f)
        assertEquals(7f, out[1], 0.001f)
    }

    @Test
    fun `Radius null liefert keine Position`() {
        assertFalse(ColorWheelInputLogic().hsvAt(100f, 100f, 0f, 100f, 100f, FloatArray(2)))
    }

    @Test
    fun `Saettigung halbiert sich auf halbem Radius`() {
        val out = FloatArray(2)
        assertTrue(ColorWheelInputLogic().hsvAt(0f, 0f, 100f, 50f, 0f, out))
        assertEquals(0.5f, out[1], 0.001f)
    }
}
