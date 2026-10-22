package com.piotv.keytab.ime

import android.content.Context
import android.widget.Button
import com.piotv.keytab.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * SwipeManager (Android-bound): Prefs, Engine-Set, Passwort-Feld-Unterdrückung.
 * Robolectric für Button/Context.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwipeManagerTest {

    private val ctx: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = Prefs.of(ctx)

    @Before
    fun clear() { prefs.edit().clear().commit() }

    private fun button(letter: Char): Button = Button(ctx).apply {
        text = letter.toString()
        tag = "letter"
        measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
        layout(0, 0, 100, 100)
    }

    private fun manager(letters: String = "abc"): SwipeManager {
        val map = linkedMapOf<Button, Char>()
        for (c in letters) map[button(c)] = c
        return SwipeManager(prefs, map)
    }

    @Test
    fun `swipeEnabled ist default false`() {
        prefs.edit().clear().apply()
        assertFalse(manager().swipeEnabled())
    }

    @Test
    fun `swipeEnabled true wenn Pref gesetzt`() {
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_SWIPE, true).apply()
        assertTrue(manager().swipeEnabled())
    }

    @Test
    fun `setEngine macht Engine fuer Scorer verfuegbar`() {
        val m = manager("abc")
        assertEquals(null, m.engine)
        val e = SuggestionEngine(listOf("haus" to 1000))
        m.setEngine(e)
        assertEquals(e, m.engine)
    }

    @Test
    fun `onSwipeRelease ohne Samples liefert leere Liste`() {
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_SWIPE, true).apply()
        val m = manager("abc")
        m.startSwipe(10f, 10f)
        m.clearSwipeSamples()
        assertTrue(m.onSwipeRelease().isEmpty())
    }

    @Test
    fun `startSwipe ohne Swipe-Pref sammelt keine Samples`() {
        prefs.edit().clear().apply()
        val m = manager("abc")
        m.startSwipe(10f, 10f)
        assertFalse(m.hasSamples())
    }

    @Test
    fun `hasSamples ist false nach clearSwipeSamples`() {
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_SWIPE, true).apply()
        val m = manager("abc")
        m.startSwipe(10f, 10f)
        m.clearSwipeSamples()
        assertFalse(m.hasSamples())
    }

    // ---------- Seeding / hasSwiped (Tap vs. Swipe) ----------

    @Test
    fun `seedFirstKey legt genau ein Sample an und zaehlt nicht als Swipe`() {
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_SWIPE, true).apply()
        val m = manager("abc")
        m.startSwipe(10f, 10f)
        m.seedFirstKey('a', 10f, 10f)
        assertTrue("Seeding muss ein Sample anlegen (Ziel-Anzeige ab Taste 1)", m.hasSamples())
        assertFalse("ein reiner Tap (nur Seeding) ist kein Swipe", m.hasSwiped())
        m.seedFirstKey('a', 10f, 10f)
        assertFalse("doppeltes Seeding darf kein zweites Sample anlegen", m.hasSwiped())
    }

    @Test
    fun `seedFirstKey ohne Swipe-Pref legt kein Sample an`() {
        prefs.edit().clear().apply()
        val m = manager("abc")
        m.startSwipe(10f, 10f)
        m.seedFirstKey('a', 10f, 10f)
        assertFalse(m.hasSamples())
        assertFalse(m.hasSwiped())
    }

    @Test
    fun `hasSwiped ist false bei nur einem Sample`() {
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_SWIPE, true).apply()
        val m = manager("abc")
        m.startSwipe(10f, 10f)
        m.seedFirstKey('a', 10f, 10f)
        assertTrue(m.hasSamples())
        assertFalse(m.hasSwiped())
    }

}
