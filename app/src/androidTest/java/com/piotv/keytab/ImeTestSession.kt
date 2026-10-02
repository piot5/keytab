package com.piotv.keytab

import android.content.ComponentName
import android.content.Intent
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Langlebige Test-Session (v0.16): startet die [ImeTargetActivity] und schaltet
 * die IME genau EINMAL um und hält beides über alle Tests offen.
 *
 * Vorher startete jede Testklasse die Activity in @Before neu und schaltete die
 * IME in @After wieder zurück — dadurch wurde das Fenster bei jedem Test aus-
 * und wieder eingeblendet. Jetzt bleibt die Session (Activity + IME-Fenster)
 * über den ganzen Lauf bestehen; pro Test wird nur noch das Feld geleert und
 * der Fokus gesetzt.
 */
object ImeTestSession {
    private const val KEYTAB_IME = "com.piotv.keytab.debug/com.piotv.keytab.ime.KeyTabImeService"

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val pkg get() = instrumentation.targetContext.packageName

    @Volatile
    private var activity: ImeTargetActivity? = null
    @Volatile
    private var imePrepared = false

    /** Startet die Activity einmalig (idempotent) und liefert sie. */
    @Synchronized
    fun activity(): ImeTargetActivity {
        activity?.let { a -> if (!a.isFinishing && !a.isDestroyed) return a }
        val intent = Intent().setComponent(
            ComponentName("com.piotv.keytab.debug", "com.piotv.keytab.ImeTargetActivity")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val started = instrumentation.startActivitySync(intent)
        val a = (started as? ImeTargetActivity)
            ?: error("ImeTargetActivity konnte nicht gestartet werden: $started")
        activity = a
        return a
    }

    /** Schaltet die IME einmalig auf KeyTab (idempotent). */
    @Synchronized
    fun prepareIme() {
        if (imePrepared) return
        shell("ime enable $KEYTAB_IME")
        shell("ime set $KEYTAB_IME")
        imePrepared = true
    }

    /** Feld leeren + fokussieren + IME anzeigen. */
    fun focus(field: EditText) {
        val imm = instrumentation.targetContext
            .getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        instrumentation.runOnMainSync {
            field.setText("")
            field.requestFocus()
            imm.showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        instrumentation.waitForIdleSync()
    }

    /** Tastatur sichtbar machen und auf den abc-Tab schalten. */
    fun waitForKeyboard() {
        device.wait(Until.findObject(By.textStartsWith("ABC")), 3_000)?.click()
        device.waitForIdle()
        if (device.wait(Until.findObject(By.res(pkg, "key_space")), 2_000) == null) {
            device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)?.click()
            device.waitForIdle()
        }
        val key = device.wait(Until.findObject(By.res(pkg, "key_space")), 10_000)
        assertTrue("KeyTab keyboard did not become visible", key != null)
    }

    /** Wort-Zustand der IME zurücksetzen (Space schließt, Backspace entfernt das Leerzeichen). */
    fun resetWordState() {
        clickImeId("key_space")
        clickImeId("key_del")
    }

    fun clickImeId(id: String) {
        val found = device.wait(Until.findObject(By.res(pkg, id)), 5_000)
        assertTrue("KeyTab key not found: $id", found != null)
        found.click()
        device.waitForIdle()
    }

    fun clickImeText(text: String) {
        val found = device.wait(Until.findObject(
            By.clazz("android.widget.Button").textStartsWith(text)), 5_000)
        assertTrue("KeyTab letter not found: $text", found != null)
        found.click()
        device.waitForIdle()
    }

    fun typeWord(word: String) = word.forEach { clickImeText(it.toString()) }

    fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return InputStreamReader(FileInputStream(descriptor.fileDescriptor)).buffered()
            .use { it.readText() }
    }
}
