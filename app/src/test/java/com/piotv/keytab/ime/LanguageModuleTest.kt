package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LanguageModule: Sprachauswahl, Fallback und Akzent-Merging (rein, kein Android).
 */
class LanguageModuleTest {

    @Test
    fun `byCode findet alle sieben Sprachen`() {
        for (code in listOf("de", "en", "es", "fr", "it", "pt", "nl")) {
            assertEquals(code, Languages.byCode(code).code)
        }
    }

    @Test
    fun `byCode faellt auf Deutsch zurueck`() {
        assertEquals("de", Languages.byCode(null).code)
        assertEquals("de", Languages.byCode("xx").code)
        assertEquals("de", Languages.byCode("").code)
        assertEquals("de", Languages.byCode("zh").code)
    }

    @Test
    fun `all enthaelt genau die sieben lateinischen Sprachen ohne Duplikate`() {
        val codes = Languages.all.map { it.code }
        assertEquals(listOf("de", "en", "es", "fr", "it", "pt", "nl"), codes)
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `Metadaten sind vollstaendig (Name, Asset, txt-Format)`() {
        for (lang in Languages.all) {
            assertTrue("${lang.code}: Anzeigename fehlt", lang.displayName.isNotBlank())
            assertTrue("${lang.code}: Asset muss txt sein",
                lang.assetName.endsWith(".txt"))
            assertTrue("${lang.code}: Asset enthaelt Code",
                lang.assetName.startsWith(lang.code))
        }
    }

    @Test
    fun `deutsch enthaelt Umlaute und Esszett`() {
        assertTrue(Languages.de.extras['a']?.contains("ä") == true)
        assertTrue(Languages.de.extras['o']?.contains("ö") == true)
        assertTrue(Languages.de.extras['u']?.contains("ü") == true)
        assertTrue(Languages.de.extras['s']?.contains("ß") == true)
        assertTrue(Languages.de.extras['A']?.contains("Ä") == true)
    }

    @Test
    fun `letterExtras merged Basis-Interpunktion mit Akzenten ohne Duplikate`() {
        val merged = Languages.de.letterExtras(Languages.basePunctuation)
        // Basis bleibt wo keine Akzente: 'r' nur in basePunctuation
        assertEquals(Languages.basePunctuation['r'], merged['r'])
        // Akzente kommen dazu: 'a' nur in extras
        assertEquals(Languages.de.extras['a'], merged['a'])
        // Keine Duplikate bei Überlappung
        val lang = KeyboardLanguage("t", "T", "t.txt",
            mapOf('r' to listOf(".", "NEU")))
        val m = lang.letterExtras(Languages.basePunctuation)
        assertEquals(listOf(".", ",", "NEU"), m['r'])
        assertEquals(m['r']!!.size, m['r']!!.toSet().size)
    }

    @Test
    fun `basePunctuation deckt Euro und Grundzeichen ab`() {
        val p = Languages.basePunctuation
        assertTrue(p.isNotEmpty())
        assertTrue(p['y']?.contains("€") == true)
        assertTrue(p[',']?.contains("?") == true)
    }
}
