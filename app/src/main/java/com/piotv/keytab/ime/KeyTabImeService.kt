package com.piotv.keytab.ime

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper

import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.piotv.keytab.R

/**
 * KeyTab-IME – schlanker Keyboard-Core (Tasten, Shift, Symbole, Long-Press).
 * Long-Press-Popup (Header + Drag-Auswahl) leben in [LetterPopup].
 * Die Sub-Features leben in eigenen Klassen:
 * - [FileManagerPanel]  – Tab-Dateimanager
 * - [EditorPanel]       – interner Editor mit Speichern/Laden
 * - [ClipboardPanel]    – Ablage (Clipboard-Historie)
 * - [TextEditLogic]     – reine, testbare Textlogik
 */
class KeyTabImeService : InputMethodService(), ThemeHost, TabHost, SuggestionHost, KeyboardInputHost {

    private companion object {
        const val LONG_PRESS_TIMEOUT = 400L
        // Beschleunigendes Wort-Löschen: Startintervall, Faktor pro Repeat, Minimum
        const val WORD_DELETE_START_MS = 250L
        const val WORD_DELETE_ACCEL = 0.85f
        const val WORD_DELETE_MIN_MS = 30L
        const val SHIFT_DOUBLE_TAP_MS = 300L
        const val ANIMATION_START_DELAY_MS = 48L
    }

    // ---------- Module ----------
    private val suggestionViews = arrayOfNulls<TextView>(3)
    override var keyScaler: DynamicKeyScaler? = null
        private set
    override var predictionManager: WordPredictionManager? = null
        private set

    // Phase-4-Controller (Service delegiert Theme/Tabs/Suggestions hierher)
    private val themeController = ThemeController(this)
    private val tabController = TabController(this)
    private val suggestionController = SuggestionController(this)
    // Phase-3-Controller: Tasten-Event-Binding (Touch/Long-Press/Del-Repeat)
    private lateinit var keyboardBinder: KeyboardBinder
    /** Swipe-Manager (v0.11): Schaltplan-Preview + Swipe-Eingabe. null vor erstem Aufbau. */
    private var swipeManager: SwipeManager? = null

    // Phase 6: Text-Commit-Orchestrierung + View-Aufbau ausgelagert
    private val textCommit = TextCommitController(object : TextCommitController.Host {
        override fun haptic() = this@KeyTabImeService.haptic()
        override val inputRouter get() = this@KeyTabImeService.inputRouter
        override val predictionManager get() = this@KeyTabImeService.predictionManager
        override fun consumeSingleShift() = this@KeyTabImeService.consumeSingleShift()
        override fun updateSuggestions() {
            suggestionController.update()
            updateSwipePreview()
        }
    })
    private val viewFactory = KeyboardViewFactory(object : KeyboardViewFactory.Deps {
        override val imeContext: Context get() = baseContext
        override fun isDarkMode() = this@KeyTabImeService.isDarkMode()
        override fun configFile() = this@KeyTabImeService.configFile()
        override fun commitText(text: String) = this@KeyTabImeService.commitText(text)
        override fun commitToApp(text: String) = this@KeyTabImeService.commitToApp(text)
        override val isInputViewShown: Boolean get() = this@KeyTabImeService.isInputViewShown
        override fun currentInputConnection() = currentInputConnection
        override fun sendKeyEvents(keyCode: Int) = sendDownUpKeyEvents(keyCode)
        override val ioExecutor: java.util.concurrent.Executor get() = this@KeyTabImeService.ioExecutor
        override val mainHandler: Handler get() = this@KeyTabImeService.mainHandler
        override val suggestionViews: Array<TextView?> get() = this@KeyTabImeService.suggestionViews
        override val baseLetters: MutableMap<Button, Char> get() = this@KeyTabImeService.baseLetters
    })

