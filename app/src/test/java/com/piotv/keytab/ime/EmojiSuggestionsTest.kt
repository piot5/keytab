package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Emoji-Gating in [SuggestionEngine.suggest]: Default **aus** (Rückwärts-
 * kompatibel), an → Emojis hängen hinten an und Wortschläge behalten mind.
 * einen Slot.
 */
class EmojiSuggestionsTest {

    /** Minimale Basis: genug Wörter für stabile Wortvorschläge. */
    private fun engine(): SuggestionEngine = SuggestionEngine(
        listOf("haus" to 100, "hund" to 90, "himmel" to 80, "hilfe" to 70,
               "hallo" to 60, "hoffnung" to 50, "handy" to 40, "herz" to 30,
               "herzlich" to 25)
    )

    @Test
    fun `Default ist aus - Vorschläge unverändert`() {
        val e = engine()
        val withEmoji = e.suggest("h", null)
        assertEquals(3, withEmoji.size)
        assertTrue(withEmoji.all { it.word.all { c -> c.isLetter() } })
    }

    @Test
    fun `emoji an - Emojis haengen hinten, Wörter bleiben vorn`() {
        val e = engine()
        e.emojiEnabled = true
        val list = e.suggest("herz", null)
        assertTrue("mind. 1 Wortvorschlag bleibt", list.first().word.all { it.isLetter() })
        assertEquals(listOf("❤️", "💕"), list.takeLast(2).map { it.word })
    }

    @Test
    fun `emoji an - max 2 Emoji-Slots bei 3 Vorschlaegen`() {
        val e = engine()
        e.emojiEnabled = true
        // "herz" matcht 1 Thema (❤️, 💕) → 2 Emojis + 1 Wortvorschlag ("herzlich")
        val list = e.suggest("herz", null)
        assertEquals(3, list.size)
        assertEquals(2, list.count { !it.word.all { c -> c.isLetter() } })
        assertEquals("herzlich", list.first().word)
    }

    @Test
    fun `leere Wortbasis - Emojis füllen die Leiste`() {
        // Basis ohne "herz*"-Präfix: completeWord liefert nichts (nur das exakte
        // Wort würde matchen und es ist nicht im Korpus) → Emojis füllen allein.
        val e = SuggestionEngine(listOf("haus" to 100, "hund" to 90, "himmel" to 80))
        e.emojiEnabled = true
        val list = e.suggest("herz", null)
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { !it.word.all { c -> c.isLetter() } })
        assertEquals(listOf("❤️", "💕"), list.map { it.word })
    }

    @Test
    fun `kein Treffer - Vorschläge ohne Emojis unverändert`() {
        val e = engine()
        e.emojiEnabled = true
        val list = e.suggest("xyz", null) // kein Keyword-Match
        assertTrue(list.all { it.word.all { c -> c.isLetter() } })
    }

    @Test
    fun `Leerraum-Fall nutzt prevWord als Emoji-Schlüssel`() {
        val e = engine()
        e.emojiEnabled = true
        // Nach "liebe" + Space: prev = "liebe" → ❤️/💕 als letzte Vorschläge
        val list = e.suggest("", "liebe")
        assertEquals(3, list.size)
        assertEquals(listOf("❤️", "💕"), list.takeLast(2).map { it.word })
    }

    @Test
    fun `emoji ohne Basiswörter - prev null, leer bleibt leer`() {
        val e = SuggestionEngine(emptyList())
        e.emojiEnabled = true
        assertTrue(e.suggest("", null).isEmpty())
    }
}
