package com.piotv.keytab.ime

/**
 * Verfügbarkeit der Schaltplan-Preview (`swipe_preview`).
 *
 * Die Preview zeigt die wahrscheinlichen Folge-Tasten des gerade getippten
 * Wortes und braucht dafür die **Prognose-Scores** der Engine. Die scores
 * werden nur erzeugt, wenn Gleit-Eingabe ([com.piotv.keytab.Prefs.KEY_SWIPE])
 * oder dynamische Tastengröße ([com.piotv.keytab.Prefs.KEY_DYNAMIC_KEYS])
 * aktiv sind — beides liest dieselbe Score-Quelle.
 *
 * Deshalb ist „Pref an" allein nicht genug: sonst gäbe es einen Schalter, der
 * sichtbar aktiviert ist und nichts anzeigt. Die UI-Regel steht hier als
 * reine Funktion, damit sie ohne Gerät testbar ist ([MainActivity] blendet den
 * Schalter ab, solange [available] false ist).
 */
object SwipePreviewAvailability {

    /**
     * Kann die Preview technisch überhaupt etwas anzeigen?
     * Mindestens einer der beiden score-liefernden Schalter muss an sein.
     */
    fun available(swipeEnabled: Boolean, dynamicKeysEnabled: Boolean): Boolean =
        swipeEnabled || dynamicKeysEnabled

    /**
     * Effektiv aktiv: gewünscht **und** verfügbar. `available` wird mit
     * übergeben, damit die UI ihren eigenen, bereits ermittelten Zustand
     * wiederverwenden kann statt ihn zu duplizieren.
     */
    fun active(previewRequested: Boolean, swipeEnabled: Boolean, dynamicKeysEnabled: Boolean): Boolean =
        previewRequested && available(swipeEnabled, dynamicKeysEnabled)
}
