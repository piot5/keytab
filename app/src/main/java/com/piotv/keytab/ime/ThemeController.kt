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
 * Refactoring (docs/REFACTORING_PLAN.md Phase 4): aus [KeyTabImeService]
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

    /** Zuletzt angewendete Theme-Version (0 = noch keine). */
    private var appliedThemeVersion = 0

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

    /**
     * Tastatur neu aufbauen, wenn die Theme-Einstellungsseite Änderungen gemacht
     * hat (Theme-Version erhöht). Ohne Änderung passiert nichts (0.9.4-Look
     * bleibt unverändert erhalten).
     */
    fun maybeRebuildForThemeChange() {
        // Nur wenn die Tastatur schon aufgebaut ist: Beim allerersten Öffnen ist
        // keyboardRoot noch null und onCreateInputView läuft ohnehin gleich an.
        if (host.keyboardRoot == null) return
        val prefs = com.piotv.keytab.Prefs.of(host.context)
        val version = ThemePrefs.themeVersion(prefs)
        if (version == appliedThemeVersion) return
        appliedThemeVersion = version
        // Theme-Version hat sich geändert → Tastatur mit übersteuertem Theme neu
        // aufbauen (Trailing-Text korrekt beim neuen wirkenden Modus).
        val newRoot = host.rebuildInputView()
        updateIcon(newRoot)
        host.setInputView(newRoot)
    }
}
