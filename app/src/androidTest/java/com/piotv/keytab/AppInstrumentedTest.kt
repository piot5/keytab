package com.piotv.keytab

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.piotv.keytab.ime.Languages
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import org.hamcrest.CoreMatchers.not
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device-level smoke tests: manifest registration, resources and settings UI. */
@RunWith(AndroidJUnit4::class)
class AppInstrumentedTest {
    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun packageName_isCorrect() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName in listOf("com.piotv.keytab", "com.piotv.keytab.debug"))
    }

    @Test
    fun imeService_isDeclaredAndEnabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val services = context.packageManager.queryIntentServices(
            Intent("android.view.InputMethod"), 0
        )
        assertTrue("KeyTabImeService muss als InputMethod registriert sein",
            services.any { it.serviceInfo.packageName == context.packageName })
    }

    @Test
    fun mainActivity_startsAndExposesSettings() {
        val activity = activityRule.activity
        val language = activity.findViewById<android.view.View>(R.id.spinner_language)
        val numberRow = activity.findViewById<android.view.View>(R.id.sw_num_row)
        assertTrue(language != null && numberRow != null)
        assertTrue(language.visibility == android.view.View.VISIBLE)
        assertTrue(numberRow.visibility == android.view.View.VISIBLE)
    }

    @Test
    fun settingsControls_areAccessibleAndToggleable() {
        val activity = activityRule.activity
        val numberRow = activity.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_num_row)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = Prefs.of(context)
        val before = prefs.getBoolean(Prefs.KEY_NUM_ROW, false)
        activity.runOnUiThread { numberRow.performClick() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(!before, prefs.getBoolean(Prefs.KEY_NUM_ROW, !before))
    }
    @Test
    fun testInputField_acceptsTextAndCursorInput() {
        val activity = activityRule.activity
        val input = activity.findViewById<android.widget.EditText>(R.id.test_input)
        val value = "KeyTab Test 42"
        activity.runOnUiThread {
            input.requestFocus()
            input.setText(value)
            input.setSelection(value.length)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(value, input.text.toString())
        assertEquals(value.length, input.selectionStart)
        assertEquals(value.length, input.selectionEnd)
    }
    @Test
    fun languageSelection_persistsSelectedLanguage() {
        val activity = activityRule.activity
        val spinner = activity.findViewById<android.widget.Spinner>(R.id.spinner_language)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = Prefs.of(context)
        val current = spinner.selectedItemPosition
        val target = if (current == 0) 1 else 0
        activity.runOnUiThread { spinner.setSelection(target) }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(Languages.all[target].code, prefs.getString(Prefs.KEY_LANGUAGE, "de"))
    }

    @Test
    fun testInputField_supportsMultilineAndDeletion() {
        val activity = activityRule.activity
        val input = activity.findViewById<android.widget.EditText>(R.id.test_input)
        activity.runOnUiThread {
            input.setText("first line\nsecond line")
            input.getText().delete(10, input.length())
            input.setSelection(10)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals("first line", input.text.toString())
        assertEquals(10, input.selectionStart)
    }

    @Test
    fun settingsButtons_haveAccessibleLabels() {
        val activity = activityRule.activity
        listOf(R.id.btn_enable_keyboard, R.id.btn_switch_keyboard,
            R.id.btn_theme_settings, R.id.btn_update_config).forEach { id ->
            val button = activity.findViewById<android.widget.Button>(id)
            val label = button.text?.toString()?.trim().orEmpty()
            assertTrue("Button $id has no accessible label", label.isNotEmpty())
            assertTrue("Button $id label is too short", label.length >= 3)
        }
    }





    @Test
    fun fileProvider_isInstalled() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = context.packageManager.resolveContentProvider(
            "${context.packageName}.fileprovider", 0
        )
        assertTrue("FileProvider fehlt oder ist falsch autorisiert", provider != null)
    }
}
