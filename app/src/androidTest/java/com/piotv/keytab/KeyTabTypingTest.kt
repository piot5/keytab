package com.piotv.keytab

import android.content.ComponentName
import android.content.Intent
import android.view.KeyEvent
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * **Ein** umfassender instrumentierter Tipp-Test (v0.16.1).
 *
 * Ersetzt die frühere Suite aus sechs Klassen. Statt vieler kleiner Tests läuft
 * ein Durchlauf über alle Tippfunktionen der echten IME: langer Standardtext,
 * Zahlenreihe, Sonderzeichen-Ebene (`?123`), Long-Press-Auswahl am Buchstaben,
 * Backspace, Leertaste, Punkt, Enter, TAB, Shift und das Passwortfeld (tippt
 * normal, zeigt keine Vorschläge).
 *
 * Warum ein Test: der CI-Emulator ist extrem langsam (gemessene Frame-Zeit
 * ~500 ms). Viele kleine Tests kosteten dort mehr Zeit in Aufbau und
 * Klassenwechsel als im Test selbst. Ein Durchlauf prüft dieselben Verträge,
 * ist schnell — und jeder Schritt hat ein eigenes Erwartungsergebnis, damit ein
 * Fehler sofort benennt, **welche** Tippfunktion bricht.
 */
@RunWith(AndroidJUnit4::class)
class KeyTabTypingTest {
    /** Test-Runner: meldet Name + Erwartung an die Anzeige im Debug-Host. */
    @get:Rule
    val runnerRule = TestRunnerRule()

