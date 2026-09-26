package com.piotv.keytab.ime

/**
 * Pure Parameter + Intervall-Rechnung für das beschleunigende Wort-Löschen
 * auf der Del-Taste (Android-frei, JUnit-testbar).
 *
 * Refactoring (docs/REFACTORING_PLAN.md Phase 3): die Konstanten aus
 * [KeyboardBinder] und die Intervall-Berechnung aus [RepeatScheduler.next]
 * hierher verschoben – eine einzige Quelle für die Repeat-Timing-Werte.
 *
 * Verhalten bleibt bit-identisch („Umziehen statt Umschreiben").
 */
internal object WordDeleteRepeat {
    /** Startintervall des ersten Word-Repeats (ms). */
    internal const val WORD_DELETE_START_MS = 250L

    /** Faktor, mit dem das Intervall pro Repeat kleiner wird. */
    internal const val WORD_DELETE_ACCEL = 0.85f

    /** Untergrenze des Intervalls (ms). */
    internal const val WORD_DELETE_MIN_MS = 30L

    /**
     * Nächstes Intervall berechnen (ab nächstem Repeat gültig):
     * [intervalMs] mal [accel], nie unter [minMs].
     */
    internal fun nextDelayMs(intervalMs: Long, accel: Float = WORD_DELETE_ACCEL, minMs: Long = WORD_DELETE_MIN_MS): Long =
        (intervalMs * accel).toLong().coerceAtLeast(minMs)
}
