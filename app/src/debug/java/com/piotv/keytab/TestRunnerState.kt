package com.piotv.keytab

/**
 * Gemeinsamer Zustand des Test-Runners (nur **Debug**-Variante, `src/debug`).
 *
 * Die instrumentierten Tests melden sich am Testanfang über [announce] an und
 * rufen vor jedem Tastendruck [awaitResume]. [ImeTargetActivity] zeigt Name und
 * Erwartung an und bedient Pause/Weiter/Play; der Beobachter kann im Notizfeld
 * beschreiben, wenn der Verlauf abweicht.
 *
 * Alles ist `@Volatile`: die Tests laufen im Instrumentation-Thread, die
 * Oberfläche im UI-Thread — ohne das sähe der Poll-Schleife den Pause-Klick nie.
 */
object TestRunnerState {
    /** Lauf pausiert (vom Pause-Knopf gesetzt). */
    @Volatile
    var paused: Boolean = false
        private set

    /** Genau ein Schritt trotz Pause (Weiter-Knopf). */
    @Volatile
    private var stepOnce: Boolean = false

    /** Name des laufenden Tests (Methodenname). */
    @Volatile
    var currentTest: String = ""
        private set

    /** Was in diesem Test passieren soll (Anzeige für den Beobachter). */
    @Volatile
    var expectation: String = ""
        private set

    /** Freitext des Beobachters: Abweichungen im Verlauf. */
    @Volatile
    var notes: String = ""
        private set

    /** Wird nach jeder Änderung gerufen, damit die Oberfläche nachzieht. */
    @Volatile
    var onChange: (() -> Unit)? = null

    /** Test angemeldet: Name + Erwartung setzen, Notiz leeren, nicht pausieren. */
    fun announce(test: String, expectation: String) {
        currentTest = test
        this.expectation = expectation
        notes = ""
        paused = false
        stepOnce = false
        onChange?.invoke()
    }

    fun pause() {
        paused = true
        onChange?.invoke()
    }

    fun resume() {
        paused = false
        stepOnce = false
        onChange?.invoke()
    }

    /** Einen einzelnen Schritt freigeben, danach wieder pausiert bleiben. */
    fun step() {
        stepOnce = true
        onChange?.invoke()
    }

    fun setNotes(value: String) {
        notes = value
    }

    /**
     * Blockiert, solange pausiert ist. Wird vor jedem Tastendruck gerufen, damit
     * der Beobachter genau an einer Taste anhalten kann. Ist nicht pausiert,
     * kostet der Aufruf nur einen volatile-Read.
     */
    fun awaitResume() {
        if (!paused) return
        while (paused) {
            if (stepOnce) {
                stepOnce = false
                return
            }
            Thread.sleep(50)
        }
    }
}
