package com.piotv.keytab.ime

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper

import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.piotv.keytab.R

/**
 * KeyTab-IME – schlanker Keyboard-Core (Tasten, Shift, Symbole, Long-Press).
 * Long-Press-Popup (Header + Drag-Auswahl) leben in [LetterPopup].
 * Die Sub-Features leben in eigenen Klassen:
 * - [FileManagerPanel]  – Tab-Dateimanager
 * - [EditorPanel]       – interner Editor mit Speichern/Laden
 * - [ClipboardPanel]    – Ablage (Clipboard-Historie)
 * - [TextEditLogic]     – reine, testbare Textlogik
 *
 * `@Suppress("TooManyFunctions")` ist bewusst und begründet: der Service ist der
 * Eingabe-Kern und bündelt Lebenszyklus, Tasten-Routing, Shift/Caps, Long-Press,
 * Symbol-Ebenen und die Host-Interfaces der Panels. detekts Schwelle liegt bei
 * 20 Funktionen, hier sind es 27 (29.10.2026). Ein Aufteilen wäre ein eigener
 * Umbau (mit Testanpassungen) — die Baseline bleibt leer, deshalb steht die
 * Ausnahme sichtbar im Code.
 */
@Suppress("TooManyFunctions")
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
    /**
     * Letztes Eingabefeld (EditorInfo) – Quelle der harten Sicherheitsregel
     * [TrailLogic.isPersonalizedProcessingAllowed] für Lernen, Vorschläge und
     * Autokorrektur. Trail/Swipe bekommen dieselbe Instanz über
     * [KeyboardBinder.setEditorInfo] (gesetzt in onStartInput/onStartInputView).
     */
    private var lastEditorInfo: android.view.inputmethod.EditorInfo? = null
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
    /** Swipe-Manager (v0.11): Swipe-Eingabe. null vor erstem Aufbau. */
    private var swipeManager: SwipeManager? = null

    /**
     * Trail-Manager (Prefs-Listener). Wird bei jedem Rebuild neu erzeugt — der
     * alte muss vorher abgemeldet werden, sonst sammeln sich die Listener
     * (N5).
     */
    private var trailManager: TrailManager? = null

    // Phase 6: Text-Commit-Orchestrierung + View-Aufbau ausgelagert
    private val textCommit = TextCommitController(object : TextCommitController.Host {
        override fun haptic() = this@KeyTabImeService.haptic()
        override val inputRouter get() = this@KeyTabImeService.inputRouter
        override val predictionManager get() = this@KeyTabImeService.predictionManager
        override fun consumeSingleShift() = this@KeyTabImeService.consumeSingleShift()
        override fun updateSuggestions() {
            suggestionController.update()
        }
    })
    private val viewFactory = KeyboardViewFactory(object : KeyboardViewFactory.Deps {
        override val imeContext: Context get() = baseContext
        override fun isDarkMode() = this@KeyTabImeService.isDarkMode()
        override fun configFile() = this@KeyTabImeService.configFile()
        override fun commitText(text: String) = this@KeyTabImeService.commitText(text)
        override fun commitToApp(text: String) = this@KeyTabImeService.commitToApp(text)
        override val isInputViewShown: Boolean get() = this@KeyTabImeService.isInputViewShown
        override fun isPersonalizedProcessingAllowed(): Boolean =
            TrailLogic.isPersonalizedProcessingAllowed(this@KeyTabImeService.lastEditorInfo)
        override fun currentInputConnection() = currentInputConnection
        override fun sendKeyEvents(keyCode: Int) = sendDownUpKeyEvents(keyCode)
        override fun selectEditor() { tabController.select(TabController.TabKind.EDITOR) }
        override val ioExecutor: java.util.concurrent.Executor get() = this@KeyTabImeService.ioExecutor
        override val mainHandler: Handler get() = this@KeyTabImeService.mainHandler
        override val coroutineScope: CoroutineScope get() = this@KeyTabImeService.serviceScope
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
    /** Scope für lifecycle-bewusste Panel-Arbeit; wird beim Service-Lifecycle beendet. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override val baseLetters = mutableMapOf<Button, Char>()

    /** Eingabe-Routing (Phase 2): wohin Text fließt (App/Editor). */
    override var inputRouter: InputRouter? = null
        private set

    override var fileManagerPanel: FileManagerPanel? = null
        private set
    private var editorPanelInstance: EditorPanel? = null
    override var clipboardPanel: ClipboardPanel? = null
        private set
    override var snippetPanel: SnippetPanel? = null
        private set

    /** Editor-Panel-Instanz (TabHost-Interface). */
    override val editorPanel: EditorPanel?
        get() = editorPanelInstance

    override fun refreshEditorBadge() {
        tabController.refreshEditorBadge()
    }

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
        trailManager?.release()
        trailManager = null
        swipeManager = null
        fileManagerPanel = null
        editorPanelInstance = null
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
        editorPanelInstance = result.editorPanel
        editorPanelInstance?.tabHost = this
        clipboardPanel = result.clipboardPanel
        snippetPanel = result.snippetPanel
        inputRouter = result.router
        activeLanguage = result.language
        predictionManager = result.predictionManager
        keyScaler = result.keyScaler

        val root = result.root
        // Edge-to-Edge-Erzwingung (targetSdk ≥ 35): Das IME-Fenster läuft bis
        // zum Bildschirmrand, die always-on-Navigationsleiste überlagert die
        // unterste Tastenreihe. Gelauscht wird am decorView des IME-Fensters —
        // dort landen die Insets als Erstes; das interne IMS-Layout konsumiert
        // sie sonst, bevor sie den Keyboard-Root erreichen (Listener am Root
        // feuerte deshalb nie, Praxis-Test 2026-10-10). Oben kein Padding —
        // über dem IME liegt kein Systembar; insets bleiben unkonsumiert, damit
        // PanelHeights.maximizeAvailableHeight weiter rechnen kann.
        window.window?.decorView?.let { decor ->
            ViewCompat.setOnApplyWindowInsetsListener(decor) { _, insets ->
                val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                keyboardRoot?.setPadding(nav.left, 0, nav.right, nav.bottom)
                insets
            }
        }
        // Module einhängen
        tabController.setup(root)
        // Trail-Manager erstellen (baseLetters noch leer, wird nach hook() aktualisiert)
        val trailManager = TrailManager(
            com.piotv.keytab.Prefs.of(this),
            baseLetters
        )
        this.trailManager = trailManager
        // Swipe-Manager (v0.11): erstellt mit baseLetters (noch leer, wird nach hook gefüllt).
        val sm = SwipeManager(com.piotv.keytab.Prefs.of(this), baseLetters)
        swipeManager = sm
        keyboardBinder = KeyboardBinder(
            this, LONG_PRESS_TIMEOUT, tabController, themeController, suggestionController,
            KeyboardBinder.Managers(trailManager, sm)
        )
        keyboardBinder.hook(root)
        // baseLetters sind jetzt gefüllt; TrailManager und SwipeManager halten die
        // Map per Referenz (kein Nachtrag nötig — der frühere No-op-Aufruf
        // `trailManager.updateBaseLetters(...)` ist in v0.18 entfallen).
        keyboardBinder.applyLetterCase(root)
        // Suggestion-Views holen, Module starten (Engine lazy, Skaler aufbauen)
        suggestionController.setup(root, suggestionViews, activeLanguage)
        // Swipe-Engine anbinden: sobald die Engine ready ist, am SwipeManager setzen.
        predictionManager?.setOnEngineReady {
            swipeManager?.setEngine(predictionManager?.engine)
            suggestionController.update()
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
        // Feld für die harte Sicherheitsregel merken (Lernen/Vorschläge/Autokorrektur).
        lastEditorInfo = attribute
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
        lastEditorInfo = editorInfo
        if (::keyboardBinder.isInitialized) keyboardBinder.setEditorInfo(editorInfo)
    }

    /**
     * Insets-Dispatch nachziehen, sobald das Fenster sicher sichtbar ist: Der
     * Listener am Keyboard-Root (siehe [onCreateInputView]) feuerte beim
     * View-Attach im Praxis-Test 2026-10-10 nicht zuverlässig — die Tastatur
     * lief weiter bis unter die Navigationsleiste (targetSdk-36-Edge-to-Edge).
     * Hier ist alles attached und [ViewCompat.requestApplyInsets] liefert das
     * Event nach; die Listener-Logik selbst bleibt unverändert.
     */
    override fun onWindowShown() {
        super.onWindowShown()
        // Insets-Event nachziehen: am decorView anfragen (Quelle aller Insets),
        // nicht am Root — dort können sie von internen IMS-Views konsumiert sein.
        window.window?.decorView?.let { ViewCompat.requestApplyInsets(it) }
    }

    /**
     * Feld beendet (Fokus weg / App geschlossen): den gemerkten [EditorInfo]
     * verwerfen, damit der Passwort-/Sensibel-Zustand nicht in den nächsten
     * Kontext nachwirkt.
     */
    override fun onFinishInput() {
        super.onFinishInput()
        lastEditorInfo = null
    }

    /**
     * Speicherdruck bzw. Tastatur versteckt: gelerntes Wörterbuch sichern und die
     * Vorschlags-Engine (Wortkorpus) freigeben — sie wird beim nächsten Anzeigen
     * neu geladen. Ab [ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN] ist die Tastatur
     * nicht sichtbar, es geht also kein Tipp-Kontext verloren.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            predictionManager?.let { pm ->
                pm.engine?.let { engine ->
                    com.piotv.keytab.Prefs.of(this).edit {
                        putString(com.piotv.keytab.Prefs.KEY_USER_DICT, engine.serializeUserDict())
                    }
                }
                pm.releaseEngine()
            }
            swipeManager?.setEngine(null)
        }
    }

    private fun autoCapitalize(attribute: android.view.inputmethod.EditorInfo?): Boolean =
        CapsLogic.wantsCapitalization(attribute)

    override fun haptic() {
        keyboardRoot?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Dark-Mode-Override; Standard dunkel, `system` folgt dem UiMode. */
    override fun isDarkMode(): Boolean = ThemePrefs.isDarkMode(this)

    // ---------- KeyboardHost-Implementation (Phase 3+4) ----------

    /** Tastatur-View neu aufbauen (für Theme-Wechsel via [ThemeController]). */
    override fun rebuildInputView(): View = onCreateInputView()

    override fun isShifted(): Boolean = shiftController.isUpper()
    override fun isCapsLock(): Boolean = shiftController.state().capsLock
    override fun isEditorTab(): Boolean =
        tabController.currentTabKind().let {
            it == TabController.TabKind.EDITOR
        }
    /**
     * Vollbild-Modus ein-/ausschalten (Maximieren-Zeile am unteren Rand, in jedem
     * Tab). Die reine Sichtbarkeits- und Hoehenlogik steckt in [KeyboardCollapse].
     */
    override fun hideKeyboard() {
        val root = keyboardRoot ?: return
        val collapsed = root.getTag(R.id.maximized_state) as? Boolean ?: false
        val next = !collapsed
        root.setTag(R.id.maximized_state, next)
        if (next) {
            // Im Editor bleibt die Tastatur auch maximiert sichtbar, in
            // Files/Clip/Snippets nie — die Regel haengt am Tab, nicht am
            // Zustand. Im abc-Tab gibt es keinen Vollbild-Modus.
            KeyboardCollapse.collapse(
                root,
                keyboardVisible = tabController.isKeyboardAlwaysVisible(),
                maximizeRowVisible = tabController.showsMaximizeRow(),
                heightPx = TabHeights.overridePx(
                    this, tabController.currentTabKind(), maximized = true
                )
            )
        } else {
            KeyboardCollapse.expand(root)
            // Der Tab-Wechsler entscheidet, welches Panel sichtbar ist und
            // setzt die Normalhoehe — nach dem Vollbild-Modus wieder noetig.
            tabController.select(tabController.currentTabKind())
        }
        // N1: Das ⇱/⇲-Symbol muss dem neuen Zustand folgen — ohne Tab-Wechsel
        // aendert es sich nur hier.
        tabController.updateMaximizeSymbol(root)
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

    override fun onDestroy() {
        predictionManager?.engine?.let {
            val raw = it.serializeUserDict()
            com.piotv.keytab.Prefs.of(this).edit {
                putString(com.piotv.keytab.Prefs.KEY_USER_DICT, raw)
            }
        }
        swipeManager = null
        letterPopup.dismiss()
        longPressHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        super.onDestroy()
        releasePanels()
    }
}
