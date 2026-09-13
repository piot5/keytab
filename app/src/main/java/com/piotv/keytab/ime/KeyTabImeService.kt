package com.piotv.keytab.ime

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper

import android.view.ContextThemeWrapper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.piotv.keytab.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * KeyTab-IME – schlanker Keyboard-Core (Tasten, Shift, Symbole, Long-Press).
 * Long-Press-Popup (Header + Drag-Auswahl) leben in [LetterPopup].
 * Die Sub-Features leben in eigenen Klassen:
 * - [FileManagerPanel]  – Tab-Dateimanager
 * - [EditorPanel]       – interner Editor mit Speichern/Laden
 * - [ClipboardPanel]    – Ablage (Clipboard-Historie)
 * - [TextEditLogic]     – reine, testbare Textlogik
 */
class KeyTabImeService : InputMethodService(), KeyboardHost {

    private companion object {
        const val LONG_PRESS_TIMEOUT = 400L
        // Beschleunigendes Wort-Löschen: Startintervall, Faktor pro Repeat, Minimum
        const val WORD_DELETE_START_MS = 250L
        const val WORD_DELETE_ACCEL = 0.85f
        const val WORD_DELETE_MIN_MS = 30L
        const val SHIFT_DOUBLE_TAP_MS = 300L
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

    /** KeyboardHost: Basis-Kontext für Prefs/Resources/Assets (gleiche Instanz wie baseContext). */
    override val context: Context get() = baseContext

    /** Shift-/CapsLock-Zustandsmaschine (Phase 2 extrahiert, Phase 3 angebunden). */
    private val shiftController = ShiftController(SHIFT_DOUBLE_TAP_MS)
    override var keyboardRoot: View? = null
        internal set
    override val letterPopup = LetterPopup(this)
    override val longPressHandler = Handler(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
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

    /** Aktive Sprache (aus Einstellungen, default Deutsch). */
    private var activeLanguage: KeyboardLanguage = Languages.de

    /**
     * Aktive Konfiguration (aus keytab_config.txt im externen Files-Dir,
     * Defaults wenn fehlend). Wird bei jedem Aufbau der Tastatur neu geladen,
     * damit Änderungen über die App ohne Neustart greifen.
     */
    private var config: KeyTabConfig = KeyTabConfig()

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
        terminalPanel?.shutdown()
        fileManagerPanel = null
        editorPanel = null
        terminalPanel = null
        clipboardPanel = null
        keyboardRoot = null
        suggestionViews.fill(null)
        baseLetters.clear()
        suggestionController.clearGaming()
    }

    override fun onCreateInputView(): View {
        // Vorherige Panels/Views freigeben (Leak-Fix): Rebuilds entstehen bei
        // Theme-Wechsel (toggleDarkMode) und Konfigurationsänderungen.
        releasePanels()
        // Dark/Light-Override (Persistiert in SharedPreferences, Default = System)
        val conf = Configuration(baseContext.resources.configuration)
        conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (isDarkMode()) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val cfgCtx = baseContext.createConfigurationContext(conf)
        val themedContext = ContextThemeWrapper(cfgCtx, R.style.Theme_KeyTab)
        val inflater = themedContext.getSystemService(android.view.LayoutInflater::class.java)
            ?: layoutInflater
        val root = inflater.cloneInContext(themedContext)
            .inflate(R.layout.keyboard_view, null)
        keyboardRoot = root
        // Konfiguration laden (Skalierung, Verschiebung, Seiten-Hinweise)
        config = KeyTabConfig.load(configFile())
        KeyScaleLogic.params = KeyScaleLogic.Params(
            maxScale = config.maxScale,
            midScale = config.midScale,
            hotThreshold = config.hotThreshold,
            midThreshold = config.midThreshold,
            minNeighborScale = config.minNeighborScale,
            midNeighborScale = config.midNeighborScale
        )
        // Theme-Umschalter-Icon passend zum aktiven Modus – monochrom (☾ Dark / ☀ Light),
        // kräftige Schrift in key_text (sw) und kleiner als zuvor
        val themeBtn = root.findViewById<Button>(R.id.key_theme)
        themeBtn?.text = if (isDarkMode()) ThemeController.MOON_SYMBOL else ThemeController.SUN_SYMBOL
        // Farbe über den THEMA-übersteuerten Kontext (cfgCtx) auflösen, NICHT baseContext:
        // sonst gilt die System-Night-Farbe (weiß) trotz Light-Override → weiß auf weiß
        themeBtn?.setTextColor(ContextCompat.getColor(cfgCtx, R.color.key_text))
        themeBtn?.typeface = Typeface.DEFAULT_BOLD
        themeBtn?.textSize = 14f
        // Theme-Verlauf + Farb-Overrides (aus den Theme-Einstellungen) anwenden
        ThemeApplier.apply(
            baseContext.getSharedPreferences(com.piotv.keytab.Prefs.FILE, MODE_PRIVATE),
            isDarkMode(), root, cfgCtx)
        // Optionale Zahlenreihe aus den Einstellungen
        root.findViewById<View>(R.id.num_row)?.visibility =
            if (baseContext.getSharedPreferences(com.piotv.keytab.Prefs.FILE, MODE_PRIVATE)
                    .getBoolean(com.piotv.keytab.Prefs.KEY_NUM_ROW, false)) View.VISIBLE else View.GONE
                                val fileManager = FileManagerPanel(this, root, ioExecutor, mainHandler) { commitText(it) }
        val editor = EditorPanel(this, root, ioExecutor, mainHandler) { commitToApp(it) }
        val terminal = TerminalPanel(this, root, mainHandler)
        val clipboard = ClipboardPanel(this, ioExecutor, mainHandler,
            onCommit = { commitToApp(it) },
            canAutoCapture = { isInputViewShown })
        fileManagerPanel = fileManager
        editorPanel = editor
        terminalPanel = terminal
        clipboardPanel = clipboard
        // Eingabe-Routing (Phase 2): Ziele App/Editor/Terminal hinter einem Router;
        // die editorActive/terminalActive-Verzweigungsketten entfallen damit.
        val router = InputRouter(
            AppInputTarget(
                connection = { currentInputConnection },
                sendKey = { sendDownUpKeyEvents(it) }),
            EditorInputTarget(editor),
            TerminalInputTarget(terminal))
        inputRouter = router
        // Aktive Sprache aus den Einstellungen übernehmen (wirkt beim nächsten Öffnen)
        activeLanguage = com.piotv.keytab.MainActivity.activeLanguage(baseContext)
        // Module einhängen
        predictionManager = WordPredictionManager(
            baseContext, ioExecutor, mainHandler, suggestionViews,
            inputOps = object : WordPredictionManager.InputOperations {
                override fun deleteBefore(count: Int) = router.deleteBefore(count)
                override fun deleteBeforeKeys(count: Int) = router.deleteBeforeKeys(count)
                override fun textBefore(count: Int): String = router.textBefore(count)
                override fun insert(text: String) = router.insert(text)
                override fun commitToApp(text: String) =
                    this@KeyTabImeService.commitToApp(text)
            }
        ).also { pm ->
            pm.setOnEngineReady { suggestionController.update() }
        }
        keyScaler = DynamicKeyScaler(baseLetters)
        // Skalierung darf über das Raster hinausragen: Clipping der gesamten
        // View-Hierarchie deaktivieren (sonst werden vergrößerte Tasten an den
        // Container-Grenzen abgeschnitten).
        disableClipping(root)

        tabController.setup(root)
        keyboardBinder = KeyboardBinder(this, LONG_PRESS_TIMEOUT, tabController, themeController, suggestionController)
        keyboardBinder.hook(root)
        keyboardBinder.applyLetterCase(root)
        // Suggestion-Views holen, Module starten (Engine lazy, Skaler aufbauen)
        suggestionController.setup(root, suggestionViews, activeLanguage)
        return root
    }

    private fun configFile(): java.io.File {
        val dir = baseContext.getExternalFilesDir(null) ?: baseContext.filesDir
        return java.io.File(dir, KeyTabConfig.FILE_NAME)
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        themeController.maybeRebuildForThemeChange()
        shiftController.resetForInput(autoCapitalize(attribute))
        // onStartInput kann VOR onCreateInputView feuern (IME-Start, bevor die
        // Tastatur das erste Mal angezeigt wird) → keyboardBinder ist dann noch
        // nicht initialisiert. Guard verhindert UninitializedPropertyAccessException.
        if (::keyboardBinder.isInitialized) {
            keyboardBinder.applyLetterCase(keyboardRoot)
            keyboardBinder.updateShiftVisual(keyboardRoot)
        }
    }

    override fun onStartInputView(editorInfo: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        // Theme-Änderungen (Farben/Alpha aus der Settings-Activity) übernehmen, auch
        // wenn dasselbe Textfeld weiterläuft – onStartInput feuert dann nicht erneut,
        // die Tastatur zeigte sonst die alten Farben (u. a. Alpha nicht angewendet).
        themeController.maybeRebuildForThemeChange()
    }

    private fun autoCapitalize(attribute: android.view.inputmethod.EditorInfo?): Boolean =
        CapsLogic.wantsCapitalization(attribute)

    override fun haptic() {
        keyboardRoot?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Dark-Mode-Override; ohne gesetzte Pref gilt der System-Modus. */
    override fun isDarkMode(): Boolean {
        val prefs = getSharedPreferences(com.piotv.keytab.Prefs.FILE, MODE_PRIVATE)
        if (prefs.contains(ThemePrefs.KEY_DARK)) return prefs.getBoolean(ThemePrefs.KEY_DARK, false)
        val mask = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    // ---------- KeyboardHost-Implementation (Phase 3+4) ----------

    /** Tastatur-View neu aufbauen (für Theme-Wechsel via [ThemeController]). */
    override fun rebuildInputView(): View = onCreateInputView()

    override fun isShifted(): Boolean = shiftController.isUpper()
    override fun isCapsLock(): Boolean = shiftController.state().capsLock

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

    override fun resetShiftForInput(autoCapitalize: Boolean) {
        shiftController.resetForInput(autoCapitalize)
    }

    override fun applyLetterCase(root: View?) {
        if (::keyboardBinder.isInitialized) keyboardBinder.applyLetterCase(root)
    }

    override fun updateShiftVisual(root: View?) {
        if (::keyboardBinder.isInitialized) keyboardBinder.updateShiftVisual(root)
    }

    // ---------- Text-Eingabe-Delegate (Phase 3, für KeyboardBinder) ----------

    override fun commitText(text: String) {
        haptic()
        // Aktive Autokorrektur (v0.9.1): Space nach unbekanntem Wort → Wort
        // ersetzen, wenn ein klarer Wörterbuch-Kandidat existiert (nur App-Felder;
        // Editor/Terminal buchen ihren Text selbst).
        if (text == " " && inputRouter?.isApp == true &&
            predictionManager?.autoCorrectBeforeSpace() == true
        ) {
            consumeSingleShift()
            suggestionController.update()
            return
        }
        inputRouter?.insert(text)
        // Wortvorhersage-Buchführung: Buchstaben sammeln, Abschluss lernen
        if (text.length == 1 && text[0].isLetter()) {
            predictionManager?.onCharacter(text)
        } else if (text == " " || text == "." || text == "\n") {
            predictionManager?.onWordCompleted()
        } else if ((predictionManager?.currentTypedWord?.isNotEmpty() == true) && !text[0].isLetter()) {
            predictionManager?.onWordCompleted()
        }
        consumeSingleShift()
        suggestionController.update()
    }

    override fun deleteLastWord() {
        haptic()
        inputRouter?.deleteWord()
        predictionManager?.reset()
        suggestionController.update()
    }

    override fun openSettings() {
        startActivity(Intent(this, com.piotv.keytab.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun disableClipping(v: View?) {
        var cur: View? = v
        while (cur != null) {
            if (cur is ViewGroup) {
                cur.clipChildren = false
                cur.clipToPadding = false
            }
            cur = cur.parent as? View
        }
    }

    /**
     * Clipboard-Einfügen IMMER direkt ins Zielfeld (InputConnection) – der Editor
     * (Notes-Tab) fängt die Eingabe nicht ab. Nur der Editor-Load befüllt den Editor.
     */
    private fun commitToApp(text: String) {
        haptic()
        runCatching { currentInputConnection?.commitText(text, 1) }
        consumeSingleShift()
    }

    override fun onDestroy() {
        predictionManager?.engine?.let {
            val raw = it.serializeUserDict()
            baseContext.getSharedPreferences(com.piotv.keytab.Prefs.FILE, Context.MODE_PRIVATE)
                .edit().putString(com.piotv.keytab.Prefs.KEY_USER_DICT, raw).apply()
        }
        letterPopup.dismiss()
        longPressHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
        ioExecutor.shutdownNow()
        releasePanels()
    }
}
