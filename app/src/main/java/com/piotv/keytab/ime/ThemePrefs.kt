package com.piotv.keytab.ime

import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import com.piotv.keytab.R

/**
 * Theme-Einstellungen (Long-Press auf Mond/Sonne):
 * - Verlauf: Farbe 1/2 + Modus (Oben→Unten, Umgekehrt, Radial) + Presets
 * - Farben je Theme (Dark/Light): Background, Highlight, Schriftfarbe –
 *   als ARGB inkl. Alpha (Alpha-Slider im [ThemeSettingsPanel]).
 * Verlauf ist aktiv, sobald beide Farben gesetzt sind; Farben gelten als
 * Override über den Theme-Ressourcen (Default = Werte aus colors.xml).
 */
object ThemePrefs {

    // ---------- Pref-Keys ----------
    const val PREFS = com.piotv.keytab.Prefs.FILE
    const val KEY_DARK = "dark_mode"
    const val KEY_GRADIENT_COLOR1 = "gradient_color1"
    const val KEY_GRADIENT_COLOR2 = "gradient_color2"
    const val KEY_GRADIENT_MODE = "gradient_mode"
    const val GRADIENT_TOP_DOWN = "top_down"
    const val GRADIENT_INVERT = "invert"
    const val GRADIENT_RADIAL = "radial"

    private const val PREFIX_DARK = "theme_dark_"
    private const val PREFIX_LIGHT = "theme_light_"
    /** Default-Verlaufs-Farbe (wenn nicht gesetzt). */
    private const val INT_DEF_GRADIENT = 0xFF000000.toInt()
    const val KIND_BG = "bg"
    const val KIND_KEY = "key"
    const val KIND_HL = "hl"
    const val KIND_TEXT = "text"
    const val KIND_GAMING = "gaming"

    // ---------- Gaming-Modus (Theme-Einstellungen) ----------
    /** Färbung der wahrscheinlichsten nächsten Taste (Gaming-Look). */
    const val KEY_GAMING = "gaming_mode"
    /** Zufriedenstellender Puls-Effekt, wenn die Wahrscheinlichkeit erreicht ist. */
    const val KEY_GAMING_EFFECT = "gaming_effect"

    fun gamingMode(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_GAMING, false)
    fun gamingEffect(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_GAMING_EFFECT, true)

    /** Zähler: ändert sich bei jeder Theme-Änderung → IME baut die Tastatur neu. */
    const val KEY_THEME_VERSION = "theme_version"
    fun themeVersion(prefs: SharedPreferences): Int = prefs.getInt(KEY_THEME_VERSION, 0)
    fun bumpVersion(prefs: SharedPreferences) {
        prefs.edit().putInt(KEY_THEME_VERSION, themeVersion(prefs) + 1).apply()
    }

