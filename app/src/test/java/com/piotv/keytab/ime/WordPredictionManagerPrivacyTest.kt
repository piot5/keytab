package com.piotv.keytab.ime

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * **Harte Sicherheitsregel für Lernen, Vorschläge und Autokorrektur.**
 *
 * Trail und Swipe waren schon immer in Passwort-Feldern (und bei
 * `IME_FLAG_NO_PERSONALIZED_LEARNING`) abgeschaltet ([TrailLogic.isTrailAllowed]).
 * Die drei Pfade, die die Eingabe *auswerten* oder *speichern*, hatten diese
 * Prüfung nicht:
 *  - Wortlernen ins User-Dictionary ([WordPredictionManager.onWordCompleted]),
 *  - Wortvorschläge in der Leiste ([WordPredictionManager.updateSuggestions]),
 *  - aktive Autokorrektur beim Space ([WordPredictionManager.autoCorrectBeforeSpace]).
 *
 * Diese Suite fixiert, dass alle drei Pfade dieselbe Regel respektieren — und
 * enthält für jeden Pfad eine **Gegenprobe** in einem normalen Feld. Die
 * Gegenprobe ist wesentlich: ein stumpfes `return` würde die Sperre sonst
 * „bestehen“, obwohl die Funktion gar nicht mehr arbeitet.
 *
 * `allowed = false` steht im Test-Rig für ein Passwort-/Sensibel-Feld
 * ([TrailLogic.isPersonalizedProcessingAllowed] == false); die Verdrahtung
 * dieser Regel mit dem echten EditorInfo ist in `TrailLogicTest` abgedeckt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WordPredictionManagerPrivacyTest {

    /** Minimal-Feld: Insert/Delete am Cursor, beide Lösch-Wege identisch. */
    private class FakeField(private var text: String) : WordPredictionManager.InputOperations {
        val inserts = mutableListOf<String>()
        private var cursor = text.length

        override fun deleteBefore(count: Int) {
            val start = (cursor - count).coerceAtLeast(0)
            text = text.removeRange(start, cursor)
            cursor = start
        }

        override fun deleteBeforeKeys(count: Int) = deleteBefore(count)

        override fun textBefore(count: Int): String =
            text.substring((cursor - count).coerceAtLeast(0), cursor)

        override fun insert(text: String) {
            this.text = this.text.substring(0, cursor) + text + this.text.substring(cursor)
            cursor += text.length
            inserts.add(text)
        }

        override fun commitToApp(text: String) = insert(text)

        fun value(): String = text
    }

    /** Test-Rig: Manager mit Feld, Vorschlags-Slots, Leiste und Feld-Freigabe. */
    private class Rig(text: String, allowed: Boolean, withViews: Boolean) {
        val app = RuntimeEnvironment.getApplication()
        val field = FakeField(text)
        val views: Array<TextView?> =
            if (withViews) arrayOf(TextView(app), TextView(app), TextView(app)) else arrayOfNulls(3)
        val bar = View(app)
        val manager = WordPredictionManager(
            app,
            java.util.concurrent.Executor { it.run() },   // synchron: Engine sofort bereit
            Handler(Looper.getMainLooper()),
            views,
            field,
            personalizedProcessingAllowed = { allowed })

        /** Lädt den echten deutschen Korpus (Asset) und prüft, dass er da ist. */
        fun loadEngine() {
            manager.loadEngine(Languages.de, forceReload = true)
            assertTrue("Engine muss geladen sein", manager.engine != null)
            assertTrue(
                "Deutscher Korpus muss lesbar sein (sonst sind die Gegenproben blind)",
                manager.engine!!.baseScore("haus") > 0.0
            )
        }

        fun type(word: String) {
            for (c in word) manager.onCharacter(c.toString())
        }
    }

    // ---------- 1. Wortlernen ----------

    @Test
    fun `Passwortfeld lernt das abgeschlossene Wort nicht`() {
        val rig = Rig("", allowed = false, withViews = false)
        rig.loadEngine()
        rig.type("hausx")
        rig.manager.onWordCompleted()

        assertTrue(
            "Im Passwortfeld darf nichts im User-Dictionary landen",
            rig.manager.engine!!.userFreq.isEmpty()
        )
        assertEquals("", rig.manager.currentTypedWord)
    }

    @Test
    fun `normales Feld lernt weiterhin (Gegenprobe)`() {
        val rig = Rig("", allowed = true, withViews = false)
        rig.loadEngine()
        rig.type("zebrawort")
        rig.manager.onWordCompleted()

        assertTrue(
            "Ohne Lernen waere die Sperre nur ein stumpfes return",
            rig.manager.engine!!.userFreq.containsKey("zebrawort")
        )
    }

    // ---------- 2. Vorschlags-Uebernahme ----------

    @Test
    fun `Passwortfeld uebernimmt einen Vorschlag ohne zu lernen`() {
        // Kontext "der " davor: kein Satzanfang, damit die Gross-/Kleinschreibung
        // nicht mit dem hier geprueften Verhalten vermischt wird.
        val rig = Rig("der hauss", allowed = false, withViews = false)
        rig.loadEngine()
        rig.type("hauss")
        rig.manager.applySuggestion("haus")

        assertEquals("der haus ", rig.field.value())
        assertTrue(
            "Vorschlags-Uebernahme darf im Passwortfeld nichts ins Dictionary schreiben",
            rig.manager.engine!!.userFreq.isEmpty()
        )
    }

    @Test
    fun `normales Feld lernt die Vorschlags-Uebernahme weiterhin (Gegenprobe)`() {
        val rig = Rig("der hauss", allowed = true, withViews = false)
        rig.loadEngine()
        rig.type("hauss")
        rig.manager.applySuggestion("haus")

        assertEquals("der haus ", rig.field.value())
        assertTrue(rig.manager.engine!!.userFreq.containsKey("haus"))
    }

    // ---------- 3. Aktive Autokorrektur ----------

    @Test
    fun `Passwortfeld korrigiert nicht beim Space`() {
        val rig = Rig("hauss", allowed = false, withViews = false)
        rig.loadEngine()
        rig.type("hauss")

        assertFalse("keine Autokorrektur im Passwortfeld", rig.manager.autoCorrectBeforeSpace())
        assertEquals("hauss", rig.field.value())
    }

    @Test
    fun `abgeschaltete Autokorrektur ersetzt beim Space nicht`() {
        val prefs = com.piotv.keytab.Prefs.of(RuntimeEnvironment.getApplication())
        prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_AUTOCORRECT, false).commit()
        try {
            val rig = Rig("hauss", allowed = true, withViews = false)
            rig.loadEngine()
            rig.type("hauss")

            assertFalse("Einstellung muss Autokorrektur deaktivieren", rig.manager.autoCorrectBeforeSpace())
            assertEquals("hauss", rig.field.value())
        } finally {
            prefs.edit().putBoolean(com.piotv.keytab.Prefs.KEY_AUTOCORRECT, true).commit()
        }
    }

    @Test
    fun `normales Feld korrigiert weiterhin (Gegenprobe)`() {
        val rig = Rig("hauss", allowed = true, withViews = false)
        rig.loadEngine()
        rig.type("hauss")

        assertTrue("Autokorrektur muss erhalten bleiben", rig.manager.autoCorrectBeforeSpace())
        // Welches Korpuswort die Engine waehlt (Naehe vs. Frequenz) ist nicht Teil
        // dieser Regel — geprueft wird: es wurde ersetzt, mit Leerzeichen, und das
        // Ergebnis ist ein echtes Wörterbuchwort.
        val korrigiert = rig.field.value().trim()
        assertNotEquals("hauss", korrigiert)
        assertTrue("Korrektur endet mit Leerzeichen", rig.field.value().endsWith(" "))
        assertTrue(
            "Ergebnis muss ein Korpuswort sein (nicht 'hauss')",
            rig.manager.engine!!.baseScore(korrigiert.lowercase()) > 0.0
        )
    }

    // ---------- 4. Vorschlagsleiste ----------

    @Test
    fun `Passwortfeld zeigt keine Vorschlaege und versteckt die Leiste`() {
        val rig = Rig("", allowed = false, withViews = true)
        rig.loadEngine()
        rig.type("h")
        rig.manager.updateSuggestions(rig.bar, enabled = true)

        assertEquals(View.GONE, rig.bar.visibility)
        assertTrue(rig.manager.currentSuggestions.isEmpty())
        for (v in rig.views) {
            assertTrue("Slot muss existieren", v != null)
            assertFalse(
                "Chip darf im Passwortfeld nicht sichtbar sein",
                v!!.visibility == View.VISIBLE
            )
        }
    }

    @Test
    fun `normales Feld zeigt Vorschlaege weiterhin (Gegenprobe)`() {
        val rig = Rig("", allowed = true, withViews = true)
        rig.loadEngine()
        rig.type("h")
        rig.manager.updateSuggestions(rig.bar, enabled = true)

        assertEquals(View.VISIBLE, rig.bar.visibility)
        assertFalse("Zu 'h' muss es Vorschlaege geben", rig.manager.currentSuggestions.isEmpty())
        assertTrue(rig.views.any { it?.visibility == View.VISIBLE })
    }
}
