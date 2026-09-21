package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reiner Swipe-Scorer: Teilfolge-Match, Score-Kombination, Auto-Commit-Schwelle.
 */
class SwipeScorerTest {

    private fun engine(vararg words: String) =
        SuggestionEngine(words.map { it to 1000 })

    // ---------- subsequenceMatch ----------

    @Test
    fun `subsequenceMatch erkennt Teilfolge mit Luecken`() {
        val (ok, gaps) = SwipeScorer.subsequenceMatch("haus", "hxaubcs")
        assertTrue(ok)
        assertEquals(3, gaps)
    }

    @Test
    fun `subsequenceMatch erkennt exakte Folge ohne Luecken`() {
        val (ok, gaps) = SwipeScorer.subsequenceMatch("ab", "ab")
        assertTrue(ok)
        assertEquals(0, gaps)
    }

    @Test
    fun `subsequenceMatch lehnt falsche Reihenfolge ab`() {
        val (ok, _) = SwipeScorer.subsequenceMatch("ba", "ab")
        assertTrue(!ok)
    }

    @Test
    fun `subsequenceMatch lehnt laengeres Wort als Route ab`() {
        val (ok, _) = SwipeScorer.subsequenceMatch("haus", "ha")
        assertTrue(!ok)
    }

    @Test
    fun `subsequenceMatch leeres Wort trifft immer`() {
        val (ok, gaps) = SwipeScorer.subsequenceMatch("", "abc")
        assertTrue(ok)
        assertEquals(0, gaps)
    }

    // ---------- score ----------

    @Test
    fun `score liefert leere Liste bei leerer Route`() {
        assertTrue(SwipeScorer.score("", engine("haus")).isEmpty())
    }

    @Test
    fun `score liefert leere Liste ohne Engine`() {
        assertTrue(SwipeScorer.score("haus", null).isEmpty())
    }

    @Test
    fun `score findet Kandidat dessen Buchstaben Teilfolge der Route sind`() {
        // Route h-a-u-s (direkt) -> "haus" ist ein perfekter Treffer
        val out = SwipeScorer.score("haus", engine("haus", "hallo", "hand"))
        assertEquals("haus", out.first().word)
    }

    @Test
    fun `score findet Kandidat trotz Luecken in der Route`() {
        // Route h-x-a-u-s -> "haus" ist Teilfolge mit Luecken
        val out = SwipeScorer.score("hxaus", engine("haus", "hallo"))
        assertTrue(out.any { it.word == "haus" })
    }

    @Test
    fun `score schliesst Kandidat aus dessen Reihenfolge nicht passt`() {
        // Route "s-u-a-h" -> "haus" ist keine Teilfolge
        val out = SwipeScorer.score("suah", engine("haus"))
        assertTrue(out.none { it.word == "haus" })
    }

    @Test
    fun `score respektiert den Startbuchstaben der Route`() {
        // Route beginnt auf 'h' -> "auto" (Start a) darf nicht treffer sein
        val out = SwipeScorer.score("haus", engine("haus", "auto"))
        assertTrue(out.none { it.word == "auto" })
    }

    @Test
    fun `score sortiert absteigend nach Score`() {
        val out = SwipeScorer.score("haus", engine("haus", "hallo", "hand"))
        val scores = out.map { it.score }
        for (i in 1 until scores.size) {
            assertTrue("scores muessen absteigend sein", scores[i - 1] >= scores[i])
        }
    }

    @Test
    fun `score begrenzt die Anzahl Kandidaten auf max`() {
        val eng = engine("ab", "abc", "abcd", "abcde", "abcdef")
        val out = SwipeScorer.score("abcdef", eng, max = 2)
        assertTrue(out.size <= 2)
    }

    // ---------- autoCommit ----------

    @Test
    fun `autoCommit liefert null bei leerer Kandidatenliste`() {
        assertNull(SwipeScorer.autoCommit(emptyList()))
    }

    @Test
    fun `autoCommit liefert null unter der Schwelle`() {
        assertNull(SwipeScorer.autoCommit(
            listOf(SwipeScorer.Candidate("haus", 0.5))))
    }

    @Test
    fun `autoCommit liefert Top bei klarer Dominanz`() {
        assertEquals("haus", SwipeScorer.autoCommit(
            listOf(SwipeScorer.Candidate("haus", 2.0),
                SwipeScorer.Candidate("hallo", 0.5))))
    }

    @Test
    fun `autoCommit liefert null wenn Zweitbeste zu nah an Top`() {
        assertNull(SwipeScorer.autoCommit(
            listOf(SwipeScorer.Candidate("haus", 2.0),
                SwipeScorer.Candidate("hallo", 1.8))))
    }

    // ---------- scoreWord ----------

    @Test
    fun `scoreWord liefert 0 bei Nicht-Teilfolge`() {
        val eng = engine("haus")
        assertEquals(0.0, SwipeScorer.scoreWord("haus", "xyz", eng, null), 0.0001)
    }

    @Test
    fun `scoreWord liefert 0 wenn Kandidat laenger als Route`() {
        val eng = engine("haus")
        assertEquals(0.0, SwipeScorer.scoreWord("haus", "ha", eng, null), 0.0001)
    }
}
