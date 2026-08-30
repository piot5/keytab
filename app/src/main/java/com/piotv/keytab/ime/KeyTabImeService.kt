package com.piotv.keytab.ime

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
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
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.GridLayout
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * KeyTab-IME – schlanker Keyboard-Core (Tasten, Shift, Symbole, Long-Press-Popups).
 * Die Sub-Features leben in eigenen Klassen:
 * - [FileManagerPanel]  – Tab-Dateimanager
 * - [EditorPanel]       – interner Editor mit Speichern/Laden
 * - [ClipboardPanel]    – Ablage (Clipboard-Historie)
 * - [TextEditLogic]     – reine, testbare Textlogik
 */
class KeyTabImeService : InputMethodService() {

    private companion object {
        const val LONG_PRESS_TIMEOUT = 400L
        const val WORD_DELETE_REPEAT_MS = 250L
        const val SHIFT_DOUBLE_TAP_MS = 300L
        const val PREFS = "keytab_prefs"
        const val KEY_DARK = "dark_mode"
    }

    private var shifted = false
    private var capsLock = false
    private var lastShiftTap = 0L
    private var keyboardRoot: View? = null
    private var showSymbols = false
    private var activePopup: PopupWindow? = null
    // Drag-Auswahl im Popup: Zellen + aktuell hervorgehobene Zelle
    private var popupCells: List<TextView> = emptyList()
    private var popupHighlighted: TextView? = null
    private var popupHighlightBg: android.graphics.drawable.Drawable? = null
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var editorActive = false
    private val baseLetters = mutableMapOf<Button, Char>()

    private var fileManagerPanel: FileManagerPanel? = null
    private var editorPanel: EditorPanel? = null
    private var clipboardPanel: ClipboardPanel? = null

    private val letterExtras = mapOf(
        // === EXAKT wie FlorisBoard popupMappings/de.json (KeyVariation.ALL) ===
        // Reihenfolge = akzent-Priorität, Hauptzeichen (main) ist der erste Eintrag.
        'a' to listOf("ä", "æ", "ã", "å", "ā", "â", "à", "á"),
        'A' to listOf("Ä", "Æ", "Ã", "Å", "Ā", "Â", "À", "Á"),
        'c' to listOf("ç"),
        'C' to listOf("Ç"),
        'e' to listOf("é", "ē", "ê", "è", "ë"),
        'E' to listOf("É", "Ē", "Ê", "È", "Ë"),
        'i' to listOf("í", "ì", "ï", "î", "ī"),
        'I' to listOf("Í", "Ì", "Ï", "Î", "Ī"),
        'n' to listOf("ñ", "ń"),
        'N' to listOf("Ñ", "Ń"),
        'o' to listOf("ö", "ō", "ø", "õ", "œ", "ó", "ò", "ô"),
        'O' to listOf("Ö", "Ō", "Ø", "Õ", "Œ", "Ó", "Ò", "Ô"),
        // case_selector: ß<->ẞ (FlorisBoard verwendet case_selector)
        's' to listOf("ß", "š", "ś"),
        'S' to listOf("ẞ", "Š", "Ś"),
        'u' to listOf("ü", "ū", "ù", "û", "ú"),
        'U' to listOf("Ü", "Ū", "Ù", "Û", "Ú"),
        // Komma-Taste (rechts unten): FlorisBoard "~right" mit & % + " - : ' @ ; / ( ) # ! ?
        ',' to listOf("&", "%", "+", "\"", "-", ":", "'", "@", ";", "/", "(", ")", "#", "!", "?"),
        // Interpunktion auf Buchstaben-Tasten (Long-Press) – nur auf Tasten ohne Akzent-Konflikt.
        // Erster Eintrag = Hinweis-Zeichen rechts unten auf der Taste.
        'r' to listOf(".", ","),
        't' to listOf("?", "!"),
        'z' to listOf("!"),
        'p' to listOf("/", "\\", "|"),
        'f' to listOf("@", "#", "&"),
        'g' to listOf("(", "[", "{"),
        'h' to listOf(")", "]", "}"),
        'j' to listOf(":", ";"),
        'k' to listOf(";", ":"),
        'l' to listOf(",", "\"", "'"),
        'm' to listOf("&", "%", "*"),
        'v' to listOf("\""),
        'b' to listOf("'"),
        'x' to listOf("+", "-", "="),
        'w' to listOf("-", "_"),
        'y' to listOf("#", "$", "€"),
        'd' to listOf("_")
    )

    override fun onCreateInputView(): View {
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
        // Theme-Umschalter-Icon passend zum aktiven Modus
        root.findViewById<Button>(R.id.key_theme)?.text = if (isDarkMode()) "☀" else "🌙"
        // Optionale Zahlenreihe aus den Einstellungen
        root.findViewById<View>(R.id.num_row)?.visibility =
            if (baseContext.getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getBoolean(com.piotv.keytab.MainActivity.KEY_NUM_ROW, false)) View.VISIBLE else View.GONE
        fileManagerPanel = FileManagerPanel(this, root, ioExecutor, mainHandler) { commitText(it) }
        editorPanel = EditorPanel(this, root, ioExecutor, mainHandler)
        clipboardPanel = ClipboardPanel(this, root, ioExecutor, mainHandler,
            onCommit = { commitText(it) },
            canAutoCapture = { isInputViewShown })
        setupTabs(root)
        hookKeyboardButtons(root)
        applyLetterCase(root)
        return root
    }