    /** Dark-Mode-Override (Pref) bzw. System-Modus – identisch zum IME. */
    fun isDarkMode(context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        if (prefs.contains(KEY_DARK)) return prefs.getBoolean(KEY_DARK, false)
        val mask = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /** Default-Farbe einer Theme-Art (colors.xml, thema-richtig aufgelöst). */
    fun defaultColor(context: android.content.Context, dark: Boolean, kind: String): Int {
        val conf = android.content.res.Configuration(context.resources.configuration)
        conf.uiMode = (conf.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (dark) android.content.res.Configuration.UI_MODE_NIGHT_YES
            else android.content.res.Configuration.UI_MODE_NIGHT_NO)
        val ctx = context.createConfigurationContext(conf)
        val res = when (kind) {
            KIND_KEY -> R.color.key_bg
            KIND_HL -> R.color.key_pressed
            KIND_TEXT -> R.color.key_text
            KIND_GAMING -> R.color.primary
            else -> R.color.kbd_bg
        }
        return androidx.core.content.ContextCompat.getColor(ctx, res)
    }

    /** Pref-Key für eine Theme-Farbe (dark/light × Art). */
    fun colorKey(dark: Boolean, kind: String): String =
        (if (dark) PREFIX_DARK else PREFIX_LIGHT) + kind

    /** Farbe lesen; [default] = aufgelöste Theme-Ressource (mit Alpha 0xFF). */
    fun getColor(prefs: SharedPreferences, dark: Boolean, kind: String, default: Int): Int =
        prefs.getInt(colorKey(dark, kind), default)

    /** Farbe schreiben (ARGB inkl. Alpha). */
    fun setColor(prefs: SharedPreferences, dark: Boolean, kind: String, value: Int) {
        prefs.edit().putInt(colorKey(dark, kind), value).apply()
    }

    /** Alle aktuell gespeicherten Theme-Farben (beide Modi) + Verlauf als JSON. */
    fun exportColors(prefs: SharedPreferences): String {
        val b = org.json.JSONObject()
        for (dark in listOf(true, false)) {
            val prefix = if (dark) "dark" else "light"
            val m = org.json.JSONObject()
            for (kind in listOf(KIND_BG, KIND_KEY, KIND_HL, KIND_TEXT, KIND_GAMING)) {
                val key = colorKey(dark, kind)
                if (prefs.contains(key)) m.put(kind, prefs.getInt(key, 0))
            }
            b.put(prefix, m)
        }
        val g = org.json.JSONObject()
        g.put("color1", prefs.getInt(KEY_GRADIENT_COLOR1, INT_DEF_GRADIENT))
        g.put("color2", prefs.getInt(KEY_GRADIENT_COLOR2, INT_DEF_GRADIENT))
        g.put("mode", prefs.getInt(KEY_GRADIENT_MODE, 0))
        b.put("gradient", g)
        val gm = org.json.JSONObject()
        gm.put("enabled", prefs.getBoolean(KEY_GAMING, false))
        gm.put("effect", prefs.getBoolean(KEY_GAMING_EFFECT, true))
        b.put("gaming", gm)
        b.put("version", themeVersion(prefs))
        return b.toString(2)
    }

    /** Alle Farb-Overrides + Verlauf zurücksetzen (Standard-Theme). */
    fun resetAll(prefs: SharedPreferences) {
        prefs.edit().remove(colorKey(true, KIND_BG)).remove(colorKey(true, KIND_KEY))
            .remove(colorKey(true, KIND_HL)).remove(colorKey(true, KIND_TEXT))
            .remove(colorKey(true, KIND_GAMING)).remove(colorKey(false, KIND_BG))
            .remove(colorKey(false, KIND_KEY)).remove(colorKey(false, KIND_HL))
            .remove(colorKey(false, KIND_TEXT)).remove(colorKey(false, KIND_GAMING))
            .remove(KEY_GAMING).remove(KEY_GAMING_EFFECT)
            .remove(KEY_GRADIENT_COLOR1).remove(KEY_GRADIENT_COLOR2)
            .remove(KEY_GRADIENT_MODE).apply()
    }

    // ---------- Verlauf ----------
    /** Verlauf aktiv? (Beide Farben gesetzt) */
    fun hasGradient(prefs: SharedPreferences): Boolean =
        prefs.contains(KEY_GRADIENT_COLOR1) && prefs.contains(KEY_GRADIENT_COLOR2)

    fun gradientColor1(prefs: SharedPreferences, default: Int = 0xFFE0E0E0.toInt()): Int =
        prefs.getInt(KEY_GRADIENT_COLOR1, default)

    fun gradientColor2(prefs: SharedPreferences, default: Int = 0xFFFFFFFF.toInt()): Int =
        prefs.getInt(KEY_GRADIENT_COLOR2, default)

    fun gradientMode(prefs: SharedPreferences): String =
        prefs.getString(KEY_GRADIENT_MODE, GRADIENT_TOP_DOWN) ?: GRADIENT_TOP_DOWN

    /**
     * Verlauf-Drawable für den Tastatur-Hintergrund; null wenn nicht konfiguriert.
     * [widthPx] dient als Radial-Radius-Basis (Root ist beim ersten Aufruf noch
     * nicht gemessen → Display-Breite übergeben).
     */
    fun gradientDrawable(prefs: SharedPreferences, widthPx: Int): GradientDrawable? {
        if (!hasGradient(prefs)) return null
        val d = GradientDrawable()
        when (gradientMode(prefs)) {
            GRADIENT_INVERT -> d.orientation = GradientDrawable.Orientation.BOTTOM_TOP
            GRADIENT_RADIAL -> {
                d.setGradientType(GradientDrawable.RADIAL_GRADIENT)
                d.setGradientCenter(0.5f, 0.15f)
                d.setGradientRadius(widthPx * 0.75f)
            }
            else -> d.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
        d.colors = intArrayOf(gradientColor1(prefs), gradientColor2(prefs))
        return d
    }

    /** Preset-Verläufe (Name, Farbe1, Farbe2, Modus). */
    data class GradientPreset(val name: String, val c1: Int, val c2: Int, val mode: String)

    val GRADIENTS = listOf(
        GradientPreset("Grau", 0xFFFFFFFF.toInt(), 0xFFE0E0E0.toInt(), GRADIENT_TOP_DOWN),
        GradientPreset("Nacht", 0xFF3A3A3A.toInt(), 0xFF121212.toInt(), GRADIENT_TOP_DOWN),
        GradientPreset("Ozean", 0xFF2196F3.toInt(), 0xFF0D47A1.toInt(), GRADIENT_TOP_DOWN),
        GradientPreset("Wald", 0xFF4CAF50.toInt(), 0xFF1B5E20.toInt(), GRADIENT_TOP_DOWN),
        GradientPreset("Abend", 0xFFFF9800.toInt(), 0xFFF44336.toInt(), GRADIENT_TOP_DOWN),
        GradientPreset("Lila", 0xFF7B1FA2.toInt(), 0xFF311B92.toInt(), GRADIENT_RADIAL)
    )

    /** Farbpalette für die Farb-Chips (Background/Highlight/Text). */
    val PALETTE = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFE0E0E0.toInt(), 0xFF9E9E9E.toInt(), 0xFF424242.toInt(),
        0xFF000000.toInt(), 0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFF9C27B0.toInt(),
        0xFFFF9800.toInt(), 0xFFF44336.toInt()
    )

    /** Alpha (0–255) in eine ARGB-Farbe einblenden (RGB bleibt). */
    fun withAlpha(color: Int, alpha: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)
}
