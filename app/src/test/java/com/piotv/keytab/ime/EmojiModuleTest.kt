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
}
