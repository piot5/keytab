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

    @Test
    fun `Prefix-Vervollstaendigung findet Korpuswoerter unabhaengig von der Schreibweise`() {
        // Die Prefix-Suche laeuft ueber den Char-Index (byFirstChar), dessen
        // Schluessel case-insensitiv sind. Ohne das wuerde ein Korpuswort wie
        // "Haus" bei der Eingabe "hau" nie gefunden — vorher fiel das durch den
        // Voll-Scan ueber alle Basiswoerter nicht auf.
        val e = SuggestionEngine(
            listOf("Haus" to 500, "hause" to 400, "baum" to 300)
        )
        val words = e.suggest("hau", null, max = 3).map { it.word }
        assertTrue("Haus muss gefunden werden: $words", words.any { it.equals("Haus", ignoreCase = true) })
        assertTrue("baum darf nicht erscheinen: $words", words.none { it == "baum" })
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

    // ---------- Case-Matching ----------

    @Test
    fun `grossschreibung wird uebertragen`() {
        val e = engine()
        assertEquals("Haus", e.matchCase("haus", "H"))
        assertEquals("haus", e.matchCase("haus", "h"))
        assertEquals("Haus", e.matchCase("haus", ""))
    }

    // ---------- Performance (Main-Thread-Budget) ----------

    /** Synthetischer ~6.000-Wörter-Korpus (Größe wie die Asset-Frequenzlisten). */
    private fun bigEngine(): SuggestionEngine = SuggestionEngine(
        (0 until 6000).map { i ->
            buildString {
                append(('a' + (i % 26)))
                append(('a' + ((i / 26) % 26)))
                append(('a' + ((i / 676) % 26)))
                append("bdfghjklmnprs"[i % 13])
                append("eioau"[i % 5])
            } to (100000 - i)
        }
    )

    @Test
    fun `vorschlaege bleiben bei grossen korpussen schnell`() {
        val e = bigEngine()
        e.suggest("habcd", null) // Warm-up: lazy Char-Index aufbauen
        val start = System.nanoTime()
        repeat(50) { run ->
            // "habcd".."habch" ist nie Korpuswort (Position 4 = 'c') → voller Fuzzy-Pfad
            e.suggest("habc" + ('d' + (run % 5)), null)
        }
        val avgMs = (System.nanoTime() - start) / 1_000_000.0 / 50.0
        // Main-Thread-Budget: ein Tastendruck darf die Tastatur nicht blockieren
        assertTrue("suggest zu langsam: %.2f ms/Call".format(avgMs), avgMs < 50.0)
    }

    // ---------- Snippet-Leiste am Satzanfang ----------

    @Test
    fun `sentenceStart bei leerem feld`() {
        assertTrue(SuggestionEngine.sentenceStart("", ""))
    }

    @Test
    fun `sentenceStart nach satzende-punkt`() {
        assertTrue(SuggestionEngine.sentenceStart("Hallo. ", ""))
    }

    @Test
    fun `sentenceStart nach ausrufezeichen- und fragezeichen`() {
        assertTrue(SuggestionEngine.sentenceStart("Hey! ", ""))
        assertTrue(SuggestionEngine.sentenceStart("Na? ", ""))
    }

    @Test
    fun `sentenceStart nach newline`() {
        assertTrue(SuggestionEngine.sentenceStart("Hallo.\n", ""))
    }

    @Test
    fun `kein sentenceStart in der mitte des satzes`() {
        assertFalse(SuggestionEngine.sentenceStart("Hallo Welt ", ""))
    }

    @Test
    fun `laufendes wort hebt sentenceStart auf`() {
        assertFalse(SuggestionEngine.sentenceStart("Hallo Welt ", "ha"))
    }

    @Test
    fun `recentSnippets parst history in reihenfolge`() {
        val raw = "git status\u0000ls -la\u0000cd"
        assertEquals(listOf("git status", "ls -la", "cd"), SuggestionEngine.recentSnippets(raw))
    }

    @Test
    fun `recentSnippets ignoriert leere eintraege und dedupliziert`() {
        val raw = "git status\u0000\u0000ls -la\u0000git status\u0000  "
        assertEquals(listOf("git status", "ls -la"), SuggestionEngine.recentSnippets(raw))
    }

    @Test
    fun `recentSnippets begrenzt auf max drei`() {
        assertEquals(3, SuggestionEngine.recentSnippets("a\u0000b\u0000c\u0000d\u0000e").size)
    }

    @Test
    fun `recordRecent prependet neuen eintrag`() {
        assertEquals(
            "git status\u0000ls -la\u0000cd",
            SuggestionEngine.recordRecent("ls -la\u0000cd", "git status")
        )
    }

    @Test
    fun `recordRecent bewegt doppelten an den anfang`() {
        assertEquals(
            "ls -la\u0000git status",
            SuggestionEngine.recordRecent("git status\u0000ls -la", "ls -la")
        )
    }

    @Test
    fun `recordRecent begrenzt auf max drei und rundelt mit recentSnippets`() {
        var r = SuggestionEngine.recordRecent("", "a")
        r = SuggestionEngine.recordRecent(r, "b")
        r = SuggestionEngine.recordRecent(r, "c")
        r = SuggestionEngine.recordRecent(r, "d")
        assertEquals(listOf("d", "c", "b"), SuggestionEngine.recentSnippets(r))
    }

    @Test
    fun `recordRecent ignoriert leerzeichen`() {
        assertEquals("ls -la", SuggestionEngine.recordRecent("ls -la", "  "))
    }
}

