package com.piotv.keytab.ime

import android.graphics.Color
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LetterLabelComposer: vertraglich festgehaltene Lay-out-Werte des
 * Buchstaben-Labels. Die Zahlen sind Absicht, nicht Zufall – ein Test, der sie
 * nur "irgendwie grün" hält, bringt hier nichts; deshalb prüft er die
 * tatsächlichen Span-Intervalle und -Faktoren.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LetterLabelComposerTest {

    private val secondary = Color.GRAY

    private fun sizes(text: Spanned) =
        text.getSpans(0, text.length, RelativeSizeSpan::class.java)
            .map { text.getSpanStart(it) to text.getSpanEnd(it) to it.sizeChange }

    @Test
    fun `ohne Extras traegt nur der Hauptbuchstabe die Spans`() {
        val label = LetterLabelComposer.compose('a', emptyList(), secondary)
        assertEquals("a", label.toString())
        assertEquals(
            listOf(0 to 1 to LetterLabelComposer.MAIN_SIZE_SCALE),
            sizes(label)
        )
    }

    @Test
    fun `mit Extras wird das geschuetzte Leerzeichen als Trenner eingefuegt`() {
        val label = LetterLabelComposer.compose('a', listOf("ä", "x"), secondary)
        assertEquals("a\u00A0ä", label.toString())
        assertTrue("Trenner muss geschütztes Leerzeichen sein",
            label.toString().contains(LetterLabelComposer.EXTRA_SEPARATOR))
    }

    @Test
    fun `Hinweis traegt eigene Groesse und eigene Farbe, getrenntes Intervall`() {
        val label = LetterLabelComposer.compose('a', listOf("ä"), secondary)
        assertEquals(
            listOf(
                0 to 1 to LetterLabelComposer.MAIN_SIZE_SCALE,
                1 to 3 to LetterLabelComposer.EXTRA_SIZE_SCALE
            ),
            sizes(label)
        )
        val colors = label.getSpans(0, label.length, ForegroundColorSpan::class.java)
        assertEquals("nur der Hinweis ist eingefaerbt", 1, colors.size)
        assertEquals(secondary, colors[0].foregroundColor)
        // Der Hinweis beginnt erst nach dem Trenner, nicht beim Buchstaben.
        assertEquals(1, label.getSpanStart(colors[0]))
    }

    @Test
    fun `von mehreren Extras wird nur das erste angezeigt`() {
        val label = LetterLabelComposer.compose('a', listOf("ä", "é", "ñ"), secondary)
        assertEquals("a\u00A0ä", label.toString())
    }

    @Test
    fun `Hinweis liegt unter der Basislinie, Hauptbuchstabe darueber`() {
        val label = LetterLabelComposer.compose('a', listOf("ä"), secondary)
        // Zwei LiftSpans: einer ueber den Buchstaben, einer ueber den Hinweis.
        // Der Versatz selbst steckt in [LiftSpan] (private) – hier zaehlt und
        // lokalisiert die Reihenfolge, die Verschiebung prueft LiftSpanTest.
        val lifts = label.getSpans(0, label.length, LiftSpan::class.java)
        assertEquals(2, lifts.size)
        assertEquals("Buchstaben-Lift zuerst", 0, label.getSpanStart(lifts[0]))
        assertEquals("Hinweis-Lift danach", 1, label.getSpanStart(lifts[1]))
    }

    @Test
    fun `leere Extra-Liste ist dasselbe wie fehlende Extras`() {
        assertEquals(
            LetterLabelComposer.compose('z', emptyList(), secondary).toString(),
            LetterLabelComposer.compose('z', listOf(), secondary).toString()
        )
    }

    @Test
    fun `compose liefert exklusive Spans, damit spaeteres Append nicht mitwandert`() {
        val label = LetterLabelComposer.compose('a', listOf("ä"), secondary)
        val spans = label.getSpans(0, label.length, RelativeSizeSpan::class.java)
        assertTrue(spans.isNotEmpty())
        assertTrue("Spans müssen SPAN_EXCLUSIVE_EXCLUSIVE sein",
            spans.all { label.getSpanFlags(it) and Spanned.SPAN_EXCLUSIVE_EXCLUSIVE == Spanned.SPAN_EXCLUSIVE_EXCLUSIVE })
    }
}
