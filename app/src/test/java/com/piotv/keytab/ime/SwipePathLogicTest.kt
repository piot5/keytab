package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine Swipe-Pfad-Logik: Dedup, charAt (Zentren-Matrix), Pfad-Buchstaben,
 * Preview-Knoten und die Passwort-Schutzregel (gespiegelt von TrailLogic).
 */
class SwipePathLogicTest {

    private fun centers(): List<SwipePathLogic.KeyCenter> = listOf(
        SwipePathLogic.KeyCenter('a', 30f, 10f),
        SwipePathLogic.KeyCenter('b', 90f, 10f),
        SwipePathLogic.KeyCenter('c', 30f, 70f),
        SwipePathLogic.KeyCenter('d', 90f, 70f),
        SwipePathLogic.KeyCenter('e', 150f, 70f)
    )

    private fun s(x: Float, y: Float) = SwipePathLogic.Sample(x, y)

    // ---------- Dedup ----------

    @Test
    fun `dedup entfernt unmittelbar aufeinanderfolgende Duplikate`() {
        val out = SwipePathLogic.dedup(listOf(s(1f, 1f), s(1f, 1f), s(2f, 2f), s(2f, 2f), s(2f, 2f)))
        assertEquals(listOf(s(1f, 1f), s(2f, 2f)), out)
    }

    @Test
    fun `dedup laesst verschiedene aufeinanderfolgende Koordinaten stehen`() {
        val out = SwipePathLogic.dedup(listOf(s(1f, 1f), s(1f, 2f), s(1f, 1f)))
        assertEquals(3, out.size)
    }

    @Test
    fun `dedup bei leerer Liste liefert leer`() {
        assertTrue(SwipePathLogic.dedup(emptyList()).isEmpty())
    }

    // ---------- charAt ----------

    @Test
    fun `charAt trifft die naechste Taste innerhalb des Toleranz-Rings`() {
        assertEquals('a', SwipePathLogic.charAt(35f, 15f, centers()))
        assertEquals('b', SwipePathLogic.charAt(85f, 12f, centers()))
    }

    @Test
    fun `charAt liefert null ausserhalb des Toleranz-Rings`() {
        assertNull(SwipePathLogic.charAt(500f, 500f, centers()))
    }

    @Test
    fun `charAt liefert null bei leeren Zentren`() {
        assertNull(SwipePathLogic.charAt(10f, 10f, emptyList()))
    }

    @Test
    fun `charAt ist case-insensitiv (liefert Kleinbuchstabe)`() {
        // KeyCenter mit Grossbuchstabe -> charAt liefert klein
        val cs = listOf(SwipePathLogic.KeyCenter('A', 30f, 10f))
        assertEquals('a', SwipePathLogic.charAt(30f, 10f, cs))
    }

    // ---------- pathLetters ----------

    @Test
    fun `pathLetters liefert die Buchstabenfolge bei Tastenwechsel ohne Wiederholung`() {
        // Route a -> b -> d (verweilt auf b)
        val samples = listOf(s(30f, 10f), s(90f, 10f), s(90f, 10f), s(90f, 70f))
        assertEquals("abd", SwipePathLogic.pathLetters(samples, centers()))
    }

    @Test
    fun `pathLetters ignoriert Samples ausserhalb aller Tasten`() {
        val samples = listOf(s(30f, 10f), s(500f, 500f), s(90f, 10f))
        assertEquals("ab", SwipePathLogic.pathLetters(samples, centers()))
    }

    @Test
    fun `pathLetters bei leerer Route liefert leer`() {
        assertEquals("", SwipePathLogic.pathLetters(emptyList(), centers()))
    }

    // ---------- previewNodes ----------

