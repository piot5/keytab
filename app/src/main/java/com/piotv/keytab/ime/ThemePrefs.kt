package com.piotv.keytab.ime

import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable

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
    const val KEY_GRADIENT_COLOR1 = "gradient_color1"
    const val KEY_GRADIENT_COLOR2 = "gradient_color2"
    const val KEY_GRADIENT_MODE = "gradient_mode"
    const val GRADIENT_TOP_DOWN = "top_down"
    const val GRADIENT_INVERT = "invert"
    const val GRADIENT_RADIAL = "radial"

    private const val PREFIX_DARK = "theme_dark_"
    private const val PREFIX_LIGHT = "theme_light_"
    const val KIND_BG = "bg"
    const val KIND_HL = "hl"
    const val KIND_TEXT = "text"

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

    /** Alle Farb-Overrides + Verlauf zurücksetzen (Standard-Theme). */
    fun resetAll(prefs: SharedPreferences) {
        prefs.edit().remove(colorKey(true, KIND_BG)).remove(colorKey(true, KIND_HL))
            .remove(colorKey(true, KIND_TEXT)).remove(colorKey(false, KIND_BG))
            .remove(colorKey(false, KIND_HL)).remove(colorKey(false, KIND_TEXT))
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
