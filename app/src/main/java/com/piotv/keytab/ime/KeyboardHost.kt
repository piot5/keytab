package com.piotv.keytab.ime

import android.content.Context
import android.os.Handler
import android.view.View
import android.widget.Button

/**
 * Schmale Schnittstelle, über die die ausgelagerten Controller- und Panel-Module
 * auf die Fähigkeiten des [KeyTabImeService] zugreifen.
 *
 * Refactoring (docs/REFACTORING_PLAN.md Phase 4): reduziert die breite Kopplung
 * – Module kennen den Service nur noch als [KeyboardHost], nicht als konkrete
 * God-Class. Das gleiche Interface wird später (Phase 6) auch von den Panels
 * verwendet, sodass kein Panel/Controller den Service direkt referenziert.
 *
 * Abhängigkeiten zeigen nach innen: Views → Controller → Core.
 * Core kennt keine Views; [KeyboardHost] ist die Außengrenze des Service.
 */
interface KeyboardHost {

    /** Basis-Kontext des IME (für Prefs, Resources, Assets, String-Ressourcen). */
    val context: Context

    /** Aktueller Root-View der Tastatur (null vor erstem Aufbau / nach Release). */
    val keyboardRoot: View?

    /** Long-Press-Handler (MainThread) für verzögerte Aktionen. */
    val longPressHandler: Handler

    /** Long-Press-Popup (für dismiss bei Tab-/Tastenwechsel). */
    val letterPopup: LetterPopup

    /** Aktiver Input-Router (Ziel: App/Editor/Terminal). */
    val inputRouter: InputRouter?

    /** Wortvorhersage-Manager. */
    val predictionManager: WordPredictionManager?

    /** Dynamischer Tasten-Skaler. */
    val keyScaler: DynamicKeyScaler?

    /** Buchstaben-Tasten → Basiszeichen (für Gaming-Highlights). */
    val baseLetters: Map<Button, Char>

    /** Dateimanager-Panel (für show() beim Files-Tab). */
    val fileManagerPanel: FileManagerPanel?

    /** Clipboard-Panel (für onSelected() beim Notes-Tab). */
    val clipboardPanel: ClipboardPanel?

    // ---------- Theme ----------

    /** Dark-Mode aktiv? (Pref-Override oder System-Modus). */
    fun isDarkMode(): Boolean

    /** Tastatur-View komplett neu aufbauen (Theme-Wechsel) und zurückgeben. */
    fun rebuildInputView(): View

    /** Neuen View als aktiven Input-View setzen. */
    fun setInputView(view: View)

    // ---------- Shift ----------

    fun isShifted(): Boolean
    fun isCapsLock(): Boolean

    /**
     * Einzelne Shift-Aktivierung zurücksetzen (CapsLock bleibt) und View
     * aktualisieren (Alpha/Bold + Buchstaben-Groß-/Kleinschreibung).
     */
    fun consumeSingleShift()

    // ---------- Feedback / Routing ----------

    /** Haptisches Feedback auf dem Tastatur-Root. */
    fun haptic()
}
