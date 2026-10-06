package com.piotv.keytab

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

/**
 * Meldet jeden Test an den Test-Runner (Anzeige im Debug-Host
 * [ImeTargetActivity]) und protokolliert die Abweichungs-Notiz am Testende.
 *
 *  * [starting] — Name + Erwartung an [TestRunnerState] melden: der Beobachter
 *    sieht VOR dem ersten Tastendruck, was passieren soll.
 *  * [finished] — Notiz aus dem Notizfeld protokollieren: auf stdout (landet im
 *    Instrumentation-Output/CI-Log) und in `keytab_test_notes.txt` im
 *    App-Verzeichnis, damit der Host sie abholen kann.
 *
 * Pausieren: [TestRunnerState.awaitResume] rufen die Klick-Helfer der
 * Testklassen vor jedem Tastendruck — der Beobachter kann also an jeder Taste
 * anhalten, den Verlauf im Notizfeld beschreiben und weiterlaufen lassen.
 */
class TestRunnerRule : TestWatcher() {

    override fun starting(description: Description) {
        TestRunnerState.announce(
            description.methodName,
            TestExpectations.of(description.methodName)
        )
        // Beobachter-Pause: wer pausiert hat, sieht die Beschreibung, bevor der
        // erste Tastendruck passiert.
        TestRunnerState.awaitResume()
    }

    override fun finished(description: Description) {
        val note = TestRunnerState.notes.trim().replace('\n', ' ')
        val line = description.methodName +
            (if (note.isEmpty()) " | ok" else " | ABWEICHUNG | $note")
        println("TESTRUNNER $line")
        runCatching {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.filesDir, "keytab_test_notes.txt").appendText("$line\n")
        }
    }
}
