package com.piotv.keytab

import android.content.Context
import android.content.SharedPreferences

/**
 * Zentrale Konstante für SharedPreferences-Name (keytab_prefs) + Feature-Keys,
 * die von mehreren Modulen geteilt werden ([MainActivity], [KeyTabImeService],
 * [FileManagerPanel], [ThemePrefs]).
 *
 * Zweck (Refactoring, Phase 1): Die Datei beseitigt
 * die duplizierte `PREFS = "keytab_prefs"`-Konstante, die zuvor in
 * [MainActivity] UND [KeyTabImeService] getrennt existierte und per
 * `MainActivity.PREFS`/`<eigene>.PREFS` gemischt verwendet wurde → Drift-Risiko.
 *
 * Seit P3 (2026-09-18) liegt hier zusätzlich der **einzige Zugriffspunkt** auf die
 * SharedPreferences: [of]. Vorher riefen 12 Dateien 25-mal direkt
 * `context.getSharedPreferences(Prefs.FILE, MODE_PRIVATE)` auf — jede Stelle
 * konnte Dateiname oder Modus abweichend setzen. Jetzt gibt es genau eine
 * Methode, die den Dateinamen kennt.
 *
 * Theme-spezifische Keys bleiben in [com.piotv.keytab.ime.ThemePrefs]
 * (dark_mode, gradient_*, theme_dark_*, theme_light_*, likely, theme_version).
 * Hinweis: Die alten on-disk-Namen aus Versionen ≤ v0.9.6 (`gaming_mode`,
 * `gaming_effect`, `theme_*_gaming`) waren Fehlbenennungen — gemeint war immer
 * die Hervorhebung der wahrscheinlichsten nächsten Taste. Sie werden beim Lesen
 * umbenannt ([com.piotv.keytab.ime.ThemePrefs.migrateLegacyNames]).
 */
object Prefs {

    /** Name der SharedPreferences-Datei der App. */
    const val FILE = "keytab_prefs"

    /**
     * Einziger Zugriffspunkt auf die App-SharedPreferences.
     *
     * Alle Module (Service, Controller, Panels, Activities, Sections) holen die
     * Instanz hierüber; Dateiname und Modus sind damit unveränderlich zentral.
     * `MODE_PRIVATE` ist der einzige zulässige Modus — die Datei enthält das
     * gelernte Benutzer-Wörterbuch und verlässt das Gerät nicht.
     */
    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Optionale Zahlenreihe an/aus. */
    const val KEY_NUM_ROW = "num_row"

    /**
     * Wortvorhersage-Leiste an/aus (Config-Key `suggestions`, Pref `suggestions_enabled`).
     */
    const val KEY_SUGGESTIONS = "suggestions_enabled"

    // ---------- Tab-Konstanten ----------
    /**
     * Clipboard-Tab sichtbar an/aus.
     */
    const val KEY_CLIP_TAB = "clip_tab_enabled"

    /**
     * Snippet-Tab sichtbar an/aus (benannte Befehle/Snippets).
     */
    const val KEY_SNIPPET_TAB = "snippet_tab_enabled"

    /**
     * Snippet-History für die Vorschlags-Leiste: bis zu 3 zuletzt eingefügte
     * Snippets (NUL-getrennt, most-recent-first), die angezeigt werden, wenn
     * gerade keine Wortvorschläge gezeigt werden (Satzanfang). Siehe
     * SuggestionEngine.recordRecent / recentSnippets.
     */
    const val KEY_RECENT_SNIPPETS = "recent_snippets"

    /** Dynamische Tastengröße an/aus. */
    const val KEY_DYNAMIC_KEYS = "dynamic_keys_enabled"

    /**
     * Swipe-Eingabe (Gleit-Eingabe) an/aus (Default **aus**). Gleiten über die
     * Tastatur bewertet die gefahrene Route gegen die Engine; bei klarem
     * Ergebnis Auto-Commit. Offline, engine-basiert, kein neues Modell.
     * Logik: [com.piotv.keytab.ime.SwipePathLogic] + [com.piotv.keytab.ime.SwipeScorer].
     */
    const val KEY_SWIPE = "swipe_enabled"

    /** Aktive Tastatur-Sprache (ISO-Code, z. B. "de", "en"). */
    const val KEY_LANGUAGE = "language"

    /** Serialisiertes Benutzer-Wörterbuch (Lernwörter, Klartext-Format). */
    const val KEY_USER_DICT = "user_dict"

    /** Hintergrundbild-URI (Theming). */
    const val KEY_BG_IMAGE_URI = "bg_image_uri"

    /** Hintergrundbild-Fill-Modus: "fit" / "cover" / "stretch". */
    const val KEY_BG_IMAGE_FILL = "bg_image_fill"
    const val FILL_FIT = "fit"
    const val FILL_COVER = "cover"
    const val FILL_STRETCH = "stretch"
}
