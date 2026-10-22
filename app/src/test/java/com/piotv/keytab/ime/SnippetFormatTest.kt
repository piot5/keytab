package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Test

/** Roundtrip + Kommentar-Erkennung der Snippet-Escape-Logik (G10). */
class SnippetFormatTest {

    @Test
    fun `escape und unescape sind invers`() {
        val input = "echo \"#1\" \\ #kommentar\nzeile2"
        assertEquals(input, SnippetFormat.unescapeValue(SnippetFormat.escapeValue(input)))
    }

    @Test
    fun `Hash wird escaped und als Text zurueckgelesen`() {
        assertEquals("\\#", SnippetFormat.escapeValue("#"))
        assertEquals("#", SnippetFormat.unescapeValue("\\#"))
    }

    @Test
    fun `Backslash und newline ueberleben den Roundtrip`() {
        assertEquals("\\\\", SnippetFormat.escapeValue("\\"))
        assertEquals("\\n", SnippetFormat.escapeValue("\n"))
        assertEquals("\\", SnippetFormat.unescapeValue("\\\\"))
        assertEquals("\n", SnippetFormat.unescapeValue("\\n"))
    }

    @Test
    fun `commentStart erkennt nur unescaped Hash`() {
        assertEquals(-1, SnippetFormat.commentStart("kein kommentar"))
        assertEquals(0, SnippetFormat.commentStart("# kommentar"))
        assertEquals(5, SnippetFormat.commentStart("wert # kommentar"))
        assertEquals(-1, SnippetFormat.commentStart("wert \\# kein kommentar"))
        assertEquals(7, SnippetFormat.commentStart("wert \\\\# kommentar"))
    }

    @Test
    fun `unbekannte Escapes bleiben erhalten`() {
        assertEquals("\\x", SnippetFormat.unescapeValue("\\x"))
    }
}
