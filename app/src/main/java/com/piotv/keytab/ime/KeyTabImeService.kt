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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
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
class KeyTabImeService : InputMethodService() {

    private companion object {
        const val LONG_PRESS_TIMEOUT = 400L
        // Beschleunigendes Wort-Löschen: Startintervall, Faktor pro Repeat, Minimum
        const val WORD_DELETE_START_MS = 250L
        const val WORD_DELETE_ACCEL = 0.85f
        const val WORD_DELETE_MIN_MS = 30L
        const val SHIFT_DOUBLE_TAP_MS = 300L
        const val KEY_DARK = "dark_mode"
        // Monochrome (schwarz/weiß) Theme-Symbole – einheitlich farbig via key_text,
        // im Kontrast zu den bunten Emojis (🌙/☀)
        const val SUN_SYMBOL = "\u2600\uFE0E"  // ☀ (Text-Präsentation)
        const val MOON_SYMBOL = "\u263E\uFE0E" // ☾ (Text-Präsentation)
    }

    // ---------- Module ----------
    private val suggestionViews = arrayOfNulls<TextView>(3)
    private var keyScaler: DynamicKeyScaler? = null
    private var predictionManager: WordPredictionManager? = null

    private var shifted = false
    private var capsLock = false
    private var lastShiftTap = 0L
    internal var keyboardRoot: View? = null
    private var showSymbols = false
    // Long-Press-Popup: Zustand/Fenster liegen in der eigenen Klasse
    private val letterPopup = LetterPopup(this)
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var editorActive = false
    private var terminalActive = false
    private val baseLetters = mutableMapOf<Button, Char>()
    /** Gaming-Modus: momentan hervorgehobene Tasten + deren Originale-Background. */
    private val gamingHighlighted = mutableListOf<Pair<Button, android.graphics.drawable.Drawable>>()

    /** Eingabe-Routing (Phase 2): wohin Text fließt (App/Editor/Terminal). */
    private var inputRouter: InputRouter? = null

