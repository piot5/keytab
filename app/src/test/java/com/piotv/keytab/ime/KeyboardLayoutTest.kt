package com.piotv.keytab.ime

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Layout-Vertrag der Tastatur (v0.20).
 *
 * Die Änderungen dieser Version sind reine Layout-Zusagen und würden sonst erst
 * auf dem Gerät auffallen: ß-Taste entfallen (dafür Backspace doppelt breit),
 * Leertaste und Enter mit zentriertem Symbol.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardLayoutTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    private fun inflateRoot(): View =
        LayoutInflater.from(ContextThemeWrapper(app, R.style.Theme_KeyTab))
            .inflate(R.layout.keyboard_view, null)

    /** Alle Tasten mit `tag="letter"` (Buchstaben-Tasten) in Layout-Reihenfolge. */
    private fun letterKeys(root: View): List<Button> {
        val out = mutableListOf<Button>()
        ThemeApplier.forEachView(root) { v ->
            (v as? Button)?.let { if (it.tag == "letter") out += it }
        }
        return out
    }

    @Test
    fun `Layout hat 29 Buchstaben-Tasten und keine eigene ß-Taste mehr`() {
        val keys = letterKeys(inflateRoot())
        assertEquals("11 + 11 + 7 Buchstaben", 29, keys.size)
        assertFalse("ß-Taste ist entfallen (ß kommt über den Long-Press auf s)",
            keys.any { it.text.toString() == "ß" })
        assertTrue("die s-Taste bleibt", keys.any { it.text.toString() == "s" })
    }

    @Test
    fun `Backspace ist doppelt so breit wie eine Buchstabentaste`() {
        val root = inflateRoot()
        val del = root.findViewById<Button>(R.id.key_del)
        val row = del.parent as LinearLayout
        assertEquals("untere Reihe: Shift + y..m + Backspace", 9, row.childCount)
        assertEquals("Gewicht 2 gegen 1 bei den Buchstaben", 2f,
            (del.layoutParams as LinearLayout.LayoutParams).weight, 0.001f)
        // 8 × 1 (Shift, y..m) + 2 (Backspace) = 10 Einheiten — genau so breit war
        // die Reihe mit der ß-Taste auch: ihr Gewicht ist in Backspace aufgegangen.
        val summe = (0 until row.childCount).sumOf { i ->
            (row.getChildAt(i).layoutParams as LinearLayout.LayoutParams).weight.toDouble()
        }
        assertEquals(10.0, summe, 0.001)
    }

    @Test
    fun `Leertaste und Enter zeigen ihr Symbol mittig`() {
        val root = inflateRoot()
        val space = root.findViewById<Button>(R.id.key_space)
        val enter = root.findViewById<Button>(R.id.key_enter)
        assertEquals("␣ mittig", Gravity.CENTER, space.gravity)
        assertEquals("↵ mittig", Gravity.CENTER, enter.gravity)
        assertEquals("kein Start-Padding (KeyDark setzt 8dp)", 0, space.paddingStart)
        assertEquals("kein Top-Padding (KeyDark setzt 9dp)", 0, space.paddingTop)
        assertEquals(0, enter.paddingStart)
        assertEquals(0, enter.paddingTop)
    }

    @Test
    fun `Buchstaben-Tasten behalten den Versatz oben links`() {
        val a = letterKeys(inflateRoot()).first { it.text.toString() == "a" }
        assertTrue("Buchstabe bleibt oben links (nicht zentriert)",
            a.gravity != Gravity.CENTER)
        assertEquals("Start-Padding bleibt klein (Platz für den Hinweis)", 5, a.paddingStart)
        assertEquals("Top-Padding bleibt klein", 5, a.paddingTop)
    }
}
