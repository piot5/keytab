package com.piotv.keytab.ime

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan

import android.view.ContextThemeWrapper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.piotv.keytab.MainActivity
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

    /** KeyboardHost: Basis-Kontext für Prefs/Resources/Assets (gleiche Instanz wie baseContext). */
    override val context: Context get() = baseContext

    private var shifted = false
    private var capsLock = false
    private var lastShiftTap = 0L
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
    private val letterExtras: Map<Char, List<String>>
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
        val editor = EditorPanel(this, root, ioExecutor, mainHandler)
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

        // 📋-Button öffnet den Clipboard-Picker; gewählter Eintrag → Editorfeld einfügen
        editorPanel?.setClipboardPicker {
            clipboardPanel?.showPicker(keyboardRoot?.windowToken) {
                editorPanel?.insert(it) ?: commitToApp(it)
            }
        }
        tabController.setup(root)
        hookKeyboardButtons(root)
        applyLetterCase(root)
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
        capsLock = false
        shifted = autoCapitalize(attribute)
        applyLetterCase(keyboardRoot)
        updateShiftVisual(keyboardRoot)
    }

    override fun onStartInputView(editorInfo: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        // Theme-Änderungen (Farben/Alpha aus der Settings-Activity) übernehmen, auch
        // wenn dasselbe Textfeld weiterläuft – onStartInput feuert dann nicht erneut,
        // die Tastatur zeigte sonst die alten Farben (u. a. Alpha nicht angewendet).
        themeController.maybeRebuildForThemeChange()
    }

    private fun autoCapitalize(attribute: android.view.inputmethod.EditorInfo?): Boolean {
        attribute ?: return false
        val inputType = attribute.inputType
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        ) return false
        return inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 ||
            attribute.initialCapsMode != 0
    }

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

    // ---------- KeyboardHost-Implementation (Phase 4) ----------

    /** Tastatur-View neu aufbauen (für Theme-Wechsel via [ThemeController]). */
    override fun rebuildInputView(): View = onCreateInputView()

    override fun isShifted(): Boolean = shifted
    override fun isCapsLock(): Boolean = capsLock

    /** Einzelne Shift-Aktivierung zurücksetzen (CapsLock bleibt) + View aktualisieren. */
    override fun consumeSingleShift() {
        if (shifted && !capsLock) {
            shifted = false
            updateShiftVisual(keyboardRoot)
            applyLetterCase(keyboardRoot)
        }
    }

    private fun updateShiftVisual(root: View?) {
        val shift = root?.findViewById<Button>(R.id.key_shift) ?: return
        shift.alpha = if (shifted || capsLock) 1f else 0.6f
        shift.setTypeface(null, if (capsLock) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun hookKeyboardButtons(root: View) {
        ThemeApplier.forEachView(root) { v ->
            val btn = v as? Button ?: return@forEachView
            when {
                btn.tag == "letter" -> setupLetterButton(btn)
                btn.tag == "sym" -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    commitText(btn.text.toString())
                }
                btn.id == R.id.key_toggle -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    tabController.toggleSymbols(root)
                }
                btn.id == R.id.key_shift -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    haptic()
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastShiftTap <= SHIFT_DOUBLE_TAP_MS) {
                        // Doppel-Tipp = Caps Lock
                        capsLock = true
                        shifted = true
                        lastShiftTap = 0L
                    } else if (capsLock) {
                        // einzelner Tipp verlässt Caps Lock
                        capsLock = false
                        shifted = false
                        lastShiftTap = now
                    } else {
                        shifted = !shifted
                        lastShiftTap = now
                    }
                    updateShiftVisual(root)
                    applyLetterCase(root)
                }
                // Del-Taste (⌫): Touch-Listener für Einzellöschung + Long-Press
                // Wort-Löschung + Auto-Repeat. Wurde in der Phase-2-Refactorung
                // versehentlich aus der when-Anweisung entfernt → Taste tot.
                btn.id == R.id.key_del -> setupDelButton(btn)
                btn.id == R.id.key_tab -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    haptic()
                    inputRouter?.insert("\t")
                }
                btn.id == R.id.key_dot -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    commitText(".")
                }
                btn.id == R.id.key_theme -> themeController.setupButton(btn)
                btn.id == R.id.key_settings -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    haptic()
                    startActivity(Intent(this, com.piotv.keytab.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                btn.id == R.id.key_enter -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    haptic()
                    inputRouter?.onEnter()
                }
                btn.id == R.id.key_space -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    commitText(" ")
                }
            }
        }
    }

    private fun setupLetterButton(btn: Button) {
        baseLetters[btn] = btn.text?.toString()?.firstOrNull() ?: ' '
        var pendingLongPress: Runnable? = null
        var longPressFired = false
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressFired = false
                    btn.isPressed = true
                    pendingLongPress = Runnable {
                        longPressFired = true
                        showLetterExtras(btn)
                    }
                    longPressHandler.postDelayed(pendingLongPress!!, LONG_PRESS_TIMEOUT)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (longPressFired) letterPopup.highlightCellUnder(event) { haptic() }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    btn.isPressed = false
                    pendingLongPress?.let { longPressHandler.removeCallbacks(it) }
                    if (longPressFired) {
                        // Drag-Auswahl: markierte Zelle committen, sonst nichts
                        val picked = letterPopup.pickedChar()
                        letterPopup.dismiss()
                        if (picked != null) commitText(picked.toString())
                    } else {
                        commitText(tapLetter(btn))
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    btn.isPressed = false
                    pendingLongPress?.let { longPressHandler.removeCallbacks(it) }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupDelButton(btn: Button) {
        var pendingLongPress: Runnable? = null
        var repeater: Runnable? = null
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    btn.isPressed = true
                    haptic()
                    inputRouter?.deleteBackspace()
                    predictionManager?.deleteLast()
                    suggestionController.update()
                    pendingLongPress = Runnable {
                        deleteLastWord()
                        // Beschleunigend: Intervall wird pro Repeat kleiner, bis Minimum
                        var interval = WORD_DELETE_START_MS
                        repeater = object : Runnable {
                            override fun run() {
                                deleteLastWord()
                                interval = (interval * WORD_DELETE_ACCEL).toLong().coerceAtLeast(WORD_DELETE_MIN_MS)
                                longPressHandler.postDelayed(this, interval)
                            }
                        }
                        longPressHandler.postDelayed(repeater!!, interval)
                    }
                    longPressHandler.postDelayed(pendingLongPress!!, LONG_PRESS_TIMEOUT)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    btn.isPressed = false
                    pendingLongPress?.let { longPressHandler.removeCallbacks(it) }
                    repeater?.let { longPressHandler.removeCallbacks(it) }
                    if (event.action == MotionEvent.ACTION_UP) {
                        // falls Wort-Löschung schon lief, nichts mehr tun; kurzer Tap = einzelnes DEL (oben schon gesendet)
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Hauptbuchstabe einer Buchstaben-Taste für den Tap (Shift/Caps beachten).
     * WICHTIG: nicht btn.text lesen — dort stehen inzwischen auch die
     * Sonderzeichen-Hinweise an den Rändern (applyLetterCase).
     */
    private fun tapLetter(btn: Button): String {
        val base = baseLetters[btn] ?: btn.text?.toString()?.firstOrNull() ?: return ""
        val upper = shifted || capsLock
        val ch = when {
            upper && base == 'ß' -> 'ẞ'
            upper -> base.uppercaseChar()
            else -> base.lowercaseChar()
        }
        return ch.toString()
    }

    private fun showLetterExtras(anchor: Button) {
        // Basis IMMER aus baseLetters (nicht btn.text — dort stehen inzwischen
        // auch die Sonderzeichen-Hinweise an den Rändern)
        val base = baseLetters[anchor] ?: return
        val upper = shifted || capsLock
        val showExtras = letterExtras[if (upper) base.uppercaseChar() else base]
            ?: letterExtras[base]
            ?: emptyList()
        if (showExtras.isEmpty()) return
        letterPopup.show(anchor, showExtras) { ch -> commitText(ch.toString()) }
    }
    private fun applyLetterCase(view: View?) {
        if (view == null) return
        val secondary = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)
        ThemeApplier.forEachView(view) { v ->
            val btn = v as? Button ?: return@forEachView
            if (btn.tag != "letter") return@forEachView
            val base = baseLetters[btn]
                ?: btn.text?.toString()?.firstOrNull()
                ?: return@forEachView
            val upper = shifted || capsLock
            // ß hat kein echtes Großbuchstaben per uppercaseChar → ẞ als Sonderfall
            val letter = when {
                upper && base == 'ß' -> 'ẞ'
                upper -> base.uppercaseChar()
                else -> base.lowercaseChar()
            }
            val extras = when {
                upper && base == 'ß' -> emptyList()
                // Interpunktion-Extras gibt es nur am lowercase-Key → Shift fällt darauf zurück
                upper -> (letterExtras[base.uppercaseChar()] ?: letterExtras[base]).orEmpty()
                else -> letterExtras[base].orEmpty()
            }
            val sb = SpannableStringBuilder(letter.toString())
            // Hauptbuchstabe: etwas kleiner + leicht angehoben
            sb.setSpan(RelativeSizeSpan(0.85f), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(LiftSpan(-0.2f), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (extras.isNotEmpty()) {
                // FlorisBoard-Style: Hinweis-Zeichen klein + abgedunkelt deutlich
                // rechts-UNTEN (LiftSpan senkt die Basislinie stark ab)
                val start = sb.length
                sb.append("\u00A0" + extras.first()) // geschütztes Leerzeichen: Trennung zum Hauptzeichen
                sb.setSpan(RelativeSizeSpan(0.5f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(secondary), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(LiftSpan(0.55f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            btn.text = sb
        }
    }

    private fun deleteLastWord() {
        haptic()
        inputRouter?.deleteWord()
        predictionManager?.reset()
        suggestionController.update()
    }

    private fun commitText(text: String) {
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

    /**
     * Deaktiviert Clipping für [v] und alle Eltern: vergrößerte/bewegte Tasten
     * dürfen über die Raster- und Containergrenzen hinausragen.
     */
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
