package com.piotv.keytab

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Tastatur-Zustand zwischen Tests normalisieren (CI-Flake-Fix, v0.16.1).
 *
 * Der IME-Dienst überlebt Tests. `onStartInput` setzt beim Feldwechsel nur
 * CapsLock/Shift zurück — die **Symbol-Ebene** (`showSymbols`) und der zuletzt
 * genutzte Tab bleiben bestehen. v0.16 liess das Fenster über den ganzen Lauf
 * aktiv (schneller, kein Cold-Start-Rennen), dadurch vergiftete ein Test den
 * nächsten: im Symbol-Layout fehlen die Buchstaben ("letter not found: A")
 * bzw. Taps landen auf Sonderzeichen (Feld bekam "@;|._" statt "normal").
 *
 * Fix rein testseitig: vor jedem Test den sichtbaren Zustand normalisieren.
 * Anker ist die **Toggle-Beschriftung** — `TabController.toggleSymbols` setzt
 * sie auf `key_toggle_letters` ("abc"), solange die Symbol-Ebene aktiv ist,
 * sonst "?123". `key_space` taugt NICHT als Anker: die gemeinsame
 * Funktionsleiste (`bottom_row`) bleibt auch im Symbol-Layout sichtbar.
 *
 * Bewusst KEIN IME-Umschalten pro Test (das war der Stand vor v0.16): es
 * beendet den Dienst zwar und löscht allen Zustand, erzeugt aber das
 * Cold-Start-Rennen zurück — die Tastatur ist dann beim ersten Tap noch nicht
 * da, und Taps/Texte gehen verloren (CI: "expected:<a[ ]> but was:<a[]>",
 * "expected:<[haus]> but was:<[]>").
 */
object ImeTestReset {
    fun resetKeyboardState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val pkg = instrumentation.targetContext.packageName

        // 1) abc-Tab: setzt die Tab-Sichtbarkeiten und den Maximiert-Zustand
        //    zurück (TabController.applySelected -> setTag(maximized_state,false)).
        device.wait(Until.findObject(By.textStartsWith("ABC")), 2_000)?.click()
        device.waitForIdle()

        // 2) Symbol-Ebene verlassen: der Toggle zeigt dann "abc" statt "?123".
        val toggle = device.wait(Until.findObject(By.res(pkg, "key_toggle")), 2_000)
        if (toggle?.text?.contains("abc", ignoreCase = true) == true) {
            toggle.click()
            device.waitForIdle()
        }
    }
}
