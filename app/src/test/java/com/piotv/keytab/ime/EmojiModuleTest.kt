package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spezifikation des Emoji-Katalogs ([EmojiModule]).
 *
 * Stand 2026-09-27: Die Keyword->Text-Matcher (`emojisFor`, `MAX_EMOJI`) sind mit
 * der Emoji-Suggestions-Funktion entfallen - die Suggestions haengten Emojis an
 * die Wortvorschlaege an. Uebrig bleibt der Katalog, den den ☺-Button anzeigt.
 * Getestet wird deshalb ausschliesslich der Katalog und sein Paging.
 */
class EmojiModuleTest {

    // ---------- Katalog (☺-Button) ----------

    @Test
    fun `Katalog enthaelt die erwarteten Basis-Symbole`() {
        for (e in listOf("😂", "❤️", "🐛", "🚀", "☕", "📁")) {
            assertTrue("$e fehlt im Katalog", EmojiModule.catalog.contains(e))
        }
    }

    @Test
    fun `Katalog ist dedupliziert`() {
        val all = EmojiModule.catalog
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `Katalog ist nach der Erweiterung auf mindestens 55 Symbole gewachsen`() {
        // Erweiterung 2026-09-27: 116 Keyword-Zuordnungen, nach distinct() 59
        // eindeutige Symbole (vorher waren es 25). Die Schwelle ist bewusst
        // unter dem Ist-Stand, damit sie eine Regression faengt statt den
        // exakten Wert zu wiederholen.
        assertTrue(
            "Katalog zu klein: ${EmojiModule.catalog.size}",
            EmojiModule.catalog.size >= 55
        )
        // 55 Symbole / 5 je Seite = 11 Seiten, also mindestens fuenf.
        assertTrue(EmojiModule.pageCount() >= 5)
    }

    // ---------- Katalog-Paging (Suggestion-Leiste) ----------

    @Test
    fun `Seitenlaenge ist 5 - passt zu den fuenf Slots der Leiste`() {
        assertEquals(5, EmojiModule.PAGE_SIZE)
    }

    @Test
    fun `Katalog-Seiten sind PAGE_SIZE-Bloecke in Katalog-Reihenfolge`() {
        val p0 = EmojiModule.page(0)
        val p1 = EmojiModule.page(1)
        assertEquals(EmojiModule.PAGE_SIZE, p0.size)
        assertEquals(EmojiModule.PAGE_SIZE, p1.size)
        assertEquals(p0 + p1, EmojiModule.catalog.take(EmojiModule.PAGE_SIZE * 2))
    }

    @Test
    fun `Katalog-letzte Seite kann kuerzer sein, danach leer`() {
        val pages = EmojiModule.pageCount()
        val last = EmojiModule.page(pages - 1)
        assertTrue(last.isNotEmpty() && last.size <= EmojiModule.PAGE_SIZE)
        assertTrue(EmojiModule.page(pages).isEmpty())
        assertTrue(EmojiModule.page(-1).isEmpty())
    }

    @Test
    fun `Katalog deckt alle Katalog-Emojis ohne Duplikate ab`() {
        val all = (0 until EmojiModule.pageCount()).flatMap { EmojiModule.page(it) }
        assertTrue(all.isNotEmpty())
        assertEquals(all.size, all.toSet().size)
        assertEquals(EmojiModule.catalog.size, all.size)
    }

    @Test
    fun `pageOf findet Emojis und liefert -1 fuer Fremdes`() {
        assertTrue(EmojiModule.pageOf("😂") in 0 until EmojiModule.pageCount())
        assertEquals(-1, EmojiModule.pageOf("🚫"))
    }
}
