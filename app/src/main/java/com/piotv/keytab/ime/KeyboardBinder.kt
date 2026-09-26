package com.piotv.keytab.ime

import android.content.Intent
import android.graphics.Typeface
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.core.content.ContextCompat
import com.piotv.keytab.R

/**
 * Tasten-Binder – verbindet alle Tasten des Tastatur-Views mit Event-Listenern.
 *
 * Refactoring (docs/REFACTORING_PLAN.md Phase 3): aus [KeyTabImeService] extrahiert.
 * Kapselt die Touch-/Long-Press-Logik aller Tasten:
 * - [hook]              – Haupteinstieg: alle Tasten gemäß Tag/ID binden
 * - [setupLetterButton] – Buchstaben-Taste mit Long-Press-Popup (Drag-Auswahl);
 *   die Touch-/Swipe-Logik liegt im [LetterButtonTouchHandler]
 * - [setupDelButton]    – Del-Taste mit Einzellöschung + Long-Press-Wort-Löschung +
 *   beschleunigendem Auto-Repeat (Zeitlogik im puren [RepeatScheduler])
 * - [tapLetter]         – Hauptbuchstabe unter Shift/Caps
 * - [showLetterExtras]  – Long-Press-Popup öffnen
 * - [applyLetterCase]   – Buchstaben-Text + Rand-Hinweise aktualisieren
 * - [updateShiftVisual] – Shift-Taste Alpha/Bold
 *
 * Verhalten bleibt bit-identisch („Umziehen statt Umschreiben").
 */
