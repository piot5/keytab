package com.piotv.keytab

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.piotv.keytab.ime.KeyboardLanguage
import com.piotv.keytab.ime.Languages

/**
 * KeyTab – Einstellungsbildschirm: Tastatur aktivieren/wechseln, Theme.
 * Fragt beim Start die Speicher-Berechtigungen an, damit der IME-Dateimanager
 * auch Dateien (nicht nur Ordner) auflisten kann.
 */
class MainActivity : AppCompatActivity() {

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Ergebnis ignorieren; IME zeigt Zugriff nur wenn erteilt.
        }

    companion object {
        const val PREFS = "keytab_prefs"
        const val KEY_NUM_ROW = "num_row"
        const val KEY_TERM_TAB = "term_tab_enabled"
        const val KEY_SUGGESTIONS = "suggestions_enabled"
        const val KEY_DYNAMIC_KEYS = "dynamic_keys_enabled"
        const val KEY_LANGUAGE = "language"

        /** Aktive Sprache aus den Einstellungen (Default Deutsch). */
        fun activeLanguage(context: Context): KeyboardLanguage {
            val code = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, "de")
            return Languages.byCode(code)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Zahlenreihe-Umschalter (wirkt beim nächsten Öffnen der Tastatur)
        val swNumRow = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_num_row)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Sprachauswahl (Spinner) – wirkt beim nächsten Öffnen der Tastatur
        val langSpinner = findViewById<Spinner>(R.id.spinner_language)
        val names = Languages.all.map { it.displayName }
        langSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, names
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val currentIndex = Languages.all.indexOfFirst { it.code == activeLanguage(this).code }
        langSpinner.setSelection(currentIndex.coerceAtLeast(0))
        langSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val lang = Languages.all.getOrNull(pos) ?: return
                val prev = prefs.getString(KEY_LANGUAGE, "de")
                if (prev == lang.code) return
                prefs.edit().putString(KEY_LANGUAGE, lang.code).apply()
                Toast.makeText(this@MainActivity,
                    getString(R.string.language_changed_to, lang.displayName), Toast.LENGTH_SHORT).show()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        swNumRow.isChecked = prefs.getBoolean(KEY_NUM_ROW, false)
        swNumRow.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_NUM_ROW, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_num_row_on
            else R.string.settings_num_row_off, Toast.LENGTH_SHORT).show()
        }

        // Terminal-Tab in der Tastatur ein-/ausblenden (wirkt beim nächsten Öffnen)
        val swTermTab = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_term_tab)
        swTermTab.isChecked = prefs.getBoolean(KEY_TERM_TAB, true)
        swTermTab.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_TERM_TAB, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_term_on
            else R.string.settings_term_off, Toast.LENGTH_SHORT).show()
        }

        // Wortvorhersage ein-/ausblenden (wirkt beim nächsten Öffnen der Tastatur)
        val swSuggestions = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_suggestions)
        swSuggestions.isChecked = prefs.getBoolean(KEY_SUGGESTIONS, true)
        swSuggestions.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_SUGGESTIONS, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_suggestions_on
            else R.string.settings_suggestions_off, Toast.LENGTH_SHORT).show()
        }

        // Dynamische Tastengröße ein-/ausblenden (wirkt beim nächsten Öffnen)
        val swDynamic = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_dynamic_keys)
        swDynamic.isChecked = prefs.getBoolean(KEY_DYNAMIC_KEYS, true)
        swDynamic.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_DYNAMIC_KEYS, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_dynamic_keys_on
            else R.string.settings_dynamic_keys_off, Toast.LENGTH_SHORT).show()
        }

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

        // Speicher-Berechtigung anstoßen, falls IME Zugriff verweigert
        val missingStorage = if (Build.VERSION.SDK_INT >= 33) {
            neededPermissions(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            neededPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (missingStorage.isNotEmpty()) {
            permLauncher.launch(missingStorage)
        }

        // Button: MANAGE_EXTERNAL_STORAGE direkt öffnen (falls nötig)
        findViewById<Button>(R.id.btn_request_manage_storage).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 30) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    private fun neededPermissions(vararg perms: String): Array<String> {
        return perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }
}
