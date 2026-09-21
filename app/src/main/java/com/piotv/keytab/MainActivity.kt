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
import com.piotv.keytab.ime.SettingsConfig
import com.piotv.keytab.ime.ThemePrefs

/**
 * KeyTab – Einstellungsbildschirm: Tastatur aktivieren/wechseln, Theme.
 * Fragt beim Start die Speicher-Berechtigungen an, damit der IME-Dateimanager
 * auch Dateien (nicht nur Ordner) auflisten kann.
 */
class MainActivity : AppCompatActivity() {

    private var displayedSettings: Map<String, Any?>? = null

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Ergebnis ignorieren; IME zeigt Zugriff nur wenn erteilt.
        }

    companion object {
        /** Alias-Kompatibilität: `MainActivity.PREFS` == `Prefs.FILE` (alter Aufrufer). */
        const val PREFS = Prefs.FILE

        /** Aktive Sprache aus den Einstellungen (Default Deutsch). */
        fun activeLanguage(context: Context): KeyboardLanguage {
            val code = Prefs.of(context)
                .getString(Prefs.KEY_LANGUAGE, "de")
            return Languages.byCode(code)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // In der Tastatur gewählter Modus (☾ Dark / ☀ Light) bestimmt auch das
        // App-Layout (Einstellungen) — nicht nur das System-Theme.
        applyDarkModeOverride()
        super.onCreate(savedInstanceState)
        SettingsConfig.importIfChanged(this)
        setContentView(R.layout.activity_main)

        // Versionszeile aus PackageManager
        try {
            val vName = packageManager.getPackageInfo(packageName, 0).versionName
            findViewById<android.widget.TextView>(R.id.text_version).text =
                getString(R.string.settings_version_label, vName)
        } catch (_: Exception) { /* Fallback-Text bleibt */ }

        // Zahlenreihe-Umschalter (wirkt beim nächsten Öffnen der Tastatur)
        val swNumRow = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_num_row)
        val prefs = Prefs.of(this)

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
                val prev = prefs.getString(Prefs.KEY_LANGUAGE, "de")
                if (prev == lang.code) return
                prefs.edit().putString(Prefs.KEY_LANGUAGE, lang.code).apply()
                Toast.makeText(this@MainActivity,
                    getString(R.string.language_changed_to, lang.displayName), Toast.LENGTH_SHORT).show()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        swNumRow.isChecked = prefs.getBoolean(Prefs.KEY_NUM_ROW, false)
        swNumRow.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.KEY_NUM_ROW, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_num_row_on
            else R.string.settings_num_row_off, Toast.LENGTH_SHORT).show()
        }

        // Terminal-Tab in der Tastatur ein-/ausblenden (wirkt beim nächsten Öffnen)
        val swTermTab = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_term_tab)
        swTermTab.isChecked = prefs.getBoolean(Prefs.KEY_TERM_TAB, true)
        swTermTab.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.KEY_TERM_TAB, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_term_on
            else R.string.settings_term_off, Toast.LENGTH_SHORT).show()
        }

        // Clipboard-Tab in der Tastatur ein-/ausblenden (wirkt beim nächsten Öffnen)
        val swClipTab = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_clip_tab)
        swClipTab.isChecked = prefs.getBoolean(Prefs.KEY_CLIP_TAB, true)
        swClipTab.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.KEY_CLIP_TAB, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_clip_tab_on
            else R.string.settings_clip_tab_off, Toast.LENGTH_SHORT).show()
        }

        // Snippet-Tab in der Tastatur ein-/ausblenden (wirkt beim nächsten Öffnen)
        val swSnippetTab = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_snippet_tab)
        swSnippetTab.isChecked = prefs.getBoolean(Prefs.KEY_SNIPPET_TAB, true)
        swSnippetTab.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.KEY_SNIPPET_TAB, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_snippet_tab_on
            else R.string.settings_snippet_tab_off, Toast.LENGTH_SHORT).show()
        }

        // Wortvorhersage ein-/ausblenden (wirkt beim nächsten Öffnen der Tastatur)
        val swSuggestions = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_suggestions)
        swSuggestions.isChecked = prefs.getBoolean(Prefs.KEY_SUGGESTIONS, true)
        swSuggestions.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.KEY_SUGGESTIONS, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_suggestions_on
            else R.string.settings_suggestions_off, Toast.LENGTH_SHORT).show()
        }

        // Emoji-Vorschläge (optional, Default aus; wirkt beim nächsten Vorschlags-Update)
        val swEmoji = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_emoji)
        swEmoji.isChecked = prefs.getBoolean(Prefs.KEY_EMOJI_SUGGESTIONS, false)
        swEmoji.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.KEY_EMOJI_SUGGESTIONS, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_emoji_on
            else R.string.settings_emoji_off, Toast.LENGTH_SHORT).show()
        }

        // Aktive Autokorrektur ein-/ausschalten (wirkt sofort)
        val swAutoCorrect = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_autocorrect)
        swAutoCorrect.isChecked = prefs.getBoolean(Prefs.KEY_AUTOCORRECT, true)
        swAutoCorrect.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.KEY_AUTOCORRECT, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_autocorrect_on
            else R.string.settings_autocorrect_off, Toast.LENGTH_SHORT).show()
        }

        // Dynamische Tastengröße ein-/ausblenden (wirkt beim nächsten Öffnen)
        val swDynamic = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_dynamic_keys)
        swDynamic.isChecked = prefs.getBoolean(Prefs.KEY_DYNAMIC_KEYS, true)
        swDynamic.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.KEY_DYNAMIC_KEYS, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_dynamic_keys_on
            else R.string.settings_dynamic_keys_off, Toast.LENGTH_SHORT).show()
        }

        // Swipe-Eingabe (Gleit-Eingabe, v0.11) ein-/ausblenden
        val swSwipe = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_swipe)
        swSwipe.isChecked = prefs.getBoolean(Prefs.KEY_SWIPE, false)
        swSwipe.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.KEY_SWIPE, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_swipe_on
            else R.string.settings_swipe_off, Toast.LENGTH_SHORT).show()
        }

        // Schaltplan-Preview (passiver Pfad, v0.11) ein-/ausblenden
        val swSwipePreview = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_swipe_preview)
        swSwipePreview.isChecked = prefs.getBoolean(Prefs.KEY_SWIPE_PREVIEW, false)
        swSwipePreview.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.KEY_SWIPE_PREVIEW, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_swipe_preview_on
            else R.string.settings_swipe_preview_off, Toast.LENGTH_SHORT).show()
        }

        // Konfigurationsdatei schreiben/aktualisieren (Werte direkt editierbar)
        findViewById<Button>(R.id.btn_update_config).setOnClickListener {
            try {
                val file = SettingsConfig.fillMissing(this)
                Toast.makeText(this, getString(R.string.config_ready, file.absolutePath),
                    Toast.LENGTH_LONG).show()
                recreate()
            } catch (e: Exception) {
                Toast.makeText(this, e.localizedMessage ?: e.toString(), Toast.LENGTH_LONG).show()
            }
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

        // Zweite Einstellungsseite: Theme (Verlauf, Farben, Alpha)
        findViewById<Button>(R.id.btn_theme_settings).setOnClickListener {
            startActivity(Intent(this, ThemeSettingsActivity::class.java))
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
        displayedSettings = SettingsConfig.snapshot(prefs)
    }

    override fun onResume() {
        super.onResume()
        applyDarkModeOverride()
        SettingsConfig.importIfChanged(this)
        val current = SettingsConfig.snapshot(Prefs.of(this))
        if (displayedSettings != current) recreate()
    }

    /** Mond/Sonne-Override auf den App-Modus anwenden (beide Activities). */
    private fun applyDarkModeOverride() {
        val prefs = Prefs.of(this)
        val mode = if (prefs.contains(ThemePrefs.KEY_DARK)) {
            if (prefs.getBoolean(ThemePrefs.KEY_DARK, false))
                AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        } else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun neededPermissions(vararg perms: String): Array<String> {
        return perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }
}
