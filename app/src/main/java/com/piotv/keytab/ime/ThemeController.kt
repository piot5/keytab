package com.piotv.keytab.ime

import android.content.Intent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import com.piotv.keytab.R

/**
 * Theme-Controller – Dark/Light-Umschaltung, Theme-Taste (Mond/Sonne) und
 * Theme-Version-Rebuild-Erkennung.
 *
 * Refactoring (Phase 4): aus [KeyTabImeService]
 * extrahiert. Der Service delegiert Theme-Aktionen hierher; Theme-spezifische
 * Konstanten ([LONG_PRESS_TIMEOUT], [SUN_SYMBOL], [MOON_SYMBOL]) leben nun hier
 * statt im Service-Companion.
 *
 * Verhalten bleibt bit-identisch („Umziehen statt Umschreiben").
 */
internal class ThemeController(private val host: ThemeHost) {

    companion object {
        const val LONG_PRESS_TIMEOUT = 400L
        // Monochrome (schwarz/weiß) Theme-Symbole – einheitlich farbig via key_text,
        // im Kontrast zu den bunten Emojis (🌙/☀)
        const val SUN_SYMBOL = "\u2600\uFE0E"  // ☀ (Text-Präsentation)
        const val MOON_SYMBOL = "\u263E\uFE0E" // ☾ (Text-Präsentation)
    }

    /**
     * Mond/Sonne-Taste einrichten: Tippen = Dark/Light umschalten (wie zuvor),
     * Long-Press = Theme-Einstellungen öffnen.
     */
    fun setupButton(btn: Button) {
        var pendingLongPress: Runnable? = null
        var longPressFired = false
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressFired = false
                    btn.isPressed = true
                    pendingLongPress = Runnable {
                        longPressFired = true
                        host.haptic()
                        showThemeSettings()
                    }
                    pendingLongPress?.let { host.longPressHandler.postDelayed(it, LONG_PRESS_TIMEOUT) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    btn.isPressed = false
                    pendingLongPress?.let { host.longPressHandler.removeCallbacks(it) }
                    if (!longPressFired) {
                        host.letterPopup.dismiss()
                        host.haptic()
                        toggleDarkMode()
                        // Accessibility (Lint [ClickableViewAccessibility]): Klick
                        // als View-Event melden (kein OnClickListener → folgenlos).
                        btn.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    btn.isPressed = false
                    pendingLongPress?.let { host.longPressHandler.removeCallbacks(it) }
                    true
                }
                else -> false
            }
        }
    }

    /** Theme-Icon (☾/☀) passend zum aktiven Modus auf den Root-View setzen. */
    fun updateIcon(root: View?) {
        root?.findViewById<Button>(R.id.key_theme)?.text =
            if (host.isDarkMode()) MOON_SYMBOL else SUN_SYMBOL
    }

    /** Dark-Mode-Override toggeln und Input-View mit neuem Theme neu aufbauen. */
    fun toggleDarkMode() {
        val prefs = com.piotv.keytab.Prefs.of(host.context)
        prefs.edit().putBoolean(ThemePrefs.KEY_DARK, !host.isDarkMode()).apply()
        // Input-View mit neuem Theme neu aufbauen; Icon passend setzen
        val newRoot = host.rebuildInputView()
        // Icon spiegeln den NEUEN Zustand: Dark aktiv = ☾, Light aktiv = ☀
        updateIcon(newRoot)
        host.setInputView(newRoot)
    }

    /** Theme-Einstellungs-Seite (2. Einstellungsseite der App) öffnen. */
    fun showThemeSettings() {
        host.context.startActivity(
            Intent(host.context, com.piotv.keytab.ThemeSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
