package com.piotv.keytab.ime

/**
 * Shift-/CapsLock-Zustandsmaschine – rein, ohne Android-View-Abhängigkeit.
 *
 * Verarbeitet die Übergänge, die zuvor inline im [KeyTabImeService] lagen
 * (Refactoring, docs/REFACTORING_PLAN.md Phase 2):
 * - [tapShift]      – Shift-Taste: Doppel-Tap ⇒ CapsLock, sonst Toggle/Exit
 * - [consume]       – nach übernommenen Zeichen: einzelne Shift-Eingabe zurücksetzen
 * - [resetForInput] – beim Start in ein Feld: ggf. Auto-Caps, CapsLock aus
 *
 * Sichtbare Ausgabe (Alpha/Bold) macht der Aufrufer über die State-Getter.
 * [SHIFT_DOUBLE_TAP_MS] ist konfigurierbar (Testbarkeit) statt hartkodiert.
 */
internal class ShiftController(
    private val doubleTapMs: Long
) {
    class ShiftState(
        val shifted: Boolean,
        val capsLock: Boolean
    )

    private var shifted = false
    private var capsLock = false
    // Sehr weit in der Vergangenheit (aber ohne Long-Overflow bei `now - lastTap`;
    // Long.MIN_VALUE würde überlaufen und fälschlich als Doppel-Tap gelten) →
    // der ERSTE Tap ist nie ein Doppel-Tap.
    private var lastTap = -1_000_000_000L

    /** Zustand für den View (Alpha/Bold-Ableitungen). */
    fun state(): ShiftState = ShiftState(shifted, capsLock)

    /** true, wenn Buchstaben als Groß dargestellt werden (Shift ODER CapsLock). */
    fun isUpper(): Boolean = shifted || capsLock

    /**
     * Shift-Taste gedrückt. [now] ist der Zeitstempel in gleicher Einheit wie der
     * Abstand zu [doubleTapMs]; Rückgabe = neuer Zustand nach Übergang.
     */
    fun tapShift(now: Long): ShiftState {
        if (now - lastTap <= doubleTapMs) {
            // Doppel-Tipp = Caps Lock
            capsLock = true
            shifted = true
            lastTap = -1_000_000_000L
        } else if (capsLock) {
            // einzelner Tipp verlässt Caps Lock
            capsLock = false
            shifted = false
            lastTap = now
        } else {
            shifted = !shifted
            lastTap = now
        }
        return state()
    }

    /** Einzelne Shift-Eingabe zurücksetzen; Caps Lock bleibt. */
    fun consume() {
        if (shifted && !capsLock) shifted = false
    }

    /** Feld-Start: CapsLock aus, evtl. Auto-Caps-Initialisierung. */
    fun resetForInput(autoCapitalize: Boolean) {
        capsLock = false
        shifted = autoCapitalize
        lastTap = -1_000_000_000L
    }
}