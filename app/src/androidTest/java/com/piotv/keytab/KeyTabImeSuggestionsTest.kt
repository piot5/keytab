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
    private var previousIme: String = ""

    @Before
    fun activateKeyTab() {
        // v0.16.1: IME des Vorgaengers merken und im @After zurueckschalten —
        // das beendet den KeyTab-Dienst und loescht Symbol-Ebene, Shift/CapsLock,
        // Tab- und Maximiert-Zustand (Ursache der CI-Fehler, siehe
        // KeyTabImeEndToEndTest).
        previousIme = shell("settings get secure default_input_method").trim()
        assertTrue("Kein KeyTab-IME registriert", shell("ime list -s -a").contains(KEYTAB_IME))
        shell("ime enable $KEYTAB_IME")
        shell("ime set $KEYTAB_IME")
        val active = shell("settings get secure default_input_method").trim()
        assertTrue("KeyTab ist nicht das Standard-IME (aktiv: $active)", active == KEYTAB_IME)
        activityRule.launchActivity(
            Intent().setComponent(
                ComponentName("com.piotv.keytab.debug", "com.piotv.keytab.ImeTargetActivity")
            )
        )
        val prefs = Prefs.of(instrumentation.targetContext)
        prefs.edit().putBoolean(Prefs.KEY_SUGGESTIONS, true)
            .putBoolean(Prefs.KEY_SWIPE, false)
            .putBoolean(Prefs.KEY_DYNAMIC_KEYS, false).apply()
    }

    @After
    fun restoreIme() {
        // v0.16.1: zurueck auf das IME des Vorgaengers — beendet den
        // KeyTab-Dienst, damit der naechste Test frisch startet (vor v0.16 war
        // das der Normalfall; sein Wegfall war die Ursache der CI-Fehler).
        if (previousIme.isNotBlank() && previousIme != "null" && previousIme != KEYTAB_IME) {
            shell("ime set $previousIme")
        } else {
            val other = shell("ime list -s").lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && it != KEYTAB_IME }
            if (other != null) shell("ime set $other")
        }
    }

    // __TESTS__

    @Test
    fun vorschlagsleiste_zeigtWoerterBeimTippen() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        typeThroughVisibleIme("ha")
        val sug = device.wait(Until.findObject(By.res(pkg, "sug_1")), 5_000)
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
        val sug = device.wait(Until.findObject(By.res(pkg, "sug_1")), 5_000)
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
        // Immer zuerst auf ABC schalten: die IME merkt sich den letzten Tab; in
        // Inhalts-Tabs ist key_space nur INVISIBLE im Baum und die Buchstaben fehlen.
        device.wait(Until.findObject(By.textStartsWith("ABC")), 3_000)?.click()
        device.waitForIdle()
        if (device.wait(Until.findObject(By.res(pkg, "key_space")), 2_000) != null) return
        device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)?.click()
        device.waitForIdle()
        val key = device.wait(Until.findObject(By.res(pkg, "key_space")), 10_000)
        assertTrue("KeyTab keyboard did not become visible", key != null)
    }

    private fun typeThroughVisibleIme(word: String) {
        word.forEach { clickImeText(it.toString()) }
    }

    private fun clickImeText(text: String) {
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
    }
}
