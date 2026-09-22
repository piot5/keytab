package com.piotv.keytab.ime

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * KeyTabImeService: schmale Orchestrations-Vertraege (kein Full-Service).
 * Caps-Entscheidung, Trail-Passwortschutz-Spiegel, Shift-Delegation,
 * Dark-Mode-Pref-Override, Editor/Terminal-Tab-Mapping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyTabImeServiceTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    private fun service(): KeyTabImeService =
        Robolectric.buildService(KeyTabImeService::class.java).get()

    private fun textInfo(variation: Int = 0, capsFlag: Boolean = false): EditorInfo =
        EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or variation or
                (if (capsFlag) InputType.TYPE_TEXT_FLAG_CAP_SENTENCES else 0)
        }

    @Test
    fun `Passwortfelder bekommen weder Caps noch Trail`() {
        val pw = textInfo(InputType.TYPE_TEXT_VARIATION_PASSWORD, capsFlag = true)
        assertFalse(CapsLogic.wantsCapitalization(pw))
        assertFalse(TrailLogic.isTrailAllowed(pw))
        assertTrue(TrailLogic.isPasswordField(pw))
        val plain = textInfo(capsFlag = true)
        assertTrue(CapsLogic.wantsCapitalization(plain))
        assertTrue(TrailLogic.isTrailAllowed(plain))
    }

    @Test
    fun `NO_PERSONALIZED_LEARNING blockt Trail und Swipe`() {
        val info = textInfo().apply {
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        assertFalse(TrailLogic.isTrailAllowed(info))
        assertFalse(SwipePathLogic.isSwipeAllowed(info))
    }

    @Test
    fun `Service startet ohne Shift und ohne CapsLock`() {
        val s = service()
        assertFalse(s.isShifted())
        assertFalse(s.isCapsLock())
    }

    @Test
    fun `tapShift toggelt Shift ueber den Service`() {
        val s = service()
        s.tapShift(100L)
        assertTrue(s.isShifted())
        s.consumeSingleShift()
        assertFalse(s.isShifted())
        assertFalse(s.isCapsLock())
    }

    @Test
    fun `Doppel-Tap am Service aktiviert CapsLock`() {
        val s = service()
        s.tapShift(100L)
        s.tapShift(150L)
        assertTrue(s.isCapsLock())
        assertTrue(s.isShifted())
    }

    @Test
    fun `Dark-Mode folgt Pref-Override vor System`() {
        val prefs = com.piotv.keytab.Prefs.of(app)
        prefs.edit().putBoolean(ThemePrefs.KEY_DARK, true).commit()
        assertTrue(service().isDarkMode())
        prefs.edit().putBoolean(ThemePrefs.KEY_DARK, false).commit()
        assertFalse(service().isDarkMode())
        prefs.edit().remove(ThemePrefs.KEY_DARK).commit()
    }

    @Test
    fun `hideKeyboard ohne View crasht nicht`() {
        service().hideKeyboard()
    }

    @Test
    fun `isEditorOrTerminalTab startet false (ABC-Tab)`() {
        assertFalse(service().isEditorOrTerminalTab())
    }
}