    private var fileManagerPanel: FileManagerPanel? = null
    private var editorPanel: EditorPanel? = null
    private var terminalPanel: TerminalPanel? = null
    private var clipboardPanel: ClipboardPanel? = null

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
        gamingHighlighted.clear()
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
        themeBtn?.text = if (isDarkMode()) MOON_SYMBOL else SUN_SYMBOL
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
            pm.setOnEngineReady { updateSuggestions() }
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
        setupTabs(root)
        hookKeyboardButtons(root)
        applyLetterCase(root)
        // Suggestion-Views holen, Module starten (Engine lazy, Skaler aufbauen)
        setupSuggestions(root)
        return root
    }

    // ===================== Wortvorhersage (Module) =====================

    private fun setupSuggestions(root: View) {
        suggestionViews[0] = root.findViewById(R.id.sug_1)
        suggestionViews[1] = root.findViewById(R.id.sug_2)
        suggestionViews[2] = root.findViewById(R.id.sug_3)
        for (i in 0..2) {
            suggestionViews[i]?.setOnClickListener {
                val word = it?.tag as? String ?: return@setOnClickListener
                applySuggestion(word)
            }
        }
        // Engine der aktiven Sprache laden (async, bei Wechsel: Reload)
        predictionManager?.loadEngine(activeLanguage)
        // Nachhalten der Tasten-Nachbarschaft für den dynamischen Skaler
        keyScaler?.rebuildNeighbors()
        // Platzhalter: Leiste von Anfang an sichtbar (fixer Platz → kein Auf-/Zupoppen)
        val enabled = baseContext.getSharedPreferences(com.piotv.keytab.Prefs.FILE, MODE_PRIVATE)
            .getBoolean(com.piotv.keytab.Prefs.KEY_SUGGESTIONS, true)
        root.findViewById<View>(R.id.suggestion_bar)?.visibility =
            if (enabled) View.VISIBLE else View.GONE
    }

    /** Vorschläge berechnen (Manager) + Tasten skalieren. */
    private fun updateSuggestions() {
        val bar = keyboardRoot?.findViewById<View>(R.id.suggestion_bar) ?: return
        val suggestionEnabled = baseContext.getSharedPreferences(com.piotv.keytab.Prefs.FILE, MODE_PRIVATE)
            .getBoolean(com.piotv.keytab.Prefs.KEY_SUGGESTIONS, true)
        predictionManager?.updateSuggestions(bar, suggestionEnabled)
        updateDynamicKeys()
    }

    /** Dynamische Tastengröße (Skaler-Modul). */
    private fun updateDynamicKeys() {
        val prefs = baseContext.getSharedPreferences(com.piotv.keytab.Prefs.FILE, MODE_PRIVATE)
        val enabled = prefs.getBoolean(
            com.piotv.keytab.Prefs.KEY_DYNAMIC_KEYS, true)
        val pm = predictionManager
        keyScaler?.apply(
            pm?.currentSuggestions ?: emptyList(),
            pm?.currentTypedWord?.length ?: 0,
            enabled
        )
        updateGamingKeys()
    }

    /**
     * Gaming-Modus: die wahrscheinlichste nächste Taste bekommt die Gaming-Farbe
     * (Pref [ThemePrefs.KEY_GAMING]); wenn das getippte Wort dem Top-Vorschlag
     * entspricht (Wahrscheinlichkeit erreicht), gibt es einen Puls-Effekt
     * ([ThemePrefs.KEY_GAMING_EFFECT]). Ohne Modus werden Highlights zurückgesetzt.
     */
    private fun updateGamingKeys() {
        val prefs = baseContext.getSharedPreferences(com.piotv.keytab.Prefs.FILE, MODE_PRIVATE)
        if (!ThemePrefs.gamingMode(prefs)) { restoreGamingKeys(); return }
        val pm = predictionManager
        val sugs = pm?.currentSuggestions ?: emptyList()
        val typed = pm?.currentTypedWord ?: ""
        val next = GamingLogic.nextChar(sugs, typed.length)
        restoreGamingKeys()
        if (next != null) {
            val gamingColor = ThemePrefs.getColor(prefs, isDarkMode(), ThemePrefs.KIND_GAMING,
                ThemePrefs.defaultColor(baseContext, isDarkMode(), ThemePrefs.KIND_GAMING))
            val dip = resources.displayMetrics.density
            for ((btn, c) in baseLetters) {
                if (c.lowercaseChar() == next) {
                    gamingHighlighted.add(btn to btn.background)
                    btn.background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 8f * dip
                        setColor(gamingColor)
                    }
                }
            }
            if (ThemePrefs.gamingEffect(prefs) && GamingLogic.completed(sugs, typed)) {
                gamingCompletionEffect(
                    baseLetters.filter { it.value.lowercaseChar() == next }.keys.toList(),
                    gamingColor)
            }
        }
    }

    /** Gaming-Highlights zurücksetzen (Original-Backgrounds wiederherstellen). */
    private fun restoreGamingKeys() {
        for ((btn, bg) in gamingHighlighted) {
            if (btn.isAttachedToWindow) btn.background = bg
        }
        gamingHighlighted.clear()
    }

    /** Zufriedenstellender Effekt: Farblitz + Scale-Puls + Haptik. */
    private fun gamingCompletionEffect(buttons: List<Button>, color: Int) {
        if (buttons.isEmpty()) return
        haptic()
        val dip = resources.displayMetrics.density
        for (b in buttons) {
            val saved = gamingHighlighted.firstOrNull { it.first === b }?.second ?: b.background
            val flash = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8f * dip
                setColor(android.graphics.Color.argb(230,
                    android.graphics.Color.red(color), android.graphics.Color.green(color),
                    android.graphics.Color.blue(color)))
            }
            b.background = flash
            b.animate().scaleX(1.3f).scaleY(1.3f).setDuration(130).withEndAction {
                b.animate().scaleX(1f).scaleY(1f).setDuration(170).start()
                b.background = saved
            }.start()
        }
    }

