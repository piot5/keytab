package com.piotv.keytab.ime

import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Laden der Vorschlags-Engine beim Sprachwechsel (Regression v0.18).
 *
 * Der Manager lädt die Engine asynchron. Kommt der Auftrag einer **alten**
 * Sprache nach dem einer neuen zurück, darf er die Engine nicht mehr setzen —
 * sonst stünde die Engine der alten Sprache unter dem neuen `engineLanguage`
 * und Vorschläge/Autokorrektur liefen in der falschen Sprache.
 *
 * Der [QueueExecutor] macht das Rennen deterministisch: Aufträge werden nur
 * gesammelt und in frei wählbarer Reihenfolge ausgeführt. `und` kommt nur im
 * deutschen, `the` nur im englischen Korpus vor — damit ist belegbar, *welche*
 * Engine gerade aktiv ist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WordPredictionManagerEngineTest {

    /** Sammelt Aufgaben und führt sie erst auf Kommando aus. */
    private class QueueExecutor : Executor {
        val tasks = mutableListOf<Runnable>()
        override fun execute(command: Runnable) { tasks += command }
        fun run(index: Int) = tasks[index].run()
    }

    /** Eingabefeld-Attrappe: die Engine-Tests tippen nichts. */
    private class SilentField : WordPredictionManager.InputOperations {
        override fun deleteBefore(count: Int) = Unit
        override fun deleteBeforeKeys(count: Int) = Unit
        override fun textBefore(count: Int): String = ""
        override fun insert(text: String) = Unit
        override fun commitToApp(text: String) = Unit
    }

    private fun manager(executor: Executor) = WordPredictionManager(
        RuntimeEnvironment.getApplication(),
        executor,
        Handler(Looper.getMainLooper()),
        arrayOfNulls(3),
        SilentField()
    )

    @Test
    fun `verspaetete Engine einer alten Sprache wird verworfen`() {
        val io = QueueExecutor()
        val manager = manager(io)

        manager.loadEngine(Languages.de)                        // Auftrag 0: Deutsch
        manager.loadEngine(Languages.en, forceReload = true)    // Auftrag 1: Englisch (neuer)
        assertEquals("beide Ladeauftraege muessen eingereiht sein", 2, io.tasks.size)

        io.run(1)                                               // Englisch ist zuerst fertig
        val english = manager.engine
        assertTrue("englischer Korpus muss geladen sein", english!!.baseScore("the") > 0.0)

        io.run(0)                                               // Deutsch kommt verspaetet zurueck
        assertTrue(
            "die verspaetete deutsche Engine darf die englische nicht ersetzen",
            manager.engine === english
        )
        assertEquals("englische Engine bleibt aktiv", 0.0, manager.engine!!.baseScore("und"), 0.0)
    }

    @Test
    fun `Sprachwechsel ohne Rennen laedt die neue Sprache`() {
        val io = QueueExecutor()
        val manager = manager(io)

        manager.loadEngine(Languages.de)
        io.run(0)
        assertTrue("deutscher Korpus muss geladen sein", manager.engine!!.baseScore("und") > 0.0)

        manager.loadEngine(Languages.en, forceReload = true)
        io.run(1)
        assertTrue("englischer Korpus muss geladen sein", manager.engine!!.baseScore("the") > 0.0)
        assertEquals("deutsche Woerter sind weg", 0.0, manager.engine!!.baseScore("und"), 0.0)
    }

    @Test
    fun `releaseEngine gibt die Engine frei und der naechste Aufruf laedt neu`() {
        val io = QueueExecutor()
        val manager = manager(io)

        manager.loadEngine(Languages.de)
        io.run(0)
        assertTrue("Engine geladen", manager.engine != null)

        // Speicherdruck/Tastatur versteckt (onTrimMemory): Korpus freigeben.
        manager.releaseEngine()
        assertEquals("Engine ist freigegeben", null, manager.engine)

        // Ohne forceReload lädt der nächste Aufruf neu, weil auch engineLanguage
        // verworfen wurde (sonst würde der Guard sofort zurückkehren).
        manager.loadEngine(Languages.de)
        assertEquals("neuer Ladeauftrag eingerieht", 2, io.tasks.size)
        io.run(1)
        assertTrue("Engine wieder geladen", manager.engine!!.baseScore("und") > 0.0)
    }
}
