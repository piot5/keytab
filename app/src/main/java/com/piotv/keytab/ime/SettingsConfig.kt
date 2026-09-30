package com.piotv.keytab.ime

import android.content.Context
import android.content.SharedPreferences
import com.piotv.keytab.Prefs
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Imports explicit, valid settings once per file content; UI edits then remain authoritative. */
object SettingsConfig {
    internal const val KEY_FINGERPRINT = "config_import_sha256"

    private const val HEX_RADIX = 16
    private const val RGB_HEX_LENGTH = 7
    private const val OPAQUE_ALPHA_MASK = 0xFF000000L

    private data class Setting(
        val pref: String,
        val default: String,
        val type: String,
        val choices: Set<String> = emptySet()
    )

    private val settings = linkedMapOf<String, Setting>().apply {
        // Die Defaults sind die aus keytab_config.txt des Nutzers (2026-09-28) —
        // eine Neuinstallation sieht damit genau so aus wie sein Gerät.
        put("num_row", Setting(Prefs.KEY_NUM_ROW, "true", "boolean"))
        put("clip_tab", Setting(Prefs.KEY_CLIP_TAB, "true", "boolean"))
        put("snippet_tab", Setting(Prefs.KEY_SNIPPET_TAB, "true", "boolean"))
        put("suggestions", Setting(Prefs.KEY_SUGGESTIONS, "true", "boolean"))
        put("dynamic_keys", Setting(Prefs.KEY_DYNAMIC_KEYS, "true", "boolean"))
        // Swipe (Gleit-Eingabe), v0.11 — Default aus.
        put("swipe", Setting(Prefs.KEY_SWIPE, "false", "boolean"))
        // Trail (Tippspur + Treffer-Markierung). Vorher nur über Theme-Export
        // steuerbar – hier zusätzlich über keytab_config.txt (docs/CONFIG.md).
        put("trail", Setting(ThemePrefs.KEY_TRAIL, "false", "boolean"))
        put("trail_trace", Setting(ThemePrefs.KEY_TRAIL_TRACE, "false", "boolean"))
        put("trail_steps", Setting(ThemePrefs.KEY_TRAIL_STEPS,
            TrailLogic.DEFAULT_STEPS.toString(), "int"))
        put("language", Setting(Prefs.KEY_LANGUAGE, "de", "enum", Languages.all.map { it.code }.toSet()))
        put("bg_image_uri", Setting(Prefs.KEY_BG_IMAGE_URI, "", "string"))
        put("bg_image_fill", Setting(Prefs.KEY_BG_IMAGE_FILL, Prefs.FILL_FIT, "enum",
            setOf(Prefs.FILL_FIT, Prefs.FILL_COVER, Prefs.FILL_STRETCH)))
        put(ThemePrefs.KEY_DARK, Setting(ThemePrefs.KEY_DARK, "true", "dark"))
        for (key in listOf(ThemePrefs.KEY_LIKELY, ThemePrefs.KEY_LIKELY_EFFECT)) {
            put(key, Setting(key, "true", "boolean"))
        }
        for (key in listOf(ThemePrefs.KEY_GRADIENT_OFF, ThemePrefs.KEY_GRADIENT_OFF_LIGHT)) {
            put(key, Setting(key, "false", "boolean"))
        }
        put(ThemePrefs.KEY_GRADIENT_MODE, Setting(ThemePrefs.KEY_GRADIENT_MODE,
            ThemePrefs.GRADIENT_TOP_DOWN, "enum", setOf(ThemePrefs.GRADIENT_TOP_DOWN,
                ThemePrefs.GRADIENT_INVERT, ThemePrefs.GRADIENT_RADIAL)))
        for (key in listOf(ThemePrefs.KEY_GRADIENT_MODE_DARK, ThemePrefs.KEY_GRADIENT_MODE_LIGHT)) {
            // Nutzer-Standard: dunkel `invert`, hell `top_down` (gleiche Vorgabe
            // wie ThemeColorResolver.gradientMode).
            val standard = if (key == ThemePrefs.KEY_GRADIENT_MODE_DARK)
                ThemePrefs.GRADIENT_INVERT else ThemePrefs.GRADIENT_TOP_DOWN
            put(key, Setting(key, standard, "enum", setOf(
                ThemePrefs.GRADIENT_TOP_DOWN, ThemePrefs.GRADIENT_INVERT,
                ThemePrefs.GRADIENT_RADIAL)))
        }
        for (key in listOf(ThemePrefs.KEY_GRADIENT_COLOR1, ThemePrefs.KEY_GRADIENT_COLOR2)) {
            put(key, Setting(key, "default", "color"))
        }
        for (dark in listOf(true, false)) {
            for (kind in listOf(ThemePrefs.KIND_BG, ThemePrefs.KIND_KEY, ThemePrefs.KIND_HL,
                ThemePrefs.KIND_TEXT, ThemePrefs.KIND_LIKELY,
                ThemePrefs.KIND_GRADIENT1, ThemePrefs.KIND_GRADIENT2)) {
                val key = ThemePrefs.colorKey(dark, kind)
                put(key, Setting(key, "default", "color"))
            }
        }
        // Panel-Hoehen je Tab und Zustand (dp, 0 = automatisch berechnet) —
        // Reihenfolge hier = Reihenfolge in der Datei (Tab-Gruppen).
        for (tab in TabHeights.kinds()) {
            for (maximized in listOf(false, true)) {
                val key = TabHeights.configKey(tab, maximized)
                put(key, Setting(TabHeights.prefKey(tab, maximized),
                    TabHeights.AUTO.toString(), "int"))
            }
        }
    }

