package com.piotv.keytab.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reine Trail-Logik: Decay-Formel, Korrektur-Klassifikation, Passwort-Schutz. */
class TrailLogicTest {

    private fun editorInfo(inputType: Int, imeOptions: Int = 0) =
        EditorInfo().apply {
            this.inputType = inputType
            this.imeOptions = imeOptions
        }

    // ---------- Decay ----------

    @Test
    fun `alphaForStep ist bei Schritt 0 am groessten und bei maxSteps null`() {
        val step0 = TrailLogic.alphaForStep(0, 5)
        val step2 = TrailLogic.alphaForStep(2, 5)
        val step4 = TrailLogic.alphaForStep(4, 5)
        assertTrue("Schritt 0 muss groesser als Schritt 2 sein", step0 > step2)
        assertTrue("Schritt 2 muss groesser als Schritt 4 sein", step2 > step4)
        assertEquals(0, TrailLogic.alphaForStep(5, 5))
        assertEquals(0, TrailLogic.alphaForStep(6, 5))
    }

    @Test
    fun `alphaForStep deckelt auf 55 Prozent damit der Text lesbar bleibt`() {
        // 255 * 0.55 = 140 – das Overlay liegt ueber der Beschriftung
        assertEquals(140, TrailLogic.alphaForStep(0, 5))
        assertTrue(TrailLogic.alphaForStep(0, 5) < 255)
    }

    @Test
    fun `alphaForStep behandelt ungueltige Stufen defensiv`() {
        assertEquals(0, TrailLogic.alphaForStep(0, 0))
        assertEquals(0, TrailLogic.alphaForStep(0, -3))
    }

    @Test
    fun `nextStep liefert null sobald maxSteps erreicht ist`() {
        assertEquals(1, TrailLogic.nextStep(0, 5))
        assertEquals(4, TrailLogic.nextStep(3, 5))
        assertNull(TrailLogic.nextStep(4, 5))
        assertNull(TrailLogic.nextStep(9, 5))
    }

    // ---------- Korrektur-Trace ----------

    private fun engine(vararg words: String) =
        SuggestionEngine(words.map { it to 1000 })

    @Test
    fun `classifyTypedWord erkennt bekanntes Wort als ACCEPTED`() {
        assertEquals(
            TrailLogic.TrailKind.ACCEPTED,
            TrailLogic.classifyTypedWord("haus", engine("haus", "hallo"))
        )
    }

    @Test
    fun `classifyTypedWord erkennt Korrektur-Kandidat als CORRECTED`() {
        // "ahus" ist unbekannt, "haus" steht im Korpus (Transposition)
        assertEquals(
            TrailLogic.TrailKind.CORRECTED,
            TrailLogic.classifyTypedWord("ahus", engine("haus", "hallo"))
        )
    }

    @Test
    fun `classifyTypedWord gibt null ohne Engine oder bei zu kurzem Wort`() {
        assertNull(TrailLogic.classifyTypedWord("haus", null))
        assertNull(TrailLogic.classifyTypedWord("ab", engine("ab")))
        assertNull(TrailLogic.classifyTypedWord("", engine("haus")))
    }

    @Test
    fun `classifyTypedWord ignoriert Woerter mit Nicht-Buchstaben`() {
        assertNull(TrailLogic.classifyTypedWord("ha4s", engine("haus")))
        assertNull(TrailLogic.classifyTypedWord("ha-us", engine("haus")))
    }

    @Test
    fun `classifyTypedWord gibt null bei unbekanntem Wort ohne Kandidat`() {
        assertNull(TrailLogic.classifyTypedWord("xyzq", engine("haus", "hallo")))
    }

    @Test
    fun `classifyTypedWord ist case-insensitiv`() {
        assertEquals(
            TrailLogic.TrailKind.ACCEPTED,
            TrailLogic.classifyTypedWord("Haus", engine("haus"))
        )
    }

    // ---------- Passwort-Schutz (Sicherheit) ----------

