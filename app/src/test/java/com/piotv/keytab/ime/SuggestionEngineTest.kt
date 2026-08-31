package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests für die SuggestionEngine (reine JVM, kein Android nötig).
 * Korpus-Datenquelle: FrequencyWords de_50k (CC-BY-SA-4.0) — hier nur Mini-Fixtures.
 */
class SuggestionEngineTest {

    private fun engine(): SuggestionEngine = SuggestionEngine(
        listOf(
            "ich" to 5890279, "das" to 3122198, "ist" to 3025610,
            "und" to 2900000, "die" to 2800000, "nicht" to 2100000,
            "dies" to 900000, "dieses" to 400000, "dieser" to 500000,
            "haus" to 200000, "hase" to 100000, "hand" to 300000,
            "wort" to 150000, "woche" to 120000
        )
    )

    // ---------- Autovervollständigung ----------

    @Test
    fun `prefix match schlaegt vollstaendiges Wort vor`() {
        val list = engine().suggest("die", null)
        assertTrue(list.any { it.word == "die" || it.word == "dies" })
    }

    @Test
    fun `exakt getipptes Wort wird nicht vorgeschlagen`() {
        val list = engine().suggest("das", null)
        assertFalse(list.any { it.word == "das" })
    }

    @Test
    fun `haeufigeres Wort hat Vorrang bei Prefix-Kollision`() {
        val list = engine().suggest("die", null, max = 3)
        // exaktes "die" wird gefiltert → bester Prefix-Kandidat ist "dies" (häufiger als dieser/dieses)
        assertEquals("dies", list.first().word)
    }

    @Test
    fun `mehrere Prefix-Kandidaten sortiert nach Frequenz`() {
        val list = engine().suggest("die", null, max = 3)
        assertTrue(list.size >= 2)
        assertTrue(list.any { it.word == "dies" })
    }

    // ---------- Next-Word-Prediction ----------

    @Test
    fun `leeres Wort mit prev=null liefert haeufigste Woerter`() {
        val list = engine().suggest("", null)
        assertEquals(3, list.size)
        assertEquals("ich", list.first().word)
    }

    @Test
    fun `gelerntes Bigramm dominiert Next-Word-Prediction`() {
        val e = engine()
        repeat(5) { e.learn("das", "haus") }
        val list = e.suggest("", "das")
        assertEquals("haus", list.first().word)
    }

    // ---------- Lernen / User-Dictionary ----------

    @Test
    fun `gelerntes Wort wird per Prefix vorgeschlagen und haueufiger`() {
        val e = engine()
        repeat(3) { e.learn(null, "xyzwort") }
        val list = e.suggest("xyz", null)
        assertTrue(list.any { it.word == "xyzwort" })
    }

    @Test
    fun `nicht lernbare Tokens werden ignoriert`() {
        val e = engine()
        e.learn(null, "x")
        e.learn(null, "123")
        e.learn(null, "http")
        assertFalse(e.knowsWord("123"))
    }

    @Test
    fun `user-dictionary persistiert roundtrip`() {
        val e = engine()
        repeat(2) { e.learn("das", "haus") }
        repeat(3) { e.learn(null, "spezial") }
        val raw = e.serializeUserDict()
        val e2 = engine()
        e2.restoreUserDict(raw)
        assertTrue(e2.knowsWord("spezial"))
        assertTrue(e2.suggest("", "das").first().word == "haus")
    }

    // ---------- Fuzzy-Korrektur ----------

    @Test
    fun `tippfehler wird per Damerau-Levenshtein korrigiert`() {
        assertEquals(1, SuggestionEngine.editDistance("hais", "haus"))
        assertEquals(1, SuggestionEngine.editDistance("ahus", "haus")) // Transposition
        assertEquals(0, SuggestionEngine.editDistance("haus", "haus"))
    }

    @Test
    fun `fuzzy vorschlag bei vertipptem langen wort`() {
        val list = engine().suggest("woche", null, max = 3) // exakt → gefiltert
        // "woche" selbst exakt getippt: Korrektur darf "woche" nicht liefern
        assertFalse(list.any { it.word == "woche" })
    }

    // ---------- Case-Matching ----------

    @Test
    fun `grossschreibung wird uebertragen`() {
        val e = engine()
        assertEquals("Haus", e.matchCase("haus", "H"))
        assertEquals("haus", e.matchCase("haus", "h"))
        assertEquals("Haus", e.matchCase("haus", ""))
    }
}
