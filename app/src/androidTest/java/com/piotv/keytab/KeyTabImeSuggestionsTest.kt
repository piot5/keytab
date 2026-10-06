package com.piotv.keytab

import android.content.ComponentName
import android.content.Intent
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Suggestion-Leiste + Emoji-Katalog auf dem echten IME (v0.16).
 *
 * Ergaenzt [KeyTabImeEndToEndTest] um den Vorhersage-Pfad, der dort bewusst
 * mit [Prefs.KEY_SUGGESTIONS] = false deaktiviert ist. Diese Klasse testet
 * den Gegenpfad: Vorschlaege an, Wort-Tap, Katalog-Oeffnen und die
 * Passwort-Unterdrueckung.
 */
@RunWith(AndroidJUnit4::class)
class KeyTabImeSuggestionsTest {

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
        // v0.16.1: Zustand des Vorgaengers normalisieren (Symbol-Ebene, Tab).
        // IME bleibt gebunden — kein Umschalten pro Test (Cold-Start-Rennen).
        runCatching { ImeTestReset.resetKeyboardState() }
        assertTrue("Kein KeyTab-IME registriert", shell("ime list -s -a").contains(KEYTAB_IME))
        shell("ime enable $KEYTAB_IME")
        shell("ime set $KEYTAB_IME")
        val active = shell("settings get secure default_input_method").trim()
        assertTrue("KeyTab ist nicht das Standard-IME (aktiv: $active)", active == KEYTAB_IME)
        // Prefs VOR dem Activity-Start setzen: der IME liest sie in
        // refreshSettings() beim onStartInput/-View des neuen Feldes. Umgekehrt
        // (wie zuvor) las er noch die Werte des Vorgaengertests — dann fehlten
        // die Vorschlaege ("Kein Vorschlag gefunden") oder der Test mass am
        // falschen Zustand.
        val prefs = Prefs.of(instrumentation.targetContext)
        prefs.edit().putBoolean(Prefs.KEY_SUGGESTIONS, true)
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
        // v0.16.1: Zustand normalisieren, damit auch ein fehlgeschlagener Test
        // keinen Schrott fuer die Folgetests hinterlaesst (best effort).
        runCatching { ImeTestReset.resetKeyboardState() }
    }

    // __TESTS__

    @Test
    fun vorschlagsleiste_zeigtWoerterBeimTippen() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        typeThroughVisibleIme("ha")
        val sug = device.wait(Until.findObject(By.res(pkg, "sug_1")), SUGGESTION_WAIT_MS)
        assertTrue("Vorschlagsleiste muss einen Vorschlag zeigen", sug != null)
        assertTrue("Vorschlag muss sichtbar sein", sug.visibleBounds.height() > 0)
    }

    @Test
    fun vorschlagsleiste_verborgenBeiDeaktivierung() {
        Prefs.of(instrumentation.targetContext).edit()
            .putBoolean(Prefs.KEY_SUGGESTIONS, false).apply()
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        typeThroughVisibleIme("ha")
        val sug = device.wait(Until.findObject(By.res(pkg, "sug_1")), 300)
        assertTrue("Ohne Vorschlaege darf sug_1 nicht sichtbar sein",
            sug == null || !sug.isEnabled || sug.visibleBounds.isEmpty)
    }

    @Test
    fun vorschlagTap_fuegtWortMitLeerzeichenEin() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        typeThroughVisibleIme("ha")
        val sug = device.wait(Until.findObject(By.res(pkg, "sug_1")), SUGGESTION_WAIT_MS)
        assertTrue("Kein Vorschlag gefunden", sug != null)
        val word = sug.text?.trim().orEmpty()
        assertTrue("Vorschlag darf nicht leer sein", word.isNotEmpty())
        sug.click()
        device.waitForIdle()
        assertTrue("Tap muss das Wort einfügen (Feld: '${activity.normalField.text}')",
            activity.normalField.text.toString().startsWith(word))
    }

    @Test
    fun emojiKatalog_oeffnetBeimEmojiButton() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        val emoji = device.wait(Until.findObject(By.res(pkg, "sug_emoji")), 5_000)
        assertTrue("Emoji-Button (sug_emoji) fehlt", emoji != null)
        emoji.click()
        device.waitForIdle()
        val grid = device.wait(Until.findObject(By.res(pkg, "sug_grid")), 5_000)
        assertTrue("Emoji-Katalog (sug_grid) muss sichtbar werden", grid != null)
    }

    @Test
    fun passwortFeld_zeigtKeineVorschlaege() {
        val activity = activityRule.activity
        focus(activity.passwordField)
        waitForKeyboard()
        typeThroughVisibleIme("haus")
        val sug = device.wait(Until.findObject(By.res(pkg, "sug_1")), 300)
        assertTrue("Passwortfeld darf keine Vorschlaege zeigen",
            sug == null || !sug.isEnabled || sug.visibleBounds.isEmpty)
    }

    // ---------------- Hilfen (wie KeyTabImeEndToEndTest) ----------------

    // Die Vorschlags-Engine laedt den Wortkorpus asynchron: auf langsamen
    // Geraeten/Emulatoren ist `sug_1` direkt nach dem ersten Tastendruck noch
    // nicht da (CI: "Kein Vorschlag gefunden" trotz korrekt getipptem "ha").
    // Die Wartezeit ist deshalb grosszuegig (15 s statt der 5 s, die fuer einen
    // warmen Lauf reichen) — siehe SUGGESTION_WAIT_MS.

    private fun focus(field: EditText) {
        val imm = instrumentation.targetContext
            .getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        instrumentation.runOnMainSync {
            field.requestFocus()
            imm.showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        instrumentation.waitForIdleSync()
    }

    private fun waitForKeyboard() {
        TestRunnerState.awaitResume()
        // Immer zuerst auf ABC schalten: die IME merkt sich den letzten Tab; in
        // Inhalts-Tabs ist key_space nur INVISIBLE im Baum und die Buchstaben fehlen.
        device.wait(Until.findObject(By.textStartsWith("ABC")), 3_000)?.click()
        device.waitForIdle()
        // Bis zu drei Anlaeufe: ein echter Touch auf das Eingabefeld blendet das
        // IME-Fenster zuverlaessig ein (showSoftInput aus dem Instrumentation-
        // Thread geht verloren, wenn das Fenster keinen Fokus hat).
        repeat(3) { round ->
            val wait = if (round == 0) 5_000L else 2_000L
            if (device.wait(Until.findObject(By.res(pkg, "key_space")), wait) != null) return
            device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)?.click()
            device.waitForIdle()
        }
        val key = device.wait(Until.findObject(By.res(pkg, "key_space")), 5_000)
        assertTrue("KeyTab keyboard did not become visible", key != null)
    }

    private fun typeThroughVisibleIme(word: String) {
        word.forEach { clickImeText(it.toString()) }
    }

    private fun clickImeText(text: String) {
        TestRunnerState.awaitResume()
        val found = device.wait(Until.findObject(
            By.clazz("android.widget.Button").textStartsWith(text)), 5_000)
        assertTrue("KeyTab letter not found: $text", found != null)
        found.click()
        device.waitForIdle()
    }

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return InputStreamReader(FileInputStream(descriptor.fileDescriptor)).buffered()
            .use { it.readText() }
    }

    private companion object {
        const val KEYTAB_IME = "com.piotv.keytab.debug/com.piotv.keytab.ime.KeyTabImeService"
        /** Wartezeit auf den ersten Vorschlag (Korpus laedt asynchron). */
        const val SUGGESTION_WAIT_MS = 15_000L
    }
}
