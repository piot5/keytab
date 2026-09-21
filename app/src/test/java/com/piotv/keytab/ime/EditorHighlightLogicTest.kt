package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spezifikationen für [EditorHighlightLogic] (reine Logik, kein Android). */
class EditorHighlightLogicTest {

    @Test
    fun `leerer Text liefert keine Spans`() {
        assertTrue(EditorHighlightLogic.spans("").isEmpty())
    }

    @Test
    fun `Hash-Kommentar endet am Zeilenende`() {
        val s = EditorHighlightLogic.spans("# hi\nval x")
        assertEquals(
            listOf(
                EditorHighlightLogic.Span(0, 4, EditorHighlightLogic.Kind.COMMENT),
                EditorHighlightLogic.Span(5, 8, EditorHighlightLogic.Kind.KEYWORD)
            ), s
        )
    }

    @Test
    fun `Slash-Kommentar endet am Zeilenende`() {
        val s = EditorHighlightLogic.spans("// c\nx")
        assertEquals(listOf(EditorHighlightLogic.Span(0, 4, EditorHighlightLogic.Kind.COMMENT)), s)
    }

    @Test
    fun `Shebang wird als Kommentar erkannt`() {
        val s = EditorHighlightLogic.spans("#!/bin/sh\necho hi")
        assertEquals(
            listOf(
                EditorHighlightLogic.Span(0, 9, EditorHighlightLogic.Kind.COMMENT),
                EditorHighlightLogic.Span(10, 14, EditorHighlightLogic.Kind.KEYWORD)
            ), s
        )
    }

    @Test
    fun `String mit Escape wird komplett erfasst`() {
        val s = EditorHighlightLogic.spans("\"a\\\"b\"")
        assertEquals(listOf(EditorHighlightLogic.Span(0, 6, EditorHighlightLogic.Kind.STRING)), s)
    }

    @Test
    fun `offener String endet spätestens am Zeilenende`() {
        val s = EditorHighlightLogic.spans("\"abc\nx")
        assertEquals(listOf(EditorHighlightLogic.Span(0, 4, EditorHighlightLogic.Kind.STRING)), s)
    }

    @Test
    fun `Dezimalzahl mit Punkt`() {
        val s = EditorHighlightLogic.spans("x=42.5")
        assertEquals(listOf(EditorHighlightLogic.Span(2, 6, EditorHighlightLogic.Kind.NUMBER)), s)
    }

    @Test
    fun `Hexzahl erfasst Hex-Zeichen und stoppt davor`() {
        assertEquals(listOf(EditorHighlightLogic.Span(0, 5, EditorHighlightLogic.Kind.NUMBER)),
            EditorHighlightLogic.spans("0x1Fa"))
        assertEquals(listOf(EditorHighlightLogic.Span(0, 3, EditorHighlightLogic.Kind.NUMBER)),
            EditorHighlightLogic.spans("0x1G"))
    }

    @Test
    fun `Schlüsselwort nur als ganzes Identifier`() {
        assertEquals(listOf(EditorHighlightLogic.Span(0, 3, EditorHighlightLogic.Kind.KEYWORD)),
            EditorHighlightLogic.spans("val x"))
        assertTrue(EditorHighlightLogic.spans("valx").isEmpty())
    }

    @Test
    fun `gemischte Zeile liefert sortierte, nicht überlappende Spans`() {
        val spans = EditorHighlightLogic.spans("x=1 # val \"a\"")
        assertTrue(spans.size >= 2)
        for (i in 1 until spans.size) {
            assertTrue(spans[i - 1].start < spans[i].start)
            assertTrue(spans[i - 1].end <= spans[i].start)
        }
        assertEquals(EditorHighlightLogic.Kind.COMMENT, spans.last().kind)
    }

    @Test
    fun `maxChars begrenzt die Scan-Länge`() {
        val text = "abcdef # spät"
        assertTrue(EditorHighlightLogic.spans(text, maxChars = 6).isEmpty())
        assertTrue(EditorHighlightLogic.spans(text).isNotEmpty())
    }
}
