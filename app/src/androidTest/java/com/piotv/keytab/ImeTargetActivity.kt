package com.piotv.keytab

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout

/**
 * Foreign target window for real InputMethodService instrumentation tests.
 * Packaged only in the test APK and intentionally independent of production code.
 */
class ImeTargetActivity : Activity() {
    lateinit var normalField: EditText
        private set
    lateinit var passwordField: EditText
        private set
    lateinit var noLearningField: EditText
        private set
    val receivedKeyCodes: MutableList<Int> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        normalField = createField("normal")
        passwordField = createField("password", InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_PASSWORD)
        noLearningField = createField("no-learning", InputType.TYPE_CLASS_TEXT).apply {
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(normalField)
            addView(passwordField)
            addView(noLearningField)
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) receivedKeyCodes += event.keyCode
        return super.dispatchKeyEvent(event)
    }

    private fun createField(label: String, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(this).apply {
            hint = label
            setSingleLine(false)
            this.inputType = inputType
        }
}
