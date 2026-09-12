package com.piotv.keytab.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Reine Ableitungen rund um Groß-/Kleinschreibung (keine View-Abhängigkeit).
 * Refactoring (docs/REFACTORING_PLAN.md Phase 2): aus [KeyTabImeService]
 * extrahiert, damit die Auto-Caps-Entscheidung unit-testbar ist.
 */
object CapsLogic {

    /**
     * Auto-Caps beim Start in ein Feld: true wenn das Feld Satzanfänge bzw.
     * initial Caps erwartet (CAP_SENTENCES / initialCapsMode). Passwort-Felder
     * und Nicht-Text niemals. [attribute] null → false.
     */
    fun wantsCapitalization(attribute: EditorInfo?): Boolean {
        attribute ?: return false
        val inputType = attribute.inputType
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        ) return false
        return inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 ||
            attribute.initialCapsMode != 0
    }
}