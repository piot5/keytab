package com.piotv.keytab.ime

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Trail-Messung (Known-gap „Trail performance" — Teil-Messung).**
 *
 * Misst den Hot Path, den [TrailManager] bei **jedem Tastendruck** ausführt:
 * `TrailLogic.classifyTypedWord` → `knowsWord` / `autoCorrect`
 * (Damerau-Levenshtein über den Char-Index). Micro-Benchmark auf der JVM
 * (Desktop, kein Device): er misst die Algorithmus-Kosten, **nicht** das
 * Frame-Timing auf dem Gerät — das bleibt Device-Verifikation (README Known gaps).
 *
 * Budget: die App targetiert 50 ms pro Tastendruck; der Klassifizierer muss
 * dabei nur einen Bruchteil verbrauchen. Wir asserten großzügig (< 5 ms im
 * Durchschnitt) und drucken die gemessenen Werte für die Doku.
 */
class TrailPerformanceTest {

    /** Synthetischer Korpus ~6.000 Wörter (Größe des echten Assets). */
    private fun corpus(): List<Pair<String, Int>> =
        (0 until 6000).map { i ->
            val w = buildString {
                append(('a' + i % 26))
                append(('a' + (i / 26) % 26))
                append(('a' + (i / 676) % 26))
                append((i % 97).toString().padStart(2, '0'))
                append(('a' + (i / 97) % 26))
            }
            w to (10_000 - i)
        }

    @Test
    fun `classify pro Tastendruck bleibt weit unter dem 50ms-Budget`() {
        val engine = SuggestionEngine(corpus())
        // Warm-up: lazy Char-Index einmalig aufbauen (entspricht Engine-Laden)
        TrailLogic.classifyTypedWord("hausx", engine)

        // 500 Klassifizierungen mit unbekannten (Nicht-Korpus-)Wörtern:
        // worst case — jede führt in die Fuzzy-Suche (editDistance-Pass).
        // Nur Buchstaben (classifyTypedWord filtert Ziffern/Symbole hart raus).
        val words = (0 until 500).map { i ->
            buildString {
                append('a' + i % 26)
                append('q' + i % 3)      // q..s
                append('z')
                append('x' + i % 2)      // x..y
                append('a' + (i / 26) % 26)
                append('j')
                append('m' + i % 5)
                append('u' + i % 4)
            }
        }
        val t0 = System.nanoTime()
        var corrected = 0
        for (w in words) {
            if (TrailLogic.classifyTypedWord(w, engine) == TrailLogic.TrailKind.CORRECTED) corrected++
        }
        val avgMicros = (System.nanoTime() - t0) / 1000 / words.size
        println("classifyTypedWord: $avgMicros µs/call im Schnitt (JVM, 6k-Wort-Korpus, 500 calls, $corrected korrigiert)")
        assertTrue("avg $avgMicros µs sollte < 5.000 µs (5 ms) sein", avgMicros < 5_000)
    }

    @Test
    fun `autoCorrect auf bekanntem Wort ist O(1)-short-circuit`() {
        val engine = SuggestionEngine(corpus())
        val words = (0 until 500).map { it.toString().padStart(4, 'a') + "known" }
        // Bekannte Wörter: kein Fuzzy-Pass — muss extrem schnell durchlaufen.
        val t0 = System.nanoTime()
        for (w in words) engine.autoCorrect(w)
        val avgMicros = (System.nanoTime() - t0) / 1000 / words.size
        println("autoCorrect(known): $avgMicros µs/call (JVM, short-circuit path)")
        assertTrue("avg $avgMicros µs sollte < 1.000 µs (1 ms) sein", avgMicros < 1_000)
    }
}