    fun configFile(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, KeyTabConfig.FILE_NAME)

    fun importIfChanged(context: Context): Boolean = importIfChanged(
        configFile(context), Prefs.of(context))

    /** Missing/unreadable files never clear preferences. */
    fun importIfChanged(file: File, prefs: SharedPreferences): Boolean = try {
        if (file.isFile) importTextIfChanged(file.readText(), prefs) else false
    } catch (_: Exception) { false }

    internal fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    internal fun importTextIfChanged(text: String, prefs: SharedPreferences): Boolean {
        val fingerprint = fingerprint(text)
        if (prefs.getString(KEY_FINGERPRINT, null) == fingerprint) return false
        val editor = prefs.edit()
        for ((key, value) in KeyTabConfig.entries(text)) {
            val spec = settings[key] ?: continue
            when (spec.type) {
                "boolean" -> strictBoolean(value)?.let { editor.putBoolean(spec.pref, it) }
                "dark" -> if (value == "system") editor.remove(spec.pref)
                    else strictBoolean(value)?.let { editor.putBoolean(spec.pref, it) }
                "enum" -> if (value in spec.choices) editor.putString(spec.pref, value)
                "string" -> editor.putString(spec.pref, value)
                "int" -> value.toIntOrNull()?.let { editor.putInt(spec.pref, it) }
                "color" -> if (value == "default") editor.remove(spec.pref)
                    else parseColor(value)?.let { editor.putInt(spec.pref, it) }
            }
        }
        // Scale-only changes must also invalidate a cached IME view.
        editor.putInt(ThemePrefs.KEY_THEME_VERSION, ThemePrefs.themeVersion(prefs) + 1)
            .putString(KEY_FINGERPRINT, fingerprint).apply()
        return true
    }

    private fun strictBoolean(value: String): Boolean? = when (value) {
        "true" -> true
        "false" -> false
        else -> null
    }

    internal fun parseColor(value: String): Int? {
        if (!Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?").matches(value)) return null
        val number = value.drop(1).toLongOrNull(HEX_RADIX) ?: return null
        return (if (value.length == RGB_HEX_LENGTH) number or OPAQUE_ALPHA_MASK else number).toInt()
    }

    /** UI settings only: learned words, clipboard, and panel state must not rebuild the IME. */
    fun snapshot(prefs: SharedPreferences): Map<String, Any?> {
        val all = prefs.all
        return (settings.values.map { it.pref } + ThemePrefs.KEY_THEME_VERSION)
            .associateWith { all[it] }
    }

    /** Add absent keys from current preferences; never replace existing edits or comments. */
    fun fillMissing(context: Context): File {
        val file = configFile(context)
        fillMissing(file, Prefs.of(context))
        return file
    }

    fun fillMissing(file: File, prefs: SharedPreferences) {
        val existing = if (file.isFile) file.readText() else ""
        if (file.isFile) importTextIfChanged(existing, prefs)
        val merged = completeText(existing, prefs)
        if (merged != existing) {
            file.parentFile?.mkdirs()
            file.writeText(merged)
        }
        // Completion reflects current preferences, not a new external config edit.
        prefs.edit().putString(KEY_FINGERPRINT, fingerprint(merged)).apply()
    }

    internal fun completeText(existing: String, prefs: SharedPreferences): String = buildString {
        append(existing)
        if (isNotEmpty() && last() != '\n') appendLine()
        val present = KeyTabConfig.entries(existing).keys
        val values = linkedMapOf<String, String>()
        values.putAll(KeyTabConfig.entries(KeyTabConfig().serialize()))
        val all = prefs.all
        for ((key, spec) in settings) {
            val value = all[spec.pref]
            values[key] = when {
                value == null -> spec.default
                spec.type == "color" && value is Int -> String.format(Locale.ROOT, "#%08X", value)
                else -> value.toString()
            }
        }
        val missing = values.filterKeys { it !in present }
        if (missing.isNotEmpty()) {
            appendLine("# KeyTab: fehlende Einstellungen ergänzt; vorhandene Werte bleiben erhalten.")
            appendLine("# Farben: #AARRGGBB / #RRGGBB oder default; dark_mode: true / false / system.")
            appendLine("# Boolesche Werte: true / false. Änderungen wirken beim Öffnen von Settings/IME.")
            for ((key, value) in missing) appendLine("$key = $value")
        }
    }
}
