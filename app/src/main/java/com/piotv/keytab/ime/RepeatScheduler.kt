package com.piotv.keytab.ime

/**
 * Pure Zeitlogik für den beschleunigenden Del-Repeat (Android-frei, JUnit-testbar).
 *
 * Refactoring (docs/REFACTORING_PLAN.md Phase 3): die Zeit-/Intervall-Berechnung
 * aus [KeyTabImeService.setupDelButton] extrahiert. Der Service ruft [interval]
 * nach jedem Repeat; [next] liefert das nächste Intervall (kleiner werdend,
 * bis zum Minimum) – die Handler-Post-Logik bleibt beim Aufrufer ([KeyboardBinder]).
 *
 * Verhalten bleibt bit-identisch zum Original („Umziehen statt Umschreiben").
 */
class RepeatScheduler(
    private val startMs: Long,
    private val accel: Float,
    private val minMs: Long
) {
    private var current = startMs

    /** Aktuelles Intervall (beim ersten Repeat = Startwert). */
    val interval: Long get() = current

    /** Nächstes Intervall berechnen (ab nächstem Repeat gültig). */
    fun next() {
        current = (current * accel).toLong().coerceAtLeast(minMs)
    }

    /** Reset auf Startintervall (neuer Long-Press-Beginn). */
    fun reset() {
        current = startMs
    }
}
