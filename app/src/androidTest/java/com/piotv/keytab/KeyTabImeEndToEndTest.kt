package com.piotv.keytab

import android.content.ComponentName
import android.content.Intent
import android.view.KeyEvent
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.io.InputStreamReader

/** Device-level contract tests for the real Android InputMethodService. */
@RunWith(AndroidJUnit4::class)
class KeyTabImeEndToEndTest {

    /** Test-Runner: meldet Name + Erwartung an die Anzeige im Debug-Host. */
    @get:Rule
    val runnerRule = TestRunnerRule()
    @get:Rule
    val activityRule = object : ActivityTestRule<ImeTargetActivity>(
        ImeTargetActivity::class.java, false, false
    ) {
        override fun getActivityIntent(): Intent =
            Intent().setComponent(
                ComponentName("com.piotv.keytab.debug", "com.piotv.keytab.ImeTargetActivity")
            )
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val pkg get() = instrumentation.targetContext.packageName

    @Before
    fun activateKeyTab() {
        // v0.16.1: Zustand des Vorgaengers normalisieren (Symbol-Ebene, Tab).
        // Das IME bleibt bewusst gebunden — ein Umschalten pro Test wuerde das
        // Cold-Start-Rennen zurueckbringen (siehe ImeTestReset).
        runCatching { ImeTestReset.resetKeyboardState() }
        // Reihenfolge ist entscheidend: erst das IME umschalten, dann die
        // Activity starten. Umgekehrt bekommt das erste EditText den Fokus,
        // solange noch das Standard-IME laeuft, und haelt diese Input-Session
        // fest - ein spaeterer "ime set" reicht dann nicht mehr, weil die
        // Session nicht neu gebunden wird. Im Logcat zu sehen als
        // GoogleInputMethodService.onStartInput 75 ms VOR dem Wechsel auf
        // KeyTabImeService; KeyTab wurde dadurch nie gefragt.
        // v0.16: kein previousIme-Save mehr — das IME-Fenster bleibt über den Lauf aktiv.
        assertTrue("Kein KeyTab-IME im Testgerät registriert", shell("ime list -s -a")
            .contains(KEYTAB_IME))
        shell("ime enable $KEYTAB_IME")
        shell("ime set $KEYTAB_IME")
        // Ohne diese Pruefung wartet der Test 10 s auf ein IME-Fenster, das
        // nie kommt, und meldet dann nur "keyboard did not become visible" -
        // ohne Hinweis darauf, dass gar nicht KeyTab das Standard-IME ist.
        val active = shell("settings get secure default_input_method").trim()
        assertTrue("KeyTab ist nicht das Standard-IME (aktiv: $active)", active == KEYTAB_IME)
        // Prefs VOR dem Activity-Start setzen: der IME liest sie beim
        // onStartInput/-View des neuen Feldes (refreshSettings). Umgekehrt las er
        // noch die Werte des Vorgaengertests (siehe KeyTabImeSuggestionsTest).
        val prefs = Prefs.of(instrumentation.targetContext)
        prefs.edit().putBoolean(Prefs.KEY_SUGGESTIONS, false)
            .putBoolean(Prefs.KEY_SWIPE, false).apply()
        activityRule.launchActivity(
            Intent().setComponent(
                ComponentName("com.piotv.keytab.debug", "com.piotv.keytab.ImeTargetActivity")
            )
        )
    }

    @After
    fun restoreIme() {
        // v0.16.1: Zustand normalisieren, damit auch ein fehlgeschlagener Test
        // keinen Schrott fuer die Folgetests hinterlaesst (best effort).
        runCatching { ImeTestReset.resetKeyboardState() }
    }

    @Test
    fun realIme_commitsCharactersSpaceTabEnterAndBackspace() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        clickImeText("x")
        clickImeId("key_space")
        assertEquals("x ", activity.normalField.text.toString())

        activity.receivedKeyCodes.clear()
        clickImeId("key_tab")
        assertTrue("Tab must arrive as KEYCODE_TAB", activity.receivedKeyCodes.contains(KeyEvent.KEYCODE_TAB))

        focus(activity.normalField)
        activity.receivedKeyCodes.clear()
        clickImeId("key_enter")
        assertTrue("Enter must arrive as KEYCODE_ENTER",
            activity.receivedKeyCodes.contains(KeyEvent.KEYCODE_ENTER))

        focus(activity.normalField)
        activity.receivedKeyCodes.clear()
        clickImeId("key_del")
        assertTrue("Backspace must arrive as KEYCODE_DEL",
            activity.receivedKeyCodes.contains(KeyEvent.KEYCODE_DEL))
    }