internal class KeyboardBinder(
    private val host: KeyboardInputHost,
    private val longPressTimeout: Long,
    private val tabController: TabController,
    private val themeController: ThemeController,
    private val suggestionController: SuggestionController,
    private val trailManager: TrailManager?,
    private val swipeManager: SwipeManager? = null
) {

    private companion object {
        // Beschleunigendes Wort-Löschen: Startintervall, Faktor pro Repeat, Minimum
        // (Werte + Intervall-Rechnung liegen in [WordDeleteRepeat]).
        const val WORD_DELETE_START_MS = WordDeleteRepeat.WORD_DELETE_START_MS
        const val WORD_DELETE_ACCEL = WordDeleteRepeat.WORD_DELETE_ACCEL
        const val WORD_DELETE_MIN_MS = WordDeleteRepeat.WORD_DELETE_MIN_MS
    }

    /** Alle Tasten des Roots gemäß Tag/ID an Listener binden. */
    fun hook(root: View) {
        ThemeApplier.forEachView(root) { v ->
            val btn = v as? Button ?: return@forEachView
            when {
                btn.tag == "letter" -> setupLetterButton(btn)
                btn.tag == "sym" -> btn.setOnClickListener {
                    host.letterPopup.dismiss()
                    host.commitText(btn.text.toString())
                }
                btn.id == R.id.key_toggle -> btn.setOnClickListener {
                    host.letterPopup.dismiss()
                    tabController.toggleSymbols(root)
                }
                btn.id == R.id.key_shift -> btn.setOnClickListener {
                    host.letterPopup.dismiss()
                    host.haptic()
                    host.tapShift(SystemClock.elapsedRealtime())
                    host.updateShiftVisual(root)
                    host.applyLetterCase(root)
                }
                // Del-Taste (⌫): Touch-Listener für Einzellöschung + Long-Press
                // Wort-Löschen + Auto-Repeat.
                btn.id == R.id.key_del -> setupDelButton(btn)
                btn.id == R.id.key_tab -> btn.setOnClickListener {
                    host.letterPopup.dismiss()
                    host.haptic()
                    host.inputRouter?.onTab()
                }
                btn.id == R.id.key_dot -> btn.setOnClickListener {
                    host.letterPopup.dismiss()
                    host.commitText(".")
                }
                btn.id == R.id.key_theme -> themeController.setupButton(btn)
                btn.id == R.id.key_settings -> btn.setOnClickListener {
                    host.letterPopup.dismiss()
                    host.haptic()
                    host.openSettings()
                }
                btn.id == R.id.key_enter -> btn.setOnClickListener {
                    host.letterPopup.dismiss()
                    host.haptic()
                    host.inputRouter?.onEnter()
                }
                btn.id == R.id.key_space -> btn.setOnClickListener {
                    host.letterPopup.dismiss()
                    host.commitText(" ")
                }
            }
        }
    }

    /** Letztes an den Trail gemeldetes Feld (EditorInfo) – Passwort-Schutz. */
    private var lastEditorInfo: android.view.inputmethod.EditorInfo? = null

    /**
     * Aktuelles Eingabefeld setzen. In Passwort-Feldern schaltet [TrailManager]
     * die Spur vollständig ab ([TrailLogic.isTrailAllowed]) – hier wird nur
     * durchgereicht und die Spur geleert.
     */
    fun setEditorInfo(info: android.view.inputmethod.EditorInfo?) {
        lastEditorInfo = info
        trailManager?.setEditorInfo(info)
        swipeManager?.editorInfo = info
    }

    private fun setupLetterButton(btn: Button) {
        host.baseLetters[btn] = btn.text?.toString()?.firstOrNull() ?: ' '
        // Touch-Logik (Tap / Long-Press / Swipe) liegt in [LetterButtonTouchHandler].
        LetterButtonTouchHandler(
            host = host,
            longPressTimeout = longPressTimeout,
            suggestionController = suggestionController,
            trailManager = trailManager,
            swipeManager = swipeManager,
            handlers = LetterButtonTouchHandler.Handlers(
                onTapLetter = { b -> tapLetter(b) },
                onAutoSelectExtra = { b -> autoSelectExtra(b) },
                onShowLetterExtras = { b -> showLetterExtras(b) },
                onTraceTrail = { traceTrail() }
            )
        ).attach(btn)
    }

    /**
     * Treffer-Markierung nach einem Buchstaben-Tap: prüft das aktuell getippte Wort
     * gegen die [SuggestionEngine] und färbt es bei Bedarf rot. No-Op, solange
     * die Trace-Pref aus ist – der Trail bleibt also der reine Tippspur-Effekt.
     */
    private fun traceTrail() {
        val tm = trailManager ?: return
        val pm = host.predictionManager ?: return
        tm.traceWord(
            pm.currentTypedWord,
            pm.engine,
            pm.currentSuggestions.firstOrNull()?.word
        )
    }

    private fun setupDelButton(btn: Button) {
        var pendingLongPress: Runnable? = null
        var repeater: Runnable? = null
        val scheduler = RepeatScheduler(WORD_DELETE_START_MS, WORD_DELETE_ACCEL, WORD_DELETE_MIN_MS)
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    btn.isPressed = true
                    host.haptic()
                    host.inputRouter?.deleteBackspace()
                    host.predictionManager?.deleteLast()
                    trailManager?.onBackspace()
                    suggestionController.update()
                    scheduler.reset()
                    pendingLongPress = Runnable {
                        host.deleteLastWord()
                        repeater = object : Runnable {
                            override fun run() {
                                host.deleteLastWord()
                                scheduler.next()
                                host.longPressHandler.postDelayed(this, scheduler.interval)
                            }
                        }
                        repeater?.let { host.longPressHandler.postDelayed(it, scheduler.interval) }
                    }
                    pendingLongPress?.let { host.longPressHandler.postDelayed(it, longPressTimeout) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    btn.isPressed = false
                    pendingLongPress?.let { host.longPressHandler.removeCallbacks(it) }
                    repeater?.let { host.longPressHandler.removeCallbacks(it) }
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
        val base = host.baseLetters[btn] ?: btn.text?.toString()?.firstOrNull() ?: return ""
        val upper = host.isShifted() || host.isCapsLock()
        return LetterCaseRenderer.letterFor(base, upper).toString()
    }

    private fun showLetterExtras(anchor: Button) {
        // Basis IMMER aus baseLetters (nicht btn.text — dort stehen inzwischen
        // auch die Sonderzeichen-Hinweise an den Rändern)
        val base = host.baseLetters[anchor] ?: return
        val upper = host.isShifted() || host.isCapsLock()
        val extras = host.letterExtras
        val showExtras = extras[if (upper) base.uppercaseChar() else base]
            ?: extras[base]
            .orEmpty()
        if (showExtras.isEmpty()) return
        host.letterPopup.show(anchor, showExtras) { ch -> host.commitText(ch.toString()) }
    }

    /**
     * Swipe-Modus-Long-Press: statt des Drag-Poppos das erste verfügbare
     * Sonderzeichen automatisch auswählen und committen. Kein Popup, kein
     * Drag — Drücken-und-Halten (ohne Bewegung) gibt direkt das erste
     * Sonderzeichen (Akzent/Interpunktion), damit der Long-Press im
     * Swipe-Modus trotzdem nützlich bleibt, ohne die Wisch-Eingabe zu stören.
     * Shift-/Caps-Bucheinstaben werden beachtet (gleiche Quelle wie
     * [showLetterExtras]).
     */
    private fun autoSelectExtra(anchor: Button) {
        val base = host.baseLetters[anchor] ?: return
        val upper = host.isShifted() || host.isCapsLock()
        val extras = host.letterExtras
        val showExtras = extras[if (upper) base.uppercaseChar() else base]
            ?: extras[base]
            .orEmpty()
        if (showExtras.isEmpty()) return
        val picked = showExtras.first()
        host.commitText(picked.toString())
        trailManager?.snap(picked.first().lowercaseChar())
        traceTrail()
    }

    /** Buchstaben-Groß-/Kleinschreibung + Sonderzeichen-Hinweise auf [view] anwenden. */
    fun applyLetterCase(view: View?) {
        if (view == null) return
        val secondary = ContextCompat.getColor(host.context, R.color.text_secondary)
        val extras = host.letterExtras
        val upper = host.isShifted() || host.isCapsLock()
        ThemeApplier.forEachView(view) { v ->
            val btn = v as? Button ?: return@forEachView
            if (btn.tag != "letter") return@forEachView
            val base = host.baseLetters[btn]
                ?: btn.text?.toString()?.firstOrNull()
                ?: return@forEachView
            val letter = LetterCaseRenderer.letterFor(base, upper)
            val letterExtras = LetterCaseRenderer.extrasFor(base, upper, extras)
            val sb = SpannableStringBuilder(letter.toString())
            // Hauptbuchstabe: etwas kleiner + leicht angehoben
            sb.setSpan(RelativeSizeSpan(0.85f), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(LiftSpan(-0.2f), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (letterExtras.isNotEmpty()) {
                // FlorisBoard-Style: Hinweis-Zeichen klein + abgedunkelt deutlich
                // rechts-UNTEN (LiftSpan senkt die Basislinie stark ab)
                val start = sb.length
                sb.append("\u00A0" + letterExtras.first())
                sb.setSpan(RelativeSizeSpan(0.5f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(secondary), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(LiftSpan(0.55f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            btn.text = sb
        }
    }

    /** Shift-Taste visuell aktualisieren (Alpha/Bold nach Zustand). */
    fun updateShiftVisual(root: View?) {
        val shift = root?.findViewById<Button>(R.id.key_shift) ?: return
        shift.alpha = if (host.isShifted() || host.isCapsLock()) 1f else 0.6f
        shift.setTypeface(null, if (host.isCapsLock()) Typeface.BOLD else Typeface.NORMAL)
    }
}
