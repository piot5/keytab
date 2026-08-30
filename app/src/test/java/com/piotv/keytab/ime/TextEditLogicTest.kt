package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class TextEditLogicTest {

    // ---------- wordStartIndex / wordDeleteCount ----------

    @Test
    fun `Wortanfang des letzten Worts`() {
        assertEquals(6, TextEditLogic.wordStartIndex("hello world", 11))
    }

    @Test
    fun `Trailing Whitespace wird uebersprungen`() {
        // "hello world " → Cursor nach dem Leerzeichen: Wortanfang von "world"
        assertEquals(6, TextEditLogic.wordStartIndex("hello world ", 12))
    }

    @Test
    fun `Nur Whitespace vor Cursor - loescht alles inkl Wort davor`() {
        // "ab   " cursor=5: skip ws → 2, skip non-ws → 0
        assertEquals(0, TextEditLogic.wordStartIndex("ab   ", 5))
    }

    @Test
    fun `Ein Wort ohne Vorgaenger`() {
        assertEquals(0, TextEditLogic.wordStartIndex("abc", 3))
        assertEquals(3, TextEditLogic.wordDeleteCount("abc", 3))
    }

    @Test
    fun `Leerer Text`() {
        assertEquals(0, TextEditLogic.wordStartIndex("", 0))
        assertEquals(0, TextEditLogic.wordDeleteCount("", 0))
    }

    @Test
    fun `DeleteCount entspricht cursor minus Wortanfang`() {
        assertEquals(5, TextEditLogic.wordDeleteCount("hello world", 11))
    }

    @Test
    fun `Cursor mitten im Wort`() {
        assertEquals(6, TextEditLogic.wordStartIndex("hello world", 9))
        assertEquals(3, TextEditLogic.wordDeleteCount("hello world", 9))
    }

    @Test
    fun `Cursor wird auf gueltigen Bereich begrenzt`() {
        assertEquals(0, TextEditLogic.wordStartIndex("ab", 99))
        assertEquals(0, TextEditLogic.wordStartIndex("ab", -1))
    }

    @Test
    fun `Tabs und Newlines zaehlen als Whitespace`() {
        // Cursor am Ende: Whitespace (\t\n ) wird komplett übersprungen → Wortanfang "wort"
        assertEquals(0, TextEditLogic.wordStartIndex("wort\t\n ", 7))
        assertEquals(7, TextEditLogic.wordDeleteCount("wort\t\n ", 7))
    }

    // ---------- formatSize ----------

    @Test
    fun `Bytes unter 1 KB`() {
        assertEquals("0 B", TextEditLogic.formatSize(0))
        assertEquals("512 B", TextEditLogic.formatSize(512))
        assertEquals("1023 B", TextEditLogic.formatSize(1023))
    }

    @Test
    fun `Kilobytes`() {
        assertEquals("1 KB", TextEditLogic.formatSize(1024))
        assertEquals("10 KB", TextEditLogic.formatSize(10 * 1024))
    }

    @Test
    fun `Megabytes mit einer Nachkommastelle`() {
        assertEquals("1.0 MB", TextEditLogic.formatSize(1024 * 1024))
        assertEquals("1.5 MB", TextEditLogic.formatSize((1.5 * 1024 * 1024).toLong()))
    }

    // ---------- Clip-History Encoding ----------

    @Test
    fun `Roundtrip erhält Reihenfolge und Inhalte`() {
        val entries = listOf("erstens", "zweitens", "mit\nZeilenumbruch", "")
        val decoded = TextEditLogic.decodeClipHistory(TextEditLogic.encodeClipHistory(entries))
        assertEquals(listOf("erstens", "zweitens", "mit\nZeilenumbruch"), decoded)
    }

    @Test
    fun `Leere Liste ergibt leeren String und zurueck`() {
        val encoded = TextEditLogic.encodeClipHistory(emptyList())
        assertEquals("", encoded)
        assertEquals(emptyList<String>(), TextEditLogic.decodeClipHistory(encoded))
    }

    // ---------- clipDisplayText ----------

    @Test
    fun `Kurzer Text bleibt unveraendert ohne Newlines`() {
        assertEquals("hallo welt", TextEditLogic.clipDisplayText("hallo\nwelt".replace("\n", " ")))
    }

    @Test
    fun `Newlines werden durch Leerzeichen ersetzt`() {
        assertEquals("a b", TextEditLogic.clipDisplayText("a\nb"))
    }

    @Test
    fun `Langer Text wird gekuerzt mit Ellipse`() {
        val s = "x".repeat(100)
        val shown = TextEditLogic.clipDisplayText(s)
        assertEquals(81, shown.length)
        assertEquals("…", shown.last().toString())
    }
}