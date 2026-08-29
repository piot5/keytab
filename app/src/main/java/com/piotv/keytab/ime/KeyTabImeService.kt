package com.piotv.keytab.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.piotv.keytab.R

/** KeyTab IME – Bildschirmtastatur mit echter TAB-Taste (KEYCODE_TAB). */
class KeyTabImeService : InputMethodService() {

    private var shifted = false

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout
        forEachButton(view) { btn ->
            val tag = btn.tag as? String
            when {
                tag?.startsWith("letter:") == true -> btn.setOnClickListener {
                    commitLetter(tag.removePrefix("letter:"), view)
                }
                btn.id == R.id.key_shift -> btn.setOnClickListener {
                    shifted = !shifted
                    btn.alpha = if (shifted) 1f else 0.6f
                    applyCase(view)
                }
                btn.id == R.id.key_tab -> btn.setOnClickListener {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
                }
                btn.id == R.id.key_del -> btn.setOnClickListener {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                }
                btn.id == R.id.key_enter -> btn.setOnClickListener {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
                btn.id == R.id.key_space -> btn.setOnClickListener {
                    currentInputConnection?.commitText(" ", 1)
                }
            }
        }
        applyCase(view)
        return view
    }

    private fun commitLetter(letter: String, root: View) {
        currentInputConnection?.commitText(if (shifted) letter.uppercase() else letter, 1)
        if (shifted) {
            shifted = false
            applyCase(root)
        }
    }

    private fun applyCase(root: View) {
        forEachButton(root) { btn ->
            val tag = btn.tag as? String
            if (tag?.startsWith("letter:") == true) {
                val letter = tag.removePrefix("letter:")
                btn.text = if (shifted) letter.uppercase() else letter
            }
        }
    }

    private fun forEachButton(root: View, action: (Button) -> Unit) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) forEachButton(root.getChildAt(i), action)
        } else if (root is Button) {
            action(root)
        }
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        shifted = false
    }
}
