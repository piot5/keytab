package com.piotv.keytab.ime

import android.content.Context
import com.piotv.keytab.Prefs

/**
 * Höhen-Vorgaben **je Tab und Zustand**, steuerbar über `keytab_config.txt`
 * (siehe `docs/CONFIG.md`).
 *
 * Ohne Vorgabe rechnet [PanelHeights] alles aus Fenster und Messung — das ist
 * der Normalfall und der Grund, warum alle Tabs gleich hoch aussehen. Wer einen
 * Tab bewusst anders hoch haben will (mehr Listenzeilen im Datei-Tab, ein
 * flacheres Vollbild-Fenster), schreibt eine Zahl in **dp**:
 *
 * ```ini
 * normal_height_files = 220   # dp, 0 = automatisch
 * max_height_files    = 640   # dp, 0 = Fenster füllen
 * ```
 *
 * Der Wert wird **begrenzt**, nie überschrieben: passt die Vorgabe nicht in das
 * IME-Fenster, gewinnt das Fenster (sonst schöbe der Inhalt die unterste Zeile
 * aus dem Fenster). Der abc-Tab hat keine Vorgaben — seine Höhe kommt aus den
 * Tasten, eine eigene Zahl würde nur das Layout zerlegen.
 *
 * Gespeichert wird der dp-Wert (nicht px): die Datei bleibt dadurch zwischen
 * Geräten mit anderer Dichte gültig, umgerechnet wird erst beim Setzen des Panels.
 */
internal object TabHeights {

    /** Vorgabe „automatisch berechnen" (in der Datei: `0`). */
    const val AUTO = 0

    /** Tabs mit eigener Höhe — der abc-Tab fehlt mit Absicht (siehe oben). */
    fun kinds(): List<TabController.TabKind> = listOf(
        TabController.TabKind.EDITOR, TabController.TabKind.FILES,
        TabController.TabKind.CLIP, TabController.TabKind.SNIPPET
    )

    /** Tab-Suffix in Config- und Pref-Key. */
    fun slug(kind: TabController.TabKind): String = when (kind) {
        TabController.TabKind.EDITOR -> "editor"
        TabController.TabKind.FILES -> "files"
        TabController.TabKind.CLIP -> "clip"
        TabController.TabKind.SNIPPET -> "snippet"
        TabController.TabKind.ABC -> "abc"
    }

    /** Config-Schlüssel: `normal_height_<tab>` bzw. `max_height_<tab>`. */
    fun configKey(kind: TabController.TabKind, maximized: Boolean): String =
        (if (maximized) "max_height_" else "normal_height_") + slug(kind)

    /** Pref-Key (dp). */
    fun prefKey(kind: TabController.TabKind, maximized: Boolean): String =
        "tab_height_" + (if (maximized) "max_" else "normal_") + slug(kind)

    /** Vorgabe in dp, [AUTO] wenn nicht gesetzt. */
    fun overrideDp(context: Context, kind: TabController.TabKind, maximized: Boolean): Int =
        Prefs.of(context).getInt(prefKey(kind, maximized), AUTO)

    /**
     * Vorgabe in px für [PanelHeights], 0 = keine.
     *
     * Negative oder nicht darstellbare Werte gelten als „keine Vorgabe" — so
     * kann eine kaputte Datei die Tastatur nicht unbenutzbar machen.
     */
    fun overridePx(context: Context, kind: TabController.TabKind, maximized: Boolean): Int {
        val dp = overrideDp(context, kind, maximized)
        if (dp <= AUTO) return 0
        val px = (dp * context.resources.displayMetrics.density).toInt()
        return if (px in 1..MAX_PX) px else 0
    }

    /** Obergrenze gegen Tippfehler wie `normal_height_files = 4000`. */
    private const val MAX_PX = 8000
}
