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
import android.util.TypedValue
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
        const val PREFS = "keytab_prefs"
        const val KEY_DARK = "dark_mode"
        // Monochrome (schwarz/weiß) Theme-Symbole – einheitlich farbig via key_text,
        // im Kontrast zu den bunten Emojis (🌙/☀)
        const val SUN_SYMBOL = "\u2600\uFE0E"  // ☀ (Text-Präsentation)
        const val MOON_SYMBOL = "\u263E\uFE0E" // ☾ (Text-Präsentation)
        const val KEY_USER_DICT = "user_dict"
    }

    // ---------- Module ----------
    private val suggestionViews = arrayOfNulls<TextView>(3)
    private var keyScaler: DynamicKeyScaler? = null
    private var predictionManager: WordPredictionManager? = null

    private var shifted = false
    private var capsLock = false
    private var lastShiftTap = 0L
    private var keyboardRoot: View? = null
    private var showSymbols = false
    // Long-Press-Popup: Zustand/Fenster liegen in der eigenen Klasse
    private val letterPopup = LetterPopup(this)
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var editorActive = false
    private var terminalActive = false
    private val baseLetters = mutableMapOf<Button, Char>()

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
        // Optionale Zahlenreihe aus den Einstellungen
        root.findViewById<View>(R.id.num_row)?.visibility =
            if (baseContext.getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getBoolean(com.piotv.keytab.MainActivity.KEY_NUM_ROW, false)) View.VISIBLE else View.GONE
                                fileManagerPanel = FileManagerPanel(this, root, ioExecutor, mainHandler) { commitText(it) }
        editorPanel = EditorPanel(this, root, ioExecutor, mainHandler)
        terminalPanel = TerminalPanel(this, root, mainHandler)
        clipboardPanel = ClipboardPanel(this, ioExecutor, mainHandler,
            onCommit = { commitToApp(it) },
            canAutoCapture = { isInputViewShown })
        // Aktive Sprache aus den Einstellungen übernehmen (wirkt beim nächsten Öffnen)
        activeLanguage = com.piotv.keytab.MainActivity.activeLanguage(baseContext)
        // Module einhängen
        predictionManager = WordPredictionManager(
            baseContext, ioExecutor, mainHandler, suggestionViews,
            inputOps = object : WordPredictionManager.InputOperations {
                override fun deleteBefore(count: Int) {
                    if (editorActive) editorPanel?.deleteBefore(count)
                    else if (terminalActive) terminalPanel?.deleteBefore(count)
                    else currentInputConnection?.deleteSurroundingText(count, 0)
                }
                override fun deleteBeforeKeys(count: Int) {
                    if (editorActive) { editorPanel?.deleteBefore(count); return }
                    if (terminalActive) { terminalPanel?.deleteBefore(count); return }
                    // KEYCODE_DEL-Key-Events: funktioniert auch in Feldern, die
                    // deleteSurroundingText ignorieren (z. B. Termux, WebView)
                    repeat(count.coerceAtLeast(0)) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
                }
                override fun textBefore(count: Int): String {
                    if (editorActive) {
                        val ctx = editorPanel?.cursorContext() ?: return ""
                        val (t, cursor) = ctx
                        val start = (cursor - count).coerceAtLeast(0)
                        return t.substring(start, cursor).toString()
                    }
                    if (terminalActive) {
                        val ctx = terminalPanel?.cursorContext() ?: return ""
                        val (t, cursor) = ctx
                        val start = (cursor - count).coerceAtLeast(0)
                        return t.substring(start, cursor).toString()
                    }
                    return currentInputConnection?.getTextBeforeCursor(count, 0)?.toString() ?: ""
                }
                override fun insert(text: String) {
                    if (editorActive) editorPanel?.insert(text)
                    else if (terminalActive) terminalPanel?.insert(text)
                    else currentInputConnection?.commitText(text, 1)
                }
                override fun commitToApp(text: String) {
                    currentInputConnection?.commitText(text, 1)
                }
            }
        ).also { pm ->
            pm.setOnEngineReady { updateSuggestions() }
        }
        keyScaler = DynamicKeyScaler(baseLetters)
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
        val enabled = baseContext.getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(com.piotv.keytab.MainActivity.KEY_SUGGESTIONS, true)
        root.findViewById<View>(R.id.suggestion_bar)?.visibility =
            if (enabled) View.VISIBLE else View.GONE
    }

    /** Vorschläge berechnen (Manager) + Tasten skalieren. */
    private fun updateSuggestions() {
        val bar = keyboardRoot?.findViewById<View>(R.id.suggestion_bar) ?: return
        val suggestionEnabled = baseContext.getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(com.piotv.keytab.MainActivity.KEY_SUGGESTIONS, true)
        predictionManager?.updateSuggestions(bar, suggestionEnabled)
        updateDynamicKeys()
    }

    /** Dynamische Tastengröße (Skaler-Modul). */
    private fun updateDynamicKeys() {
        val enabled = baseContext.getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(com.piotv.keytab.MainActivity.KEY_DYNAMIC_KEYS, true)
        val pm = predictionManager
        keyScaler?.apply(pm?.currentSuggestions ?: emptyList(), pm?.currentTypedWord?.length ?: 0, enabled)
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
        capsLock = false
        shifted = autoCapitalize(attribute)
        applyLetterCase(keyboardRoot)
        updateShiftVisual(keyboardRoot)
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
    private fun isDarkMode(): Boolean {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.contains(KEY_DARK)) return prefs.getBoolean(KEY_DARK, false)
        val mask = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun toggleDarkMode() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK, !isDarkMode()).apply()
        // Input-View mit neuem Theme neu aufbauen; Icon passend setzen
        val newRoot = onCreateInputView()
        // Icon spiegeln den NEUEN Zustand: Dark aktiv = ☾, Light aktiv = ☀
        newRoot.findViewById<Button>(R.id.key_theme)?.text = if (isDarkMode()) MOON_SYMBOL else SUN_SYMBOL
        setInputView(newRoot)
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
        val termEnabled = baseContext.getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(com.piotv.keytab.MainActivity.KEY_TERM_TAB, true)
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
                if (pos == 2) fileManagerPanel?.show()
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
        forEachView(root) { v ->
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
                btn.id == R.id.key_del -> setupDelButton(btn)
                btn.id == R.id.key_tab -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    haptic()
                    if (editorActive) editorPanel?.insert("\t")
                    else if (terminalActive) terminalPanel?.insert("\t")
                    else sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
                }
                btn.id == R.id.key_dot -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    commitText(".")
                }
                btn.id == R.id.key_theme -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    haptic()
                    toggleDarkMode()
                }
                btn.id == R.id.key_settings -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    haptic()
                    startActivity(Intent(this, com.piotv.keytab.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                btn.id == R.id.key_enter -> btn.setOnClickListener {
                    letterPopup.dismiss()
                    haptic()
                    if (terminalActive) terminalPanel?.send()
                    else if (editorActive) editorPanel?.insert("\n")
                    else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
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
                    if (editorActive) editorPanel?.delete(word = false)
                    else if (terminalActive) terminalPanel?.delete(word = false)
                    else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
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

    /** Theme-Attribut-Farbe auflösen (funktioniert über Day/Night hinweg). */
    private fun themeColor(context: android.content.Context, attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) androidx.core.content.ContextCompat.getColor(context, tv.resourceId)
        else tv.data
    }

    private fun applyLetterCase(view: View?) {
        if (view == null) return
        val secondary = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)
        forEachView(view) { v ->
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
        if (editorActive) {
            editorPanel?.delete(word = true)
            return
        }
        if (terminalActive) {
            terminalPanel?.delete(word = true)
            return
        }
        val ic = currentInputConnection ?: return
        val text = ic.getTextBeforeCursor(200, 0)?.toString() ?: ""
        val toDelete = TextEditLogic.wordDeleteCount(text, text.length)
        if (toDelete > 0) {
            ic.deleteSurroundingText(toDelete, 0)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        predictionManager?.reset()
        updateSuggestions()
    }

    private fun commitText(text: String) {
        haptic()
        if (editorActive) {
            editorPanel?.insert(text)
        } else if (terminalActive) {
            terminalPanel?.insert(text)
        } else {
            currentInputConnection?.commitText(text, 1)
        }
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
     * Clipboard-Einfügen IMMER direkt ins Zielfeld (InputConnection) – der Editor
     * (Notes-Tab) fängt die Eingabe nicht ab. Nur der Editor-Load befüllt den Editor.
     */
    private fun commitToApp(text: String) {
        haptic()
        currentInputConnection?.commitText(text, 1)
        if (shifted && !capsLock) {
            shifted = false
            updateShiftVisual(keyboardRoot)
            applyLetterCase(keyboardRoot)
        }
    }

    private fun forEachView(root: View, action: (View) -> Unit) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) forEachView(root.getChildAt(i), action)
        } else {
            action(root)
        }
    }

    /**
     * Baseline-Versatz für Tasten-Label: positiver shift hebt an, negativer senkt ab
     * (Faktor relativ zur Schriftgröße). Wirkung über MetricAffectingSpan + DrawState.
     */
    private class LiftSpan(private val shift: Float) : android.text.style.MetricAffectingSpan() {
        private fun apply(tp: android.text.TextPaint) {
            tp.baselineShift += (tp.textSize * shift).toInt()
        }
        override fun updateMeasureState(tp: android.text.TextPaint) = apply(tp)
        override fun updateDrawState(tp: android.text.TextPaint) = apply(tp)
    }

    override fun onDestroy() {
        predictionManager?.engine?.let {
            val raw = it.serializeUserDict()
            baseContext.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .edit().putString(MainActivity.KEY_USER_DICT, raw).apply()
        }
        letterPopup.dismiss()
        longPressHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
        ioExecutor.shutdownNow()
        releasePanels()
    }
}