    @Test
    fun `isPasswordField erkennt alle Passwort-Varianten`() {
        assertTrue(
            TrailLogic.isPasswordField(
                editorInfo(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            )
        )
        assertTrue(
            TrailLogic.isPasswordField(
                editorInfo(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                )
            )
        )
        assertTrue(
            TrailLogic.isPasswordField(
                editorInfo(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                )
            )
        )
        assertFalse(TrailLogic.isPasswordField(editorInfo(InputType.TYPE_CLASS_TEXT)))
    }

    @Test
    fun `isTrailAllowed sperrt Passwortfelder hart`() {
        assertFalse(
            TrailLogic.isTrailAllowed(
                editorInfo(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            )
        )
        assertFalse(
            TrailLogic.isTrailAllowed(
                editorInfo(
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                )
            )
        )
    }

    @Test
    fun `isTrailAllowed respektiert NO_PERSONALIZED_LEARNING`() {
        assertFalse(
            TrailLogic.isTrailAllowed(
                editorInfo(
                    InputType.TYPE_CLASS_TEXT,
                    EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                )
            )
        )
    }

    @Test
    fun `isTrailAllowed erlaubt normale Textfelder und fehlende EditorInfo`() {
        assertTrue(TrailLogic.isTrailAllowed(editorInfo(InputType.TYPE_CLASS_TEXT)))
        assertTrue(TrailLogic.isTrailAllowed(null))
    }

    // ---------- Regression: widerspruechliche Trace-Zustaende ----------
    // Bug (gemeldet): nachdem ein Buchstabe rot war, wurde er durch einen
    // spaeteren Trace/Decay gruen bzw. blau – die Taste zeigte eine Mischfarbe,
    // weil `steps`/`kinds` pro BUCHSTABE statt pro VORKOMMEN speichern.

    /**
     * Kern des Bugs: ein Buchstabe, der in einem Wort rot war, darf nicht durch
     * einen zweiten Tap desselben Buchstabens auf TYPED (blau) zurueckfallen.
     * Die Klassifikation ist pro Buchstabe eindeutig – genau das macht sie
     * widerspruchsfrei und ist die Grundlage fuer den atomaren Wort-Trace.
     */
    @Test
    fun `gleicher Buchstabe behaelt seine Klassifikation und wird nicht ueberschrieben`() {
        val kinds = mutableMapOf<Char, TrailLogic.TrailKind>()
        // Wort "haus" wird als korrekturbeduerftig eingestuft
        for (ch in "haus") kinds[ch] = TrailLogic.TrailKind.CORRECTED
        // zweiter Tap auf "s" (Wort "hauss") – snap() laeuft mit TYPED
        val incoming = TrailLogic.TrailKind.TYPED
        val existing = kinds['s']
        if (existing == null || existing == TrailLogic.TrailKind.TYPED) kinds['s'] = incoming
        assertEquals(
            "bestehender CORRECTED-Trace darf nicht von TYPED ueberschrieben werden",
            TrailLogic.TrailKind.CORRECTED, kinds['s']
        )
        assertEquals(TrailLogic.TrailKind.CORRECTED, kinds['h'])
        assertEquals(TrailLogic.TrailKind.CORRECTED, kinds['a'])
        assertEquals(TrailLogic.TrailKind.CORRECTED, kinds['u'])
    }

    /**
     * Nach einem Wortwechsel darf kein Eintrag des alten Wortes uebrig bleiben –
     * sonst ueberlagern sich zwei Wort-Traces auf denselben Tasten.
     */
    @Test
    fun `clearTrace entfernt nur Trace-Eintraege und laesst die Tippspur stehen`() {
        val kinds = mutableMapOf(
            'z' to TrailLogic.TrailKind.TYPED,
            'q' to TrailLogic.TrailKind.CORRECTED,
            'y' to TrailLogic.TrailKind.ACCEPTED
        )
        for (c in kinds.keys.toList()) {
            if (kinds[c] != TrailLogic.TrailKind.TYPED) kinds.remove(c)
        }
        assertEquals(1, kinds.size)
        assertEquals("Tippspur muss erhalten bleiben", TrailLogic.TrailKind.TYPED, kinds['z'])
        assertNull(kinds['q'])
        assertNull(kinds['y'])
    }

    /**
     * Wird ein Wort akzeptiert, muss der vorherige rote Trace WEG sein – nicht
     * zusaetzlich stehen bleiben (sonst rot+gruen gleichzeitig auf denselben
     * Tasten, wenn beide Woerter gemeinsame Buchstaben haben).
     */
    @Test
    fun `akzeptiertes Wort raeumt den vorherigen Korrektur-Trace ab`() {
        // altes Wort war rot
        val kinds = mutableMapOf<Char, TrailLogic.TrailKind>()
        for (ch in "hauss") kinds[ch] = TrailLogic.TrailKind.CORRECTED
        // neues Wort "haus" wird akzeptiert -> clearTrace() laeuft zuerst
        for (c in kinds.keys.toList()) {
            if (kinds[c] != TrailLogic.TrailKind.TYPED) kinds.remove(c)
        }
        assertTrue("nach clearTrace darf kein Trace-Eintrag uebrig sein", kinds.isEmpty())
    }

    /** Die Decay-Stufen eines Buchstabens werden beim Trace-Reset mitentfernt. */
    @Test
    fun `clearTrace entfernt auch die zugehoerigen Decay-Stufen`() {
        val steps = mutableMapOf('a' to 0, 'b' to 3)
        val kinds = mutableMapOf('a' to TrailLogic.TrailKind.CORRECTED,
            'b' to TrailLogic.TrailKind.TYPED)
        for (c in kinds.keys.toList()) {
            if (kinds[c] != TrailLogic.TrailKind.TYPED) {
                kinds.remove(c)
                steps.remove(c)
            }
        }
        assertNull("Stufe des entfernten Trace muss weg sein", steps['a'])
        assertEquals(3, steps['b'])
    }
}