    @Test
    fun passwordField_typesNormallyButShowsNoSuggestions() {
        val activity = activityRule.activity
        focus(activity.passwordField)
        waitForKeyboard()
        typeThroughVisibleIme("haus")
        assertEquals("haus", activity.passwordField.text.toString())
        assertNoSuggestions()
    }

    @Test
    fun noPersonalizedLearningField_typesNormallyButShowsNoSuggestions() {
        val activity = activityRule.activity
        focus(activity.noLearningField)
        waitForKeyboard()
        typeThroughVisibleIme("haus")
        assertEquals("haus", activity.noLearningField.text.toString())
        assertNoSuggestions()
    }

    @Test
    fun fieldSwitch_keepsTargetsSeparateAndKeepsKeyboardUsable() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        typeThroughVisibleIme("normal")
        assertEquals("normal", activity.normalField.text.toString())

        focus(activity.passwordField)
        typeThroughVisibleIme("secret")
        assertEquals("secret", activity.passwordField.text.toString())
        assertNoSuggestions()

        focus(activity.normalField)
        assertEquals("normal", activity.normalField.text.toString())
        typeThroughVisibleIme("x")
        assertEquals("normalx", activity.normalField.text.toString())
    }

    @Test
    fun activityRecreation_keepsImeUsableForNewField() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        typeThroughVisibleIme("before")

        instrumentation.runOnMainSync { activity.recreate() }
        instrumentation.waitForIdleSync()
        val recreated = activityRule.activity
        focus(recreated.normalField)
        waitForKeyboard()
        typeThroughVisibleIme("after")
        assertEquals("after", recreated.normalField.text.toString())
    }

    @Test
    fun shiftKey_schreibtGrossbuchstaben() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        // Shift kann von einem spaeten onStartInput (Bindung der Input-Session
        // an das neue Fenster) wieder zurueckgesetzt werden — dann bleiben die
        // Buchstaben klein und "A" ist nicht im Baum (CI: "KeyTab letter not
        // found: A"). Deshalb bis zu zwei Anlaeufe: der zweite Klick setzt
        // Shift wieder auf an.
        var upper = false
        repeat(2) {
            if (upper) return@repeat
            clickImeId("key_shift")
            upper = uppercaseLetterPresent(1_500)
        }
        assertTrue("Nach Shift muessen Grossbuchstaben anliegen. Tasten: ${visibleButtonLabels()}", upper)
        clickImeText("A")
        assertEquals("A", activity.normalField.text.toString())
    }

    @Test
    fun doppelTapShift_aktiviertCapsLock() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        val shift = device.wait(Until.findObject(
            By.res(instrumentation.targetContext.packageName, "key_shift")), 5_000)
        assertTrue("Shift-Taste fehlt", shift != null)
        // Doppel-Tap per UiDevice.click (direkte Injektion): UiObject2.click()
        // macht pro Klick einen UiAutomator-Roundtrip und reisst auf langsamen
        // Geraeten/Emulatoren die 300-ms-Grenze von ShiftController.tapShift —
        // dann bleibt es bei zwei Einzel-Tipps (Shift an, Shift aus), die
        // Buchstaben bleiben klein und der Test scheitert mit
        // "KeyTab letter not found: A".
        val shiftBounds = shift.visibleBounds
        // Bis zu zwei Anlaeufe: ein spaetes onStartInput kann CapsLock wieder
        // loeschen — der zweite Doppel-Tap setzt es erneut.
        var caps = false
        repeat(2) {
            if (caps) return@repeat
            device.click(shiftBounds.centerX(), shiftBounds.centerY())
            device.click(shiftBounds.centerX(), shiftBounds.centerY())
            device.waitForIdle()
            caps = uppercaseLetterPresent(1_500)
        }
        assertTrue("CapsLock muss Grossbuchstaben liefern. Tasten: ${visibleButtonLabels()}", caps)
        clickImeText("A")
        clickImeText("B")
        assertEquals("AB", activity.normalField.text.toString())
        // CapsLock verlassen, damit Folgetests nicht im CapsLock starten.
        clickImeId("key_shift")
    }

    @Test
    fun symbolUmschalter_schreibtSymbol() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        clickImeId("key_toggle")
        val exclaim = device.wait(Until.findObject(
            By.clazz("android.widget.Button").textStartsWith("!")), 5_000)
        assertTrue("Symbol ! nicht gefunden", exclaim != null)
        exclaim.click()
        device.waitForIdle()
        assertEquals("!", activity.normalField.text.toString())
        // Zustand zurücksetzen: sonst bleibt die Tastatur im Symbol-Modus und
        // bricht alle Folgetests (Buchstaben kommen als Symbole an).
        clickImeId("key_toggle")
    }

    @Test
    fun backspace_loeschtEinZeichen() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        typeThroughVisibleIme("ab")
        clickImeId("key_del")
        assertEquals("a", activity.normalField.text.toString())
    }

    @Test
    fun enterTaste_brichtZeileUm() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        typeThroughVisibleIme("a")
        activity.receivedKeyCodes.clear()
        clickImeId("key_enter")
        assertTrue("Enter muss als KEYCODE_ENTER ankommen",
            activity.receivedKeyCodes.contains(KeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun punktTaste_schreibtPunkt() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        typeThroughVisibleIme("a")
        clickImeId("key_dot")
        assertEquals("a.", activity.normalField.text.toString())
    }

    @Test
    fun spaceTaste_fuegtLeerzeichenEin() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()
        typeThroughVisibleIme("a")
        clickImeId("key_space")
        assertEquals("a ", activity.normalField.text.toString())
    }

    private fun focus(field: EditText) {
        val imm = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        instrumentation.runOnMainSync {
            field.requestFocus()
            // requestFocus()/clearFocus() reichten nicht: dumpsys input_method
            // zeigte danach mRequestedShowExplicitly=false mShowForced=false
            // mInputShown=false - das System hatte also gar keine Anforderung,
            // das IME-Fenster zu zeigen, und blieb bei mVisibleBound=false.
            // showSoftInput setzt diese Anforderung explizit.
            imm.showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        instrumentation.waitForIdleSync()
    }

    private fun typeThroughVisibleIme(word: String) {
        word.forEach { clickImeText(it.toString()) }
    }

    private fun assertNoSuggestions() {
        val suggestion = device.wait(Until.findObject(
            By.res(instrumentation.targetContext.packageName, "sug_1")), 300)
        assertTrue("Suggestion bar must stay hidden in a sensitive field",
            suggestion == null || !suggestion.isEnabled || suggestion.visibleBounds.isEmpty)
    }

    private fun waitForKeyboard() {
        TestRunnerState.awaitResume()
        // Immer auf ABC schalten: die IME merkt sich den zuletzt genutzten Tab
        // (persistiert auf dem Gerät); in Inhalts-Tabs ist key_space nur INVISIBLE
        // im Baum und die Buchstaben fehlen → clickImeText schlüge fehl.
        device.wait(Until.findObject(By.textStartsWith("ABC")), 3_000)?.click()
        device.waitForIdle()
        // Bis zu drei Anlaeufe: ein echter Touch auf das Eingabefeld ist der
        // zuverlaessigste Weg, das IME-Fenster einzublenden — showSoftInput aus
        // dem Instrumentation-Thread geht verloren, wenn das Fenster gerade
        // keinen Fokus hat (CI/Emulator: "KeyTab keyboard did not become
        // visible", obwohl das richtige IME aktiv ist).
        repeat(3) { round ->
            val wait = if (round == 0) 5_000L else 2_000L
            if (device.wait(Until.findObject(By.res(pkg, "key_space")), wait) != null) return
            device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)?.click()
            device.waitForIdle()
        }
        val key = device.wait(Until.findObject(By.res(pkg, "key_space")), 5_000)
        assertTrue("KeyTab keyboard did not become visible. ${imeState()}", key != null)
    }

    /**
     * IME-Zustand fuer die Fehlermeldung. "did not become visible" allein sagt
     * nicht aus, *warum* - ohne das bleibt nur Raten, ob das falsche IME aktiv
     * ist, der Dienst nicht startet oder das Fenster den Fokus verliert.
     */
    private fun imeState(): String {
        val current = shell("settings get secure default_input_method").trim()
        val dumpsys = shell("dumpsys input_method | grep -E " +
            "'mCurMethodId|mCurToken|mServedView|mShowRequested|mImeWindowVis|mHaveConnection'")
        return "aktiv=$current erwartet=$KEYTAB_IME\n$dumpsys"
    }

    private fun clickImeId(id: String) {
        TestRunnerState.awaitResume()
        val selector = By.res(instrumentation.targetContext.packageName, id)
        val found = device.wait(Until.findObject(selector), 5_000)
        assertTrue("KeyTab key not found: $id", found != null)
        found.click()
        device.waitForIdle()
    }

    /**
     * Liegt ein Grossbuchstabe im Baum? Anker fuer „Shift/CapsLock greift“:
     * die Buchstaben-Tasten tragen dann "A" statt "a".
     */
    private fun uppercaseLetterPresent(timeoutMs: Long): Boolean =
        device.wait(Until.findObject(
            By.clazz("android.widget.Button").textStartsWith("A")), timeoutMs) != null

    /**
     * Sichtbare Tastenbeschriftungen — Diagnose, wenn Shift/CapsLock nicht
     * greift: dann steht hier die tatsaechlich angezeigte Reihe (klein/gross,
     * Zahlenreihe, Symbol-Ebene) statt nur „kein 'A' im Baum".
     */
    private fun visibleButtonLabels(): String =
        device.findObjects(By.clazz("android.widget.Button"))
            .mapNotNull { it.text }
            .filter { it.isNotBlank() }
            .take(16)
            .joinToString(" | ")

    private fun clickImeText(text: String) {
        TestRunnerState.awaitResume()
        // Buchstaben-Tasten tragen komponierte Labels (LetterLabelComposer):
        // Hauptbuchstabe + geschütztes Leerzeichen + erster Sonderzeichen-Hinweis,
        // z. B. "b\u00A0'". By.text("b") matcht dieses Spanned nie exakt — deshalb
        // Präfix-Match. clazz=Button grenzt von TextViews ab: Tab-Label ("abc")
        // und Vorschlags-Chips könnten ebenfalls mit dem Buchstaben beginnen,
        // sind aber keine Buttons.
        val found = device.wait(Until.findObject(
            By.clazz("android.widget.Button").textStartsWith(text)), 5_000)
        assertTrue("KeyTab letter not found: $text", found != null)
        found.click()
        device.waitForIdle()
    }

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return InputStreamReader(FileInputStream(descriptor.fileDescriptor)).buffered()
            .use { it.readText() }
    }

    private companion object {
        const val KEYTAB_IME = "com.piotv.keytab.debug/com.piotv.keytab.ime.KeyTabImeService"
    }
}
