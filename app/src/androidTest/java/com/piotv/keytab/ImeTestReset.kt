package com.piotv.keytab

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Tastatur-Zustand zwischen Tests normalisieren (CI-Flake-Fix, v0.16.1).
 *
 * Der IME-Dienst überlebt Tests. `onStartInput` setzt beim Feldwechsel nur
 * CapsLock/Shift zurück — die **Symbol-Ebene** (`SymbolPlane`, der Zustand des
 * `?123`-Schalters) und der zuletzt genutzte Tab bleiben bestehen. v0.16 liess
 * das Fenster über den ganzen Lauf aktiv (schneller, kein Cold-Start-Rennen),
 * dadurch vergiftete ein Test den nächsten: im Symbol-Layout fehlen die
 * Buchstaben ("letter not found: A") bzw. Taps landen auf Sonderzeichen (Feld
 * bekam "@;|._" statt "normal").
 *
 * Fix rein testseitig: vor jedem Test den sichtbaren Zustand normalisieren.
 * Anker ist die **Toggle-Beschriftung** — `TabController` beschriftet den
 * Schalter mit der *nächsten* Ebene: "?123" auf der Buchstaben-Ebene, "<v" auf
 * `?123`, "abc" auf der zweiten Symbol-Ebene. Die Buchstaben-Ebene ist also
 * genau dann aktiv, wenn dort "?123" steht. `key_space` taugt NICHT als Anker:
 * die gemeinsame Funktionsleiste (`bottom_row`) bleibt in *jeder* Ebene sichtbar.
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

        // Schnellpfad: abc-Tab, Buchstaben-Ebene, Kleinschreibung -> fertig.
        // Kostet nur einen kurzen Blick in den Baum, statt Tab/Klicks zu fahren.
        if (lowercaseLetterPresent(device, pkg)) return

        // 1) abc-Tab: setzt die Tab-Sichtbarkeiten und den Maximiert-Zustand
        //    zurück (TabController.applySelected -> setTag(maximized_state,false)).
        device.wait(Until.findObject(By.textStartsWith("ABC")), 800)?.click()
        device.waitForIdle()

        // 2) Symbol-Ebenen verlassen: der Toggle traegt "?123", wenn die
        //    Buchstaben-Ebene aktiv ist — er zeigt immer die *naechste* Ebene
        //    (abc -> "?123" -> "<v"). Aus jeder Symbol-Ebene fuehren daher
        //    hoechstens zwei Tipps zurueck. Die Beschriftung wird pro Durchlauf
        //    neu gelesen: UiObject2 cached den Knoten nach einem Klick.
        repeat(3) {
            val toggle = device.wait(Until.findObject(By.res(pkg, "key_toggle")), 800)
                ?: return@repeat
            if (toggle.text?.contains("?123") == true) return@repeat
            toggle.click()
            device.waitForIdle()
        }

        // 3) Shift/CapsLock loesen: die Buchstaben-Tasten zeigen dann "q" statt
        //    "Q". Der Zustand ueberlebt sogar einen neuen `am instrument`-Lauf
        //    (der IME-Dienst bleibt stehen) — blieb er an, scheiterte der erste
        //    Buchstabe mit "Taste 'h' nicht gefunden".
        repeat(2) {
            if (lowercaseLetterPresent(device, pkg)) return
            device.wait(Until.findObject(By.res(pkg, "key_shift")), 800)?.click()
            device.waitForIdle()
        }
    }

    /** Liegt ein kleiner Buchstabe im Baum ("q" statt "Q")? */
    private fun lowercaseLetterPresent(device: UiDevice, pkg: String): Boolean =
        device.wait(Until.findObject(
            By.clazz("android.widget.Button").textStartsWith("q")), 600) != null
}
