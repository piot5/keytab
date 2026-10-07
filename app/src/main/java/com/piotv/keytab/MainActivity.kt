package com.piotv.keytab

import android.Manifest
import android.app.AlertDialog
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
import com.piotv.keytab.ime.SuggestionEngine
import com.piotv.keytab.ime.ThemePrefs

/**
 * KeyTab – Einstellungsbildschirm: Tastatur aktivieren/wechseln, Theme.
 *
 * Der Dateizugriff für den IME-Dateimanager (und damit die Datei-Liste des
 * Files-Tabs) wird **nicht** beim Start, sondern gezielt über den Button
 * „Grant file access“ erteilt — der IME kann den System-Dialog nicht selbst
 * zeigen, und Tippen funktioniert ohne Zugriff vollständig (F-Droid-Review
 * 2026-10-06, Punkt 1). Daneben steht der aktuelle Status.
 */
class MainActivity : AppCompatActivity() {

    private var displayedSettings: Map<String, Any?>? = null

    /**
     * Wird ausschließlich vom „Grant file access“-Button ausgelöst (kein Prompt
     * beim App-Start). Die Rückmeldung richtet sich nach dem erreichten Zugriff:
     * voll, nur ausgewählte Bilder (Android 14+) oder abgelehnt.
     */
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshFileAccess()
            when (fileAccessLevel()) {
                2 -> Toast.makeText(this, R.string.settings_storage_granted,
                    Toast.LENGTH_SHORT).show()
                1 -> Toast.makeText(this, R.string.settings_storage_partial,
                    Toast.LENGTH_LONG).show()
                else -> onFileAccessDenied()
            }
        }

    companion object {
        private const val ANDROID_14 = 34

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
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) { /* kein Bedarf: keine Neutralposition */ return }
        }
        swNumRow.isChecked = prefs.getBoolean(Prefs.KEY_NUM_ROW, true)
        swNumRow.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.KEY_NUM_ROW, checked).apply()
            Toast.makeText(this, if (checked) R.string.settings_num_row_on
            else R.string.settings_num_row_off, Toast.LENGTH_SHORT).show()
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

        findViewById<Button>(R.id.btn_cleanup_learned_dictionary).setOnClickListener {
            cleanupLearnedDictionary()
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

        // Dateizugriff nur auf ausdrücklichen Wunsch (F-Droid-Review 2026-10-06):
        // kein Dialog beim Start, sondern Status-Zeile + Button. Nur
        // READ_MEDIA_IMAGES/READ_EXTERNAL_STORAGE — Audio/Video liest kein
        // Codepfad (siehe AndroidManifest-Kommentar).
        findViewById<Button>(R.id.btn_grant_file_access).setOnClickListener {
            val missing = missingFileAccessPermissions()
            if (missing.isEmpty()) {
                Toast.makeText(this, R.string.settings_storage_granted, Toast.LENGTH_SHORT).show()
                refreshFileAccess()
            } else {
                permLauncher.launch(missing)
            }
        }
        refreshFileAccess()
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

    /** Prüft das gelernte Feld offline und entfernt nur bestätigte Artefakte. */
    private fun cleanupLearnedDictionary() {
        val prefs = Prefs.of(this)
        val engine = SuggestionEngine(emptyList())
        val raw = prefs.getString(Prefs.KEY_USER_DICT, null)
        if (raw != null) engine.restoreUserDict(raw)
        val entries = LearnedDictionaryApi.listAll(engine)
        val suspicious = entries.filter { LearnedDictionaryApi.looksLikeArtifact(it.word) }
        if (suspicious.isEmpty()) {
            Toast.makeText(this, R.string.settings_cleanup_dictionary_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val revision = engine.revision
        val preview = LearnedDictionaryApi.batchPreview(
            engine,
            suspicious.map { LearnedDictionaryApi.CleanupOperation.Delete(it.word, revision) },
            emptyList()
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_cleanup_dictionary_title)
            .setMessage(getString(R.string.settings_cleanup_dictionary_found, preview.wouldDelete.size))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                when (val result = LearnedDictionaryApi.applyPreview(engine, preview, confirm = true)) {
                    is LearnedDictionaryApi.CleanupResult.Ok -> {
                        prefs.edit().putString(Prefs.KEY_USER_DICT, engine.serializeUserDict()).apply()
                        Toast.makeText(this, getString(R.string.settings_cleanup_dictionary_done,
                            result.deletedCount), Toast.LENGTH_SHORT).show()
                    }
                    is LearnedDictionaryApi.CleanupResult.Rejected ->
                        Toast.makeText(this, R.string.settings_cleanup_dictionary_failed, Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    /** 0 = kein Zugriff, 1 = nur ausgewählte Bilder (Android 14+), 2 = voll. */
    private fun fileAccessLevel(): Int {
        val images = isGranted(Manifest.permission.READ_MEDIA_IMAGES)
        return when {
            Build.VERSION.SDK_INT >= ANDROID_14 -> when {
                images -> 2
                isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> 1
                else -> 0
            }
            Build.VERSION.SDK_INT >= 33 -> if (images) 2 else 0
            else -> if (isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) 2 else 0
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /** Noch fehlende Berechtigungen für den Files-Tab (nichts anderes liest Dateien). */
    private fun missingFileAccessPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= ANDROID_14 -> neededPermissions(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> neededPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        else -> neededPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Status-Zeile und Button an den aktuellen Zugriff anpassen (ohne Dialog). */
    private fun refreshFileAccess() {
        val level = fileAccessLevel()
        findViewById<android.widget.TextView>(R.id.text_file_access).setText(
            when (level) {
                2 -> R.string.settings_storage_granted
                1 -> R.string.settings_storage_partial
                else -> R.string.settings_storage_missing
            }
        )
        findViewById<Button>(R.id.btn_grant_file_access).isEnabled = level != 2
    }

    /**
     * Nach einer Ablehnung: läuft der System-Dialog noch, genügt ein Hinweis;
     * ist die Berechtigung endgültig abgelehnt, zeigt das System keinen Dialog
     * mehr — dann führt der Weg nur über die App-Einstellungen.
     */
    private fun onFileAccessDenied() {
        val missing = missingFileAccessPermissions()
        if (missing.isEmpty() || missing.any { shouldShowRequestPermissionRationale(it) }) {
            Toast.makeText(this, R.string.settings_storage_denied, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setMessage(R.string.settings_storage_denied)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_open_app_settings) { _, _ ->
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", packageName, null)
                    )
                )
            }
            .show()
    }

    private fun neededPermissions(vararg perms: String): Array<String> {
        return perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }
}
