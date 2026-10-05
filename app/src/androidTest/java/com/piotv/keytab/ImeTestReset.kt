package com.piotv.keytab

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Gemeinsamer Tastatur-Reset für IME-Contract-Tests (CI-Flake-Fix, v0.16.1).
 *
 * Problem: Der IME-Prozess überlebt Tests — Symbol-Modus (`key_toggle`),
 * Shift/CapsLock und der zuletzt genutzte Tab bleiben zwischen Tests bestehen.
 * `onStartInput` setzt nur CapsLock zurück, NICHT den Symbol-Modus und NICHT
 * den Tab. Ein Test, der im Symbol-Modus endet (Abbruch vor dem Rückschalten),
 * bricht alle Folgetests ("letter not found: A",
 * "expected:<n[ormal]> but was:<n[@;|._]>").
 *
 * Fix (rein testseitig, kein Produktions-Code): am Ende jedes Tests den
 * sichtbaren Zustand über echte UI-Klicks normalisieren — ABC-Tab,
 * Buchstaben statt Symbole, Shift/CapsLock raus. Wird in @After aufgerufen,
 * damit auch ein fehlgeschlagener Test keinen Schrott hinterlässt (soweit das
 * Fenster noch bedienbar ist; best-effort mit kurzen Timeouts).
 */
object ImeTestReset {
    fun resetKeyboardState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val pkg = instrumentation.targetContext.packageName

        // 1) Auf den ABC-Tab schalten (falls ein Inhalts-Tab aktiv ist).
        device.wait(Until.findObject(By.textStartsWith("ABC")), 2_000)?.click()
        device.waitForIdle()

        // 2) Symbol-Modus verlassen: key_toggle toggelt. Anker ist key_space —
        //    nur im Buchstaben-Layout vorhanden. Fehlt er, genau 1x toggeln.
        if (device.wait(Until.findObject(By.res(pkg, "key_space")), 1_500) == null) {
            device.wait(Until.findObject(By.res(pkg, "key_toggle")), 2_000)?.click()
            device.waitForIdle()
        }

        // 3) Shift/CapsLock verlassen: Die Shift-Logik (ShiftController) kennt
        //    drei Zustände: normal -> Shift -> CapsLock -> Shift -> normal ...
        //    (Klick toggelt Shift, Doppel-Tap CapsLock, Klick aus CapsLock geht
        //    über Shift). Ohne lesbaren Zustand: 3 Klicks enden IMMER in
        //    normal oder Shift — nie in CapsLock:
        //      aus normal: an, aus, an = Shift AN (konsumiert sich beim nächsten
        //        Buchstaben-Tap von selbst via consume() — jeder Test tippt)
        //      aus Shift: aus, an, aus = normal
        //      aus CapsLock: Shift, normal, Shift = Shift AN (s.o., konsumiert sich)
        //    Schlimmstenfalls bleibt Einzel-Shift, das sich beim ersten
        //    Buchstaben-Tap des Folgetests selbst konsumiert. CapsLock und
        //    Symbol-Modus — die harten Folgetest-Killer — sind sicher weg.
        repeat(3) {
            device.wait(Until.findObject(By.res(pkg, "key_shift")), 2_000)?.click()
            device.waitForIdle()
        }
    }
}