    /**
     * KeyboardHost-Rollen (P3, 2026-09-18): Der Service implementiert alle vier
     * Rollen-Interfaces ([ThemeHost], [TabHost], [SuggestionHost],
     * [KeyboardInputHost]) statt eines 25-Member-Interfaces. Jeder Controller
     * deklariert jetzt nur noch die Rolle, die er wirklich braucht
     * (Interface Segregation).
     */

    /** KeyboardHost: Basis-Kontext für Prefs/Resources/Assets (gleiche Instanz wie baseContext). */
    override val context: Context get() = baseContext

    /** Shift-/CapsLock-Zustandsmaschine (Phase 2 extrahiert, Phase 3 angebunden). */
    private val shiftController = ShiftController(SHIFT_DOUBLE_TAP_MS)
    override var keyboardRoot: View? = null
        internal set
    override val letterPopup = LetterPopup(this)
    override val longPressHandler = Handler(Looper.getMainLooper())
    // Phase "Thread-Konsolidierung": gemeinsame Pools statt eigener Instanzen
    // (siehe KeyTabExecutors) – onDestroy ruft kein shutdownNow() mehr auf,
    // die Daemons überleben den Service bewusst (Kostet ~0, verhindert
    // RejectedExecutionException bei erneuter IME-Sitzung).
    private val mainHandler = KeyTabExecutors.main
    private val ioExecutor: java.util.concurrent.Executor = KeyTabExecutors.io
    override val baseLetters = mutableMapOf<Button, Char>()

    /** Eingabe-Routing (Phase 2): wohin Text fließt (App/Editor/Terminal). */
    override var inputRouter: InputRouter? = null
        private set

    override var fileManagerPanel: FileManagerPanel? = null
        private set
    private var editorPanel: EditorPanel? = null
    private var terminalPanel: TerminalPanel? = null
    override var clipboardPanel: ClipboardPanel? = null
        private set
    override var snippetPanel: SnippetPanel? = null
        private set

    /** Aktive Sprache (aus Einstellungen, default Deutsch). */
    private var activeLanguage: KeyboardLanguage = Languages.de

    /** Kombinierte Long-Press-Zuordnungen der aktiven Sprache (Akzente + Interpunktion). */
    override val letterExtras: Map<Char, List<String>>
        get() = activeLanguage.letterExtras(Languages.basePunctuation)

    /**
     * Alte Panel-Instanzen freigeben, bevor der Input-View neu aufgebaut wird.
     * Ohne dieses Release behalten die Panels eine Referenz auf den alten
     * Root-View → jeder Rebuild (Theme-Wechsel, Konfigurationswechsel)
     * akkumuliert einen kompletten View-Baum (Memory-Leak).
     */
    private fun releasePanels() {
        // Swipe-Preview (Overlay + Knoten-Färbung) VOR dem Vergessen der alten
        // Instanz entfernen — sonst bleibt das EdgeOverlay im alten View-Baum
        // hängen und ist nicht mehr removebar („Verbindung geht nicht weg"-Bug).
        swipeManager?.clearPreview()
        swipeManager = null
        terminalPanel?.shutdown()
        fileManagerPanel = null
        editorPanel = null
        terminalPanel = null
        clipboardPanel = null
        snippetPanel = null
        keyboardRoot = null
        suggestionViews.fill(null)
        baseLetters.clear()
        suggestionController.clearLikelyHighlights()
    }

    private var appliedSettings: Map<String, Any?>? = null
    private var appliedDarkMode: Boolean? = null

