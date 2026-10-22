package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LetterCaseRenderer: Schreibweise des Hauptbuchstabens und Sonderzeichen-Reihe
 * (Android-frei, reine JUnit-Suite).
 *
 * Vertrag seit v0.20: die Reihe hängt **nur** an der Basistaste — Shift/Caps
 * ändern ausschließlich den Hauptbuchstaben. Vorher lieferte der Shift-Zustand
 * die Großbuchstaben-Reihe (`extras['A']`), während Tasten ohne
 * Großbuchstaben-Eintrag auf die Kleinbuchstaben-Reihe zurückfielen: dieselbe
 * Taste zeigte also je nach Shift etwas anderes.
 */
class LetterCaseRendererTest {

    private val extras = mapOf(
        'a' to listOf("ä", "à", "á"),
        'm' to listOf("µ", "&"),
        'A' to listOf("Ä", "À")
    )

    @Test
    fun `Kleinbuchstabe bleibt klein, Shift macht gross`() {
        assertEquals('a', LetterCaseRenderer.letterFor('a', upper = false))
        assertEquals('A', LetterCaseRenderer.letterFor('a', upper = true))
    }

    @Test
    fun `Umlaut-Taste wird mit Shift gross`() {
        assertEquals('ä', LetterCaseRenderer.letterFor('ä', upper = false))
        assertEquals('Ä', LetterCaseRenderer.letterFor('ä', upper = true))
    }

    @Test
    fun `Eszett mit Shift wird zu ẞ (Sonderfall ohne uppercaseChar)`() {
        assertEquals('\u00DF', LetterCaseRenderer.letterFor('\u00DF', upper = false))
        assertEquals('\u1E9E', LetterCaseRenderer.letterFor('\u00DF', upper = true))
    }

    @Test
    fun `Reihe kommt von der Basistaste - ein Grossbuchstaben-Eintrag aendert sie nicht`() {
        val row = LetterCaseRenderer.extrasFor('a', extras)
        assertEquals(listOf("ä", "à", "á"), row)
        assertTrue("kein Großbuchstaben-Zeichen in der Reihe (ist $row)",
            row.none { it == "Ä" || it == "À" })
    }

    @Test
    fun `Grossbuchstaben-Eintrag gilt nur fuer eine eigene Taste`() {
        // Layout-Tasten sind Kleinbuchstaben; ein 'A'-Eintrag wäre nur an einer
        // echten 'A'-Taste wirksam (die es im Layout nicht gibt).
        assertEquals(listOf("Ä", "À"), LetterCaseRenderer.extrasFor('A', extras))
    }

    @Test
    fun `Taste ohne Eintrag liefert eine leere Reihe`() {
        assertTrue(LetterCaseRenderer.extrasFor('ü', extras).isEmpty())
        assertTrue(LetterCaseRenderer.extrasFor('x', emptyMap()).isEmpty())
    }

    @Test
    fun `Alle Layout-Tasten liefern eine Reihe - nur die Umlaute bleiben leer`() {
        val real = Languages.de.letterExtras(Languages.basePunctuation)
        val ohneReihe = "qwertzuiopüasdfghjklöäyxcvbnm"
            .filter { LetterCaseRenderer.extrasFor(it, real).isEmpty() }
        assertEquals("nur die Umlaut-Tasten tragen ihren Akzent selbst", "üöä", ohneReihe)
        assertTrue("q braucht eine Reihe (v0.20)",
            LetterCaseRenderer.extrasFor('q', real).contains("@"))
        assertTrue("m braucht eine Reihe (v0.20)",
            LetterCaseRenderer.extrasFor('m', real).contains("µ"))
    }
}
