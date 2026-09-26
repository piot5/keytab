package com.piotv.keytab.ime

import android.view.MotionEvent
import android.widget.Button

/**
 * Touch-Logik der Buchstaben-Tasten: Tap, Long-Press (Drag-Popup bzw.
 * Auto-Select im Swipe-Modus) und Swipe-Erkennung.
 *
 * Refactoring (docs/REFACTORING_PLAN.md Phase 3): aus [KeyboardBinder.setupLetterButton]
 * extrahiert. Pro Buchstaben-Taste wird eine eigene Instanz erzeugt – die
 * Zustandsflags (longPressFired/autoSelected/swipeActive) gelten also je Taste,
 * genau wie vorher die lokalen Variablen in [KeyboardBinder.setupLetterButton].
 *
 * Verhalten bleibt bit-identisch („Umziehen statt Umschreiben").
 *
 * @param handlers Callbacks/Referenzen, die [KeyboardBinder] liefert.
 */
internal class LetterButtonTouchHandler(
    private val host: KeyboardInputHost,
    private val longPressTimeout: Long,
    private val suggestionController: SuggestionController,
    private val trailManager: TrailManager?,
    private val swipeManager: SwipeManager?,
    private val handlers: Handlers
) {
    /**
     * Bündel der Callbacks, die [KeyboardBinder] pro Buchstaben-Taste injiziert.
     * Hält den Konstruktor von [LetterButtonTouchHandler] unter der
     * detekt-LongParameterList-Schwelle.
     *
     * @param onTapLetter Hauptbuchstabe der Taste (Shift/Caps beachten).
     * @param onAutoSelectExtra Long-Press im Swipe-Modus: erstes Sonderzeichen committen.
     * @param onShowLetterExtras Long-Press im Popup-Modus: Drag-Popup öffnen.
     * @param onTraceTrail Treffer-Markierung nach einem Tap (Trail/Passwort-Schutz).
     */
    internal data class Handlers(
        val onTapLetter: (Button) -> String,
        val onAutoSelectExtra: (Button) -> Unit,
        val onShowLetterExtras: (Button) -> Unit,
        val onTraceTrail: () -> Unit
    )

    /** Mutierbarer Touch-Zustand je Buchstaben-Taste (entspricht den früheren lokalen Variablen). */
    private class TouchState {
        var pendingLongPress: Runnable? = null
        var longPressFired = false
        var autoSelected = false
        var swipeActive = false
    }

    /** Touch-Listener an [btn] hängen (State je Taste). */
    fun attach(btn: Button) {
        val state = TouchState()
        btn.setOnTouchListener { _, event -> onTouch(btn, event, state) }
    }

    /** Dispatch des Touch-Events an die Aktions-Handler. */
    private fun onTouch(btn: Button, event: MotionEvent, state: TouchState): Boolean {
        // Swipe-Koordinaten in Fenster-Relativ (wie Trail/LetterPopup).
        val loc = IntArray(2); btn.getLocationInWindow(loc)
        val x = loc[0] + event.x
        val y = loc[1] + event.y
        val swipeOn = swipeManager != null && swipeManager.swipeEnabled()
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> onActionDown(btn, x, y, swipeOn, state)
            MotionEvent.ACTION_MOVE -> onActionMove(event, x, y, swipeOn, state)
            MotionEvent.ACTION_UP -> onActionUp(btn, state)
            MotionEvent.ACTION_CANCEL -> onActionCancel(btn, state)
            else -> false
        }
    }

    private fun onActionDown(
        btn: Button,
        x: Float,
        y: Float,
        swipeOn: Boolean,
        state: TouchState
    ): Boolean {
        state.longPressFired = false
        state.autoSelected = false
        state.swipeActive = false
        btn.isPressed = true
        // Swipe starten (Manager prüft selbst Pref + Passwort-Feld).
        swipeManager?.startSwipe(x, y)
        // Most-likely-Ziel schon ab der ersten Taste anzeigen: die
        // gedrückte Taste als erstes Routen-Sample vorbelegen, damit
        // sofort sichtbar ist, wohin der Finger fahren sollte.
        // Wichtig: Das ist noch KEIN Swipe — ein reiner Tap hat genau
        // dieses eine Sample und wird über hasSwiped() als Tap erkannt.
        if (swipeOn) {
            host.baseLetters[btn]?.let { letter ->
                swipeManager?.seedFirstKey(letter, x, y)
                swipeManager?.applySwipeLikely()
            }
        }
        state.pendingLongPress = Runnable { runLongPress(btn, swipeOn, state) }
        state.pendingLongPress?.let { host.longPressHandler.postDelayed(it, longPressTimeout) }
        return true
    }

    /** Delayed-Runnable des Long-Press: Auto-Select (Swipe-Modus) oder Drag-Popup. */
    private fun runLongPress(btn: Button, swipeOn: Boolean, state: TouchState) {
        state.longPressFired = true
        if (swipeOn) {
            // Swipe-Modus: Long-Press öffnet KEIN Drag-Popup (das
            // würde den Swipe nach 400ms abbrechen). Stattdessen wird
            // das erste Sonderzeichen automatisch ausgewählt und
            // committet — Drücken-und-Halten ohne Bewegung gibt also
            // direkt das erste Sonderzeichen. Bewegt sich der Finger
            // (Swipe), wurde dieser Runnable bereits in MOVE abgebrochen.
            state.autoSelected = true
            swipeManager?.clearSwipeSamples()
            state.swipeActive = false
            handlers.onAutoSelectExtra(btn)
        } else {
            // Kein Swipe-Modus: klassisches Drag-Popup wie bisher.
            swipeManager?.clearSwipeSamples()
            state.swipeActive = false
            handlers.onShowLetterExtras(btn)
        }
    }

    private fun onActionMove(
        event: MotionEvent,
        x: Float,
        y: Float,
        swipeOn: Boolean,
        state: TouchState
    ): Boolean {
        if (state.longPressFired) {
            // Nur im Popup-Modus (nicht swipeOn) gibt es Zellen zu highlighten.
            if (!state.autoSelected) host.letterPopup.highlightCellUnder(event) { host.haptic() }
        } else if (swipeOn) {
            onSwipeMove(x, y, state)
        }
        return true
    }

    private fun onSwipeMove(x: Float, y: Float, state: TouchState) {
        val hit = swipeManager?.onSwipeMove(x, y)
        state.swipeActive = swipeManager?.hasSwiped() ?: false
        // Sobald der Swipe wirklich losgeht (Samples gesammelt),
        // den Auto-Select-LongPress abbrechen — sonst würde nach
        // 400ms ein Sonderzeichen committet, obwohl der Nutzer wischt.
        if (state.swipeActive) {
            state.pendingLongPress?.let { host.longPressHandler.removeCallbacks(it) }
                            // Trail-Snap pro Tastenwechsel: die gefahrene Route wird
            // live als farbige Spur sichtbar — so sieht man, welche
            // Tasten bereits aktiviert wurden (wie beim normalen Tippen).
            if (hit != null) {
                // Konsistenz Trail ↔ Swipe: war die erreichte Taste
                // ein zuvor als most-likely angezeigtes Ziel, wird
                // der Trail grün (ACCEPTED) statt blau (TYPED) – die
                // gefahrene Route leuchtet grün, solange man dem
                // Schaltplan folgt. wasLikelyHit muss VOR
                // applySwipeLikely stehen, weil jenes die Likely-
                // Knoten für die verlängerte Route neu berechnet.
                val wasLikely = swipeManager?.wasLikelyHit(hit) == true
                trailManager?.snap(
                    hit,
                    if (wasLikely) TrailLogic.TrailKind.ACCEPTED
                    else TrailLogic.TrailKind.TYPED
                )
                // Likely-Grün: die wahrscheinlichen Folge-Tasten der
                // bisherigen Route einfärben (wie beim Tippen).
                swipeManager?.applySwipeLikely()
            }
        }
    }

    private fun onActionUp(btn: Button, state: TouchState): Boolean {
        btn.isPressed = false
        state.pendingLongPress?.let { host.longPressHandler.removeCallbacks(it) }
        when {
            state.longPressFired -> onLongPressRelease(state)
            state.swipeActive -> onSwipeRelease(btn)
            else -> {
                // SwipeSamples verwerfen (Mindestbewegung nicht erreicht → Tap).
                swipeManager?.clearSwipeSamples()
                commitTap(btn)
            }
        }
        btn.clearFocus()
        return true
    }

    /** Nach Long-Press: Auto-Select (Swipe) hat schon committet, sonst Drag-Auswahl committen. */
    private fun onLongPressRelease(state: TouchState) {
        if (state.autoSelected) {
            // Swipe-Modus: Auto-Select hat bereits committet (kein
            // Popup). Nur aufräumen — nichts doppelt committen.
            return
        }
        // Drag-Auswahl: markierte Zelle committen, sonst Tooltip nur schließen
        val picked = host.letterPopup.pickedChar()
        host.letterPopup.dismiss()
        host.letterPopup.clearPicked()
        if (picked != null) {
            host.commitText(picked.toString())
            trailManager?.snap(picked.lowercaseChar())
            handlers.onTraceTrail()
        }
    }

    /** Swipe-Release: Route bewerten, Auto-Commit oder Kandidaten-Leiste. */
    private fun onSwipeRelease(btn: Button) {
        // Likely-Marks (während des Wischens gesetzt) entfernen.
        swipeManager?.clearPreview()
        val candidates = swipeManager?.onSwipeRelease().orEmpty()
        val auto = swipeManager?.autoCommitCandidate(candidates)
        if (auto != null) {
            // Konsistent zum normalen Wort-Abschluss: das Swipe-Wort
            // über den Vorschlags-Weg einfügen → Leerzeichen,
            // Wort-Lernen und frische Wortvorschläge (statt rohem
            // commitText ohne Trenner und ohne Lernschritt).
            suggestionController.applySuggestion(auto)
        } else if (candidates.isNotEmpty()) {
            suggestionController.showSwipeCandidates(
                candidates.take(3).map { it.word })
        } else {
            // Swipe ohne Kandidat → Fallback auf normalen Tap (kein Buchstabe verloren).
            commitTap(btn)
        }
    }

    /** Normaler Tap: Buchstabe committen + Trail/Trace-Markierung. */
    private fun commitTap(btn: Button) {
        val letter = handlers.onTapLetter(btn)
        host.commitText(letter)
        trailManager?.snap(letter.first().lowercaseChar())
        handlers.onTraceTrail()
    }

    private fun onActionCancel(btn: Button, state: TouchState): Boolean {
        btn.isPressed = false
        state.pendingLongPress?.let { host.longPressHandler.removeCallbacks(it) }
        host.letterPopup.dismiss()
        host.letterPopup.clearPicked()
        swipeManager?.clearSwipeSamples()
        swipeManager?.clearPreview()
        state.swipeActive = false
        btn.clearFocus()
        return true
    }
}
