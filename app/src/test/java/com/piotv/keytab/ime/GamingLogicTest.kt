package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reine Gaming-Logik: nächstes Zeichen + Vervollständigungs-Erkennung. */
class GamingLogicTest {

    private fun sug(word: String, score: Double = 1.0) =
        SuggestionEngine.Suggestion(word, score)

    @Test
    fun `nextChar ist erster Buchstabe des Top-Vorschlags bei leerem Tippstand`() {
        assertEquals("h", GamingLogic.nextChar(listOf(sug("hallo")), 0)?.toString())
    }

    @Test
    fun `nextChar folgt dem getippten Teilwort`() {
        assertEquals("l", GamingLogic.nextChar(listOf(sug("hello")), 3)?.toString())
    }

    @Test
    fun `nextChar null ohne Vorschlaege oder am Wortende`() {
        assertNull(GamingLogic.nextChar(emptyList(), 0))
        assertNull(GamingLogic.nextChar(listOf(sug("hi")), 2))
    }

    @Test
    fun `nextChar liefert Kleinbuchstaben auch bei Grossschreibung`() {
        assertEquals("h", GamingLogic.nextChar(listOf(sug("Hallo")), 0)?.toString())
        assertEquals("p", GamingLogic.nextChar(listOf(sug("Apfel")), 1)?.toString())
    }

    @Test
    fun `completed wenn getipptes Wort dem Top-Vorschlag entspricht`() {
        assertTrue(GamingLogic.completed(listOf(sug("hello")), "hello"))
        assertTrue(GamingLogic.completed(listOf(sug("hello")), "Hello"))
        assertFalse(GamingLogic.completed(listOf(sug("hello")), "hell"))
        assertFalse(GamingLogic.completed(emptyList(), "hello"))
        assertFalse(GamingLogic.completed(listOf(sug("hello")), ""))
    }
}