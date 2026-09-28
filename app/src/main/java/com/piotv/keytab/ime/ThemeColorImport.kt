package com.piotv.keytab.ime

import org.json.JSONObject

/**
 * Liest die Farb-Overrides eines Theme-Exports (siehe [ThemePrefs.exportColors])
 * aus dem JSON. Reine Parser-Logik ohne Prefs-Zugriff, damit sie ohne Android
 * testbar ist.
 *
 * Refactoring (docs/REFACTORING_PLAN.md, P2): der Teil von
 * [ThemePrefs.importColors], der nur JSON liest, ist hierher gewandert. Vorher
 * steckten acht [org.json.JSONObject]-Zugriffe, zwei `for`-Schleifen und ein
 * `when` in einer Funktion — Grund für zwei der drei detekt-Befunde dort.
 *
 * Wichtig: das [Json] ist absichtlich tolerant. Fehlende Sektionen oder
 * Schlüssel sind kein Fehler (der Export kann aus einem älteren Stand
 * stammen), nur unlesbares JSON insgesamt. Deshalb liefert jede Funktion
 * `false` statt zu werfen, wenn [root] null ist.
 */
internal object ThemeColorImport {

    /** Farb-Arten, die je Modus importiert werden (Reihenfolge = Export-Reihenfolge). */
    internal val COLOR_KINDS = listOf(
        ThemePrefs.KIND_BG, ThemePrefs.KIND_KEY, ThemePrefs.KIND_HL, ThemePrefs.KIND_TEXT,
        ThemePrefs.KIND_LIKELY, ThemePrefs.KIND_TRAIL,
        ThemePrefs.KIND_SWIPE, ThemePrefs.KIND_SWIPE_EDGE
    )

    /** Die beiden Modi, in denen gesucht wird. */
    internal val MODES = listOf(true, false)

    /** Name des JSON-Objekts für [dark]. */
    internal fun section(dark: Boolean): String = if (dark) "dark" else "light"

    /**
     * Farb-Overrides aus [root] lesen.
     *
     * @param onColor bekommt `(dark, kind, argb)` je gesetztem Eintrag
     * @return true, wenn [root] lesbar war (auch dann, wenn nichts gesetzt war)
     */
    internal fun readColors(root: JSONObject, onColor: (dark: Boolean, kind: String, argb: Int) -> Unit): Boolean {
        for (dark in MODES) {
            val section = root.optJSONObject(section(dark)) ?: continue
            for (kind in COLOR_KINDS) {
                if (section.has(kind)) onColor(dark, kind, section.getInt(kind))
            }
        }
        return true
    }

    /**
     * Legacy-Verlauf (global, vor dem per-Modus-Split). Wird nur gelesen, wenn
     * `gradient` im Export steht.
     */
    internal fun readLegacyGradient(
        root: JSONObject,
        onColor1: (Int) -> Unit,
        onColor2: (Int) -> Unit,
        onMode: (String) -> Unit,
        onOff: (dark: Boolean, off: Boolean) -> Unit
    ): Boolean {
        val g = root.optJSONObject("gradient") ?: return false
        if (g.has("color1")) onColor1(g.getInt("color1"))
        if (g.has("color2")) onColor2(g.getInt("color2"))
        if (g.has("mode")) onMode(g.getString("mode"))
        if (g.has("off_dark")) onOff(true, g.getBoolean("off_dark"))
        if (g.has("off_light")) onOff(false, g.getBoolean("off_light"))
        return true
    }

    /**
     * Per-Modus-Verlauf (`gradient_dark` / `gradient_light`). Gewinnt gegen
     * [readLegacyGradient], weil [ThemePrefs.gradientModeKey] je Modus liest.
     */
    internal fun readModeGradient(
        root: JSONObject,
        dark: Boolean,
        sink: GradientSink
    ): Boolean {
        val g = root.optJSONObject("gradient_" + section(dark)) ?: return false
        if (g.has("color1")) sink.color1(g.getInt("color1"))
        if (g.has("color2")) sink.color2(g.getInt("color2"))
        if (g.has("mode")) sink.mode(g.getString("mode"))
        if (g.has("off")) sink.off(g.getBoolean("off"))
        return true
    }

    /** Senke für [readModeGradient] — als Objekt statt Parameterliste, sonst
     *  überschreitet die Funktion die LongParameterList-Schwelle. */
    internal class GradientSink(
        private val onColor1: (Int) -> Unit,
        private val onColor2: (Int) -> Unit,
        private val onMode: (String) -> Unit,
        private val onOff: (Boolean) -> Unit
    ) {
        fun color1(v: Int) = onColor1(v)
        fun color2(v: Int) = onColor2(v)
        fun mode(v: String) = onMode(v)
        fun off(v: Boolean) = onOff(v)
    }

    /** Likely-Highlighting-Schalter. */
    internal fun readLikely(
        root: JSONObject,
        onEnabled: (Boolean) -> Unit,
        onEffect: (Boolean) -> Unit
    ): Boolean {
        val lk = root.optJSONObject("likely") ?: return false
        if (lk.has("enabled")) onEnabled(lk.getBoolean("enabled"))
        if (lk.has("effect")) onEffect(lk.getBoolean("effect"))
        return true
    }

    /** Trail-Effekt: Schalter, Farbe, Decay-Stufen, Treffer-Markierung. */
    internal fun readTrail(root: JSONObject, sink: TrailSink): Boolean {
        val t = root.optJSONObject("trail") ?: return false
        if (t.has("enabled")) sink.enabled(t.getBoolean("enabled"))
        if (t.has("color")) sink.color(t.getInt("color"))
        if (t.has("steps")) sink.steps(t.getInt("steps"))
        if (t.has("trace")) sink.trace(t.getBoolean("trace"))
        if (t.has("accepted_color")) sink.acceptedColor(t.getInt("accepted_color"))
        return true
    }

    /** Senke für [readTrail] — Objekt statt Parameterliste (siehe [GradientSink]). */
    internal class TrailSink(
        private val onEnabled: (Boolean) -> Unit,
        private val onColor: (Int) -> Unit,
        private val onSteps: (Int) -> Unit,
        private val onTrace: (Boolean) -> Unit,
        private val onAcceptedColor: (Int) -> Unit
    ) {
        fun enabled(v: Boolean) = onEnabled(v)
        fun color(v: Int) = onColor(v)
        fun steps(v: Int) = onSteps(v)
        fun trace(v: Boolean) = onTrace(v)
        fun acceptedColor(v: Int) = onAcceptedColor(v)
    }
}
