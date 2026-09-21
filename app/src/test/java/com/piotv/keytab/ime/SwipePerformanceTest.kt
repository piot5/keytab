package com.piotv.keytab.ime

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Swipe-Messung** – Hot Path des Swipe-Samplings: `charAt` (HashMap-/Matrix-
 * Lookup) + Dedup. Micro-Benchmark auf der JVM (Desktop, kein Device): misst
 * die Algorithmus-Kosten, **nicht** das Frame-Timing auf dem Gerät (das bleibt
 * Device-Verifikation). Kalibriert an `TrailPerformanceTest`.
 *
 * Budget: 50 ms pro Tastendruck / 16 ms pro Frame; das Move-Event muss nur
 * `charAt` + Dedup ausführen (O(1), keine Allokation im Hot Path außer der
 * Ergebnisliste). Wir asserten großzügig (< 5 ms im Durchschnitt) und drucken
 * die gemessenen Werte für die Doku.
 */
class SwipePerformanceTest {

    private fun centers(): List<SwipePathLogic.KeyCenter> =
        (0 until 30).map { i ->
            val row = i / 10
            val col = i % 10
            SwipePathLogic.KeyCenter(('a' + i), 30f + col * 60f, 10f + row * 60f)
        }

    @Test
    fun `charAt bleibt weit unter dem Frame-Budget bei 10000 Move-Events`() {
        val cs = centers()
        val samples = (0 until 10_000).map { i ->
            val c = cs[i % cs.size]
            SwipePathLogic.Sample(c.x + 1f, c.y + 1f)
        }
        val t0 = System.nanoTime()
        var hits = 0
        for (s in samples) {
            if (SwipePathLogic.charAt(s.x, s.y, cs) != null) hits++
        }
        val avgMicros = (System.nanoTime() - t0) / 1000 / samples.size
        println("charAt: $avgMicros µs/call im Schnitt (JVM, 30-Tasten-Matrix, 10000 calls, $hits Treffer)")
        assertTrue("avg $avgMicros µs sollte < 5.000 µs (5 ms) sein", avgMicros < 5_000)
    }

    @Test
    fun `dedup ist O(n) und bleibt unter dem Frame-Budget`() {
        val cs = centers()
        val samples = (0 until 10_000).map { i ->
            val c = cs[i % cs.size]
            SwipePathLogic.Sample(c.x, c.y)
        }
        val t0 = System.nanoTime()
        val out = SwipePathLogic.dedup(samples)
        val avgMicros = (System.nanoTime() - t0) / 1000 / samples.size
        println("dedup: $avgMicros µs/call im Schnitt (JVM, 10000 samples -> ${out.size} eindeutige)")
        assertTrue("avg $avgMicros µs sollte < 5.000 µs (5 ms) sein", avgMicros < 5_000)
    }

    @Test
    fun `scorer auf 6k-Korpus bleibt unter dem 50ms-Budget`() {
        val corpus = (0 until 6000).map { i ->
            val w = buildString {
                append(('a' + i % 26))
                append(('a' + (i / 26) % 26))
                append(('a' + (i / 676) % 26))
                append((i % 97).toString().padStart(2, '0'))
                append(('a' + (i / 97) % 26))
            }
            w to (10_000 - i)
        }
        val engine = SuggestionEngine(corpus)
        val route = "abcde"
        // Warm-up
        SwipeScorer.score(route, engine)
        val t0 = System.nanoTime()
        val out = SwipeScorer.score(route, engine)
        val micros = (System.nanoTime() - t0) / 1000
        println("scorer: $micros µs/call (JVM, 6k-Korpus, Route '$route', ${out.size} Kandidaten)")
        assertTrue("$micros µs sollte < 50.000 µs (50 ms) sein", micros < 50_000)
    }
}
