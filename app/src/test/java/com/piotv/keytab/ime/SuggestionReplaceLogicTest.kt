package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine Ersetzungs-Logik für die Vorschlags-Übernahme: Entscheidung
 * (ersetzen / mit Trenner anhängen / einfach einfügen), normalisierter
 * Wortvergleich und die Lösch-Verifikation.
 */
class SuggestionReplaceLogicTest {

    // ---------- sameWord ----------

    @Test
    fun `sameWord ignoriert Gross- und Kleinschreibung`() {
        assertTrue(SuggestionReplaceLogic.sameWord("Hauss", "hauss"))
    }

    @Test
    fun `sameWord erkennt zusammengesetzte Umlaute als gleich (NFC)`() {
        // "a" + kombinierender Umlaut (NFD) vs. vorkomponiertes "ä" (NFC)
        val nfd = "a\u0308"
        val nfc = "\u00E4"
        assertTrue(SuggestionReplaceLogic.sameWord(nfd, nfc))
    }

    @Test
    fun `sameWord ist falsch fuer verschiedene Woerter`() {
        assertFalse(SuggestionReplaceLogic.sameWord("haus", "hauss"))
    }

    // ---------- endsWith ----------

    @Test
    fun `endsWith erkennt Suffix normalisiert und case-insensitiv`() {
        assertTrue(SuggestionReplaceLogic.endsWith("Ich hauss", "Hauss"))
        assertTrue(SuggestionReplaceLogic.endsWith("hauss", "hauss"))
    }

    @Test
    fun `endsWith ist falsch bei zu kurzem Text oder anderem Suffix`() {
        assertFalse(SuggestionReplaceLogic.endsWith("hau", "hauss"))
        assertFalse(SuggestionReplaceLogic.endsWith("Ich hallo", "hauss"))
    }

    // ---------- action ----------

    @Test
    fun `action ohne getipptes Wort fuegt einfach ein`() {
        assertEquals(SuggestionReplaceLogic.Action.INSERT_PLAIN,
            SuggestionReplaceLogic.action("", "irgendwas"))
    }

    @Test
    fun `action ohne lesbaren Kontext ersetzt (nie anhaengen)`() {
        // Kern des Verdopplungs-Bugs: unlesbares Feld darf NICHT zum Anhängen führen.
        assertEquals(SuggestionReplaceLogic.Action.REPLACE_TYPED,
            SuggestionReplaceLogic.action("hauss", ""))
    }

    @Test
    fun `action ersetzt wenn genau das getippte Wort davor steht`() {
        assertEquals(SuggestionReplaceLogic.Action.REPLACE_TYPED,
            SuggestionReplaceLogic.action("hauss", "hauss"))
        assertEquals(SuggestionReplaceLogic.Action.REPLACE_TYPED,
            SuggestionReplaceLogic.action("hauss", "Hauss"))
    }

    @Test
    fun `action ersetzt trotz Unicode-Normalisierung des Feldes`() {
        // NFD im Feld, NFC getippt → frueher tailMatches=false → Anhaengen.
        assertEquals(SuggestionReplaceLogic.Action.REPLACE_TYPED,
            SuggestionReplaceLogic.action("h\u00E4user", "ha\u0308user"))
    }

    @Test
    fun `action haengt nur an wenn exakt gleich lang aber ein anderes Wort`() {
        assertEquals(SuggestionReplaceLogic.Action.INSERT_WITH_SEPARATOR,
            SuggestionReplaceLogic.action("hauss", "hallo"))
    }

    @Test
    fun `action ersetzt wenn der Kontext kuerzer ist als das Wort`() {
        assertEquals(SuggestionReplaceLogic.Action.REPLACE_TYPED,
            SuggestionReplaceLogic.action("hauss", "ha"))
    }

    // ---------- deleteWorked ----------

    @Test
    fun `deleteWorked true wenn sich der Kontext veraendert hat`() {
        assertTrue(SuggestionReplaceLogic.deleteWorked("hauss", "Ich g"))
    }

    @Test
    fun `deleteWorked false wenn der Kontext unveraendert ist`() {
        // Feld ignoriert deleteSurroundingText → Kontext bleibt identisch.
        assertFalse(SuggestionReplaceLogic.deleteWorked("hauss", "hauss"))
    }

    // ---------- rawWordLength (Lösch-Menge in Roh-Zeichen) ----------

    @Test
    fun `rawWordLength liefert die Wortlaenge bei gleicher Repraesentation`() {
        assertEquals(5, SuggestionReplaceLogic.rawWordLength("hauss", "hauss"))
        assertEquals(5, SuggestionReplaceLogic.rawWordLength("Ich hauss", "hauss"))
    }

    @Test
    fun `rawWordLength erkennt NFD-Umlaute als laenger`() {
        val nfd = "ha\u0308user"   // "häuser" in NFD = 7 Zeichen
        val nfc = "h\u00E4user"    // "häuser" in NFC  = 6 Zeichen
        assertEquals(7, SuggestionReplaceLogic.rawWordLength(nfd, nfc))
    }

    @Test
    fun `rawWordLength ist null wenn kein Suffix passt`() {
        assertNull(SuggestionReplaceLogic.rawWordLength("hallo", "hauss"))
        assertNull(SuggestionReplaceLogic.rawWordLength("ha", "hauss"))
    }

    @Test
    fun `rawWordLength ist null bei leerem getipptem Wort`() {
        assertNull(SuggestionReplaceLogic.rawWordLength("irgendwas", ""))
    }
}
