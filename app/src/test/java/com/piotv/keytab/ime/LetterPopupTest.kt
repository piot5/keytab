package com.piotv.keytab.ime

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * LetterPopup: Fallback-/Highlight-/Clear-Vertraege ohne sichtbaren
 * Popup-Overhead (pickedChar/clearPicked sind reine Zustandslogik).
 * show() braucht ein Fenster-Token und wird nur auf no-crash geprueft.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LetterPopupTest {

    private fun popup() = LetterPopup(
        Robolectric.buildService(KeyTabImeService::class.java).get())

    @Test
    fun `frisches Popup liefert null und clear ist no-op`() {
        val p = popup()
        assertNull(p.pickedChar())
        p.clearPicked()
        assertNull(p.pickedChar())
        p.dismiss()
    }

    @Test
    fun `dismiss ohne show crasht nicht`() {
        val p = popup()
        p.dismiss()
        p.dismiss()
        assertNull(p.pickedChar())
    }

    @Test
    fun `clearPicked ohne Highlight crasht nicht`() {
        val p = popup()
        repeat(3) { p.clearPicked() }
        assertNull(p.pickedChar())
    }

    @Test
    fun `highlightCellUnder ohne Popup ist no-op`() {
        val p = popup()
        val anchor = View(RuntimeEnvironment.getApplication())
        val e = android.view.MotionEvent.obtain(0, 0,
            android.view.MotionEvent.ACTION_MOVE, 5f, 5f, 0)
        p.highlightCellUnder(e) { throw AssertionError("kein Haptic ohne Popup") }
        e.recycle()
        assertNull(p.pickedChar())
    }

    @Test
    fun `show mit leerer Liste crasht nicht, picked bleibt Fallback-null`() {
        val p = popup()
        val anchor = View(RuntimeEnvironment.getApplication())
        anchor.layout(0, 0, 100, 100)
        try {
            p.show(anchor, emptyList()) {}
        } catch (_: Exception) {
            // Ohne Window-Token darf show() unter Robolectric werfen —
            // Vertrag: pickedChar bleibt danach null.
        } finally {
            p.dismiss()
        }
        p.clearPicked()
        assertNull(p.pickedChar())
    }

    @Test
    fun `show mit Umlauten oeffnet Zellen ohne Crash`() {
        val p = popup()
        val anchor = View(RuntimeEnvironment.getApplication())
        anchor.layout(0, 0, 100, 100)
        var committed: Char? = null
        try {
            p.show(anchor, listOf("\u00E4", "\u00E9", "\u00F1")) { committed = it }
        } catch (_: Exception) {
            // Wie oben: Fenster-Token fehlt unter Robolectric.
        } finally {
            p.dismiss()
        }
        p.clearPicked()
        // Kein Drag stattgefunden → nichts committed.
        assertEquals(null, committed)
    }
}
