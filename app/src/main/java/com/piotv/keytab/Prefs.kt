package com.piotv.keytab

/**
 * Zentrale Konstante für SharedPreferences-Name (keytab_prefs) + Feature-Keys,
 * die von mehreren Modulen geteilt werden ([MainActivity], [KeyTabImeService],
 * [FileManagerPanel], [ThemePrefs]).
 *
 * Zweck (Refactoring, siehe docs/REFACTORING_PLAN.md Phase 1): Die Datei beseitigt
 * die duplizierte `PREFS = "keytab_prefs"`-Konstante, die zuvor in
 * [MainActivity] UND [KeyTabImeService] getrennt existierte und per
 * `MainActivity.PREFS`/`<eigene>.PREFS` gemischt verwendet wurde → Drift-Risiko.
 *
 * Theme-spezifische Keys bleiben in [com.piotv.keytab.ime.ThemePrefs]
 * (dark_mode, gradient_*, theme_dark_*, theme_light_*, gaming_*, theme_version).
 */
object Prefs {

    /** Name der SharedPreferences-Datei der App. */
    const val FILE = "keytab_prefs"

    /** Optionale Zahlenreihe an/aus. */
    const val KEY_NUM_ROW = "num_row"

    /** Terminal-Tab sichtbar an/aus. */
    const val KEY_TERM_TAB = "term_tab_enabled"

    /** Wortvorhersage-Leiste an/aus. */
    const val KEY_SUGGESTIONS = "suggestions_enabled"

    /** Auto-Korrektur an/aus. */
    const val KEY_AUTOCORRECT = "autocorrect_enabled"

    /** Dynamische Tastengröße an/aus. */
    const val KEY_DYNAMIC_KEYS = "dynamic_keys_enabled"

    /** Aktive Tastatur-Sprache (ISO-Code, z. B. "de", "en"). */
    const val KEY_LANGUAGE = "language"

    /** Serialisiertes Benutzer-Wörterbuch (Lernwörter, Klartext-Format). */
    const val KEY_USER_DICT = "user_dict"
}