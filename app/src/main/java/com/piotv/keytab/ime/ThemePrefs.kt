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
    const val PREFS = com.piotv.keytab.Prefs.FILE
    const val KEY_DARK = "dark_mode"
    const val KEY_GRADIENT_COLOR1 = "gradient_color1"
    const val KEY_GRADIENT_COLOR2 = "gradient_color2"
    const val KEY_GRADIENT_MODE = "gradient_mode"
    const val KEY_GRADIENT_OFF = "gradient_off_dark"
    const val KEY_GRADIENT_OFF_LIGHT = "gradient_off_light"
    /** Verlauf-Modus JETZT pro Modus (Legacy: KEY_GRADIENT_MODE als Fallback). */
    const val KEY_GRADIENT_MODE_DARK = "theme_dark_gradient_mode"
    const val KEY_GRADIENT_MODE_LIGHT = "theme_light_gradient_mode"
    const val GRADIENT_TOP_DOWN = "top_down"
    const val GRADIENT_INVERT = "invert"
    const val GRADIENT_RADIAL = "radial"

    // ---------- Radialer Verlauf ----------
    // Der Lichtpunkt sitzt bewusst oben statt mittig: der Verlauf soll wie eine
    // von oben einfallende Beleuchtung wirken, nicht wie eine Kugel in der
    // Tastaturmitte. [RADIAL_RADIUS_FACTOR] skaliert die Display-Breite, weil
    // der Root beim ersten Aufruf noch nicht gemessen ist.
    /** X-Position des Lichtpunkts (0.5 = Mitte). */
    const val RADIAL_CENTER_X = 0.5f

    /** Y-Position des Lichtpunkts (0.15 = deutlich oben). */
    const val RADIAL_CENTER_Y = 0.15f

    /** Radius als Anteil der übergebenen Breite. */
    const val RADIAL_RADIUS_FACTOR = 0.75f

    // ---------- Farb-Arten (Definition in [ThemeColorResolver]) ----------
    const val KIND_BG = ThemeColorResolver.KIND_BG
    const val KIND_KEY = ThemeColorResolver.KIND_KEY
    const val KIND_HL = ThemeColorResolver.KIND_HL
    const val KIND_TEXT = ThemeColorResolver.KIND_TEXT
    const val KIND_LIKELY = ThemeColorResolver.KIND_LIKELY
    const val KIND_TRAIL = ThemeColorResolver.KIND_TRAIL
    const val KIND_GRADIENT1 = ThemeColorResolver.KIND_GRADIENT1
    const val KIND_GRADIENT2 = ThemeColorResolver.KIND_GRADIENT2

    // ---------- Trail-Effekt (Tippspur) ----------
    /** Trail-Effekt an/aus. */
    const val KEY_TRAIL = "trail_enabled"
    /** Farbe des Trail-Effekts (ARGB). */
    const val KEY_TRAIL_COLOR = "trail_color"
    /** Anzahl der Decay-Stufen bevor die Spur komplett verschwindet (Default 5). */
    const val KEY_TRAIL_STEPS = "trail_steps"
    /** Treffer-Markierung an/aus: färbt ein Wort grün, wenn es dem Top-Vorschlag entspricht. */
    const val KEY_TRAIL_TRACE = "trail_trace"
    /** Grundfarbe der Treffer-Markierung (Default grün). */
    const val KEY_TRAIL_ACCEPTED_COLOR = "trail_accepted_color"

    // ---------- Likely Highlighting (Theme-Einstellungen) ----------
    /**
     * Farbe der wahrscheinlichsten nächsten Taste („likely highlight").
     *
     * Der frühere Puls-Effekt-Schalter (`gaming_effect`) ist in v0.18 entfallen —
     * er war unerreichbarer Code (siehe `SuggestionController.updateLikelyKeys`).
     * Ein evtl. noch gespeicherter Wert wird ignoriert und beim Theme-Reset
     * entfernt.
     */
    const val KEY_LIKELY = "likely"
    /** Legacy-Key `gaming_mode` (früherer Name des Likely-Highlightings) — Migration. */
    const val KEY_LIKELY_LEGACY = "gaming_mode"
    const val KEY_SUGGESTIONS = "suggestions"
    const val KEY_TAB_HIGHLIGHT_MODE = "tab_highlight_mode"

    /**
     * `active_tab` = Balken am aktiven Tab (Standard) | `suggestion_top` = Vorschlag oben.
     */
    const val TAB_HIGHLIGHT_MODE_ACTIVE = "active_tab"
    const val TAB_HIGHLIGHT_MODE_SUGGESTION = "suggestion_top"
    val TAB_HIGHLIGHT_MODES = setOf(TAB_HIGHLIGHT_MODE_ACTIVE, TAB_HIGHLIGHT_MODE_SUGGESTION)


    /** Likely-Highlighting. Standard **an** (Nutzer-Standard). */
    fun likelyHighlighting(prefs: SharedPreferences): Boolean {
        if (prefs.contains(KEY_LIKELY_LEGACY)) {
            val v = prefs.getBoolean(KEY_LIKELY_LEGACY, true)
            prefs.edit().remove(KEY_LIKELY_LEGACY).putBoolean(KEY_LIKELY, v).apply()
            return v
        }
        return prefs.getBoolean(KEY_LIKELY, true)
    }

    // ---------- Tab-Highlight-Modus ----------
    /**
     * `tab_highlight_mode`:
     * - `active_tab`: Balken am ausgewählten Tab (Standard)
     * - `suggestion_top`: Vorschlag oben, kein Balken (weiterhin nicht implementiert)
     */
    fun tabHighlightMode(prefs: SharedPreferences, dark: Boolean): String {
        val raw = prefs.getString(KEY_TAB_HIGHLIGHT_MODE, TAB_HIGHLIGHT_MODE_ACTIVE) ?: TAB_HIGHLIGHT_MODE_ACTIVE
        return if (TAB_HIGHLIGHT_MODES.contains(raw)) raw else TAB_HIGHLIGHT_MODE_ACTIVE
    }

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
    /** Treffer-Markierung an/aus. Default: false (nur zusammen mit Trail sinnvoll). */
    fun trailTraceEnabled(prefs: SharedPreferences): Boolean {
        val enabled = prefs.getBoolean(KEY_TRAIL_TRACE, false)
        // Ensure green trail mark is disabled by default;
        // reset any previously stored true value to avoid config not working
        if (enabled) {
            prefs.edit().putBoolean(KEY_TRAIL_TRACE, false).apply()
        }
        return false
    }
    /** Grundfarbe der Treffer-Markierung (grün) — erscheint, wenn das Wort dem Top-Vorschlag entspricht. */
    fun trailAcceptedColor(prefs: SharedPreferences): Int =
        if (prefs.contains(KEY_TRAIL_ACCEPTED_COLOR)) prefs.getInt(KEY_TRAIL_ACCEPTED_COLOR, 0)
        else 0xFF4CAF50.toInt()
    /** Trail-Farbe mit aktuellem Alpha (Decay) – nutzt dieselbe Formel wie das Overlay. */
    fun trailColorWithAlpha(prefs: SharedPreferences, step: Int, maxSteps: Int): Int =
        withAlpha(trailColor(prefs), TrailLogic.alphaForStep(step, maxSteps))

    // ---------- Swipe (Gleit-Eingabe) ----------
    /** Zähler: ändert sich bei jeder Theme-Änderung → IME baut die Tastatur neu. */
    const val KEY_THEME_VERSION = "theme_version"
    fun themeVersion(prefs: SharedPreferences): Int = prefs.getInt(KEY_THEME_VERSION, 0)
    fun bumpVersion(prefs: SharedPreferences) {
        prefs.edit().putInt(KEY_THEME_VERSION, themeVersion(prefs) + 1).apply()
    }

    /**
     * Dark-Mode-Override (Pref) bzw. System-Modus – identisch zum IME.
     *
     * Unverändert: ein *fehlender* [KEY_DARK] folgt dem Gerät. Der Standard des
     * Werts selbst (dunkel) steht in [SettingsConfig] — dort landet er in einer
     * neu erzeugten `keytab_config.txt` und damit in den Einstellungen.
     */
    fun isDarkMode(context: android.content.Context): Boolean {
        val prefs = com.piotv.keytab.Prefs.of(context)
        if (prefs.contains(KEY_DARK)) return prefs.getBoolean(KEY_DARK, true)
        val mask = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /** Default-Farbe einer Theme-Art (colors.xml, thema-richtig aufgelöst). */
    fun defaultColor(context: android.content.Context, dark: Boolean, kind: String): Int =
        ThemeColorResolver.defaultColor(context, dark, kind)

    /** Pref-Key für eine Theme-Farbe (dark/light × Art) — der Verlauf ist
     *  **je Modus** gespeichert (dark/light haben eigene Verläufe). */
    fun colorKey(dark: Boolean, kind: String): String = ThemeColorResolver.colorKey(dark, kind)

    /** Verlauf-Modus-Key je Modus (Legacy-Fallback: KEY_GRADIENT_MODE). */
    fun gradientModeKey(dark: Boolean): String =
        if (dark) KEY_GRADIENT_MODE_DARK else KEY_GRADIENT_MODE_LIGHT

    /** Farbe lesen; [default] = aufgelöste Theme-Ressource (mit Alpha 0xFF). */
    fun getColor(prefs: SharedPreferences, dark: Boolean, kind: String, default: Int): Int =
        ThemeColorResolver.getColor(prefs, dark, kind, default)

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
        for (dark in listOf(true, false)) {
            // Verlauf JETZT je Modus exportiert (dark/light getrennt)
            val gg = org.json.JSONObject()
            if (prefs.contains(colorKey(dark, KIND_GRADIENT1)))
                gg.put("color1", prefs.getInt(colorKey(dark, KIND_GRADIENT1), 0))
            if (prefs.contains(colorKey(dark, KIND_GRADIENT2)))
                gg.put("color2", prefs.getInt(colorKey(dark, KIND_GRADIENT2), 0))
            gg.put("mode", gradientMode(prefs, dark))
            gg.put("off", isGradientOff(prefs, dark))
            b.put(if (dark) "gradient_dark" else "gradient_light", gg)
        }
        val g = org.json.JSONObject()
        // Legacy-Block (alter globaler Verlauf) bleibt für ältere Importe lesbar
        if (prefs.contains(KEY_GRADIENT_COLOR1)) g.put("color1", prefs.getInt(KEY_GRADIENT_COLOR1, 0))
        if (prefs.contains(KEY_GRADIENT_COLOR2)) g.put("color2", prefs.getInt(KEY_GRADIENT_COLOR2, 0))
        // KEY_GRADIENT_MODE ist ein STRING-Pref ("top_down"/"invert"/"radial") –
        // getInt() warf hier eine ClassCastException → ExportButton crashte.
        g.put("mode", gradientMode(prefs, true))
        g.put("off_dark", isGradientOff(prefs, true))
        g.put("off_light", isGradientOff(prefs, false))
        b.put("gradient", g)
        val lk = org.json.JSONObject()
        lk.put("enabled", likelyHighlighting(prefs))
        b.put("likely", lk)
        val tr = org.json.JSONObject()
        tr.put("enabled", trailEnabled(prefs))
        tr.put("color", trailColor(prefs))
        tr.put("steps", trailSteps(prefs))
        tr.put("trace", trailTraceEnabled(prefs))
        tr.put("accepted_color", trailAcceptedColor(prefs))
        b.put("trail", tr)
        b.put("version", themeVersion(prefs))
        return b.toString(2)
    }

    /**
     * Import eines Theme-Exports (JSON, siehe [exportColors]): setzt alle
     * enthaltenen Farb-Overrides, Verlauf (2 Farben + Modus + OFF-Schalter)
     * und Likely-Highlighting. @return false bei ungültigem JSON.
     *
     * Das JSON-Lesen liegt in [ThemeColorImport] (Android-frei testbar); hier
     * wird nur noch in die Prefs geschrieben. Unbekannte Sektionen im Export
     * werden stillschweigend überlesen — ein Export aus einem älteren Stand ist
     * kein Fehler. Nur unlesbares JSON insgesamt ergibt `false`.
     */
    fun importColors(prefs: SharedPreferences, json: String): Boolean = try {
        val root = org.json.JSONObject(json)
        // Atomar (G11): alle Werte in EINEN Editor sammeln und erst nach
        // vollständig erfolgreichem Parsen anwenden. Wirft ein Wert einen
        // Typfehler (z. B. String statt Zahl), wird der Editor verworfen —
        // kein Halb-Import.
        val editor = prefs.edit()

        ThemeColorImport.readColors(root) { dark, kind, argb ->
            putColor(editor, dark, kind, argb)
        }
        ThemeColorImport.readLegacyGradient(
            root,
            onColor1 = { editor.putInt(KEY_GRADIENT_COLOR1, it) },
            onColor2 = { editor.putInt(KEY_GRADIENT_COLOR2, it) },
            onMode = { editor.putString(KEY_GRADIENT_MODE, it) },
            onOff = { dark, off -> putGradientOff(editor, dark, off) }
        )
        // Neuer per-Modus-Verlauf (dark/light getrennt) – gewinnt gegen Legacy
        for (dark in ThemeColorImport.MODES) {
            ThemeColorImport.readModeGradient(
                root, dark,
                ThemeColorImport.GradientSink(
                    onColor1 = { putColor(editor, dark, KIND_GRADIENT1, it) },
                    onColor2 = { putColor(editor, dark, KIND_GRADIENT2, it) },
                    onMode = { editor.putString(gradientModeKey(dark), it) },
                    onOff = { off -> putGradientOff(editor, dark, off) }
                )
            )
        }
        ThemeColorImport.readLikely(
            root,
            onEnabled = { editor.putBoolean(KEY_LIKELY, it) }
        )
        ThemeColorImport.readTrail(
            root,
            ThemeColorImport.TrailSink(
                onEnabled = { editor.putBoolean(KEY_TRAIL, it) },
                onColor = { editor.putInt(KEY_TRAIL_COLOR, it) },
                onSteps = { editor.putInt(KEY_TRAIL_STEPS, it) },
                onTrace = { editor.putBoolean(KEY_TRAIL_TRACE, it) },
                onAcceptedColor = { editor.putInt(KEY_TRAIL_ACCEPTED_COLOR, it) }
            )
        )
        editor.putInt(KEY_THEME_VERSION, themeVersion(prefs) + 1).apply()
        true
    } catch (_: Exception) { false }

    /** [setColor] auf einen übergebenen Editor (für den atomaren Import). */
    private fun putColor(editor: SharedPreferences.Editor, dark: Boolean, kind: String, value: Int) {
        editor.putInt(colorKey(dark, kind), value)
        if (kind == KIND_GRADIENT1 || kind == KIND_GRADIENT2) {
            editor.remove(colorKey(dark, KIND_BG)).putBoolean(gradientOffKey(dark), false)
        }
    }

    /** [setGradientOff] auf einen übergebenen Editor (für den atomaren Import). */
    private fun putGradientOff(editor: SharedPreferences.Editor, dark: Boolean, off: Boolean) {
        editor.putBoolean(gradientOffKey(dark), off)
    }

    /** Alle Farb-Overrides + Verlauf zurücksetzen (Standard-Theme). */
    fun resetAll(prefs: SharedPreferences) {
        prefs.edit().remove(colorKey(true, KIND_BG)).remove(colorKey(true, KIND_KEY))
            .remove(colorKey(true, KIND_HL)).remove(colorKey(true, KIND_TEXT))
            .remove(colorKey(true, KIND_LIKELY)).remove(colorKey(true, KIND_TRAIL))
            .remove(KEY_TRAIL_TRACE)
            .remove(KEY_TRAIL_ACCEPTED_COLOR)
            .remove(colorKey(false, KIND_BG)).remove(colorKey(false, KIND_KEY))
            .remove(colorKey(false, KIND_HL)).remove(colorKey(false, KIND_TEXT))
            .remove(colorKey(false, KIND_LIKELY)).remove(colorKey(false, KIND_TRAIL))
            .remove(KEY_LIKELY).remove(KEY_LIKELY_LEGACY).remove("gaming_effect")
            .remove(KEY_TRAIL).remove(KEY_TRAIL_COLOR).remove(KEY_TRAIL_STEPS)
            .remove(KEY_GRADIENT_COLOR1).remove(KEY_GRADIENT_COLOR2)
            .remove(KEY_GRADIENT_MODE).remove(KEY_GRADIENT_OFF)
            .remove(KEY_GRADIENT_OFF_LIGHT)
            .remove(colorKey(true, KIND_GRADIENT1)).remove(colorKey(true, KIND_GRADIENT2))
            .remove(colorKey(false, KIND_GRADIENT1)).remove(colorKey(false, KIND_GRADIENT2))
            .remove(KEY_GRADIENT_MODE_DARK).remove(KEY_GRADIENT_MODE_LIGHT)
            .remove(com.piotv.keytab.Prefs.KEY_BG_IMAGE_URI)
            .remove(com.piotv.keytab.Prefs.KEY_BG_IMAGE_FILL).apply()
    }

    // ---------- Verlauf ----------
    /** Standard-Verlaufsfarben je Modus (User-Standard 2026-09-16):
     *  dunkel = „Nacht"-Preset (3A3A3A→121212), hell = weiß→hellgrau. */
    fun defaultGradientColor1(dark: Boolean): Int = ThemeColorResolver.defaultGradientColor1(dark)
    fun defaultGradientColor2(dark: Boolean): Int = ThemeColorResolver.defaultGradientColor2(dark)

    /**
     * Hintergrund separat pro Modus setzbar: eine explizite BG-Farbe
     * ([KIND_BG]) gewinnt gegen den Verlauf → flat Background.
     */
    fun hasExplicitBg(prefs: SharedPreferences, dark: Boolean): Boolean =
        ThemeColorResolver.hasExplicitBg(prefs, dark)

    /** Schalter: Verlauf für diesen Modus deaktivieren (flat kbd_bg-Default). */
    fun gradientOffKey(dark: Boolean): String =
        if (dark) KEY_GRADIENT_OFF else KEY_GRADIENT_OFF_LIGHT
    fun isGradientOff(prefs: SharedPreferences, dark: Boolean): Boolean =
        ThemeColorResolver.isGradientOff(prefs, dark)
    fun setGradientOff(prefs: SharedPreferences, dark: Boolean, off: Boolean) {
        prefs.edit().putBoolean(gradientOffKey(dark), off).apply()
    }

    /**
     * Verlauf aktiv für [dark]? (Explizite BG-Farbe oder OFF-Schalter deaktivieren;
     * sonst immer aktiv — mit gesetzten 2 Farben oder den Modus-Defaults.)
     */
    fun hasGradient(prefs: SharedPreferences, dark: Boolean): Boolean =
        ThemeColorResolver.hasGradient(prefs, dark)

    /** Verlaufs-Farbe 1 je Modus (per-mode Override → Legacy global → Default). */
    fun gradientColor1(prefs: SharedPreferences, dark: Boolean): Int =
        ThemeColorResolver.gradientColor1(prefs, dark)

    /** Verlaufs-Farbe 2 je Modus (per-mode Override → Legacy global → Default). */
    fun gradientColor2(prefs: SharedPreferences, dark: Boolean): Int =
        ThemeColorResolver.gradientColor2(prefs, dark)

    /** Verlaufs-Modus je Modus (per-mode Override → Legacy global → Default). */
    fun gradientMode(prefs: SharedPreferences, dark: Boolean): String =
        ThemeColorResolver.gradientMode(prefs, dark)

    /**
     * Verlauf-Drawable für den Tastatur-Hintergrund; null wenn deaktiviert
     * (explizite BG-Farbe oder OFF-Schalter für [dark]) — dann flat.
     * [widthPx] dient als Radial-Radius-Basis (Root ist beim ersten Aufruf noch
     * nicht gemessen → Display-Breite übergeben).
     */
    fun gradientDrawable(prefs: SharedPreferences, dark: Boolean, widthPx: Int): GradientDrawable? {
        if (!hasGradient(prefs, dark)) return null
        val d = GradientDrawable()
        when (gradientMode(prefs, dark)) {
            GRADIENT_INVERT -> d.orientation = GradientDrawable.Orientation.BOTTOM_TOP
            GRADIENT_RADIAL -> {
                d.setGradientType(GradientDrawable.RADIAL_GRADIENT)
                d.setGradientCenter(RADIAL_CENTER_X, RADIAL_CENTER_Y)
                d.setGradientRadius(widthPx * RADIAL_RADIUS_FACTOR)
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
    fun withAlpha(color: Int, alpha: Int): Int = ThemeColorResolver.withAlpha(color, alpha)
}