// keyNeighborLetters logic moved to DynamicKeyScaler

    /** Pfad der Konfigurationsdatei (externes Files-Dir; dort direkt editierbar). */
    private fun configFile(): java.io.File {
        val dir = baseContext.getExternalFilesDir(null) ?: baseContext.filesDir
        return java.io.File(dir, KeyTabConfig.FILE_NAME)
    }

    /** Vorschlag übernehmen (delegiert an Manager). */
    private fun applySuggestion(word: String) {
        haptic()
        predictionManager?.applySuggestion(word)
        if (shifted && !capsLock) {
            shifted = false
            updateShiftVisual(keyboardRoot)
            applyLetterCase(keyboardRoot)
        }
        updateSuggestions()
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        maybeRebuildForThemeChange()
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
        maybeRebuildForThemeChange()
    }

    /**
     * Tastatur neu aufbauen, wenn die Theme-Einstellungsseite Änderungen gemacht
     * hat (Theme-Version erhöht). Ohne Änderung passiert nichts (0.9.4-Look
     * bleibt unverändert erhalten).
     */
    private var appliedThemeVersion = 0
    private fun maybeRebuildForThemeChange() {
        // Nur wenn die Tastatur schon aufgebaut ist: Beim allerersten Öffnen ist
        // keyboardRoot noch null und onCreateInputView läuft ohnehin gleich an.
        if (keyboardRoot == null) return
        val prefs = baseContext.getSharedPreferences(com.piotv.keytab.Prefs.FILE, MODE_PRIVATE)
        val version = ThemePrefs.themeVersion(prefs)
        if (version == appliedThemeVersion) return
        appliedThemeVersion = version
        // Theme-Version hat sich geändert → Tastatur mit übersteuertem Theme neu
        // aufbauen (Trailing-Text korrekt beim neuen wirkenden Modus).
        val newRoot = onCreateInputView()
        newRoot.findViewById<Button>(R.id.key_theme)?.text =
            if (isDarkMode()) MOON_SYMBOL else SUN_SYMBOL
        setInputView(newRoot)
    }

    /**
     * Auto-Caps: bei Textfeldern mit CAP_SENTENCES-Flag bzw. initialCapsMode startet
     * die Tastatur in Shift. Passwort-Felder und Nicht-Text (Zahlen etc.) nie.
     */
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

    private fun haptic() {
        keyboardRoot?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Dark-Mode-Override; ohne gesetzte Pref gilt der System-Modus. */
    internal fun isDarkMode(): Boolean {
        val prefs = getSharedPreferences(com.piotv.keytab.Prefs.FILE, MODE_PRIVATE)
        if (prefs.contains(KEY_DARK)) return prefs.getBoolean(KEY_DARK, false)
        val mask = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun toggleDarkMode() {
        val prefs = getSharedPreferences(com.piotv.keytab.Prefs.FILE, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK, !isDarkMode()).apply()
        // Input-View mit neuem Theme neu aufbauen; Icon passend setzen
        val newRoot = onCreateInputView()
        // Icon spiegeln den NEUEN Zustand: Dark aktiv = ☾, Light aktiv = ☀
        newRoot.findViewById<Button>(R.id.key_theme)?.text = if (isDarkMode()) MOON_SYMBOL else SUN_SYMBOL
        setInputView(newRoot)
    }

    /**
     * Mond/Sonne-Taste: Tippen = Dark/Light umschalten (wie zuvor),
     * Long-Press = Theme-Einstellungen öffnen.
     */
    private fun setupThemeButton(btn: Button) {
        var pendingLongPress: Runnable? = null
        var longPressFired = false
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressFired = false
                    btn.isPressed = true
                    pendingLongPress = Runnable {
                        longPressFired = true
                        haptic()
                        showThemeSettings(btn)
                    }
                    longPressHandler.postDelayed(pendingLongPress!!, LONG_PRESS_TIMEOUT)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    btn.isPressed = false
                    pendingLongPress?.let { longPressHandler.removeCallbacks(it) }
                    if (!longPressFired) {
                        letterPopup.dismiss()
                        haptic()
                        toggleDarkMode()
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

    /** Theme-Einstellungs-Seite (2. Einstellungsseite der App) öffnen. */
    fun showThemeSettings(anchor: View) {
        startActivity(Intent(this, com.piotv.keytab.ThemeSettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun updateShiftVisual(root: View?) {
        val shift = root?.findViewById<Button>(R.id.key_shift) ?: return
        shift.alpha = if (shifted || capsLock) 1f else 0.6f
        shift.setTypeface(null, if (capsLock) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun setupTabs(root: View) {
        val tabs = root.findViewById<TabLayout>(R.id.ime_tabs) ?: return
        // Beschriftungen explizit setzen (TabItem-Texte können beim
        // Inflaten mit eigenem LayoutInflater verloren gehen)
        // Tab 0=abc, 1=Notes (Editor + Ablage), 2=Files, 3=Terminal
        tabs.getTabAt(0)?.text = getString(R.string.ime_tab_letters)
        tabs.getTabAt(1)?.text = getString(R.string.ime_tab_editor)
        tabs.getTabAt(2)?.text = getString(R.string.ime_tab_files)
        tabs.getTabAt(3)?.text = getString(R.string.ime_tab_term_short)
        val kb = root.findViewById<View>(R.id.kb_panel) ?: return
        val sym = root.findViewById<View>(R.id.sym_panel) ?: return
        val fm = root.findViewById<View>(R.id.file_panel) ?: return
        val ed = root.findViewById<View>(R.id.editor_panel) ?: return
        val term = root.findViewById<View>(R.id.term_panel) ?: return
        val bottom = root.findViewById<View>(R.id.bottom_row) ?: return
        // Terminal-Tab ist optional (Einstellungen-App): aus -> Tab entfernen
        val termEnabled = baseContext.getSharedPreferences(com.piotv.keytab.Prefs.FILE, MODE_PRIVATE)
            .getBoolean(com.piotv.keytab.Prefs.KEY_TERM_TAB, true)
        if (!termEnabled) {
            tabs.getTabAt(3)?.let { tabs.removeTab(it) }
            term.visibility = View.GONE
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                letterPopup.dismiss()
                val pos = tab.position
                // Tab 0=abc, 1=Notes, 2=Files, 3=Terminal
                // Notes/Terminal zeigen die Tastatur + Eingabezeile über der Tastatur
                val keyboardVisible = pos == 0 || pos == 1 || pos == 3
                editorActive = pos == 1
                terminalActive = pos == 3
                // Eingabe-Routing: aktives Ziel an den Tab koppeln
                inputRouter?.kind = when (pos) {
                    1 -> InputKind.EDITOR
                    3 -> InputKind.TERMINAL
                    else -> InputKind.APP
                }
                kb.visibility = if (keyboardVisible && !showSymbols) View.VISIBLE else View.GONE
                sym.visibility = if (keyboardVisible && showSymbols) View.VISIBLE else View.GONE
                ed.visibility = if (pos == 1) View.VISIBLE else View.GONE
                term.visibility = if (pos == 3) View.VISIBLE else View.GONE
                fm.visibility = if (pos == 2) View.VISIBLE else View.GONE
                // In ALLEN Tabs die ENTER-Taste erreichbar lassen – mit konstanter Größe und
                // Position. Dafür werden die übrigen Tasten auf INVISIBLE (Platz bleibt)
                // statt GONE gesetzt, damit Enter rechtsbündig und identisch bleibt.
                bottom.visibility = View.VISIBLE
                root.findViewById<View>(R.id.key_toggle)?.visibility =
                    if (keyboardVisible) View.VISIBLE else View.INVISIBLE
                root.findViewById<View>(R.id.key_tab)?.visibility =
                    if (keyboardVisible) View.VISIBLE else View.INVISIBLE
                root.findViewById<View>(R.id.key_space)?.visibility =
                    if (keyboardVisible) View.VISIBLE else View.INVISIBLE
                root.findViewById<View>(R.id.key_dot)?.visibility =
                    if (keyboardVisible) View.VISIBLE else View.INVISIBLE
                if (pos == 2) {
                    // Files-Tab genauso hoch wie Notes-Tab: Das Datei-Panel nimmt die
                    // Höhe von Editor-Panel + Buchstaben-Panel ein (gemessen, nicht
                    // hartkodiert) → gleiche Gesamthöhe beim Tab-Wechsel.
                    val h = PanelHeights.filesPanelHeight(ed, kb,
                        root.resources.displayMetrics.widthPixels)
                    if (h > 0 && fm.layoutParams.height != h) {
                        fm.layoutParams = fm.layoutParams.apply { height = h }
                    }
                    fileManagerPanel?.show()
                }
                if (pos == 1) clipboardPanel?.onSelected()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        kb.visibility = View.VISIBLE
        sym.visibility = View.GONE
        ed.visibility = View.GONE
        fm.visibility = View.GONE
        term.visibility = View.GONE
        bottom.visibility = View.VISIBLE
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
                    toggleSymbols(root)
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
                btn.id == R.id.key_tab -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    haptic()
                    inputRouter?.insert("\t")
                }
                btn.id == R.id.key_dot -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    commitText(".")
                }
                btn.id == R.id.key_theme -> setupThemeButton(btn)
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
                    updateSuggestions()
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

    private fun toggleSymbols(root: View) {
        showSymbols = !showSymbols
        root.findViewById<View>(R.id.kb_panel)?.visibility =
            if (showSymbols) View.GONE else View.VISIBLE
        root.findViewById<View>(R.id.sym_panel)?.visibility =
            if (showSymbols) View.VISIBLE else View.GONE
        root.findViewById<Button>(R.id.key_toggle)?.text =
            if (showSymbols) getString(R.string.key_toggle_letters) else "?123"
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
        updateSuggestions()
    }

    private fun commitText(text: String) {
        haptic()
        // Aktive Autokorrektur (v0.9.1): Space nach unbekanntem Wort → Wort
        // ersetzen, wenn ein klarer Wörterbuch-Kandidat existiert (nur App-Felder;
        // Editor/Terminal buchen ihren Text selbst).
        if (text == " " && inputRouter?.isApp == true &&
            predictionManager?.autoCorrectBeforeSpace() == true
        ) {
            if (shifted && !capsLock) {
                shifted = false
                updateShiftVisual(keyboardRoot)
                applyLetterCase(keyboardRoot)
            }
            updateSuggestions()
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
        if (shifted && !capsLock) {
            shifted = false
            updateShiftVisual(keyboardRoot)
            applyLetterCase(keyboardRoot)
        }
        updateSuggestions()
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
        if (shifted && !capsLock) {
            shifted = false
            updateShiftVisual(keyboardRoot)
            applyLetterCase(keyboardRoot)
        }
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
