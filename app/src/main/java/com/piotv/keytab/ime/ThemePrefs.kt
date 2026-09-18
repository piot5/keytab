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
    const val KEY_GRADIENT_OFF = "gradient_off_dark"
    const val KEY_GRADIENT_OFF_LIGHT = "gradient_off_light"
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
    /** Farb-Art der Hervorhebung der wahrscheinlichsten nächsten Taste. */
    const val KIND_LIKELY = "gaming"
    /** Farbe des Tippspur-Effekts (Trail der zuletzt geklickten Taste). */
    const val KIND_TRAIL = "trail"
    /** Gradient-Farbe 1 (über dem Farbkreis auswählbar). */
    const val KIND_GRADIENT1 = "gradient1"
    /** Gradient-Farb 2 (über dem Farbkreis auswählbar). */
    const val KIND_GRADIENT2 = "gradient2"

    // ---------- Trail-Effekt (Tippspur) ----------
    /** Trail-Effekt an/aus. */
    const val KEY_TRAIL = "trail_enabled"
    /** Farbe des Trail-Effekts (ARGB). */
    const val KEY_TRAIL_COLOR = "trail_color"
    /** Anzahl der Decay-Stufen bevor die Spur komplett verschwindet (Default 5). */
    const val KEY_TRAIL_STEPS = "trail_steps"
    /** Korrektur-Trace an/aus: färbt getippte Wörter nach Engine-Urteil. */
    const val KEY_TRAIL_TRACE = "trail_trace"
    /** Grundfarbe des Trace für akzeptierte Wörter (Default grün). */
    const val KEY_TRAIL_ACCEPTED_COLOR = "trail_accepted_color"
    /** Grundfarbe des Trace für korrigierte Wörter (Default rot). */
    const val KEY_TRAIL_CORRECTED_COLOR = "trail_corrected_color"

    // ---------- Likely Highlighting (Theme-Einstellungen) ----------
    /** Farbe der wahrscheinlichsten nächsten Taste („likely highlight"). */
    const val KEY_LIKELY = "gaming_mode"
    /** Zufriedenstellender Puls-Effekt, wenn die Wahrscheinlichkeit erreicht ist. */
    const val KEY_LIKELY_EFFECT = "gaming_effect"

    fun likelyHighlighting(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_LIKELY, false)
    fun likelyEffect(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_LIKELY_EFFECT, true)

    // ---------- Trail-Effekt (Tippspur) ----------
    /** Trail-Effekt an/aus. Default: false (aus). */
    fun trailEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_TRAIL, false)
    /** Trail-Farbe (ARGB). Default: Blau #2196F3. */
    fun trailColor(prefs: SharedPreferences): Int =
        if (prefs.contains(KEY_TRAIL_COLOR)) prefs.getInt(KEY_TRAIL_COLOR, 0)
        else 0xFF2196F3.toInt()
    /** Anzahl Decay-Stufen. Default 5. */
    fun trailSteps(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_TRAIL_STEPS, TrailLogic.DEFAULT_STEPS).coerceAtLeast(1)
    /** Korrektur-Trace an/aus. Default: false (nur zusammen mit Trail sinnvoll). */
    fun trailTraceEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_TRAIL_TRACE, false)
    /** Grundfarbe für „Engine kennt das Wort" (grün). */
    fun trailAcceptedColor(prefs: SharedPreferences): Int =
        if (prefs.contains(KEY_TRAIL_ACCEPTED_COLOR)) prefs.getInt(KEY_TRAIL_ACCEPTED_COLOR, 0)
        else 0xFF4CAF50.toInt()
    /** Grundfarbe für „Fuzzy-Korrektur würde greifen" (rot). */
    fun trailCorrectedColor(prefs: SharedPreferences): Int =
        if (prefs.contains(KEY_TRAIL_CORRECTED_COLOR)) prefs.getInt(KEY_TRAIL_CORRECTED_COLOR, 0)
        else 0xFFF44336.toInt()
    /** Trail-Farbe mit aktuellem Alpha (Decay) – nutzt dieselbe Formel wie das Overlay. */
    fun trailColorWithAlpha(prefs: SharedPreferences, step: Int, maxSteps: Int): Int =
        withAlpha(trailColor(prefs), TrailLogic.alphaForStep(step, maxSteps))

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
            KIND_LIKELY -> R.color.primary
            KIND_TRAIL -> R.color.trail_color
            else -> R.color.kbd_bg
        }
        return androidx.core.content.ContextCompat.getColor(ctx, res)
    }

    /** Pref-Key für eine Theme-Farbe (dark/light × Art). */
    fun colorKey(dark: Boolean, kind: String): String = when (kind) {
        KIND_GRADIENT1 -> KEY_GRADIENT_COLOR1
        KIND_GRADIENT2 -> KEY_GRADIENT_COLOR2
        else -> (if (dark) PREFIX_DARK else PREFIX_LIGHT) + kind
    }

    /** Farbe lesen; [default] = aufgelöste Theme-Ressource (mit Alpha 0xFF). */
    fun getColor(prefs: SharedPreferences, dark: Boolean, kind: String, default: Int): Int = when (kind) {
        KIND_GRADIENT1 -> gradientColor1(prefs, dark)
        KIND_GRADIENT2 -> gradientColor2(prefs, dark)
        else -> prefs.getInt(colorKey(dark, kind), default)
    }

    /** Farbe schreiben (ARGB inkl. Alpha). Verlauf-Farbwahl aktiviert den Verlauf. */
    fun setColor(prefs: SharedPreferences, dark: Boolean, kind: String, value: Int) {
        val edit = prefs.edit().putInt(colorKey(dark, kind), value)
        if (kind == KIND_GRADIENT1 || kind == KIND_GRADIENT2) {
            edit.remove(colorKey(dark, KIND_BG)).putBoolean(gradientOffKey(dark), false)
        }
        edit.apply()
    }

    /** Alle aktuell gespeicherten Theme-Farben (beide Modi) + Verlauf als JSON. */
    fun exportColors(prefs: SharedPreferences): String {
        val b = org.json.JSONObject()
        for (dark in listOf(true, false)) {
            val prefix = if (dark) "dark" else "light"
            val m = org.json.JSONObject()
            for (kind in listOf(KIND_BG, KIND_KEY, KIND_HL, KIND_TEXT, KIND_LIKELY, KIND_TRAIL)) {
                val key = colorKey(dark, kind)
                if (prefs.contains(key)) m.put(kind, prefs.getInt(key, 0))
            }
            b.put(prefix, m)
        }
        val g = org.json.JSONObject()
        if (prefs.contains(KEY_GRADIENT_COLOR1)) g.put("color1", prefs.getInt(KEY_GRADIENT_COLOR1, 0))
        if (prefs.contains(KEY_GRADIENT_COLOR2)) g.put("color2", prefs.getInt(KEY_GRADIENT_COLOR2, 0))
        // KEY_GRADIENT_MODE ist ein STRING-Pref ("top_down"/"invert"/"radial") –
        // getInt() warf hier eine ClassCastException → ExportButton crashte.
        g.put("mode", gradientMode(prefs))
        g.put("off_dark", isGradientOff(prefs, true))
        g.put("off_light", isGradientOff(prefs, false))
        b.put("gradient", g)
        val lk = org.json.JSONObject()
        lk.put("enabled", likelyHighlighting(prefs))
        lk.put("effect", likelyEffect(prefs))
        b.put("likely", lk)
        val tr = org.json.JSONObject()
        tr.put("enabled", trailEnabled(prefs))
        tr.put("color", trailColor(prefs))
        tr.put("steps", trailSteps(prefs))
        tr.put("trace", trailTraceEnabled(prefs))
        tr.put("accepted_color", trailAcceptedColor(prefs))
        tr.put("corrected_color", trailCorrectedColor(prefs))
        b.put("trail", tr)
        b.put("version", themeVersion(prefs))
        return b.toString(2)
    }

    /**
     * Import eines Theme-Exports (JSON, siehe [exportColors]): setzt alle
     * enthaltenen Farb-Overrides, Verlauf (2 Farben + Modus + OFF-Schalter)
     * und Likely-Highlighting. @return false bei ungültigem JSON.
     */
    fun importColors(prefs: SharedPreferences, json: String): Boolean = try {
        val b = org.json.JSONObject(json)
        for (dark in listOf(true, false)) {
            val m = b.optJSONObject(if (dark) "dark" else "light") ?: continue
            for (kind in listOf(KIND_BG, KIND_KEY, KIND_HL, KIND_TEXT, KIND_LIKELY, KIND_TRAIL)) {
                if (m.has(kind)) setColor(prefs, dark, kind, m.getInt(kind))
            }
        }
        b.optJSONObject("gradient")?.let { g ->
            if (g.has("color1")) prefs.edit().putInt(KEY_GRADIENT_COLOR1, g.getInt("color1")).apply()
            if (g.has("color2")) prefs.edit().putInt(KEY_GRADIENT_COLOR2, g.getInt("color2")).apply()
            if (g.has("mode")) prefs.edit().putString(KEY_GRADIENT_MODE, g.getString("mode")).apply()
            if (g.has("off_dark")) setGradientOff(prefs, true, g.getBoolean("off_dark"))
            if (g.has("off_light")) setGradientOff(prefs, false, g.getBoolean("off_light"))
        }
        b.optJSONObject("likely")?.let { lk ->
            if (lk.has("enabled")) prefs.edit().putBoolean(KEY_LIKELY, lk.getBoolean("enabled")).apply()
            if (lk.has("effect")) prefs.edit().putBoolean(KEY_LIKELY_EFFECT, lk.getBoolean("effect")).apply()
        }
        b.optJSONObject("trail")?.let { t ->
            if (t.has("enabled")) prefs.edit().putBoolean(KEY_TRAIL, t.getBoolean("enabled")).apply()
            if (t.has("color")) prefs.edit().putInt(KEY_TRAIL_COLOR, t.getInt("color")).apply()
            if (t.has("steps")) prefs.edit().putInt(KEY_TRAIL_STEPS, t.getInt("steps")).apply()
            if (t.has("trace")) prefs.edit().putBoolean(KEY_TRAIL_TRACE, t.getBoolean("trace")).apply()
            if (t.has("accepted_color")) {
                prefs.edit().putInt(KEY_TRAIL_ACCEPTED_COLOR, t.getInt("accepted_color")).apply()
            }
            if (t.has("corrected_color")) {
                prefs.edit().putInt(KEY_TRAIL_CORRECTED_COLOR, t.getInt("corrected_color")).apply()
            }
        }
        bumpVersion(prefs)
        true
    } catch (_: Exception) { false }

    /** Alle Farb-Overrides + Verlauf zurücksetzen (Standard-Theme). */
    fun resetAll(prefs: SharedPreferences) {
        prefs.edit().remove(colorKey(true, KIND_BG)).remove(colorKey(true, KIND_KEY))
            .remove(colorKey(true, KIND_HL)).remove(colorKey(true, KIND_TEXT))
            .remove(colorKey(true, KIND_LIKELY)).remove(colorKey(true, KIND_TRAIL))
            .remove(KEY_TRAIL_TRACE)
            .remove(KEY_TRAIL_ACCEPTED_COLOR).remove(KEY_TRAIL_CORRECTED_COLOR)
            .remove(colorKey(false, KIND_BG)).remove(colorKey(false, KIND_KEY))
            .remove(colorKey(false, KIND_HL)).remove(colorKey(false, KIND_TEXT))
            .remove(colorKey(false, KIND_LIKELY)).remove(colorKey(false, KIND_TRAIL))
            .remove(KEY_LIKELY).remove(KEY_LIKELY_EFFECT)
            .remove(KEY_TRAIL).remove(KEY_TRAIL_COLOR).remove(KEY_TRAIL_STEPS)
            .remove(KEY_GRADIENT_COLOR1).remove(KEY_GRADIENT_COLOR2)
            .remove(KEY_GRADIENT_MODE).remove(KEY_GRADIENT_OFF)
            .remove(KEY_GRADIENT_OFF_LIGHT)
            .remove(com.piotv.keytab.Prefs.KEY_BG_IMAGE_URI)
            .remove(com.piotv.keytab.Prefs.KEY_BG_IMAGE_FILL).apply()
    }

    // ---------- Verlauf ----------
    /** Standard-Verlaufsfarben je Modus (User-Standard 2026-09-16):
     *  dunkel = „Nacht"-Preset (3A3A3A→121212), hell = weiß→hellgrau. */
    fun defaultGradientColor1(dark: Boolean): Int =
        if (dark) 0xFF3A3A3A.toInt() else 0xFFFFFFFF.toInt()
    fun defaultGradientColor2(dark: Boolean): Int =
        if (dark) 0xFF121212.toInt() else 0xFFE0E0E0.toInt()

    /**
     * Hintergrund separat pro Modus setzbar: eine explizite BG-Farbe
     * ([KIND_BG]) gewinnt gegen den Verlauf → flat Background.
     */
    fun hasExplicitBg(prefs: SharedPreferences, dark: Boolean): Boolean =
        prefs.contains(colorKey(dark, KIND_BG))

    /** Schalter: Verlauf für diesen Modus deaktivieren (flat kbd_bg-Default). */
    fun gradientOffKey(dark: Boolean): String =
        if (dark) KEY_GRADIENT_OFF else KEY_GRADIENT_OFF_LIGHT
    fun isGradientOff(prefs: SharedPreferences, dark: Boolean): Boolean =
        prefs.getBoolean(gradientOffKey(dark), false)
    fun setGradientOff(prefs: SharedPreferences, dark: Boolean, off: Boolean) {
        prefs.edit().putBoolean(gradientOffKey(dark), off).apply()
    }

    /**
     * Verlauf aktiv für [dark]? (Explizite BG-Farbe oder OFF-Schalter deaktivieren;
     * sonst immer aktiv — mit gesetzten 2 Farben oder den Modus-Defaults.)
     */
    fun hasGradient(prefs: SharedPreferences, dark: Boolean): Boolean =
        !hasExplicitBg(prefs, dark) && !isGradientOff(prefs, dark)

    /** Verlaufs-Farbe 1 (2-Farb-Verlauf; Default je Modus). */
    fun gradientColor1(prefs: SharedPreferences, dark: Boolean): Int =
        if (prefs.contains(KEY_GRADIENT_COLOR1)) prefs.getInt(KEY_GRADIENT_COLOR1, 0)
        else defaultGradientColor1(dark)

    /** Verlaufs-Farbe 2 (2-Farb-Verlauf; Default je Modus). */
    fun gradientColor2(prefs: SharedPreferences, dark: Boolean): Int =
        if (prefs.contains(KEY_GRADIENT_COLOR2)) prefs.getInt(KEY_GRADIENT_COLOR2, 0)
        else defaultGradientColor2(dark)

    fun gradientMode(prefs: SharedPreferences): String =
        prefs.getString(KEY_GRADIENT_MODE, GRADIENT_TOP_DOWN) ?: GRADIENT_TOP_DOWN

    /**
     * Verlauf-Drawable für den Tastatur-Hintergrund; null wenn deaktiviert
     * (explizite BG-Farbe oder OFF-Schalter für [dark]) — dann flat.
     * [widthPx] dient als Radial-Radius-Basis (Root ist beim ersten Aufruf noch
     * nicht gemessen → Display-Breite übergeben).
     */
    fun gradientDrawable(prefs: SharedPreferences, dark: Boolean, widthPx: Int): GradientDrawable? {
        if (!hasGradient(prefs, dark)) return null
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
        d.colors = intArrayOf(gradientColor1(prefs, dark), gradientColor2(prefs, dark))
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