    override fun onDestroy() {
        super.onDestroy()
        activePopup?.dismiss()
        longPressHandler.removeCallbacksAndMessages(null)
        ioExecutor.shutdown()
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
        newRoot.findViewById<Button>(R.id.key_theme)?.text = if (isDarkMode()) "☀" else "🌙"
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
        tabs.getTabAt(0)?.text = getString(R.string.ime_tab_letters)
        tabs.getTabAt(1)?.text = getString(R.string.ime_tab_editor)
        tabs.getTabAt(2)?.text = getString(R.string.ime_tab_files)
        tabs.getTabAt(3)?.text = getString(R.string.ime_tab_clip)
        val kb = root.findViewById<View>(R.id.kb_panel) ?: return
        val sym = root.findViewById<View>(R.id.sym_panel) ?: return
        val fm = root.findViewById<View>(R.id.file_panel) ?: return
        val ed = root.findViewById<View>(R.id.editor_panel) ?: return
        val cp = root.findViewById<View>(R.id.clip_panel) ?: return
        val bottom = root.findViewById<View>(R.id.bottom_row) ?: return
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                activePopup?.dismiss()
                val pos = tab.position
                // Tab 0=abc, 1=Editor (Tastatur bleibt sichtbar), 2=Files, 3=Ablage
                val keyboardVisible = pos == 0 || pos == 1
                editorActive = pos == 1
                kb.visibility = if (keyboardVisible && !showSymbols) View.VISIBLE else View.GONE
                sym.visibility = if (keyboardVisible && showSymbols) View.VISIBLE else View.GONE
                ed.visibility = if (pos == 1) View.VISIBLE else View.GONE
                fm.visibility = if (pos == 2) View.VISIBLE else View.GONE
                cp.visibility = if (pos == 3) View.VISIBLE else View.GONE
                bottom.visibility = if (keyboardVisible) View.VISIBLE else View.GONE
                if (pos == 2) fileManagerPanel?.show()
                if (pos == 3) clipboardPanel?.onSelected()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        kb.visibility = View.VISIBLE
        sym.visibility = View.GONE
        ed.visibility = View.GONE
        fm.visibility = View.GONE
        cp.visibility = View.GONE
        bottom.visibility = View.VISIBLE
    }

