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
 * SwipeManager (Android-bound): Preview-Färbung, Clear, Prefs, Engine-Set,
 * Passwort-Feld-Unterdrückung. Robolectric für Button/Context.
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
    fun `previewEnabled ist default false`() {
        prefs.edit().clear().apply()
        assertFalse(manager().previewEnabled())
    }

    @Test
    fun `previewEnabled true wenn Pref gesetzt`() {
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_SWIPE_PREVIEW, true).apply()
        assertTrue(manager().previewEnabled())
    }

    @Test
    fun `applyPreview faerbt Knoten-Tasten wenn Preview an`() {
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_SWIPE_PREVIEW, true).apply()
        val m = manager("abc")
        val beforeA = m.let { baseLettersA(it) }
        m.applyPreview(listOf('a'))
        val afterA = baseLettersA(m)
        // Hintergrund wurde auf einen GradientDrawable gesetzt (nicht mehr original).
        assertTrue("Knoten-A Hintergrund muss gefaerbt sein",
            afterA is android.graphics.drawable.GradientDrawable)
        assertFalse(afterA === beforeA)
    }

    @Test
    fun `applyPreview ohne Preview-Pref faerbt nichts`() {
        prefs.edit().clear().apply()
        val m = manager("abc")
        val beforeA = baseLettersA(m)
        m.applyPreview(listOf('a'))
        assertEquals("ohne Pref darf der Hintergrund unangetastet bleiben",
            beforeA, baseLettersA(m))
    }

    @Test
    fun `clearPreview leert die Knoten-Hintergrundliste`() {
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_SWIPE_PREVIEW, true).apply()
        val m = manager("abc")
        m.applyPreview(listOf('a'))
        assertTrue(nodeBackgroundsCount(m) > 0)
        m.clearPreview()
        assertEquals("clearPreview muss die Knoten-Liste leeren", 0, nodeBackgroundsCount(m))
    }

    @Test
    fun `applyPreview in Passwort-Feld unterdrueckt`() {
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_SWIPE_PREVIEW, true).apply()
        val m = manager("abc")
        m.editorInfo = android.view.inputmethod.EditorInfo().apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val beforeA = baseLettersA(m)
        m.applyPreview(listOf('a'))
        assertEquals("Passwort-Feld darf keine Preview faerben", beforeA, baseLettersA(m))
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

    @Test
    fun `wasLikelyHit false vor erstem applySwipeLikely (keine Likely-Knoten gesetzt)`() {
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_SWIPE, true).apply()
        val m = manager("abc")
        m.setEngine(SuggestionEngine(listOf("abc" to 1000)))
        assertFalse("vor erstem Likely-Lauf darf kein Treffer als likely gelten",
            m.wasLikelyHit('a'))
    }

    @Test
    fun `wasLikelyHit true nach setzen von Likely-Knoten (via applyPreview als Proxy)`() {
        // applySwipeLikely berechnet Likely-Knoten aus der Swipe-Route; da deren
        // Zentren-Auflösung in Robolectric kein echtes Window hat, setzen wir die
        // Likely-Knoten hier ueber applyPreview (nutzt dasselbe previewNodes-Feld),
        // um wasLikelyHit isoliert zu pruefen.
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_SWIPE_PREVIEW, true).apply()
        val m = manager("abc")
        m.applyPreview(listOf('a', 'u'))
        assertTrue(m.wasLikelyHit('a'))
        assertTrue(m.wasLikelyHit('u'))
        assertFalse(m.wasLikelyHit('b'))
    }

    @Test
    fun `wasLikelyHit false nach clearPreview (Likely-Knoten entfernt)`() {
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_SWIPE_PREVIEW, true).apply()
        val m = manager("abc")
        m.applyPreview(listOf('a'))
        assertTrue(m.wasLikelyHit('a'))
        m.clearPreview()
        assertFalse(m.wasLikelyHit('a'))
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

    // ---------- Crash-Regression: Kanten-Overlay (EdgeOverlay) ----------

    @Test
    fun `applyPreview mit zwei Knoten stuerzt ohne angehaengten Container nicht ab`() {
        // Dropbox-Crash: EdgeOverlay wurde mit null-Context bzw. in einen nicht
        // angehaengten Container gezeichnet -> NullPointerException in View.<init>
        // ("Context.getResources() on a null object reference"). Ohne Window
        // (Robolectric) darf der Kanten-Pfad daher weder werfen noch die
        // Knoten-Faerbung zerstoeren.
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_SWIPE_PREVIEW, true).apply()
        val m = manager("ab")
        m.applyPreview(listOf('a', 'b'))
        assertTrue("Knoten muessen trotz fehlendem Container gefaerbt sein",
            baseLettersA(m) is android.graphics.drawable.GradientDrawable)
        m.clearPreview()
    }

    @Test
    fun `clearPreview ist idempotent (kein Overlay zweimal entfernen)`() {
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_SWIPE_PREVIEW, true).apply()
        val m = manager("ab")
        m.applyPreview(listOf('a', 'b'))
        m.clearPreview()
        m.clearPreview()
        assertEquals(0, nodeBackgroundsCount(m))
    }

    private fun baseLettersA(m: SwipeManager): android.graphics.drawable.Drawable? {
        val map = baseLettersMap(m)
        val btn = map.entries.first { it.value == 'a' }.key
        return btn.background
    }

    private fun nodeBackgroundsCount(m: SwipeManager): Int {
        val field = SwipeManager::class.java.getDeclaredField("nodeBackgrounds")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = field.get(m) as List<*>
        return list.size
    }

    private fun baseLettersMap(m: SwipeManager): Map<Button, Char> {
        val field = SwipeManager::class.java.getDeclaredField("baseLetters")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(m) as Map<Button, Char>
    }
}