    @get:Rule
    val activityRule = object : ActivityTestRule<ImeTargetActivity>(
        ImeTargetActivity::class.java, false, false
    ) {
        override fun getActivityIntent(): Intent =
            Intent().setComponent(
                ComponentName("com.piotv.keytab.debug", "com.piotv.keytab.ImeTargetActivity")
            )
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val pkg get() = instrumentation.targetContext.packageName

    @Before
    fun activateKeyTab() {
        // Zustand des Vorgaengers normalisieren (Symbol-Ebene, Tab). Das IME
        // bleibt gebunden — ein Umschalten pro Test brächte das Cold-Start-Rennen
        // zurück (siehe ImeTestReset).
        // EIN Shell-Aufruf statt vier: jeder Roundtrip kostet auf dem Geraet
        // spuerbar Startzeit.
        // EIN Shell-Aufruf statt vier (Startzeit). Die LETZTE Zeile ist der
        // aktuelle Standard-IME — darauf wird geprueft; die Reihenfolge der
        // uebrigen Ausgaben ist nicht garantiert, deshalb keine Zeile davor lesen.
        val active = shell(
            "ime enable $KEYTAB_IME; ime set $KEYTAB_IME; " +
                "settings get secure default_input_method"
        ).trim().lines().lastOrNull()?.trim().orEmpty()
        assertTrue("KeyTab ist nicht das Standard-IME (aktiv: $active)", active == KEYTAB_IME)
        runCatching { ImeTestReset.resetKeyboardState() }
        // Vorschlaege AN: nur so beweist der Passwort-Schritt, dass sie dort
        // unterdrueckt werden. Prefs VOR dem Activity-Start setzen — der IME
        // liest sie beim onStartInput des neuen Feldes.
        Prefs.of(instrumentation.targetContext).edit()
            .putBoolean(Prefs.KEY_SUGGESTIONS, true)
            .putBoolean(Prefs.KEY_SWIPE, false)
            .putBoolean(Prefs.KEY_DYNAMIC_KEYS, false).apply()
        activityRule.launchActivity(
            Intent().setComponent(
                ComponentName("com.piotv.keytab.debug", "com.piotv.keytab.ImeTargetActivity")
            )
        )
    }

    @After
    fun restoreIme() {
        runCatching { ImeTestReset.resetKeyboardState() }
    }

    @Test
    fun kompletterTippdurchlauf() {
        val activity = activityRule.activity
        val normal = activity.normalField

        focus(normal)
        waitForKeyboard()
        ensureLetters()

        // 1) Langer Standardtext (Leerzeichen + Wortgrenzen). Der erste
        //    Buchstabe darf durch Auto-Shift gross werden — beide Formen gelten.
        val text1 = setOf("hallo welt", "Hallo welt")
        stepAny(normal, "", text1) { typeText("hallo welt") }
        val base1 = normal.text.toString()

        // 2) Zahlenreihe: Ziffern direkt tippen.
        step(normal, base1, base1 + "123") { typeText("123") }
        val base2 = normal.text.toString()

        // 3) Sonderzeichen-Ebene (?123): Satzzeichen und Klammern.
        step(normal, base2, base2 + "!()@") {
            withSymbols {
                clickKey("!")
                clickKey("(")
                clickKey(")")
                clickKey("@")
            }
        }

        // 4) Long-Press am Buchstaben: Sonderzeichen-Auswahl im Popup.
        val beforeExtra = normal.text.toString()
        val extra = stepExtra(normal, beforeExtra, "o")
        assertTrue("Long-Press muss ein Sonderzeichen liefern", extra.isNotEmpty())

        // 5) Backspace loescht genau ein Zeichen.
        val beforeDel = normal.text.toString()
        step(normal, beforeDel, beforeDel.dropLast(1)) { clickKeyById("key_del") }

        // 6) Leertaste und Punkt-Taste.
        val beforeSpace = normal.text.toString()
        step(normal, beforeSpace, "$beforeSpace ") { clickKeyById("key_space") }
        val beforeDot = normal.text.toString()
        step(normal, beforeDot, "$beforeDot.") { clickKeyById("key_dot") }

        // 7) Enter und TAB kommen als echte Keycodes an.
        clickUntilKeyCode("key_enter", KeyEvent.KEYCODE_ENTER,
            "Enter muss als KEYCODE_ENTER ankommen")
        clickUntilKeyCode("key_tab", KeyEvent.KEYCODE_TAB,
            "TAB muss als KEYCODE_TAB ankommen")

        // 8) Shift: Grossbuchstabe tippen.
        val beforeShift = normal.text.toString()
        shiftUntilUppercase()
        step(normal, beforeShift, beforeShift + "Q") { clickKey("Q") }

        // 9) Passwortfeld: tippt normal, zeigt aber keine Vorschlaege.
        focus(activity.passwordField)
        typeUntilTextAny(activity.passwordField, setOf("geheim", "Geheim")) { typeText("geheim") }
        assertNoSuggestions("Passwortfeld darf keine Vorschlaege zeigen")

        // 10) Feld mit IME_FLAG_NO_PERSONALIZED_LEARNING: tippt normal.
        focus(activity.noLearningField)
        typeUntilTextAny(activity.noLearningField, setOf("test", "Test")) { typeText("test") }
        assertNoSuggestions("Feld ohne personalisiertes Lernen darf keine Vorschlaege zeigen")
    }

    // ---------------- Hilfen ----------------

    /**
     * [block] ausfuehren, bis das Feld [expected] enthaelt (max. 3 Anlaeufe).
     *
     * Der erste Tap geht verloren, solange die Input-Session des neuen Fensters
     * noch nicht steht — auf Emulator wie Geraet zu sehen als
     * „expected:<[Q]> but was:<[]>“ (Klick kommt an, es wird nichts committet).
     * Zwischen den Anlaeufen wird der Ausgangsstand [from] wiederhergestellt.
     */
    private fun step(field: EditText, from: String, expected: String, block: () -> Unit) {
        repeat(2) {
            if (field.text.toString() == expected) return
            instrumentation.runOnMainSync {
                field.setText(from)
                field.setSelection(from.length)
            }
            block()
            if (field.text.toString() == expected) return
            device.waitForIdle()
        }
        assertEquals("Schritt '$from' -> '$expected' (Tasten: ${visibleButtonLabels()})",
            expected, field.text.toString())
    }

    /**
     * Wie [step], akzeptiert aber mehrere erwartete Ergebnisse. Gebraucht fuer
     * Texte, deren erster Buchstabe durch Auto-Shift gross werden kann.
     */
    private fun stepAny(field: EditText, from: String, expected: Set<String>, block: () -> Unit) {
        repeat(2) {
            if (field.text.toString() in expected) return
            instrumentation.runOnMainSync {
                field.setText(from)
                field.setSelection(from.length)
            }
            block()
            if (field.text.toString() in expected) return
            device.waitForIdle()
        }
        assertTrue("Schritt '$from' -> ${expected.joinToString(" oder ")} " +
            "(ist: '${field.text}', Tasten: ${visibleButtonLabels()})",
            field.text.toString() in expected)
    }

    /** Wie [stepAny], nur ohne festen Ausgangstext. */
    private fun typeUntilTextAny(field: EditText, expected: Set<String>, block: () -> Unit) {
        repeat(2) {
            if (field.text.toString() in expected) return
            instrumentation.runOnMainSync {
                field.setText("")
                field.setSelection(0)
            }
            block()
            if (field.text.toString() in expected) return
            device.waitForIdle()
        }
        assertTrue("Feld muss ${expected.joinToString(" oder ")} enthalten " +
            "(ist: '${field.text}', Tasten: ${visibleButtonLabels()})",
            field.text.toString() in expected)
    }


    /**
     * Wort tippen: Buchstaben als Taste, das Leerzeichen ueber die Leertaste.
     * Die Leertaste traegt das Label „␣" (key_space_label), nicht " ".
     */
    private fun typeText(word: String) = word.forEach { ch ->
        if (ch == ' ') clickKeyById("key_space") else clickKey(ch.toString())
    }

    /** Sonderzeichen-Ebene an, [block] ausfuehren, danach zurueck auf Buchstaben. */
    private fun withSymbols(block: () -> Unit) {
        ensureLetters()
        clickKeyById("key_toggle")
        block()
        ensureLetters()
    }

    /** Buchstaben-Ebene sicherstellen: der Toggle zeigt „abc", wenn Symbole aktiv sind. */
    private fun ensureLetters() {
        val toggle = device.wait(Until.findObject(By.res(pkg, "key_toggle")), 1_500)
        if (toggle?.text?.contains("abc", ignoreCase = true) == true) {
            toggle.click()
            device.waitForIdle()
        }
    }


    /** Liegt der abc-Tab mit Buchstaben an (klein oder gross)? */
    private fun lettersReady(): Boolean =
        device.wait(Until.findObject(
            By.clazz("android.widget.Button").textStartsWith("q")), 1_000) != null ||
            device.wait(Until.findObject(
                By.clazz("android.widget.Button").textStartsWith("Q")), 1_000) != null

    /**
     * Taste ueber ihr Label druecken (Buchstaben/Ziffern/Sonderzeichen).
     *
     * Case-tolerant: die Tastatur startet mit Auto-Shift (der erste Buchstabe
     * wird gross, wie bei jeder Tastatur) und ein spaetes onStartInput kann
     * einen Reset wieder aufheben — dann traegt die Taste „H" statt „h".
     */
    private fun clickKey(label: String) {
        val found = findKey(label) ?: findKey(label.swapCase())
        assertTrue("Taste '$label' nicht gefunden (Tasten: ${visibleButtonLabels()})", found != null)
        found!!.click()
        device.waitForIdle()
    }

    private fun findKey(label: String): UiObject2? =
        device.wait(Until.findObject(
            By.clazz("android.widget.Button").textStartsWith(label)), 1_200)

    private fun String.swapCase(): String = map { c ->
        if (c.isUpperCase()) c.lowercaseChar() else c.uppercaseChar()
    }.joinToString("")

    /** Funktionstaste ueber ihre Ressourcen-ID druecken. */
    private fun clickKeyById(id: String) {
        val found = device.wait(Until.findObject(By.res(pkg, id)), 2_000)
        assertTrue("Taste '$id' nicht gefunden (Tasten: ${visibleButtonLabels()})", found != null)
        found.click()
        device.waitForIdle()
    }

    /**
     * Taste druecken, bis der erwartete Keycode ankommt (max. 3 Anlaeufe).
     * Auf langsamen Geraeten geht ein einzelner Klick verloren, bevor die
     * Input-Session des neuen Fensters steht.
     */
    private fun clickUntilKeyCode(id: String, keyCode: Int, message: String) {
        val activity = activityRule.activity
        repeat(2) {
            activity.receivedKeyCodes.clear()
            clickKeyById(id)
            if (activity.receivedKeyCodes.contains(keyCode)) return
        }
        assertTrue("$message (Tasten: ${visibleButtonLabels()})",
            activity.receivedKeyCodes.contains(keyCode))
    }

    /** Shift druecken, bis Grossbuchstaben anliegen (max. 2 Anlaeufe). */
    private fun shiftUntilUppercase() {
        var upper = false
        repeat(2) {
            if (upper) return@repeat
            clickKeyById("key_shift")
            upper = device.wait(Until.findObject(
                By.clazz("android.widget.Button").textStartsWith("Q")), 3_000) != null
        }
        assertTrue("Nach Shift muessen Grossbuchstaben anliegen (Tasten: ${visibleButtonLabels()})",
            upper)
    }
    /** Long-Press-Auswahl: [from] + Sonderzeichen; liefert das gewaehlte Zeichen. */
    private fun stepExtra(field: EditText, from: String, letter: String): String {
        var extra = ""
        repeat(2) {
            if (extra.isNotEmpty()) return extra
            instrumentation.runOnMainSync {
                field.setText(from)
                field.setSelection(from.length)
            }
            extra = longPressAndPickExtra(letter)
            if (field.text.toString() != from + extra) extra = ""
        }
        assertEquals("Long-Press am '$letter' muss ein Sonderzeichen anfuegen " +
            "(Tasten: ${visibleButtonLabels()})", from + extra, field.text.toString())
        return extra
    }

    /** Buchstaben lange druecken und die im Label angekuendigte Popup-Zelle waehlen. */
    private fun longPressAndPickExtra(letter: String): String {
        // Case-tolerant wie clickKey: bei Auto-Shift traegt die Taste "O" statt "o".
        val key = findKey(letter) ?: findKey(letter.swapCase()) ?: return ""
        val extra = firstExtraOf(key.text.orEmpty()) ?: return ""
        key.longClick()
        device.waitForIdle()
        val cell = device.wait(Until.findObject(By.text(extra)), 2_000) ?: return ""
        cell.click()
        device.waitForIdle()
        return extra
    }

    /**
     * Erstes Sonderzeichen aus dem Tasten-Label: `LetterLabelComposer` setzt
     * „Hauptbuchstabe + geschütztes Leerzeichen + erster Hinweis" (z. B.
     * „o\u00A0ö") — daraus laesst sich ableiten, welche Popup-Zelle erscheint.
     */
    private fun firstExtraOf(label: String): String? =
        label.split('\u00A0').drop(1).firstOrNull { it.isNotBlank() }
            ?.trim()?.take(1)?.takeIf { it.isNotEmpty() }

    /** Im sensiblen Feld darf keine Vorschlagsleiste erscheinen. */
    private fun assertNoSuggestions(message: String) {
        val sug = device.wait(Until.findObject(By.res(pkg, "sug_1")), 500)
        assertTrue("$message (sug_1 sichtbar)",
            sug == null || !sug.isEnabled || sug.visibleBounds.isEmpty)
    }

    /** Sichtbare Tastenbeschriftungen — Diagnose fuer Fehlermeldungen. */
    private fun visibleButtonLabels(): String =
        device.findObjects(By.clazz("android.widget.Button"))
            .mapNotNull { it.text }
            .filter { it.isNotBlank() }
            .take(16)
            .joinToString(" | ")

    private fun focus(field: EditText) {
        val imm = instrumentation.targetContext
            .getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        instrumentation.runOnMainSync {
            field.requestFocus()
            // requestFocus() allein reicht nicht: ohne showSoftInput bleibt
            // mInputShown=false und das IME-Fenster wird nie sichtbar.
            imm.showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        instrumentation.waitForIdleSync()
    }

    private fun waitForKeyboard() {
        // Schnellpfad: abc-Tab mit Buchstaben liegt schon an -> nichts zu tun.
        if (lettersReady()) return
        // Sonst auf den abc-Tab schalten (die IME merkt sich den letzten Tab).
        device.wait(Until.findObject(By.textStartsWith("ABC")), 2_000)?.click()
        device.waitForIdle()
        // Bis zu drei Anlaeufe: ein echter Touch auf das Eingabefeld blendet das
        // IME-Fenster zuverlaessig ein (showSoftInput aus dem
        // Instrumentation-Thread geht verloren, wenn das Fenster keinen Fokus hat).
        repeat(2) { round ->
            val wait = if (round == 0) 3_000L else 1_500L
            if (device.wait(Until.findObject(By.res(pkg, "key_space")), wait) != null) return
            device.wait(Until.findObject(By.clazz("android.widget.EditText")), 3_000)?.click()
            device.waitForIdle()
        }
        val key = device.wait(Until.findObject(By.res(pkg, "key_space")), 3_000)
        assertTrue("KeyTab keyboard did not become visible. ${imeState()}", key != null)
    }

    /** IME-Zustand fuer die Fehlermeldung (welches IME, Fenster sichtbar?). */
    private fun imeState(): String {
        val current = shell("settings get secure default_input_method").trim()
        val dump = shell("dumpsys input_method | grep -E " +
            "'mCurMethodId|mServedView|mShowRequested|mImeWindowVis|mHaveConnection'")
        return "aktiv=$current erwartet=$KEYTAB_IME\n$dump"
    }

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return InputStreamReader(FileInputStream(descriptor.fileDescriptor)).buffered()
            .use { it.readText() }
    }

    private companion object {
        const val KEYTAB_IME = "com.piotv.keytab.debug/com.piotv.keytab.ime.KeyTabImeService"
    }
}

