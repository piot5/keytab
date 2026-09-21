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

/**
 * Regression für den Autokorrektur-Bug „Text wird verdoppelt statt ersetzt“:
 * [WordPredictionManager.applySuggestion] muss das getippte Wort **ersetzen**
 * (nicht anhängen) — auch wenn das Feld `deleteSurroundingText` ignoriert.
 *
 * Ein [FakeField] simuliert das Eingabefeld inkl. der beiden Lösch-Wege
 * (`deleteBefore` = deleteSurroundingText, `deleteBeforeKeys` = KEYCODE_DEL).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WordPredictionManagerSuggestionTest {

    /** Minimales Eingabefeld, das die beiden Lösch-Wege einzeln abschalten kann. */
    private class FakeField(
        private var text: String,
        private var cursor: Int = text.length,
        private val honourDeleteSurrounding: Boolean = true,
        private val honourKeyDelete: Boolean = true
    ) : WordPredictionManager.InputOperations {
        val inserts = mutableListOf<String>()

        override fun deleteBefore(count: Int) {
            if (!honourDeleteSurrounding) return
            val start = (cursor - count).coerceAtLeast(0)
            text = text.removeRange(start, cursor)
            cursor = start
        }

        override fun deleteBeforeKeys(count: Int) {
            if (!honourKeyDelete) return
            repeat(count) {
                if (cursor > 0) { text = text.removeRange(cursor - 1, cursor); cursor-- }
            }
        }

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

    private fun manager(ops: WordPredictionManager.InputOperations, typed: String): WordPredictionManager {
        val pm = WordPredictionManager(
            RuntimeEnvironment.getApplication(),
            java.util.concurrent.Executor { it.run() },
            Handler(Looper.getMainLooper()),
            arrayOfNulls(3),
            ops
        )
        for (c in typed) pm.onCharacter(c.toString())
        assertEquals(typed, pm.currentTypedWord)
        return pm
    }

    @Test
    fun `applySuggestion ersetzt das getippte Wort statt es anzuhaengen`() {
        val field = FakeField("hauss")
        val pm = manager(field, "hauss")
        pm.applySuggestion("haus")
        assertEquals("haus ", field.value())
        assertEquals(1, field.inserts.size)
    }

    @Test
    fun `applySuggestion verdoppelt nicht wenn deleteSurroundingText ignoriert wird`() {
        // Feld ignoriert deleteSurroundingText → Key-Event-Fallback muss greifen.
        val field = FakeField("hauss", honourDeleteSurrounding = false)
        val pm = manager(field, "hauss")
        pm.applySuggestion("haus")
        assertEquals("haus ", field.value())
    }

    @Test
    fun `applySuggestion fuegt nichts ein wenn das Wort nicht geloescht werden kann`() {
        // Beide Lösch-Wege wirkungslos → NICHT einfügen (keine Verdopplung).
        val field = FakeField("hauss", honourDeleteSurrounding = false, honourKeyDelete = false)
        val pm = manager(field, "hauss")
        pm.applySuggestion("haus")
        assertEquals("hauss", field.value())
        assertTrue("es darf nichts eingefuegt worden sein", field.inserts.isEmpty())
    }

    @Test
    fun `applySuggestion ersetzt bei zusammengesetzten Umlauten (NFC)`() {
        // Feld in NFD ("a" + Umlaut), getippt in NFC → darf nicht angehängt werden.
        val nfd = "ha\u0308user"          // häuser (NFD)
        val field = FakeField(nfd)
        val pm = manager(field, "h\u00E4user") // häuser (NFC)
        pm.applySuggestion("h\u00E4user")
        assertEquals("h\u00E4user ", field.value())
    }

    @Test
    fun `applySuggestion haengt nur bei verifiziert anderem Kontext mit Trenner an`() {
        // Kontext exakt so lang wie das getippte Wort, aber ein anderes Wort →
        // Cursor steht woanders: mit Trenner anhängen, nichts löschen.
        val field = FakeField("xyz")
        val pm = manager(field, "abc")
        pm.applySuggestion("abc")
        assertEquals("xyz abc ", field.value())
    }

    @Test
    fun `applySuggestion ohne getipptes Wort fuegt einfach ein`() {
        val field = FakeField("")
        val pm = manager(field, "")
        pm.applySuggestion("haus")
        assertEquals("haus ", field.value())
    }
}
