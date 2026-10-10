package com.piotv.keytab

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Edge-to-Edge für die Einstellungs-Activities — verpflichtend ab targetSdk 35
 * (Android 15): Das Fenster läuft hinter Status- und Navigationsleiste, der
 * Content-Frame wird um die Systembar-Insets gepadded, damit nichts verdeckt
 * wird; der Theme-/Layout-Hintergrund füllt den Randbereich weiter aus.
 *
 * Am IME-Fenster greift das nicht — dort läuft das Fenster selbst bis zum
 * Bildschirmrand; für das Navigation-Bars-Padding sorgt
 * `KeyTabImeService.onCreateInputView` (Insets-Listener am Keyboard-Root).
 *
 * Ruft [enableEdgeToEdge] und hängt den Insets-Listener an `android.R.id.content`,
 * daher erst **nach** `setContentView` aufrufen.
 */
fun ComponentActivity.applyEdgeToEdgeInsets() {
    enableEdgeToEdge()
    val content = findViewById<View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}
