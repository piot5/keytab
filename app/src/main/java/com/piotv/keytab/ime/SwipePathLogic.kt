package com.piotv.keytab.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Reine Swipe-Logik (Android-frei bis auf die [EditorInfo]-Ableitung, JUnit-testbar):
 * Pfad-Sampling (Dedup + Buchstaben-Zuordnung über eine Zentren-Matrix) und die
 * Prognose-Knoten für die Schaltplan-Preview.
 *
 * **Zwei Betriebsarten** (Details in `docs/SWIPE_PLAN.md`):
 *  - **Schaltplan-Preview (passiv):** die wahrscheinlichen Folge-Tasten des
 *    aktuell getippten Worts werden als verbundener Pfad sichtbar – Knoten =
 *    skalierte, einfarbige Tasten in der Pfad-Farbe (`KIND_SWIPE`).
 *  - **Swipe-Eingabe (aktiv):** der Finger gleitet über die Tastatur; die
 *    gefahrene Route wird gesampelt und vom [SwipeScorer] gegen die Engine
 *    bewertet.
 *
 * **Sicherheit:** In Passwort-Feldern ist Swipe komplett deaktiviert – gleiche
 * harte Regel wie [TrailLogic.isTrailAllowed] (Feld-Flag +
 * `IME_FLAG_NO_PERSONALIZED_LEARNING`). Eine sichtbare Route über `•`-Feldern
 * leakt Zeichenanzahl/Position (vgl. `scripts/articles/03-trail-side-channel.md`).
 */
object SwipePathLogic {

    /** Bewegungs-Distanz (in dp) ab der ein Tap als Swipe-Eingabe gewertet wird. */
    const val SWIPE_THRESHOLD_DP = 12

    /** Toleranz-Faktor für [charAt]: eine Taste gilt getroffen, wenn die Koordinate
     *  innerhalb von [CHAR_TOLERANCE] × Tasten-Halbmesser um das Zentrum liegt. */
    const val CHAR_TOLERANCE = 1.3f

    /** Maximal-Prognosetiefe der Preview (Empfehlung aus dem Plan: 1). */
    const val DEFAULT_PREVIEW_DEPTH = 1

    /** Ein Sample der gefahrenen Route (Geräte-Koordinaten). */
    data class Sample(val x: Float, val y: Float)

    /** Zentrum einer Taste (Geräte-Koordinaten) + zugehöriger Buchstabe. */
    data class KeyCenter(val letter: Char, val x: Float, val y: Float)

    /**
     * Entfernt unmittelbar aufeinanderfolgende Duplikate (gleiche Taste, kein
     * Tastenwechsel). O(n), keine Allokation im Hot Path außer der Ergebnisliste.
     */
    fun dedup(samples: List<Sample>): List<Sample> {
        if (samples.isEmpty()) return emptyList()
        val out = ArrayList<Sample>(samples.size)
        var last: Sample? = null
        for (s in samples) {
            val l = last
            if (l == null || l.x != s.x || l.y != s.y) out.add(s)
            last = s
        }
        return out
    }

    /**
     * Liefert den Buchstaben der Taste, auf die die Koordinate ([x]/[y]) fällt,
     * oder `null` (außerhalb aller Tasten). Eine Taste gilt getroffen, wenn ihr
     * Zentrum der nächste ist UND die Koordinate innerhalb des Toleranz-Rings
     * (`CHAR_TOLERANCE` × Halbmesser) liegt. Der Halbmesser wird aus dem mittleren
     * Abstand benachbarter Zentren geschätzt (robust gegen unterschiedliche
     * Tastaturbreiten).
     */
    fun charAt(
        x: Float,
        y: Float,
        centers: List<KeyCenter>,
        tolerance: Float = CHAR_TOLERANCE
    ): Char? {
        if (centers.isEmpty()) return null
        var best: KeyCenter? = null
        var bestDist = Float.MAX_VALUE
        for (c in centers) {
            val dx = c.x - x
            val dy = c.y - y
            val d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (d < bestDist) { bestDist = d; best = c }
        }
        val c = best ?: return null
        val radius = estimateRadius(centers)
        return if (bestDist <= radius * tolerance) c.lowercaseLetter() else null
    }