    override fun onCreateInputView(): View {
        SettingsConfig.importIfChanged(this)
        // Vorherige Panels/Views freigeben (Leak-Fix): Rebuilds entstehen bei
        // Theme-Wechsel (toggleDarkMode) und Konfigurationsänderungen.
        releasePanels()
        // Phase 6: kompletter View-Aufbau (Themed-Context, Theme, Panels, Router,
        // Prediction, Skaler) lebt in [KeyboardViewFactory]; der Service wiringt
        // nur noch die Felder und hängt die Controller ein.
        val result = viewFactory.create()
        keyboardRoot = result.root
        fileManagerPanel = result.fileManagerPanel
        editorPanel = result.editorPanel
        terminalPanel = result.terminalPanel
        clipboardPanel = result.clipboardPanel
        snippetPanel = result.snippetPanel
        inputRouter = result.router
        activeLanguage = result.language
        predictionManager = result.predictionManager
        keyScaler = result.keyScaler
        val root = result.root
        // Module einhängen
        tabController.setup(root)
        // Trail-Manager erstellen (baseLetters noch leer, wird nach hook() aktualisiert)
        val trailManager = TrailManager(
            com.piotv.keytab.Prefs.of(this),
            baseLetters
        )
        // Swipe-Manager (v0.11): erstellt mit baseLetters (noch leer, wird nach hook gefüllt).
        val sm = SwipeManager(com.piotv.keytab.Prefs.of(this), baseLetters)
        swipeManager = sm
        keyboardBinder = KeyboardBinder(this, LONG_PRESS_TIMEOUT, tabController, themeController, suggestionController, trailManager, sm)
        keyboardBinder.hook(root)
        // Jetzt baseLetters gefüllt - TrailManager aktualisieren
        trailManager.updateBaseLetters(baseLetters)
        keyboardBinder.applyLetterCase(root)
        // Suggestion-Views holen, Module starten (Engine lazy, Skaler aufbauen)
        suggestionController.setup(root, suggestionViews, activeLanguage)
        // Swipe-Engine anbinden: sobald die Engine ready ist, am SwipeManager setzen.
        predictionManager?.setOnEngineReady {
            swipeManager?.setEngine(predictionManager?.engine)
            suggestionController.update()
            updateSwipePreview()
        }
        swipeManager?.setEngine(predictionManager?.engine)
        appliedSettings = SettingsConfig.snapshot(com.piotv.keytab.Prefs.of(this))
        appliedDarkMode = isDarkMode()
        return root
    }

    private fun configFile(): java.io.File = SettingsConfig.configFile(this)

    /** Covers every UI preference, not just color edits that bump theme_version. */
    private fun refreshSettings() {
        SettingsConfig.importIfChanged(this)
        val snapshot = SettingsConfig.snapshot(com.piotv.keytab.Prefs.of(this))
        if (keyboardRoot != null && (snapshot != appliedSettings || appliedDarkMode != isDarkMode())) {
            setInputView(onCreateInputView())
        }
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        refreshSettings()
        // Feld-Start: CapsLock aus, ggf. Auto-Caps (Shift-Zustandsmaschine).
        shiftController.resetForInput(autoCapitalize(attribute))
        // onStartInput kann VOR onCreateInputView feuern (IME-Start, bevor die
        // Tastatur das erste Mal angezeigt wird) → keyboardBinder ist dann noch
        // nicht initialisiert. Guard verhindert UninitializedPropertyAccessException.
        if (::keyboardBinder.isInitialized) {
            // Passwort-/Sensibel-Feld: Trail dort hart abschalten (TrailLogic).
            keyboardBinder.setEditorInfo(attribute)
            keyboardBinder.applyLetterCase(keyboardRoot)
            keyboardBinder.updateShiftVisual(keyboardRoot)
        }
    }

    override fun onStartInputView(editorInfo: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        // Theme-Änderungen (Farben/Alpha aus der Settings-Activity) übernehmen, auch
        // wenn dasselbe Textfeld weiterläuft – onStartInput feuert dann nicht erneut,
        // die Tastatur zeigte sonst die alten Farben (u. a. Alpha nicht angewendet).
        refreshSettings()
        // Feldwechsel: Trail in Passwort-/Sensibel-Feldern abschalten. Das feuert
        // zuverlässiger als onStartInput (z. B. bei Fokuswechsel ohne neues Feld).
        if (::keyboardBinder.isInitialized) keyboardBinder.setEditorInfo(editorInfo)
    }

    private fun autoCapitalize(attribute: android.view.inputmethod.EditorInfo?): Boolean =
        CapsLogic.wantsCapitalization(attribute)

