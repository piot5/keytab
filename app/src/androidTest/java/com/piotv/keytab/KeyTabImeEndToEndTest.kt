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

/** Device-level contract tests for the real Android InputMethodService. */
@RunWith(AndroidJUnit4::class)
class KeyTabImeEndToEndTest {
    @get:Rule
    val activityRule = object : ActivityTestRule<ImeTargetActivity>(
        ImeTargetActivity::class.java, false, false
    ) {
        override fun getActivityIntent(): Intent =
            Intent().setComponent(
                ComponentName("com.piotv.keytab.debug.test", "com.piotv.keytab.test.ImeTargetActivity")
            )
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private var previousIme: String = ""

    @Before
    fun activateKeyTab() {
        activityRule.launchActivity(
            Intent().setComponent(
                ComponentName("com.piotv.keytab.debug.test", "com.piotv.keytab.test.ImeTargetActivity")
            )
        )
        previousIme = shell("settings get secure default_input_method").trim()
        assertTrue("Kein KeyTab-IME im Testgerät registriert", shell("ime list -s -a")
            .contains(KEYTAB_IME))
        shell("ime enable $KEYTAB_IME")
        shell("ime set $KEYTAB_IME")
        val prefs = Prefs.of(instrumentation.targetContext)
        prefs.edit().putBoolean(Prefs.KEY_SUGGESTIONS, false)
            .putBoolean(Prefs.KEY_SWIPE, false).apply()
    }

    @After
    fun restoreIme() {
        if (previousIme.isNotBlank() && previousIme != "null") shell("ime set $previousIme")
    }

    @Test
    fun realIme_commitsCharactersSpaceTabEnterAndBackspace() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        clickImeText("x")
        clickImeId("key_space")
        assertEquals("x ", activity.normalField.text.toString())

        activity.receivedKeyCodes.clear()
        clickImeId("key_tab")
        assertTrue("Tab must arrive as KEYCODE_TAB", activity.receivedKeyCodes.contains(KeyEvent.KEYCODE_TAB))

        focus(activity.normalField)
        activity.receivedKeyCodes.clear()
        clickImeId("key_enter")
        assertTrue("Enter must arrive as KEYCODE_ENTER",
            activity.receivedKeyCodes.contains(KeyEvent.KEYCODE_ENTER))

        focus(activity.normalField)
        activity.receivedKeyCodes.clear()
        clickImeId("key_del")
        assertTrue("Backspace must arrive as KEYCODE_DEL",
            activity.receivedKeyCodes.contains(KeyEvent.KEYCODE_DEL))
    }

    @Test
    fun passwordField_typesNormallyButShowsNoSuggestions() {
        val activity = activityRule.activity
        focus(activity.passwordField)
        waitForKeyboard()
        typeThroughVisibleIme("haus")
        assertEquals("haus", activity.passwordField.text.toString())
        assertNoSuggestions()
    }

    @Test
    fun noPersonalizedLearningField_typesNormallyButShowsNoSuggestions() {
        val activity = activityRule.activity
        focus(activity.noLearningField)
        waitForKeyboard()
        typeThroughVisibleIme("haus")
        assertEquals("haus", activity.noLearningField.text.toString())
        assertNoSuggestions()
    }

    @Test
    fun fieldSwitch_keepsTargetsSeparateAndKeepsKeyboardUsable() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        typeThroughVisibleIme("normal")
        assertEquals("normal", activity.normalField.text.toString())

        focus(activity.passwordField)
        typeThroughVisibleIme("secret")
        assertEquals("secret", activity.passwordField.text.toString())
        assertNoSuggestions()

        focus(activity.normalField)
        assertEquals("normal", activity.normalField.text.toString())
        typeThroughVisibleIme("x")
        assertEquals("normalx", activity.normalField.text.toString())
    }

    @Test
    fun activityRecreation_keepsImeUsableForNewField() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        typeThroughVisibleIme("before")

        instrumentation.runOnMainSync { activity.recreate() }
        instrumentation.waitForIdleSync()
        val recreated = activityRule.activity
        focus(recreated.normalField)
        waitForKeyboard()
        typeThroughVisibleIme("after")
        assertEquals("after", recreated.normalField.text.toString())
    }

    private fun focus(field: EditText) {
        instrumentation.runOnMainSync {
            field.requestFocus()
            field.clearFocus()
            field.requestFocus()
        }
        instrumentation.waitForIdleSync()
    }

    private fun typeThroughVisibleIme(word: String) {
        word.forEach { clickImeText(it.toString()) }
    }

    private fun assertNoSuggestions() {
        val suggestion = device.wait(Until.findObject(
            By.res(instrumentation.targetContext.packageName, "sug_1")), 300)
        assertTrue("Suggestion bar must stay hidden in a sensitive field",
            suggestion == null || !suggestion.isEnabled || suggestion.visibleBounds.isEmpty)
    }

    private fun waitForKeyboard() {
        val key = device.wait(Until.findObject(
            By.res(instrumentation.targetContext.packageName, "key_space")), 10_000)
        assertTrue("KeyTab keyboard did not become visible", key != null)
    }

    private fun clickImeId(id: String) {
        val selector = By.res(instrumentation.targetContext.packageName, id)
        val found = device.wait(Until.findObject(selector), 5_000)
        assertTrue("KeyTab key not found: $id", found != null)
        found.click()
        device.waitForIdle()
    }

    private fun clickImeText(text: String) {
        val found = device.wait(Until.findObject(By.text(text)), 5_000)
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