    /** Kleinster Abstand zwischen zwei Zentren / 2 als Halbmesser-Schätzung. */
    private fun estimateRadius(centers: List<KeyCenter>): Float {
        if (centers.size < 2) return 24f * 2f // Fallback (dp-unabhängig, Geräte-Pixel)
        var minDist = Float.MAX_VALUE
        for (i in centers.indices) {
            for (j in (i + 1) until centers.size) {
                val a = centers[i]; val b = centers[j]
                val d = Math.sqrt(((a.x - b.x) * (a.x - b.x) +
                    (a.y - b.y) * (a.y - b.y)).toDouble()).toFloat()
                if (d > 0f && d < minDist) minDist = d
            }
        }
        return (minDist / 2f).coerceAtLeast(1f)
    }

    /**
     * Buchstabenfolge aus einer gefahrenen Route (dedup-tauglich): jede Taste,
     * die das Sampling trifft, wird einmalig angefügt (Tastenwechsel, kein
     * Wiederholen derselben Taste bei Verweilen).
     */
    fun pathLetters(samples: List<Sample>, centers: List<KeyCenter>): String {
        val sb = StringBuilder()
        var last: Char? = null
        for (s in dedup(samples)) {
            val ch = charAt(s.x, s.y, centers) ?: continue
            if (ch != last) { sb.append(ch); last = ch }
        }
        return sb.toString()
    }

    /**
     * Prognose-Knoten für die Schaltplan-Preview: die nächsten wahrscheinlichen
     * Buchstaben des aktuell getippten Worts, abgeleitet aus den Top-Vorschlägen
     * an Position `typed.length`. Tiefe [depth] = 1 → nur der jeweils nächste
     * Buchstabe jedes Top-Vorschlags (dedupliziert, Reihenfolge erhalten).
     */
    fun previewNodes(
        suggestions: List<SuggestionEngine.Suggestion>,
        typedLength: Int,
        depth: Int = DEFAULT_PREVIEW_DEPTH
    ): List<Char> {
        val out = LinkedHashMap<Char, Boolean>()
        for (s in suggestions) {
            for (k in 1..depth) {
                val ch = s.word.getOrNull(typedLength + k - 1)?.lowercaseChar() ?: break
                out[ch] = true
            }
        }
        return out.keys.toList()
    }

    /**
     * Wahr, wenn [hit] (der im Swipe gerade erreichte Buchstabe) einer der zuvor
     * als most-likely angezeigten Folge-Tasten ([likelyNodes]) war. Das ist der
     * Trigger für die grüne Trail-Einfärbung während des Swipens: wer dem
     * Schaltplan folgt, sieht die gefahrene Route grün aufleuchten.
     *
     * Case-insensitiv (beide Seiten lowercase), da [previewNodes] Kleinbuchstaben
     * liefert und der Swipe-[hit] ebenfalls kleingeschrieben gemeldet wird.
     */
    fun isLikelyHit(likelyNodes: List<Char>, hit: Char): Boolean =
        likelyNodes.contains(hit.lowercaseChar())

    /**
     * Darf Swipe in diesem Feld überhaupt aktiv sein? – gleiche harte Regel wie
     * [TrailLogic.isTrailAllowed] (Passwort-Felder + `NO_PERSONALIZED_LEARNING`).
     */
    fun isSwipeAllowed(attribute: EditorInfo?): Boolean =
        TrailLogic.isTrailAllowed(attribute)

    /** Liefert den Kleinbuchstaben einer Taste (Umlaute/ß bleiben). */
    private fun KeyCenter.lowercaseLetter(): Char = letter.lowercaseChar()
}
