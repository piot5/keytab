package com.piotv.keytab.ime

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * TrailManager mit echten Calls (keine Map-Simulation): snap-Decay,
 * Kind-Schutz (ACCEPTED wird nicht von TYPED ueberschrieben), atomares
 * traceWord, Passwort-Schutz, setEditorInfo-Wechsel, clear.
 *
 * Ergaenzt TrailLogicTest (reine Formel/Klassifikation) um die
 * Zustandsmaschine in TrailManager (snap/traceWord/clearTrace/clear).
 * Leere baseLetters-Map: applyToKeyboard/clearKeyboard sind No-Ops,
 * der Zustand wird ueber snapshotForTest() geprueft.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrailManagerTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    private fun prefs() =
        app.getSharedPreferences(com.piotv.keytab.Prefs.FILE, Context.MODE_PRIVATE)

    @Before
    fun clearPrefs() { prefs().edit().clear().commit() }

    private fun enableTrail(trace: Boolean = false) {
        prefs().edit()
            .putBoolean(ThemePrefs.KEY_TRAIL, true)
            .putBoolean(ThemePrefs.KEY_TRAIL_TRACE, trace)
            .commit()
    }

    private fun manager(): TrailManager = TrailManager(prefs(), emptyMap())

    private fun engine(vararg words: String) =
        SuggestionEngine(words.map { it to 1000 })

    private fun passwordInfo(): EditorInfo = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    @Test
    fun `snap setzt TYPED mit Schritt 0`() {
        enableTrail()
        val m = manager()
        m.snap('a')
        val s = m.snapshotForTest()
        assertEquals(1, s.size)
        assertEquals(0 to TrailLogic.TrailKind.TYPED, s['a'])
    }

    @Test
    fun `zweiter snap decayt den ersten um eine Stufe`() {
        enableTrail()
        val m = manager()
        m.snap('a')
        m.snap('b')
        val s = m.snapshotForTest()
        assertEquals(1 to TrailLogic.TrailKind.TYPED, s['a'])
        assertEquals(0 to TrailLogic.TrailKind.TYPED, s['b'])
    }

    @Test
    fun `snap ohne Trail-Pref tut nichts`() {
        val m = manager()
        m.snap('a')
        assertTrue(m.snapshotForTest().isEmpty())
    }

    @Test
    fun `snap ist case-insensitiv (Grossbuchstabe landet klein)`() {
        enableTrail()
        val m = manager()
        m.snap('A')
        assertTrue(m.snapshotForTest().containsKey('a'))
    }

    @Test
    fun `snap in Passwortfeld zeigt nichts und Feldwechsel raeumt ab`() {
        enableTrail()
        val m = manager()
        m.snap('a')
        assertEquals(1, m.snapshotForTest().size)
        // Feldwechsel allein leert bereits (setEditorInfo-Clear).
        m.setEditorInfo(passwordInfo())
        assertTrue(m.snapshotForTest().isEmpty())
        m.snap('b')
        assertTrue("im Passwortfeld darf nichts markiert werden",
            m.snapshotForTest().isEmpty())
    }

    @Test
    fun `setEditorInfo-Wechsel leert die Spur`() {
        enableTrail()
        val m = manager()
        m.snap('a')
        m.setEditorInfo(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        assertTrue(m.snapshotForTest().isEmpty())
    }

    @Test
    fun `traceWord markiert Wort atomar gruen und laesst Tippspur stehen`() {
        enableTrail(trace = true)
        val m = manager()
        m.snap('z') // Tippspur (fremder Buchstabe)
        m.traceWord("haus", engine("haus", "hallo"), "haus")
        val s = m.snapshotForTest()
        for (c in "haus") {
            assertEquals("Buchstabe $c muss ACCEPTED sein",
                TrailLogic.TrailKind.ACCEPTED, s[c]?.second)
        }
        assertEquals("Tippspur bleibt erhalten",
            TrailLogic.TrailKind.TYPED, s['z']?.second)
    }

    @Test
    fun `traceWord raeumt vorherige Markierung ab (kein Ueberlagern)`() {
        enableTrail(trace = true)
        val m = manager()
        m.traceWord("abc", engine("abc", "hallo"), "abc")
        assertEquals(TrailLogic.TrailKind.ACCEPTED, m.snapshotForTest()['b']?.second)
        m.traceWord("haus", engine("haus", "xyz"), "haus")
        val s = m.snapshotForTest()
        assertNull("alte Markierung muss weg sein", s['b'])
        assertNull(s['c'])
        assertEquals(TrailLogic.TrailKind.ACCEPTED, s['h']?.second)
    }

    @Test
    fun `ACCEPTED wird von spaeterem TYPED-snap nicht ueberschrieben (Kern-Bug)`() {
        enableTrail(trace = true)
        val m = manager()
        m.traceWord("haus", engine("haus", "hallo"), "haus")
        // Zweiter Tap desselben Buchstabens (Wort hauss) laeuft mit TYPED.
        m.snap('s')
        val s = m.snapshotForTest()
        assertEquals("Treffer-Markierung darf nicht auf TYPED zurueckfallen",
            TrailLogic.TrailKind.ACCEPTED, s['s']?.second)
        assertEquals("Decay-Schritt wird trotzdem zurueckgesetzt", 0, s['s']?.first)
    }

    @Test
    fun `traceWord ohne Trace-Pref entfernt nur alte Markierung`() {
        enableTrail(trace = true)
        val m = manager()
        m.traceWord("abc", engine("abc", "hallo"), "abc")
        assertEquals(3, m.snapshotForTest().size)
        prefs().edit().putBoolean(ThemePrefs.KEY_TRAIL_TRACE, false).commit()
        m.traceWord("haus", engine("haus", "xyz"), "haus")
        val s = m.snapshotForTest()
        assertNull("alte Markierung wird abgeraeumt", s['b'])
        assertNull("ohne Trace-Pref keine neue Markierung", s['h'])
    }

    @Test
    fun `traceWord ohne Top-Match markiert nichts, raeumt aber ab`() {
        enableTrail(trace = true)
        val m = manager()
        m.traceWord("abc", engine("abc", "hallo"), "abc")
        // Getippt ungleich Top-Vorschlag: keine neue Markierung.
        m.traceWord("haus", engine("haus", "hallo"), "hallo")
        val s = m.snapshotForTest()
        assertNull(s['b'])
        assertTrue(s.isEmpty())
    }

    @Test
    fun `clear setzt alles zurueck`() {
        enableTrail(trace = true)
        val m = manager()
        m.snap('a')
        m.traceWord("haus", engine("haus", "hallo"), "haus")
        m.clear()
        assertTrue(m.snapshotForTest().isEmpty())
    }
}
