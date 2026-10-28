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
import com.piotv.keytab.ime.KeyTabExecutors
import com.piotv.keytab.ime.KeyboardLanguage
import com.piotv.keytab.ime.Languages
import com.piotv.keytab.ime.SettingsConfig
import com.piotv.keytab.ime.StorageAccess
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
                2 -> Toast.makeText(this, grantedStatusText(),
                    Toast.LENGTH_SHORT).show()
                1 -> Toast.makeText(this, R.string.settings_storage_partial,
                    Toast.LENGTH_LONG).show()
                else -> onFileAccessDenied()
            }
        }

    companion object {
        private const val ANDROID_14 = 34
        private const val ANDROID_13 = 33

        /** „Alle Dateien“ (MANAGE_EXTERNAL_STORAGE) gibt es ab API 30 (Android 11). */
        private const val ANDROID_11 = 30

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
        // Edge-to-Edge + Systembar-Insets (Pflicht ab targetSdk 35) — siehe SystemBars.kt
        applyEdgeToEdgeInsets()

        // Versionszeile aus PackageManager
        try {
            val vName = packageManager.getPackageInfo(packageName, 0).versionName
            findViewById<android.widget.TextView>(R.id.text_version).text =
                getString(R.string.settings_version_label, vName)
        } catch (_: Exception) { /* Fallback-Text bleibt */ }

        val prefs = Prefs.of(this)

        setupLanguageSpinner()
        setupBooleanSwitch(R.id.sw_num_row, Prefs.KEY_NUM_ROW, true,
            R.string.settings_num_row_on, R.string.settings_num_row_off)
        setupBooleanSwitch(R.id.sw_clip_tab, Prefs.KEY_CLIP_TAB, true,
            R.string.settings_clip_tab_on, R.string.settings_clip_tab_off)
        setupBooleanSwitch(R.id.sw_snippet_tab, Prefs.KEY_SNIPPET_TAB, true,
            R.string.settings_snippet_tab_on, R.string.settings_snippet_tab_off)
        setupBooleanSwitch(R.id.sw_suggestions, Prefs.KEY_SUGGESTIONS, true,
            R.string.settings_suggestions_on, R.string.settings_suggestions_off)
        setupBooleanSwitch(R.id.sw_dynamic_keys, Prefs.KEY_DYNAMIC_KEYS, true,
            R.string.settings_dynamic_keys_on, R.string.settings_dynamic_keys_off)
        setupBooleanSwitch(R.id.sw_swipe, Prefs.KEY_SWIPE, false,
            R.string.settings_swipe_on, R.string.settings_swipe_off)

        setupConfigButtons()
        setupKeyboardButtons()
        setupFileAccessButtons()
        displayedSettings = SettingsConfig.snapshot(prefs)
    }

    private fun setupLanguageSpinner() {
        val prefs = Prefs.of(this)
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
    }

    /**
     * Buttons im Abschnitt „Speicher & Konfiguration": Konfigurationsdatei
     * schreiben/aktualisieren und das gelernte Wörterbuch aufräumen.
     *
     * Die Verdrahtung des Aufräum-Buttons ging beim Aufteilen der
     * Settings-Initialisierung (v0.20) verloren — der Button war wirkungslos,
     * [cleanupLearnedDictionary] dadurch ungenutzt (detekt `UnusedPrivateMember`).
     * Beide Buttons stehen bewusst in **einer** Funktion: eine zweite würde die
     * detekt-Schwelle für `MainActivity` (20 Funktionen) reißen.
     */
    private fun setupConfigButtons() {
        // Konfigurationsdatei schreiben/aktualisieren (Werte direkt editierbar).
        // Datei-I/O läuft nicht auf dem Main-Thread (N21).
        findViewById<Button>(R.id.btn_update_config).setOnClickListener {
            KeyTabExecutors.io.execute {
                try {
                    val file = SettingsConfig.fillMissing(this)
                    KeyTabExecutors.main.post {
                        Toast.makeText(this, getString(R.string.config_ready, file.absolutePath),
                            Toast.LENGTH_LONG).show()
                        recreate()
                    }
                } catch (e: Exception) {
                    val message = e.localizedMessage ?: e.toString()
                    KeyTabExecutors.main.post {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        findViewById<Button>(R.id.btn_cleanup_learned_dictionary).setOnClickListener {
            cleanupLearnedDictionary()
        }
    }

    private fun setupKeyboardButtons() {
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
    }

    private fun setupFileAccessButtons() {
        // Dateizugriff nur auf ausdrücklichen Wunsch (F-Droid-Review 2026-10-06):
        // kein Dialog beim Start, sondern Status-Zeile + Button. Nur
        // READ_MEDIA_IMAGES/READ_EXTERNAL_STORAGE — Audio/Video liest kein
        // Codepfad (siehe AndroidManifest-Kommentar).
        findViewById<Button>(R.id.btn_grant_file_access).setOnClickListener {
            val missing = missingFileAccessPermissions()
            if (missing.isEmpty()) {
                Toast.makeText(this, grantedStatusText(), Toast.LENGTH_SHORT).show()
                refreshFileAccess()
            } else {
                permLauncher.launch(missing)
            }
        }
        // All-files access (API 30+) — bewusst optional und nur hier, auf
        setupAllFilesButton()
        refreshFileAccess()
    }

    override fun onResume() {
        super.onResume()
        applyDarkModeOverride()
        SettingsConfig.importIfChanged(this)
        // Zurück aus den System-Einstellungen (All-files access) → Status neu lesen.
        refreshFileAccess()
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

    /** 0 = kein Zugriff, 1 = nur ausgewählte Bilder (Android 14+), 2 = Medien, 3 = alle Dateien. */
    private fun fileAccessLevel(): Int {
        if (hasAllFilesAccess()) return 3
        val images = isGranted(Manifest.permission.READ_MEDIA_IMAGES)
        return when {
            Build.VERSION.SDK_INT >= ANDROID_14 -> when {
                images -> 2
                isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> 1
                else -> 0
            }
            Build.VERSION.SDK_INT >= ANDROID_13 -> if (images) 2 else 0
            else -> if (isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) 2 else 0
        }
    }

    /**
     * „Alle Dateien“ (API 30+) — nur über den System-Screen zu erteilen, nie
     * automatisch und nie im Hintergrund. Ohne diese Freigabe bleibt der
     * Medien-Weg (Ordner + Bilder) der Standard; mit ihr listet der Files-Tab
     * jeden Dateityp (siehe AndroidManifest-Begründung). Die Abfrage selbst liegt
     * in [StorageAccess], damit Statuszeile und Files-Tab nie auseinanderlaufen.
     */
    private fun hasAllFilesAccess(): Boolean = StorageAccess.allFilesGranted()

    /**
     * Button „Grant all-files access“ (API 30+): erst der Begründungs-Dialog, dann
     * der System-Screen — nie beim Start, nie im Hintergrund. Unter API 30 gibt es
     * die Berechtigung nicht, dort bleibt der Button ausgeblendet. Ausgelagert,
     * damit `onCreate` unter der detekt-Komplexitätsgrenze bleibt.
     */
    private fun setupAllFilesButton() {
        findViewById<Button>(R.id.btn_grant_all_files).apply {
            visibility = if (Build.VERSION.SDK_INT >= ANDROID_11) View.VISIBLE else View.GONE
            setOnClickListener { confirmAllFilesAccess() }
        }
    }

    /**
     * Begründung **vor** dem System-Screen (F-Droid-Konvention: Berechtigungen
     * erklären und im Kontext erfragen, nicht beim Start). MANAGE_EXTERNAL_STORAGE
     * lässt sich nur dort erteilen — ein Dialog kann das nicht.
     */
    private fun confirmAllFilesAccess() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_all_files_title)
            .setMessage(R.string.settings_all_files_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_all_files_open) { _, _ -> openAllFilesSettings() }
            .show()
    }

    /**
     * System-Screen „Alle Dateien“ öffnen. Der paketbezogene Intent ist der
     * direkteste Weg; kennt ein ROM ihn nicht, greift die allgemeine Variante.
     */
    private fun openAllFilesSettings() {
        val appSpecific = Intent(
            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            android.net.Uri.fromParts("package", packageName, null)
        )
        try {
            startActivity(appSpecific)
        } catch (_: Exception) {
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
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
        Build.VERSION.SDK_INT >= ANDROID_13 -> neededPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        else -> neededPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Status-Zeile und Buttons an den aktuellen Zugriff anpassen (ohne Dialog). */
    private fun refreshFileAccess() {
        val level = fileAccessLevel()
        findViewById<android.widget.TextView>(R.id.text_file_access).setText(
            when (level) {
                3 -> R.string.settings_storage_all_files
                2 -> grantedStatusText()
                1 -> R.string.settings_storage_partial
                else -> R.string.settings_storage_missing
            }
        )
        findViewById<Button>(R.id.btn_grant_file_access).isEnabled = level < 2
        findViewById<Button>(R.id.btn_grant_all_files).isEnabled = level < 3
    }

    /**
     * Text für vollen Zugriff. Ab Android 13 ist das **kein** voller Dateizugriff:
     * `READ_MEDIA_IMAGES` gibt nur Bilder frei (Ordner + Bilder sichtbar, PDF/ZIP/
     * XLSX nicht) — bis Android 12 deckt `READ_EXTERNAL_STORAGE` den ganzen
     * gemeinsamen Speicher ab. Der Status sagt jeweils, was gilt
     * (F-Droid-Testerbericht 2026-10-13).
     *
     * `sdkInt` ist ein Parameter, damit **beide** Formulierungen ohne zweites
     * Robolectric-SDK-Level testbar sind (`@Config(sdk = [32])` lädt in CI ein
     * zusätzliches android-all-Jar nach — genau das scheiterte dort am
     * TLS-Download).
     */
    internal fun grantedStatusText(sdkInt: Int = Build.VERSION.SDK_INT): Int =
        if (sdkInt >= ANDROID_13) R.string.settings_storage_granted_media
        else R.string.settings_storage_granted

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

/** Booleschen Einstellungs-Schalter mit Pref-Key, Default und an/aus-Toast verdrahten. */
private fun MainActivity.setupBooleanSwitch(id: Int, key: String, default: Boolean, onRes: Int, offRes: Int) {
    val sw = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(id)
    sw.isChecked = Prefs.of(this).getBoolean(key, default)
    sw.setOnCheckedChangeListener { _, checked ->
        Prefs.of(this).edit().putBoolean(key, checked).apply()
        Toast.makeText(this, if (checked) onRes else offRes, Toast.LENGTH_SHORT).show()
    }
}

