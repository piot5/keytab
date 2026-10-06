package com.piotv.keytab

/**
 * Was im jeweiligen Test passieren soll — Text für die Anzeige im Test-Runner.
 *
 * Bewusst gebündelt statt in jedem Test verstreut: eine Quelle für den
 * Beobachter, die sich zusammen mit dem Testnamen pflegen lässt. Fehlt ein
 * Eintrag, zeigt der Runner das sichtbar an (kein stiller Fallback).
 */
object TestExpectations {
    private val texts = mapOf(
        // ---- AppInstrumentedTest: Manifest, Assets, Einstellungen ----
        "imeService_isDeclaredAndEnabled" to
            "Der KeyTabImeService ist im Manifest als InputMethod registriert.",
        "mainActivity_startsAndExposesSettings" to
            "MainActivity startet; Sprachauswahl und Zahlenreihen-Schalter sind sichtbar.",
        "settingsControls_areAccessibleAndToggleable" to
            "Der Zahlenreihen-Schalter kippt beim Tippen und der Wert landet in den Prefs.",
        "languageSelection_persistsSelectedLanguage" to
            "Eine andere Sprache im Spinner wird sofort als Pref gespeichert.",
        "settingsButtons_haveAccessibleLabels" to
            "Alle Einstellungs-Knoepfe tragen ein lesbares Label (mind. 3 Zeichen).",
        "fileProvider_isInstalled" to
            "Der FileProvider ist installiert und korrekt autorisiert.",
        "wordCorporaAssets_areBundled" to
            "Alle sieben Wortlisten sind als Asset vorhanden und nicht leer.",
        "imeSubtype_isRegistered" to
            "KeyTab steht nach dem Aktivieren in der Liste der aktivierten IMEs.",
        "configFile_wirdAufGeraetAngelegt" to
            "keytab_config.txt wird beim ersten Zugriff angelegt und ist nicht leer.",

        // ---- KeyTabImeEndToEndTest: echte IME-Tastendruecke ----
        "realIme_commitsCharactersSpaceTabEnterAndBackspace" to
            "x, Leertaste, TAB, Enter und Backspace kommen als Zeichen/Keycodes an.",
        "passwordField_typesNormallyButShowsNoSuggestions" to
            "Im Passwortfeld wird normal getippt, aber keine Vorschlagsleiste gezeigt.",
        "noPersonalizedLearningField_typesNormallyButShowsNoSuggestions" to
            "Feld ohne personalisiertes Lernen: tippt normal, zeigt keine Vorschlaege.",
        "fieldSwitch_keepsTargetsSeparateAndKeepsKeyboardUsable" to
            "Zwei Felder bleiben getrennt; die Tastatur bleibt nach dem Wechsel bedienbar.",
        "activityRecreation_keepsImeUsableForNewField" to
            "Nach recreate() tippt die Tastatur weiter in das neue Feld.",
        "shiftKey_schreibtGrossbuchstaben" to
            "Shift einmal tippen: Buchstaben werden gross, Feld enthaelt 'A'.",
        "doppelTapShift_aktiviertCapsLock" to
            "Shift zweimal schnell tippen: CapsLock, 'AB' wird gross geschrieben.",
        "symbolUmschalter_schreibtSymbol" to
            "Der ?123-Umschalter wechselt auf Sonderzeichen; '!' landet im Feld.",
        "backspace_loeschtEinZeichen" to
            "Nach 'ab' loescht Backspace genau ein Zeichen: 'a'.",
        "enterTaste_brichtZeileUm" to
            "Enter kommt als KEYCODE_ENTER an.",
        "punktTaste_schreibtPunkt" to
            "Die Punkt-Taste ergaenzt '.': Feld enthaelt 'a.'.",
        "spaceTaste_fuegtLeerzeichenEin" to
            "Die Leertaste ergaenzt ein Leerzeichen: Feld enthaelt 'a '.",

        // ---- KeyTabImeHeightTest: Hoehenvertrag je Tab ----
        "alleInhaltsTabsHabenDieselbeImeHoehe" to
            "FILES/CLIP/SNIP gleich hoch, EDITOR innerhalb einer Maximieren-Zeile, abc kleiner.",
        "maximizeZeileNurInInhaltsTabsSichtbarUndLiegtUnten" to
            "Maximieren-Zeile nur in FILES/CLIP/SNIP, dort ganz unten; im abc nie.",
        "maximierenFuelltDenBildschirmBisAufFuenfProzent" to
            "Maximieren fuellt mind. 80 % des Displays und kehrt zur Normalhoehe zurueck.",
        "wiederholterTabWechselBleibtStabil" to
            "Zweimal FILES-Wechsel: die Hoehe bleibt stabil.",
        "tastaturBleibtNachTabWechselSichtbar" to
            "Nach Wechsel FILES -> ABC ist die Tastatur weiter sichtbar.",

        // ---- KeyTabImeSuggestionsTest: Vorschlaege, Emoji, Datenschutz ----
        "vorschlagsleiste_zeigtWoerterBeimTippen" to
            "Nach 'ha' zeigt die Leiste mindestens einen Vorschlag.",
        "vorschlagsleiste_verborgenBeiDeaktivierung" to
            "Mit ausgeschalteten Vorschlaegen bleibt sug_1 unsichtbar.",
        "vorschlagTap_fuegtWortMitLeerzeichenEin" to
            "Ein Tipp auf den Vorschlag fuegt das Wort ins Feld ein.",
        "emojiKatalog_oeffnetBeimEmojiButton" to
            "Der Emoji-Knopf oeffnet das Emoji-Raster (sug_grid).",
        "passwortFeld_zeigtKeineVorschlaege" to
            "Im Passwortfeld erscheinen keine Vorschlaege.",

        // ---- KeyTabTrailVisualTest: Pixel-Verifikation ----
        "trailTraceMarkiertGetipptesWortGruen" to
            "Mit aktivem Trail-Trace sind deutlich mehr gruen-dominante Pixel sichtbar.",

        // ---- KeyTabDeviceScreensTest: Doku-Werkzeug (opt-in) ----
        "alleTabs_normalUndMaximiert" to
            "Doku: Screenshots aller Tabs, normal und maximiert.",
        "trailGifFrames" to
            "Doku: Einzelframes des Tipp-Trails.",
        "scrollGifFrames" to
            "Doku: Einzelframes des Scrollens im Dateimanager."
    )

    /** Erwartungstext zum Testnamen (sichtbarer Hinweis, wenn keiner hinterlegt ist). */
    fun of(testName: String): String = texts[testName]
        ?: "Keine Beschreibung hinterlegt — bitte in TestExpectations.of ergaenzen."
}
