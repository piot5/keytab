package com.piotv.keytab.ime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.R

/**
 * App-Tab "abc": zeigt eine Live-Vorschau der KeyTab-Tastatur (nicht bedienbar)
 * plus Buttons zum Aktivieren/Wechseln der Tastatur.
 */
class KeyboardInfoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_keyboard, container, false)
        val preview = inflater.inflate(R.layout.keyboard_view, null)
        view.findViewById<FrameLayout>(R.id.preview_root).addView(preview)
        disableButtonsIn(preview)
        return view
    }

    /** Vorschau-Tasten deaktivieren – sie sollen nichts tun. */
    private fun disableButtonsIn(v: View) {
        when (v) {
            is Button -> v.isEnabled = false
            is TabLayout -> v.isEnabled = false
            is ViewGroup -> for (i in 0 until v.childCount) disableButtonsIn(v.getChildAt(i))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<Button>(R.id.btn_enable_keyboard).setOnClickListener {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<Button>(R.id.btn_switch_keyboard).setOnClickListener {
            val imm = requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }
}
