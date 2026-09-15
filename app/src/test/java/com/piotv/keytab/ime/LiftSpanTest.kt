package com.piotv.keytab.ime

import android.text.TextPaint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regressionstest für [LiftSpan].
 *
 * Hintergrund: [LiftSpan] hat den Baseline-Versatz früher mit `+=` auf den
 * übergebenen [TextPaint] addiert. Android reicht denselben Paint durch mehrere
 * Measure-/Draw-Pässe (TextLine), wodurch der Versatz immer weiter wuchs und die
 * Buchstaben am Ende aus der 42dp-Taste herauswanderten — Symptom: „beim Starten
 * verschwinden die Buchstaben, bis man das Theme umschaltet".
 *
 * Der Span muss deshalb idempotent sein: mehrfaches Anwenden darf das Ergebnis
 * nicht verändern.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiftSpanTest {

    @Test
    fun `LiftSpan ist idempotent - kein Aufsummieren des Baseline-Shifts`() {
        val tp = TextPaint().apply { textSize = 22f }
        val span = LiftSpan(-0.2f)
        span.updateMeasureState(tp)
        val once = tp.baselineShift
        for (i in 1..10) span.updateMeasureState(tp)
        assertEquals("Mehrfaches Anwenden darf baselineShift nicht aufsummieren",
            once, tp.baselineShift)
    }

    @Test
    fun `LiftSpan setzt Versatz proportional zur Schriftgroesse`() {
        val tp = TextPaint().apply { textSize = 22f }
        LiftSpan(-0.2f).updateMeasureState(tp)
        assertEquals("negativer Faktor senkt die Basislinie",
            (-0.2f * 22f).toInt(), tp.baselineShift)
    }

    @Test
    fun `LiftSpan bleibt bei Wechsel zwischen Measure und Draw stabil`() {
        val tp = TextPaint().apply { textSize = 22f }
        val span = LiftSpan(0.55f)
        val expected = (0.55f * 22f).toInt()
        repeat(5) {
            span.updateMeasureState(tp)
            assertEquals(expected, tp.baselineShift)
            span.updateDrawState(tp)
            assertEquals(expected, tp.baselineShift)
        }
    }

    @Test
    fun `LiftSpan Versatz liegt innerhalb der Tastenhoehe einer Buchstaben-Taste`() {
        // KeyDark: 42dp hoch, 22sp Text -> der Versatz (0.2 * 22dp) muss deutlich
        // kleiner als die halbe Tastenhöhe sein, sonst wandert der Buchstabe heraus.
        val tp = TextPaint().apply { textSize = 22f }
        LiftSpan(-0.2f).updateMeasureState(tp)
        val shift = kotlin.math.abs(tp.baselineShift)
        assertEquals("Versatz darf die Tastenhälfte (21dp) nicht erreichen",
            true, shift < 21)
        assertEquals("Versatz bleibt bei 4-5dp für 22sp", 4, shift)
    }
}