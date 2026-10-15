package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reine Likely-Highlighting-Logik: das wahrscheinlichste nächste Zeichen. */
class LikelyHighlightLogicTest {

    private fun sug(word: String, score: Double = 1.0) =
        SuggestionEngine.Suggestion(word, score)

    @Test
    fun `nextChar ist erster Buchstabe des Top-Vorschlags bei leerem Tippstand`() {
        assertEquals("h", LikelyHighlightLogic.nextChar(listOf(sug("hallo")), 0)?.toString())
    }

    @Test
    fun `nextChar folgt dem getippten Teilwort`() {
        assertEquals("l", LikelyHighlightLogic.nextChar(listOf(sug("hello")), 3)?.toString())
    }

    @Test
    fun `nextChar null ohne Vorschlaege oder am Wortende`() {
        assertNull(LikelyHighlightLogic.nextChar(emptyList(), 0))
        assertNull(LikelyHighlightLogic.nextChar(listOf(sug("hi")), 2))
    }

    @Test
    fun `nextChar liefert Kleinbuchstaben auch bei Grossschreibung`() {
        assertEquals("h", LikelyHighlightLogic.nextChar(listOf(sug("Hallo")), 0)?.toString())
        assertEquals("p", LikelyHighlightLogic.nextChar(listOf(sug("Apfel")), 1)?.toString())
    }
}
