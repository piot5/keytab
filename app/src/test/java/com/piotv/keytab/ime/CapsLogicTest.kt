package com.piotv.keytab.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Auto-Caps-Entscheidung (pure Ableitung). EditorInfo ist ein Struct mit
 * öffentlichen Feldern → direkt setzbar (Robolectric für korrekte Klasse).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CapsLogicTest {

    /** Hilfskonstruktion: EditText-Feld mit gegebenem inputType/capsMode. */
    private fun info(inputType: Int, capsMode: Int = 0): EditorInfo {
        val ei = EditorInfo()
        ei.inputType = inputType
        ei.initialCapsMode = capsMode
        return ei
    }

    private fun text(variation: Int, capsFlag: Boolean = false, capsMode: Int = 0): EditorInfo =
        info(InputType.TYPE_CLASS_TEXT or variation or
            (if (capsFlag) InputType.TYPE_TEXT_FLAG_CAP_SENTENCES else 0), capsMode)

    @Test
    fun `null attribute → nie Caps`() {
        assertFalse(CapsLogic.wantsCapitalization(null))
    }

    @Test
    fun `CAP_SENTENCES → Caps an`() {
        assertTrue(CapsLogic.wantsCapitalization(text(0, capsFlag = true)))
    }

    @Test
    fun `initialCapsMode gt 0 → Caps an (auch ohne Flag)`() {
        assertTrue(CapsLogic.wantsCapitalization(text(0, capsMode = 0x2000)))
    }

    @Test
    fun `normales Textfeld ohne Hinweis → Caps aus`() {
        assertFalse(CapsLogic.wantsCapitalization(text(0)))
    }

    @Test
    fun `Passwort → nie Caps (auch mit CAP_SENTENCES)`() {
        assertFalse(CapsLogic.wantsCapitalization(
            text(InputType.TYPE_TEXT_VARIATION_PASSWORD, capsFlag = true)))
        assertFalse(CapsLogic.wantsCapitalization(
            text(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, capsFlag = true)))
        assertFalse(CapsLogic.wantsCapitalization(
            text(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, capsFlag = true)))
    }

    @Test
    fun `nicht-Text (Zahlen) → nie Caps`() {
        val numeric = info(InputType.TYPE_CLASS_NUMBER)
        assertFalse(CapsLogic.wantsCapitalization(numeric))
        val phone = info(InputType.TYPE_CLASS_PHONE)
        assertFalse(CapsLogic.wantsCapitalization(phone))
    }
}