    @Test
    fun `previewNodes liefert die naechsten Buchstaben der Top-Vorschlaege`() {
        val sugs = listOf(
            SuggestionEngine.Suggestion("haus", 3.0),
            SuggestionEngine.Suggestion("hallo", 2.0),
            SuggestionEngine.Suggestion("hand", 1.0)
        )
        // typedLength 0 -> erste Buchstaben: h,h,h -> dedup -> [h]
        assertEquals(listOf('h'), SwipePathLogic.previewNodes(sugs, 0, 1))
        // typedLength 1 -> zweite Buchstaben: a,a,a -> [a]
        assertEquals(listOf('a'), SwipePathLogic.previewNodes(sugs, 1, 1))
    }

    @Test
    fun `previewNodes dedupliziert bei unterschiedlichen Folgebuchstaben`() {
        val sugs = listOf(
            SuggestionEngine.Suggestion("haus", 3.0),
            SuggestionEngine.Suggestion("hund", 2.0)
        )
        // typedLength 1 -> a,u -> [a,u]
        assertEquals(listOf('a', 'u'), SwipePathLogic.previewNodes(sugs, 1, 1))
    }

    @Test
    fun `previewNodes bei leerer Tiefe liefert leer`() {
        val sugs = listOf(SuggestionEngine.Suggestion("haus", 3.0))
        assertTrue(SwipePathLogic.previewNodes(sugs, 0, 0).isEmpty())
    }

    @Test
    fun `previewNodes bei zu kurzem Vorschlag bricht ab`() {
        val sugs = listOf(SuggestionEngine.Suggestion("h", 3.0))
        // typedLength 1 -> kein Buchstabe mehr
        assertTrue(SwipePathLogic.previewNodes(sugs, 1, 1).isEmpty())
    }

    // ---------- Passwort-Schutz (gespiegelt von TrailLogic.isTrailAllowed) ----------

    private fun editorInfo(inputType: Int, imeOptions: Int = 0) =
        android.view.inputmethod.EditorInfo().apply {
            this.inputType = inputType
            this.imeOptions = imeOptions
        }

    @Test
    fun `isSwipeAllowed erlaubt normales Textfeld`() {
        assertTrue(SwipePathLogic.isSwipeAllowed(editorInfo(android.text.InputType.TYPE_CLASS_TEXT)))
    }

    @Test
    fun `isSwipeAllowed verbietet Passwortfeld`() {
        assertFalse(SwipePathLogic.isSwipeAllowed(
            editorInfo(android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)))
    }

    @Test
    fun `isSwipeAllowed verbietet NO_PERSONALIZED_LEARNING`() {
        assertFalse(SwipePathLogic.isSwipeAllowed(
            editorInfo(android.text.InputType.TYPE_CLASS_TEXT,
                android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)))
    }

    @Test
    fun `isSwipeAllowed verbietet numerisches Passwortfeld`() {
        assertFalse(SwipePathLogic.isSwipeAllowed(
            editorInfo(android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD)))
    }

    @Test
    fun `isSwipeAllowed erlaubt null-EditorInfo`() {
        assertTrue(SwipePathLogic.isSwipeAllowed(null))
    }

    // ---------- isLikelyHit (grüner Trail bei Likely-Treffer während Swipe) ----------

    @Test
    fun `isLikelyHit wahr wenn Treffer unter den Likely-Knoten`() {
        assertTrue(SwipePathLogic.isLikelyHit(listOf('a', 'u'), 'a'))
        assertTrue(SwipePathLogic.isLikelyHit(listOf('a', 'u'), 'u'))
    }

    @Test
    fun `isLikelyHit falsch wenn Treffer nicht unter den Likely-Knoten`() {
        assertFalse(SwipePathLogic.isLikelyHit(listOf('a', 'u'), 'b'))
    }

    @Test
    fun `isLikelyHit case-insensitiv (Treffer als Grossbuchstabe)`() {
        assertTrue(SwipePathLogic.isLikelyHit(listOf('a'), 'A'))
    }

    @Test
    fun `isLikelyHit falsch bei leeren Likely-Knoten`() {
        assertFalse(SwipePathLogic.isLikelyHit(emptyList(), 'a'))
    }
}
