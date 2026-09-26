package com.piotv.keytab.ime

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import com.piotv.keytab.R

/**
 * Farb-Teil von [ThemePrefs]: Farb-Arten (KIND_*), Pref-Key-Auflösung
 * (dark/light × Art) und das Auslesen der gespeicherten Farben inklusive
 * Default-Auflösung aus colors.xml.
 * Die Pref-Keys selbst bleiben in [ThemePrefs], das die öffentliche API
 * als Einzeiler weiterreicht.
 */
internal object ThemeColorResolver {

    private const val PREFIX_DARK = "theme_dark_"
    private const val PREFIX_LIGHT = "theme_light_"
    private const val ALPHA_SHIFT = 24
    private const val ALPHA_MAX = 255
    private const val RGB_MASK = 0xFFFFFF

    const val KIND_BG = "bg"
    const val KIND_KEY = "key"
    const val KIND_HL = "hl"
    const val KIND_TEXT = "text"
    /** Farb-Art der Hervorhebung der wahrscheinlichsten nächsten Taste. */
    const val KIND_LIKELY = "gaming"
    /** Farbe des Tippspur-Effekts (Trail der zuletzt geklickten Taste). */
    const val KIND_TRAIL = "trail"
    /** Farbe der Swipe-Pfad-Knoten (Schaltplan-Preview + Swipe-Eingabe). */
    const val KIND_SWIPE = "swipe"
    /** Farbe der Swipe-Pfad-Kanten (Verbindungslinien zwischen Prognose-Tasten). */
    const val KIND_SWIPE_EDGE = "swipe_edge"
    /** Gradient-Farbe 1 (über dem Farbkreis auswählbar). */
    const val KIND_GRADIENT1 = "gradient1"
    /** Gradient-Farb 2 (über dem Farbkreis auswählbar). */
    const val KIND_GRADIENT2 = "gradient2"

    /** Default-Farbe einer Theme-Art (colors.xml, thema-richtig aufgelöst). */
    fun defaultColor(context: Context, dark: Boolean, kind: String): Int {
        val conf = Configuration(context.resources.configuration)
        conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        val ctx = context.createConfigurationContext(conf)
        val res = when (kind) {
            KIND_KEY -> R.color.key_bg
            KIND_HL -> R.color.key_pressed
            KIND_TEXT -> R.color.key_text
            KIND_LIKELY -> R.color.primary
            KIND_TRAIL -> R.color.trail_color
            KIND_SWIPE -> R.color.swipe_color
            KIND_SWIPE_EDGE -> R.color.swipe_edge_color
            else -> R.color.kbd_bg
        }
        return ContextCompat.getColor(ctx, res)
    }

    /** Standard-Verlaufsfarben je Modus (User-Standard 2026-09-16):
     *  dunkel = „Nacht“-Preset (3A3A3A→121212), hell = weiß→hellgrau. */
    fun defaultGradientColor1(dark: Boolean): Int =
        if (dark) 0xFF3A3A3A.toInt() else 0xFFFFFFFF.toInt()
    fun defaultGradientColor2(dark: Boolean): Int =
        if (dark) 0xFF121212.toInt() else 0xFFE0E0E0.toInt()

    /** Pref-Key für eine Theme-Farbe (dark/light × Art) — der Verlauf ist
     *  **je Modus** gespeichert (dark/light haben eigene Verläufe). */
    fun colorKey(dark: Boolean, kind: String): String =
        (if (dark) PREFIX_DARK else PREFIX_LIGHT) + kind

    /** Farbe lesen; [default] = aufgelöste Theme-Ressource (mit Alpha 0xFF). */
    fun getColor(prefs: SharedPreferences, dark: Boolean, kind: String, default: Int): Int =
        when (kind) {
            KIND_GRADIENT1 -> gradientColor1(prefs, dark)
            KIND_GRADIENT2 -> gradientColor2(prefs, dark)
            else -> prefs.getInt(colorKey(dark, kind), default)
        }

    /**
     * Hintergrund separat pro Modus setzbar: eine explizite BG-Farbe
     * ([KIND_BG]) gewinnt gegen den Verlauf → flat Background.
     */
    fun hasExplicitBg(prefs: SharedPreferences, dark: Boolean): Boolean =
        prefs.contains(colorKey(dark, KIND_BG))

    /** Schalter: Verlauf für diesen Modus deaktiviert (flat kbd_bg-Default). */
    fun isGradientOff(prefs: SharedPreferences, dark: Boolean): Boolean =
        prefs.getBoolean(ThemePrefs.gradientOffKey(dark), false)

    /**
     * Verlauf aktiv für [dark]? (Explizite BG-Farbe oder OFF-Schalter deaktivieren;
     * sonst immer aktiv — mit gesetzten 2 Farben oder den Modus-Defaults.)
     */
    fun hasGradient(prefs: SharedPreferences, dark: Boolean): Boolean =
        !hasExplicitBg(prefs, dark) && !isGradientOff(prefs, dark)

    /** Verlaufs-Farbe 1 je Modus (per-mode Override → Legacy global → Default). */
    fun gradientColor1(prefs: SharedPreferences, dark: Boolean): Int =
        when {
            prefs.contains(colorKey(dark, KIND_GRADIENT1)) ->
                prefs.getInt(colorKey(dark, KIND_GRADIENT1), 0)
            prefs.contains(ThemePrefs.KEY_GRADIENT_COLOR1) ->
                prefs.getInt(ThemePrefs.KEY_GRADIENT_COLOR1, 0)
            else -> defaultGradientColor1(dark)
        }

    /** Verlaufs-Farbe 2 je Modus (per-mode Override → Legacy global → Default). */
    fun gradientColor2(prefs: SharedPreferences, dark: Boolean): Int =
        when {
            prefs.contains(colorKey(dark, KIND_GRADIENT2)) ->
                prefs.getInt(colorKey(dark, KIND_GRADIENT2), 0)
            prefs.contains(ThemePrefs.KEY_GRADIENT_COLOR2) ->
                prefs.getInt(ThemePrefs.KEY_GRADIENT_COLOR2, 0)
            else -> defaultGradientColor2(dark)
        }

    /** Verlaufs-Modus je Modus (per-mode Override → Legacy global → Default). */
    fun gradientMode(prefs: SharedPreferences, dark: Boolean): String =
        prefs.getString(ThemePrefs.gradientModeKey(dark), null)
            ?: prefs.getString(ThemePrefs.KEY_GRADIENT_MODE, ThemePrefs.GRADIENT_TOP_DOWN)
            ?: ThemePrefs.GRADIENT_TOP_DOWN

    /** Alpha (0–255) in eine ARGB-Farbe einblenden (RGB bleibt). */
    fun withAlpha(color: Int, alpha: Int): Int =
        (alpha.coerceIn(0, ALPHA_MAX) shl ALPHA_SHIFT) or (color and RGB_MASK)
}
