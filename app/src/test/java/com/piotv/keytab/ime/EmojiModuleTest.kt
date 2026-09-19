package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spezifikation des Emoji-Keyword-Katalogs ([EmojiModule]). */
class EmojiModuleTest {

    @Test
    fun `Haeufige Keywords matchen - herzförmig bei liebe`() {
        val e = EmojiModule.emojisFor("liebe")
        assertTrue(e.contains("❤️"))
    }

    @Test
    fun `Lach-Thema liefert 2 Emojis in Katalog-Reihenfolge`() {
        assertEquals(listOf("😂", "😆"), EmojiModule.emojisFor("lachen"))
    }

    @Test
    fun `gross-kleinschreibung egal`() {
        assertEquals(EmojiModule.emojisFor("LACH"), EmojiModule.emojisFor("lach"))
    }

    @Test
    fun `unbekanntes Wort liefert keine Emojis`() {
        assertTrue(EmojiModule.emojisFor("xyzq").isEmpty())
    }

    @Test
    fun `leeres Wort oder max 0 liefert leer`() {
        assertTrue(EmojiModule.emojisFor("").isEmpty())
        assertTrue(EmojiModule.emojisFor("lach", max = 0).isEmpty())
    }

    @Test
    fun `max begrenzt und dedupliziert`() {
        // "lachen" matcht "lach" (😂, 😆) — mit max=1 nur 😂
        assertEquals(listOf("😂"), EmojiModule.emojisFor("lachen", max = 1))
        // ok + check duplizieren ✅ nicht
        val ok = EmojiModule.emojisFor("check", max = 5)
        assertEquals(ok.size, ok.toSet().size)
    }

    @Test
    fun `Dev-Thema - bug liefert raupen-emoticon, build Rakete`() {
        assertTrue(EmojiModule.emojisFor("bugfix").contains("🐛"))
        assertTrue(EmojiModule.emojisFor("rebuild").contains("🚀"))
    }

    // ---------- Katalog-Paging (Suggestion-Leiste, 😀-Button) ----------

    @Test
    fun `Katalog-Seiten sind 3er-Blöcke in Katalog-Reihenfolge`() {
        val p0 = EmojiModule.page(0)
        val p1 = EmojiModule.page(1)
        assertEquals(3, p0.size)
        assertEquals(3, p1.size)
        assertEquals(p0 + p1, EmojiModule.catalog.take(6))
    }

    @Test
    fun `Katalog-letzte Seite kann kürzer sein, danach leer`() {
        val pages = EmojiModule.pageCount()
        val last = EmojiModule.page(pages - 1)
        assertTrue(last.isNotEmpty() && last.size <= 3)
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
    fun `pageOf findet Emojis und liefert -1 für Fremdes`() {
        assertTrue(EmojiModule.pageOf("😂") in 0 until EmojiModule.pageCount())
        assertEquals(-1, EmojiModule.pageOf("🚫"))
        // 💻 ("code"-Thema) liegt alphabetisch nach den ersten 3 Keywords → nicht auf Seite 0
        assertTrue(EmojiModule.pageOf("💻") >= 1)
    }
}
