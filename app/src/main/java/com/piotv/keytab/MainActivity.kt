package com.piotv.keytab

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

/**
 * KeyTab – einfacher Einstellungs-Bildschirm:
 * Tastatur aktivieren, Tastatur wechseln, Themenwahl (Dark/Light).
 * Die eigentliche Tastatur mit abc/Files-Tabs lebt im IME (KeyTabImeService).
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_enable_keyboard).setOnClickListener {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btn_switch_keyboard).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        findViewById<Button>(R.id.btn_theme_dark).setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        findViewById<Button>(R.id.btn_theme_light).setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}
