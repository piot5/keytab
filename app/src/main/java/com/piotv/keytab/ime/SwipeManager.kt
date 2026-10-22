package com.piotv.keytab.ime

import android.content.SharedPreferences
import android.widget.Button

/**
 * Swipe-Manager (v0.11): Swipe-Eingabe.
 *
 * **Swipe-Eingabe** ([startSwipe]/[onSwipeMove]/[onSwipeRelease]): der Finger
 * gleitet über die Tastatur; die gefahrene Route wird gesampelt und vom
 * [SwipeScorer] gegen die Engine bewertet. Bei klarer Dominanz Auto-Commit,
 * sonst die besten Kandidaten in der Vorschlags-Leiste. Sampling + Scoring
 * laufen nur bei Tastenwechsel bzw. Release (nicht pro Move-Event), Scoring
 * auf dem ioExecutor (Aufrufer). Passwort-Felder sind komplett ausgenommen
 * ([SwipePathLogic.isSwipeAllowed]).
 *
 * Reine Logik in [SwipePathLogic] + [SwipeScorer] (Android-frei, unit-testbar).
 */
class SwipeManager(
    private val prefs: SharedPreferences,
    private val baseLetters: Map<Button, Char>
) {
    // Swipe-Eingabe-Sampling (nur wenn Swipe aktiv)
    private val swipeSamples = mutableListOf<SwipePathLogic.Sample>()
    private var swipeStartLoc: SwipePathLogic.Sample? = null
    private var lastKeyCenter: Char? = null
    /** Gecachte Tasten-Zentren für die Dauer einer Geste (v0.16, siehe [centers]). */
    private var cachedCenters: List<SwipePathLogic.KeyCenter>? = null

    /** Aktiver Feld-Info (Passwort-Schutz). null = unbekannt → Swipe erlaubt. */
    var editorInfo: android.view.inputmethod.EditorInfo? = null
        set(info) {
            if (field?.inputType != info?.inputType ||
                field?.imeOptions != info?.imeOptions
            ) {
                field = info
            }
        }

    /** Swipe aktiv? (Pref). */
    fun swipeEnabled(): Boolean =
        com.piotv.keytab.Prefs.KEY_SWIPE.let { prefs.getBoolean(it, false) }

    /**
     * Darf Swipe in diesem Feld überhaupt greifen? (N17)
     *
     * `swipeEnabled()` beantwortet nur die Pref-Frage. Die **Feldregel**
     * (Passwort-Felder, `IME_FLAG_NO_PERSONALIZED_LEARNING`) ist davon getrennt
     * — der Aufrufer braucht aber beides zusammen: sonst wuerde der Long-Press
     * in einem Passwortfeld den Auto-Select-Zweig nehmen (erster Buchstabe des
     * Sonderzeichen-Satzes wird sofort committet) statt das Auswahl-Popup zu
     * oeffnen — und damit im Passwortfeld Text eintragen.
     */
    fun swipeAllowedInField(): Boolean =
        swipeEnabled() && SwipePathLogic.isSwipeAllowed(editorInfo)

    /**
     * Swipe-Eingabe beginnen. [x]/[y] = Abgriff-Koordinaten (Fenster-Relativ).
     * Tut nichts, wenn Swipe aus oder Feld ein Passwort-Feld.
     */
    fun startSwipe(x: Float, y: Float) {
        if (!swipeEnabled()) return
        if (!SwipePathLogic.isSwipeAllowed(editorInfo)) return
        swipeSamples.clear()
        swipeStartLoc = SwipePathLogic.Sample(x, y)
        lastKeyCenter = null
        cachedCenters = null // frische Zentren pro Geste (v0.16)
    }

    /**
     * Ersten Tastenabgriff schon beim Drücken vorbelegen: die gedrückte Taste
     * wird zum ersten Routen-Sample.
     *
     * Wichtig: Das Sample zählt NICHT als „gewischt“ — ein reiner Tap hat genau
     * dieses eine Sample und wird vom Aufrufer über [hasSwiped] als Tap erkannt
     * (siehe KeyboardBinder). So bleibt der Tap-Fallback unverändert.
     */
    fun seedFirstKey(letter: Char, x: Float, y: Float) {
        if (!swipeEnabled()) return
        if (!SwipePathLogic.isSwipeAllowed(editorInfo)) return
        if (swipeSamples.isEmpty()) swipeSamples.add(SwipePathLogic.Sample(x, y))
        lastKeyCenter = letter.lowercaseChar()
    }

    /**
     * Swipe bewegen. [x]/[y] = aktuelle Abgriff-Koordinate.
     * Fängt erst ab, wenn der Finger eine kleine Mindestbewegung über die Taste
     * hinausgeht (Abstand > [SWIPE_THRESHOLD_DP] * density in Pixeln).
     * Sampling erfolgt O(1) pro Move; deduplizierung und Scoring erst nach dem
     * ersten gültigen Tastenwechsel.
     *
     * @return der neu getroffene Buchstabe (lowercase), wenn ein Tastenwechsel
     *   stattfand (neues Sample) – der Aufrufer kann ihn für einen Trail-Snap
     *   nutzen, damit die gefahrene Route live sichtbar wird. `null` sonst.
     */
    fun onSwipeMove(x: Float, y: Float): Char? {
        if (!swipeEnabled()) return null
        if (!SwipePathLogic.isSwipeAllowed(editorInfo)) return null
        if (swipeStartLoc == null) return null
        val start = swipeStartLoc ?: return null
        // Mindestbewegung prüfen, sonst Sample ignorieren (Long-Press-Popup-Fallback).
        val dx = x - start.x
        val dy = y - start.y
        if (Math.hypot(dx.toDouble(), dy.toDouble()).toFloat() < minSwipeDistPx()) return null

        val ch = charAt(x, y)
        // Nur echte Tastentreffer (ch != null) als Sample werten. Bewegt sich der
        // Finger zwischen Tasten (ch == null), wird weder ein Sample hinzugefügt
        // noch lastKeyCenter geändert — sonst würde ein null-Treffer den
        // Tastenwechsel-Dedup zurücksetzen und der nächste echte Treffer ggf.
        // verschluckt. So bleibt die Route sauber (nur echte Tastenwechsel).
        if (ch == null) return null
        if (ch == lastKeyCenter) return null // gleiche Taste, kein neues Sample

        // Neuen Tastenwechsel: Route wird durch Sampling fortsetzt.
        swipeSamples.add(SwipePathLogic.Sample(x, y))
        lastKeyCenter = ch
        // Den getroffenen Buchstaben zurückgeben, damit der Aufrufer (KeyboardBinder)
        // einen Trail-Snap zeichnen kann — so wird die gefahrene Route live sichtbar.
        return ch
    }

        /**
     * Swipe-Release: Route abschließen, durch den [SwipeScorer] bewerten,
     * Auto-Commit bei klarer Dominanz oder Top-3-Kandidaten in die
     * Vorschlags-Leiste setzen. Aufrufer füllt die Leiste selbst.
     *
     * **Fallback (v0.11.1):** liefert die Scorer-Ergebnisse. Wenn der Scorer
     * keine Kandidaten findet (Route ist nicht im Wörterbuch), wird die
     * Route selbst als Kandidat mit minimalem Score zurückgegeben — so wird
     * beim Absetzen **immer ein Wort erzeugt** (nicht nur der letzte Buchstabe).
     *
     * @return leere Liste, wenn keine Route (z. B. kein Sample / Passwort-Feld),
     *   sonst mindestens einen Kandidaten (ggf. der Route selbst).
     */
    fun onSwipeRelease(): List<SwipeScorer.Candidate> {
        val samples = swipeSamples.toList()
        swipeSamples.clear()
        swipeStartLoc = null
        lastKeyCenter = null
        cachedCenters = null

        if (!swipeEnabled() || samples.isEmpty()) return emptyList()
        if (!SwipePathLogic.isSwipeAllowed(editorInfo)) return emptyList()

        val route = SwipePathLogic.pathLetters(samples, centers())
        if (route.isEmpty()) return emptyList()

        val scored = SwipeScorer.score(route, engine)
        if (scored.isNotEmpty()) return scored

        // Kein Wörterbuch-Treffer: die gefahrene Route ist selbst das Wort.
        // Minimaler Score (0.01) sorgt dafür, dass Kandidaten angezeigt werden
        // (sortiert hinten) ohne Auto-Commit — der Nutzer kann dennoch tippen.
        return listOf(SwipeScorer.Candidate(route, MIN_CANDIDATE_SCORE))
    }

    /**
     * Auto-Commit-Entscheidung für die resultierenden Kandidaten. Liefert den
     * zu committenden String oder null (Leiste muss die Kandidaten zeigen).
     */
    fun autoCommitCandidate(candidates: List<SwipeScorer.Candidate>): String? =
        SwipeScorer.autoCommit(candidates)?.lowercase()

    private companion object {
        /** Kein Woerterbuch-Treffer: die Route selbst als Kandidat hinten melden. */
        private const val MIN_CANDIDATE_SCORE = 0.01
    }

    /** Setzt die Swipe-Samples zurück (z. B. bei LongPress-Auslösung). */
    fun clearSwipeSamples() {
        swipeSamples.clear()
        swipeStartLoc = null
        lastKeyCenter = null
        cachedCenters = null
    }

    /** Wahr, wenn aktuell Swipe-Samples gesammelt werden (für Touch-Delegation). */
    fun hasSamples(): Boolean = swipeSamples.isNotEmpty()

    /**
     * Wahr, wenn wirklich **gewischt** wurde: mindestens zwei Routen-Samples,
     * d. h. mindestens ein echter Tastenwechsel. Das erste Sample kann bereits
     * vom Drücken stammen ([seedFirstKey]) — ein reiner Tap (nur Seeding) gilt
     * deshalb als Tap, nicht als Swipe.
     */
    fun hasSwiped(): Boolean = swipeSamples.size >= 2

    /** @see SwipeKeyGeometry.minSwipeDistPx */
    private fun minSwipeDistPx(): Float =
        SwipeKeyGeometry.minSwipeDistPx(baseLetters)

    /**
     * Tasten-Zentren für die aktuelle Geste — einmalig berechnet und für alle
     * Move-Events derselben Geste wiederverwendet (v0.16). Vorher rief jedes
     * Move-Event [SwipeKeyGeometry.centersFor] neu auf (~30 × getLocationInWindow
     * pro Event); jetzt nur noch einmal pro Geste.
     */
    private fun centers(): List<SwipePathLogic.KeyCenter> =
        cachedCenters ?: SwipeKeyGeometry.centersFor(baseLetters).also { cachedCenters = it }

    /** @see SwipePathLogic.charAt */
    private fun charAt(x: Float, y: Float): Char? =
        SwipePathLogic.charAt(x, y, centers())

    /** Setzt die aktuelle Engine (vom Service bei Engine-Ready / Feldwechsel). */
    fun setEngine(e: SuggestionEngine?) { engine = e }

    /** Aktuelle Engine (vom Service gesetzt). */
    var engine: SuggestionEngine? = null
        private set
}
