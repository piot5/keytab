package com.piotv.keytab.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spezifikation der Verfügbarkeitsregel der Schaltplan-Vorschau. Kernpunkt:
 * der Schalter darf nie „an" dastehen, ohne dass etwas angezeigt wird.
 */
class SwipePreviewAvailabilityTest {

    @Test
    fun `mit Swipe verfuegbar`() {
        assertTrue(SwipePreviewAvailability.available(swipeEnabled = true, dynamicKeysEnabled = false))
    }

    @Test
    fun `mit dynamischen Tasten verfuegbar`() {
        assertTrue(SwipePreviewAvailability.available(swipeEnabled = false, dynamicKeysEnabled = true))
    }

    @Test
    fun `ohne beide Score-Quellen nicht verfuegbar`() {
        assertFalse(SwipePreviewAvailability.available(swipeEnabled = false, dynamicKeysEnabled = false))
    }

    @Test
    fun `aktiv nur wenn gewuenscht und verfuegbar`() {
        assertTrue(SwipePreviewAvailability.active(
            previewRequested = true, swipeEnabled = true, dynamicKeysEnabled = false))
    }

    @Test
    fun `nicht gewuenscht heisst nicht aktiv`() {
        assertFalse(SwipePreviewAvailability.active(
            previewRequested = false, swipeEnabled = true, dynamicKeysEnabled = true))
    }

    @Test
    fun `gewuenscht aber keine Score-Quelle heisst nicht aktiv`() {
        assertFalse("sonst steht der Schalter aktiv da und zeigt nichts",
            SwipePreviewAvailability.active(
                previewRequested = true, swipeEnabled = false, dynamicKeysEnabled = false))
    }
}