    override fun haptic() {
        keyboardRoot?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Dark-Mode-Override; ohne gesetzte Pref gilt der System-Modus. */
    override fun isDarkMode(): Boolean {
        val prefs = com.piotv.keytab.Prefs.of(this)
        if (prefs.contains(ThemePrefs.KEY_DARK)) return prefs.getBoolean(ThemePrefs.KEY_DARK, false)
        val mask = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    // ---------- KeyboardHost-Implementation (Phase 3+4) ----------

    /** Tastatur-View neu aufbauen (für Theme-Wechsel via [ThemeController]). */
    override fun rebuildInputView(): View = onCreateInputView()

    override fun isShifted(): Boolean = shiftController.isUpper()
    override fun isCapsLock(): Boolean = shiftController.state().capsLock
    override fun isEditorOrTerminalTab(): Boolean =
        tabController.currentTabKind().let {
            it == TabController.TabKind.EDITOR || it == TabController.TabKind.TERMINAL
        }
    override fun hideKeyboard() {
        // Nur der Tastatur-Block UNTER der Wortvorhersage-Zeile wird ausgeblendet:
        // alle Tastenreihen (Buchstaben/Symbole/Zahlen) + Funktionsleiste + Datei-Panel.
        // Die Wortvorhersage-Zeile (mit ☺ und ⌄) bleibt sichtbar → Einblenden per ⌄.
        // Das Editor-/Terminal-Panel wird MAXIMIERT: es füllt den gesamten bisherigen
        // Tastatur-Platz aus (Editor-Normalhöhe + Tastatur + Funktionsleiste).
        val root = keyboardRoot ?: return
        val collapsed = root.getTag(R.id.sug_hide) as? Boolean ?: false
        val next = !collapsed
        root.setTag(R.id.sug_hide, next)
        val ed = root.findViewById<View>(R.id.editor_panel)
        val kb = root.findViewById<View>(R.id.kb_panel)
        val term = root.findViewById<View>(R.id.term_panel)
        val bottomRow = root.findViewById<View>(R.id.bottom_row)
        val width = root.resources.displayMetrics.widthPixels
        val kind = tabController.currentTabKind()
        if (next) {
            // HÖHEN VOR dem Ausblenden messen (GONE-Views haben Höhe 0)!
            val editorH = PanelHeights.terminalPanelHeight(ed, width)
            val kbH = PanelHeights.measureHeight(kb, width)
            val bottomH = PanelHeights.measureHeight(bottomRow, width)
            // Tastenreihen ausblenden (alle Geschwister der Suggestion-Bar).
            val sugBar = root.findViewById<View>(R.id.suggestion_bar)
            val lettersRoot = sugBar?.parent as? android.view.ViewGroup
            if (lettersRoot != null) {
                for (i in 0 until lettersRoot.childCount) {
                    val child = lettersRoot.getChildAt(i)
                    if (child.id == R.id.suggestion_bar) continue
                    child.visibility = View.GONE
                }
            }
            bottomRow?.visibility = View.GONE
            // Panel MAXIMIEREN: Editor-Normalhöhe + Tastatur + Funktionsleiste.
            val maxH = editorH + kbH + bottomH
            if (maxH > 0) {
                if (kind == TabController.TabKind.EDITOR && ed != null) {
                    ed.layoutParams = ed.layoutParams.apply { height = maxH }
                }
                if (kind == TabController.TabKind.TERMINAL && term != null) {
                    term.layoutParams = term.layoutParams.apply { height = maxH }
                }
            }
        } else {
            // WIEDER EINBLENDEN: nur die Tastenreihen + Funktionsleiste sichtbar
            // machen. Panels (editor/term/file/clip/snip) NICHT anfassen — der
            // TabController verwaltet deren Sichtbarkeit. Ein unbedachtes VISIBLE
            // würde z. B. im ABC-Tab das Editor-Panel zeigen und die Tastatur nach
            // oben drücken! Nur die echten Tastatur-Views wieder auf VISIBLE setzen.
            val sugBar = root.findViewById<View>(R.id.suggestion_bar)
            val lettersRoot = sugBar?.parent as? android.view.ViewGroup
            if (lettersRoot != null) {
                for (i in 0 until lettersRoot.childCount) {
                    val child = lettersRoot.getChildAt(i)
                    if (child.id == R.id.suggestion_bar) continue
                    // Nur Tastatur-Elemente (kb_panel, sym_panel, num_row) und die
                    // Buchstabenreihen wieder sichtbar — Panels bleiben wie sie sind.
                    val id = child.id
                    if (id == R.id.editor_panel || id == R.id.term_panel ||
                        id == R.id.file_panel || id == R.id.clip_panel ||
                        id == R.id.snippet_panel
                    ) continue
                    child.visibility = View.VISIBLE
                }
            }
            bottomRow?.visibility = View.VISIBLE
            // Panel zurück auf reine Editor-Höhe.
            val h = PanelHeights.terminalPanelHeight(ed, width)
            if (h > 0) {
                if (kind == TabController.TabKind.EDITOR && ed != null) {
                    ed.layoutParams = ed.layoutParams.apply { height = h }
                }
                if (kind == TabController.TabKind.TERMINAL && term != null) {
                    term.layoutParams = term.layoutParams.apply { height = h }
                }
            }
        }
    }

    /** Einzelne Shift-Aktivierung zurücksetzen (CapsLock bleibt) + View aktualisieren. */
    override fun consumeSingleShift() {
        shiftController.consume()
        if (!shiftController.isUpper() && ::keyboardBinder.isInitialized) {
            keyboardBinder.updateShiftVisual(keyboardRoot)
            keyboardBinder.applyLetterCase(keyboardRoot)
        }
    }

    // ---------- Shift-State-Delegate (Phase 3, an ShiftController gebunden) ----------

    override fun tapShift(now: Long): ShiftController.ShiftState = shiftController.tapShift(now)

    override fun applyLetterCase(root: View?) {
        if (::keyboardBinder.isInitialized) keyboardBinder.applyLetterCase(root)
    }

    override fun updateShiftVisual(root: View?) {
        if (::keyboardBinder.isInitialized) keyboardBinder.updateShiftVisual(root)
    }

    // ---------- Text-Eingabe-Delegate (Phase 3, für KeyboardBinder) ----------

    override fun commitText(text: String) = textCommit.commit(text)

    override fun deleteLastWord() = textCommit.deleteLastWord()

    override fun openSettings() {
        startActivity(Intent(this, com.piotv.keytab.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun commitToApp(text: String) =
        textCommit.commitToApp(text, currentInputConnection)

    /**
     * Schaltplan-Preview (v0.11, passiv): die wahrscheinlichen Folge-Tasten
     * des aktuell getippten Worts als verbundener Pfad sichtbar machen.
     * Nutzt [SwipePathLogic.previewNodes] aus den aktuellen Vorschlägen.
     * Deaktiviert (Pref aus / Passwort-Feld / keine Vorschläge) → Preview gelöscht.
     */
    private fun updateSwipePreview() {
        val sm = swipeManager ?: return
        if (!sm.previewEnabled()) { sm.clearPreview(); return }
        val pm = predictionManager ?: return
        val sugs = pm.currentSuggestions
        val typedLen = pm.currentTypedWord.length
        val nodes = SwipePathLogic.previewNodes(sugs, typedLen)
        sm.applyPreview(nodes)
    }

    override fun onDestroy() {
        predictionManager?.engine?.let {
            val raw = it.serializeUserDict()
            com.piotv.keytab.Prefs.of(this)
                .edit().putString(com.piotv.keytab.Prefs.KEY_USER_DICT, raw).apply()
        }
        swipeManager?.clearPreview()
        swipeManager = null
        letterPopup.dismiss()
        longPressHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
        releasePanels()
    }
}
