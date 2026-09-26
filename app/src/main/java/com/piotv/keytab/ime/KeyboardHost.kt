package com.piotv.keytab.ime

import android.content.Context
import android.os.Handler
import android.view.View
import android.widget.Button

/**
 * Rollen-Interface: gemeinsame Basis aller Host-Rollen.
 *
 * Jede Rolle erbt von [KeyboardHost] und wird von genau den Modulen konsumiert,
 * die diese Rolle tatsächlich brauchen. Dadurch hängt kein Modul mehr am
 * 25-Member-Interface, sondern nur an den 3–8 Membern seiner Rolle
 * (Interface Segregation, docs/REFACTORING_PLAN.md Phase 7/P3).
 */
interface KeyboardHost {

    /** Basis-Kontext des IME (für Prefs, Resources, Assets, String-Ressourcen). */
    val context: Context

    /** Tastatur ausblenden (Hide-Button). Wird durch erneutes Tippen ins Feld
     *  wieder eingeblendet (Standard InputMethodService-Verhalten). */
    fun hideKeyboard()
}

/**
 * Rolle: Theme (Konsument [ThemeController]).
 *
 * Nur was zum Umschalten/Aufbauen des Themes nötig ist.
 */
interface ThemeHost : KeyboardHost {

    /** Dark-Mode aktiv? (Pref-Override oder System-Modus). */
    fun isDarkMode(): Boolean

    /** Tastatur-View komplett neu aufbauen (Theme-Wechsel) und zurückgeben. */
    fun rebuildInputView(): View

    /** Neuen View als aktiven Input-View setzen. */
    fun setInputView(view: View)

    /** Aktueller Root-View der Tastatur (null vor erstem Aufbau / nach Release). */
    val keyboardRoot: View?

    /** Long-Press-Handler (MainThread) für verzögerte Aktionen. */
    val longPressHandler: Handler

    /** Long-Press-Popup (für dismiss bei Tastenwechsel). */
    val letterPopup: LetterPopup

    /** Haptisches Feedback auf dem Tastatur-Root. */
    fun haptic()
}

/**
 * Rolle: Tabs (Konsument [TabController]).
 *
 * Tab-Umschaltung braucht Routing, Panel-Zugriffe und String-Ressourcen.
 */
interface TabHost : KeyboardHost {

    /** Aktiver Input-Router (Ziel: App/Editor). */
    val inputRouter: InputRouter?

    /** Long-Press-Popup (für dismiss beim Tab-Wechsel). */
    val letterPopup: LetterPopup

    /** Dateimanager-Panel (für show() beim Files-Tab). */
    val fileManagerPanel: FileManagerPanel?

    /** Clipboard-Panel (für onSelected() beim Notes-Tab). */
    val clipboardPanel: ClipboardPanel?

    /** Snippet-Panel (für onSelected() beim Snip-Tab). */
    val snippetPanel: SnippetPanel?
}

/**
 * Rolle: Vorschläge (Konsument [SuggestionController]).
 *
 * Vorschlagsleiste, dynamische Tastengröße und Likely-Highlighting.
 */
interface SuggestionHost : KeyboardHost {

    /** Wortvorhersage-Manager. */
    val predictionManager: WordPredictionManager?

    /** Dynamischer Tasten-Skaler. */
    val keyScaler: DynamicKeyScaler?

    /** Buchstaben-Tasten → Basiszeichen (für Likely-Highlights). */
    val baseLetters: MutableMap<Button, Char>

    /** Aktueller Root-View der Tastatur (null vor erstem Aufbau / nach Release). */
    val keyboardRoot: View?

    /** Dark-Mode aktiv? (Pref-Override oder System-Modus). */
    fun isDarkMode(): Boolean

    /** Haptisches Feedback auf dem Tastatur-Root. */
    fun haptic()

    fun isShifted(): Boolean
    fun isCapsLock(): Boolean

    /** Aktiver Tab ist Editor? (⌄-Hide-Button nur dort sichtbar.) */
    fun isEditorTab(): Boolean

    /** Einzelne Shift-Aktivierung zurücksetzen (CapsLock bleibt). */
    fun consumeSingleShift()
}

/**
 * Rolle: Tasten-Binding (Konsument [KeyboardBinder]).
 *
 * Touch-/Long-Press-/Repeat-Logik der Tasten; enthält Text-Eingabe und
 * Shift-State, aber keine Panel- oder Theme-Aufbauten.
 */
interface KeyboardInputHost : KeyboardHost {

    /** Long-Press-Popup (für dismiss und Drag-Auswahl). */
    val letterPopup: LetterPopup

    /** Long-Press-Handler (MainThread) – Tab/Enter über den Router, Auto-Repeat der Del-Taste. */
    val longPressHandler: Handler

    /** Aktiver Input-Router (TAB/Enter/Backspace gehen an das aktive Eingabeziel). */
    val inputRouter: InputRouter?

    /** Wortvorhersage-Manager (für Vorschlags-Übernahme). */
    val predictionManager: WordPredictionManager?

    /** Buchstaben-Tasten → Basiszeichen (für LetterCase). */
    val baseLetters: MutableMap<Button, Char>

    /** Aktueller Root-View der Tastatur (null vor erstem Aufbau / nach Release). */
    val keyboardRoot: View?

    /** Haptisches Feedback auf dem Tastatur-Root. */
    fun haptic()

    /** Text an das aktive Eingabeziel senden (App/Editor via Router). */
    fun commitText(text: String)

    /** Wort vor dem Cursor löschen + Vorhersage reset (Del-LongPress-Repeat). */
    fun deleteLastWord()

    /** App-Einstellungen öffnen (Settings-Taste). */
    fun openSettings()

    fun isShifted(): Boolean
    fun isCapsLock(): Boolean

    /** Einzelne Shift-Aktivierung zurücksetzen (CapsLock bleibt) und View
     *  aktualisieren (Alpha/Bold + Buchstaben-Groß-/Kleinschreibung). */
    fun consumeSingleShift()

    /**
     * Shift-Taste getippt. [now] = Zeitstempel (elapsedRealtime).
     * @return neuer Zustand (shifted/capsLock) – Aufrufer wendet Visual + LetterCase an.
     */
    fun tapShift(now: Long): ShiftController.ShiftState

    /** Buchstaben-Groß-/Kleinschreibung + Rand-Hinweise auf den View anwenden. */
    fun applyLetterCase(root: View?)

    /** Shift-Taste visuell aktualisieren (Alpha/Bold nach Zustand). */
    fun updateShiftVisual(root: View?)

    /** Kombinierte Long-Press-Zuordnungen der aktiven Sprache (Akzente + Interpunktion). */
    val letterExtras: Map<Char, List<String>>
}