    private fun hookKeyboardButtons(root: View) {
        forEachView(root) { v ->
            val btn = v as? Button ?: return@forEachView
            when {
                btn.tag == "letter" -> setupLetterButton(btn)
                btn.tag == "sym" -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    commitText(btn.text.toString())
                }
                btn.id == R.id.key_toggle -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    toggleSymbols(root)
                }
                btn.id == R.id.key_shift -> btn.setOnClickListener {
                    activePopup?.dismiss()
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
                    activePopup?.dismiss()
                    haptic()
                    if (editorActive) editorPanel?.insert("\t")
                    else sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
                }
                btn.id == R.id.key_dot -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    commitText(".")
                }
                btn.id == R.id.key_theme -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    haptic()
                    toggleDarkMode()
                }
                btn.id == R.id.key_settings -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    haptic()
                    startActivity(Intent(this, com.piotv.keytab.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                btn.id == R.id.key_enter -> btn.setOnClickListener {
                    activePopup?.dismiss()
                    haptic()
                    if (editorActive) editorPanel?.insert("\n")
                    else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
                btn.id == R.id.key_space -> btn.setOnClickListener {
                    activePopup?.dismiss()
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
                    if (longPressFired) highlightCellUnder(event)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    btn.isPressed = false
                    pendingLongPress?.let { longPressHandler.removeCallbacks(it) }
                    if (longPressFired) {
                        // Drag-Auswahl: markierte Zelle committen, sonst nichts
                        val picked = popupHighlighted
                        activePopup?.dismiss()
                        if (picked != null) commitText(picked.text.toString())
                    } else {
                        commitText(btn.text.toString().first().toString())
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

    /** Hebt die Popup-Zelle unter dem Finger hervor (Drag-Auswahl, Gboard-Stil). */
    private fun highlightCellUnder(event: MotionEvent) {
        var found: TextView? = null
        for (cell in popupCells) {
            // Popup-Inhalt liegt in einem EIGENEN Fenster → Screen-Koordinaten verwenden
            // (getLocationInWindow wäre popup-relativ und würde nie matchen)
            val loc = IntArray(2)
            cell.getLocationOnScreen(loc)
            if (event.rawX >= loc[0] && event.rawX < loc[0] + cell.width &&
                event.rawY >= loc[1] && event.rawY < loc[1] + cell.height) {
                found = cell
                break
            }
        }
        if (found != popupHighlighted) {
            popupHighlighted?.background = null
            found?.background = popupHighlightBg
            popupHighlighted = found
            if (found != null) haptic()
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
                    else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                    pendingLongPress = Runnable {
                        deleteLastWord()
                        repeater = object : Runnable {
                            override fun run() {
                                deleteLastWord()
                                longPressHandler.postDelayed(this, WORD_DELETE_REPEAT_MS)
                            }
                        }
                        longPressHandler.postDelayed(repeater!!, WORD_DELETE_REPEAT_MS)
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

    @SuppressLint("InflateParams")
    private fun showLetterExtras(anchor: Button) {
        activePopup?.dismiss()
        val letter = anchor.text?.firstOrNull() ?: return
        val extras = letterExtras[letter] ?: letterExtras[letter.lowercaseChar()] ?: return

        val ctx = anchor.context
        // FlorisBoard-Farben: Popup-Fläche + hervorgehobener Fokus
        val popupBg = androidx.core.content.ContextCompat.getColor(this, R.color.popup_bg)
        val popupText = androidx.core.content.ContextCompat.getColor(this, R.color.popup_text)
        val focusBg = androidx.core.content.ContextCompat.getColor(this, R.color.popup_focus_bg)
        val focusBgDrawable = android.graphics.drawable.GradientDrawable().apply {
            setColor(focusBg)
            cornerRadius = 8f * resources.displayMetrics.density
        }
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(popupBg)
            setPadding(6, 6, 6, 6)
        }

        // Floris-Stil: Hauptzeichen als Header (groß, zentriert), Sonderzeichen darunter in Grid
        val base = baseLetters[anchor] ?: letter.lowercaseChar()
        val upper = shifted || capsLock
        val showBase = if (upper) base.uppercaseChar() else base
        val showExtras = letterExtras[if (upper) base.uppercaseChar() else base]
            ?: letterExtras[base]
            ?: emptyList()

        // Header: Hauptzeichen groß + fokussiert; tippbar = Hauptzeichen committen
        val header = TextView(ctx).apply {
            text = showBase.toString()
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(popupText)
            setPadding(16, 12, 16, 12)
            background = focusBgDrawable
            isClickable = true
            setOnClickListener {
                commitText(showBase.toString())
                activePopup?.dismiss()
            }
        }
        container.addView(header)

        // Trennlinie (1dp breit, vertikal halbtransparent grau) – NICHT match_parent breit,
        // sonst frisst sie den kompletten Platz und das Grid kollabiert auf 0
        val divider = View(ctx).apply {
            setBackgroundColor((popupText and 0x00FFFFFF) or 0x33000000)
            val dp = (1f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
            layoutParams = LinearLayout.LayoutParams(dp, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(4, 10, 4, 10)
            }
        }
        container.addView(divider)

        // Grid der Sonderzeichen (3 pro Zeile, klein + rechts-unten)
        val grid = GridLayout(ctx).apply {
            columnCount = 3
            orientation = GridLayout.HORIZONTAL
            setBackgroundColor(popupBg)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
            )
        }
        for (ch in showExtras) {
            val tv = TextView(ctx).apply {
                text = ch
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(popupText)
                // großzügige Zellen: gut drückbar + genug Platz für Drag-Auswahl
                setPadding(18, 14, 18, 14)
                setOnClickListener {
                    commitText(ch)
                    activePopup?.dismiss()
                }
            }
            grid.addView(tv)
        }
        container.addView(grid)

        val popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            // nicht fokusierbar: klaut dem Eingabefeld keinen Fokus;
            // transparenter Background aktiviert Dismiss bei Tap außerhalb
            isOutsideTouchable = true
            isFocusable = false
            // darf über den Rand des IME-Fensters hinausragen (sonst Abschneiden oben)
            isClippingEnabled = false
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        // Popup exakt über der angetippten Taste zentrieren.
        // showAtLocation erwartet FENSTER-Relative Koordinaten → getLocationInWindow
        // (Screen-Koordinaten würden das Popup unterhalb des IME-Fensters platzieren).
        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupW = container.measuredWidth
        val popupH = container.measuredHeight
        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val rootW = anchor.rootView.width
        val x = (location[0] + anchor.width / 2 - popupW / 2)
            .coerceIn(0, (rootW - popupW).coerceAtLeast(0))
        val y = (location[1] - popupH - 8).coerceAtLeast(0)
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        activePopup = popup
        // Drag-Auswahl: alle wählbaren Zellen registrieren (Header + Grid), Highlight zurücksetzen
        popupHighlightBg = focusBgDrawable
        popupHighlighted = null
        popupCells = buildList {
            add(header)
            for (i in 0 until grid.childCount) (grid.getChildAt(i) as? TextView)?.let { add(it) }
        }
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
        val ic = currentInputConnection ?: return
        val text = ic.getTextBeforeCursor(200, 0)?.toString() ?: ""
        val toDelete = TextEditLogic.wordDeleteCount(text, text.length)
        if (toDelete > 0) {
            ic.deleteSurroundingText(toDelete, 0)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun commitText(text: String) {
        haptic()
        if (editorActive) {
            editorPanel?.insert(text)
        } else {
            currentInputConnection?.commitText(text, 1)
        }
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
}
