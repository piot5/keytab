package com.piotv.keytab.ime

import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.piotv.keytab.R

/**
 * Swipe-Manager (v0.11): Schaltplan-Preview (passiv) + Swipe-Eingabe (aktiv).
 *
 * **Preview** ([applyPreview]): die wahrscheinlichen Folge-Tasten des aktuell
 * getippten Worts werden als verbundener Pfad sichtbar – Knoten = skalierte
 * Tasten in der Pfad-Farbe (`KIND_SWIPE`), Kanten = Verbindungslinien
 * (`KIND_SWIPE_EDGE`) unter den Tasten. Nutzt dieselbe Foreground-Overlay-
 * Technik wie [TrailManager] (Button.background bleibt unberührt) plus ein
 * eigenes Overlay-View für die Kanten.
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
    /** Aktuelle Preview-Knoten (lowercase Buchstaben). Leer = kein Pfad. */
    private var previewNodes: List<Char> = emptyList()
    /** Original-Hintergründe der als Knoten gefärbten Tasten (Restore). */
    private val nodeBackgrounds = mutableListOf<Pair<Button, android.graphics.drawable.Drawable>>()
    /** Overlay-View für die Kanten (wird unter die Tasten gelegt). */
    private var edgeOverlay: SwipeEdgeOverlay? = null

    // Swipe-Eingabe-Sampling (nur wenn Swipe aktiv)
    private val swipeSamples = mutableListOf<SwipePathLogic.Sample>()
    private var swipeStartLoc: SwipePathLogic.Sample? = null
    private var lastKeyCenter: Char? = null

    /** Aktiver Feld-Info (Passwort-Schutz). null = unbekannt → Swipe erlaubt. */
    var editorInfo: android.view.inputmethod.EditorInfo? = null
        set(info) {
            if (field?.inputType != info?.inputType ||
                field?.imeOptions != info?.imeOptions
            ) {
                field = info
                clearPreview()
            }
        }

    /** Swipe aktiv? (Pref). */
    fun swipeEnabled(): Boolean =
        com.piotv.keytab.Prefs.KEY_SWIPE.let { prefs.getBoolean(it, false) }

    /** Schaltplan-Preview aktiv? (Pref). */
    fun previewEnabled(): Boolean =
        com.piotv.keytab.Prefs.KEY_SWIPE_PREVIEW.let { prefs.getBoolean(it, false) }

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
    }

    /**
     * Ersten Tastenabgriff schon beim Drücken vorbelegen: die gedrückte Taste
     * wird zum ersten Routen-Sample. Damit kann [applySwipeLikely] die
     * most-likely-Ziele **sofort** anzeigen (noch bevor der Finger sich bewegt
     * hat) — der Nutzer sieht, wohin er fahren soll.
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

        if (!swipeEnabled() || samples.isEmpty()) return emptyList()
        if (!SwipePathLogic.isSwipeAllowed(editorInfo)) return emptyList()

        val route = SwipePathLogic.pathLetters(samples, centersFor(baseLetters))
        if (route.isEmpty()) return emptyList()

        val scored = SwipeScorer.score(route, engine)
        if (scored.isNotEmpty()) return scored

        // Kein Wörterbuch-Treffer: die gefahrene Route ist selbst das Wort.
        // Minimaler Score (0.01) sorgt dafür, dass Kandidaten angezeigt werden
        // (sortiert hinten) ohne Auto-Commit — der Nutzer kann dennoch tippen.
        return listOf(SwipeScorer.Candidate(route, 0.01))
    }

    /**
     * Auto-Commit-Entscheidung für die resultierenden Kandidaten. Liefert den
     * zu committenden String oder null (Leiste muss die Kandidaten zeigen).
     */
    fun autoCommitCandidate(candidates: List<SwipeScorer.Candidate>): String? =
        SwipeScorer.autoCommit(candidates)?.lowercase()

    /** Schaltplan-Preview aufbauen (passiv): Knoten-Färbung + Kanten. */
    fun applyPreview(nodes: List<Char>) {
        clearPreview()
        if (!previewEnabled()) return
        if (!SwipePathLogic.isSwipeAllowed(editorInfo)) return
        if (nodes.isEmpty()) return
        paintNodes(nodes, ThemePrefs.swipeColor(prefs))
    }

    /**
     * Während des Wischens (aktiv): die wahrscheinlichen Folge-Tasten der
     * bisher gefahrenen Route als Schaltplan anzeigen – Knoten in der
     * Likely-Farbe (KIND_LIKELY) plus Kanten ([drawEdges]), damit der Finger
     * dem Pfad folgen kann. Die Likely-Knoten landen in [previewNodes] und sind
     * über [wasLikelyHit] abfragbar (gegen den Stand **vor** dem nächsten Treffer).
     * Unabhängig vom Schaltplan-Preview-Pref (das ist eine separate,
     * experimentelle Anzeige); nutzt denselben Restore-Mechanismus wie
     * [applyPreview] (nodeBackgrounds).
     *
     * Wird pro Tastenwechsel (neues Sample) vom KeyboardBinder aufgerufen.
     * Leere Route / keine Engine / Passwort-Feld → nichts (alte Marks bleiben).
     *
     * Früher wurde hier abgebrochen, wenn die passive Schaltplan-Preview jemals
     * an war (`previewNodes.isNotEmpty() && previewEnabled()`) – dann erschienen
     * im Swipe keine most-likely-Ziele mehr. Der Guard ist entfernt: während des
     * Swipens ist diese Anzeige maßgeblich, eine passive Tip-Preview wird durch
     * [clearPreview] sauber abgelöst.
     */
    fun applySwipeLikely() {
        clearPreview()
        if (!SwipePathLogic.isSwipeAllowed(editorInfo)) return
        val route = SwipePathLogic.pathLetters(swipeSamples, centersFor(baseLetters))
        if (route.isEmpty()) return
        val engine = engine ?: return
        // Kandidaten aus der bisherigen Route → wahrscheinliche nächste Tasten.
        val candidates = SwipeScorer.score(route, engine)
        if (candidates.isEmpty()) return
        val nodes = SwipePathLogic.previewNodes(
            candidates.map { SuggestionEngine.Suggestion(it.word, it.score) },
            route.length
        )
        if (nodes.isEmpty()) return
        // Likely-Farbe (dieselbe wie beim Tippen: KIND_LIKELY). Dark-Mode und
        // Default-Farbe aus dem Button-Kontext ableiten; falls kein Button
        // verfügbar (sollte nicht passieren), harter Light-Default (#4CAF50 grün).
        val ctx = baseLetters.keys.firstOrNull()?.context
        val color = if (ctx != null) {
            val dark = ThemePrefs.isDarkMode(ctx)
            ThemePrefs.getColor(
                prefs, dark, ThemePrefs.KIND_LIKELY,
                ThemePrefs.defaultColor(ctx, dark, ThemePrefs.KIND_LIKELY)
            )
        } else 0xFF4CAF50.toInt()
        paintNodes(nodes, color)
    }

    /**
     * Gemeinsamer Kern von [applyPreview] und [applySwipeLikely]: die Knoten
     * merken, ihre Tasten in [color] einfärben (Original-Background in
     * [nodeBackgrounds] sichern) und die Kanten zeichnen.
     *
     * Die Guards (Pref, Passwort-Feld, leere Nodes) bleiben bewusst in den
     * Aufrufern – sie unterscheiden sich je Methode. [color] wird vom Aufrufer
     * aufgelöst, weil die beiden Farbquellen verschieden sind: die Preview
     * nutzt den Legacy-Pref [ThemePrefs.swipeColor], die Likely-Knoten das
     * Theme-Kolor-Schema (KIND_LIKELY, dark/light, Default aus colors.xml).
     */
    private fun paintNodes(nodes: List<Char>, color: Int) {
        previewNodes = nodes
        val dip = baseDip()
        for ((btn, letter) in baseLetters) {
            if (nodes.contains(letter.lowercaseChar())) {
                nodeBackgrounds.add(btn to btn.background)
                btn.background = GradientDrawable().apply {
                    cornerRadius = 8f * dip
                    setColor(color)
                }
            }
        }
        drawEdges()
    }

    /**
     * Wahr, wenn [hit] (der im Swipe gerade erreichte Buchstabe) einer der zuvor
     * durch [applySwipeLikely] als most-likely angezeigten Folge-Tasten war.
     *
     * Aufruf-Reihenfolge im KeyboardBinder (MOVE): [onSwipeMove] →
     * [wasLikelyHit] → [TrailManager.snap] → [applySwipeLikely]. So greift
     * [wasLikelyHit] auf die Likely-Knoten, die **vor** dem aktuellen Treffer
     * galten (vom vorherigen applySwipeLikely-Lauf). Beim ersten Treffer ist
     * [previewNodes] leer → false (kein grüner Trail, da noch kein Ziel stand).
     */
    fun wasLikelyHit(hit: Char): Boolean =
        SwipePathLogic.isLikelyHit(previewNodes, hit)

    /**
     * Kanten zwischen aufeinanderfolgenden Preview-Knoten zeichnen.
     *
     * **Crash-Fix (Dropbox: NPE in `View.<init>`):** Der Container wird
     * ausschließlich über den **Root-View** gesucht. `Button.findViewById`
     * durchsucht nur den *Kind*-Baum einer Taste — ein Button hat keine Kinder,
     * fand `kb_container` also nie und fiel auf `rootView` zurück. Zusätzlich
     * wird das Overlay nur in einen **angehängten, gemessenen** Container
     * gehängt, und der Context wird explizit geprüft: ein null-Context führte
     * zu `NullPointerException: Context.getResources() on a null object
     * reference` tief in `View.<init>` und riss die IME beim Tippen ab.
     */
    private fun drawEdges() {
        // Kanten brauchen mindestens zwei Knoten (ein einzelner Knoten hat keine Kante).
        if (previewNodes.size < 2) { removeEdgeOverlay(); return }
        val btn0 = baseLetters.keys.firstOrNull() ?: run { removeEdgeOverlay(); return }
        val root = btn0.rootView
        val container = root.findViewById<View>(R.id.kb_container) as? ViewGroup
            ?: (root as? ViewGroup)
            ?: run { removeEdgeOverlay(); return }
        // Nur in einen angehängten, gemessenen Container zeichnen: sonst steht
        // die Geometrie nicht fest (Breite 0) und ein Rebuild reißt es weg.
        if (!container.isAttachedToWindow || container.width == 0) {
            removeEdgeOverlay(); return
        }
        val ctx = container.context ?: run { removeEdgeOverlay(); return }
        val path = SwipeEdgeDrawing.buildEdgePath(previewNodes, container, baseLetters)
        if (path == null) { removeEdgeOverlay(); return }
        // Nicht doppelt einfügen: falls noch ein altes Overlay hängt (z. B. nach
        // schnell aufeinanderfolgenden Updates), erst entfernen.
        removeEdgeOverlay()
        val ov = SwipeEdgeOverlay(ctx, path, ThemePrefs.swipeEdgeColor(prefs))
        container.addView(ov, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        edgeOverlay = ov
    }

    /** Hängt das Kanten-Overlay aus dem View-Baum (idempotent, null-sicher). */
    private fun removeEdgeOverlay() {
        edgeOverlay?.let { ov -> (ov.parent as? ViewGroup)?.removeView(ov) }
        edgeOverlay = null
    }

    /** Entfernt die aktuelle Preview (Knoten + Kanten). */
    fun clearPreview() {
        for ((btn, bg) in nodeBackgrounds) {
            if (btn.isAttachedToWindow) btn.background = bg
        }
        nodeBackgrounds.clear()
        previewNodes = emptyList()
        removeEdgeOverlay()
    }

    /** Setzt die Swipe-Samples zurück (z. B. bei LongPress-Auslösung). */
    fun clearSwipeSamples() {
        swipeSamples.clear()
        swipeStartLoc = null
        lastKeyCenter = null
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

    /** @see SwipeKeyGeometry.baseDip */
    private fun baseDip(): Float = SwipeKeyGeometry.baseDip(baseLetters)

    /** @see SwipeKeyGeometry.minSwipeDistPx */
    private fun minSwipeDistPx(): Float =
        SwipeKeyGeometry.minSwipeDistPx(baseLetters)

    /** @see SwipeKeyGeometry.centersFor */
    private fun centersFor(baseLetters: Map<Button, Char>): List<SwipePathLogic.KeyCenter> =
        SwipeKeyGeometry.centersFor(baseLetters)

    /** @see SwipeKeyGeometry.charAt */
    private fun charAt(x: Float, y: Float): Char? =
        SwipeKeyGeometry.charAt(x, y, baseLetters)

    /** Setzt die aktuelle Engine (vom Service bei Engine-Ready / Feldwechsel). */
    fun setEngine(e: SuggestionEngine?) { engine = e }

    /** Aktuelle Engine (vom Service gesetzt). */
    var engine: SuggestionEngine? = null
        private set
}
 