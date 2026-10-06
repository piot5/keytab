package com.piotv.keytab

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.piotv.keytab.ime.ThemePrefs
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Screenshot-Verifikation des Tipp-Trails (die Luecke aus README "Known
 * gaps": der Regression-Fix fuer widerspruechliche Trace-Zustaende war nur
 * auf Zustandsebene getestet, die gerenderten Farben nie).
 *
 * Geprueft wird auf Pixelebene im Tastaturbereich (unter der Vorschlagsleiste,
 * dort sitzt die gruene Accent-Leiste und wuerde das Ergebnis verfaelschen):
 * mit aktivem `trail_trace` muessen die Buchstaben des getippten Wortes gruen
 * markiert werden — also deutlich mehr grundominante Pixel als im
 * Vergleichslauf ohne Trace. Das ist theme-abhaengig bewusst relativ
 * (gruen-dominant = G deutlich ueber R und B) und nicht auf einen Hardcode
 * festgelegt.
 */
@RunWith(AndroidJUnit4::class)
class KeyTabTrailVisualTest {

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
    @Before
    fun activateKeyTab() {
        // v0.16: kein previousIme-Save mehr — das IME-Fenster bleibt über den Lauf aktiv.
        assertTrue("Kein KeyTab-IME registriert", shell("ime list -s -a").contains(KEYTAB_IME))
        shell("ime enable $KEYTAB_IME")
        shell("ime set $KEYTAB_IME")
        activityRule.launchActivity(
            Intent().setComponent(
                ComponentName("com.piotv.keytab.debug", "com.piotv.keytab.ImeTargetActivity")
            )
        )
    }

    @After
    fun restoreIme() {
        // v0.16: bewusst KEIN Rückschalten auf das vorherige IME mehr (ein Durchlauf).
    }

    @Test
    fun trailTraceMarkiertGetipptesWortGruen() {
        val prefs = Prefs.of(instrumentation.targetContext)
        fun setTrail(trace: Boolean) {
            instrumentation.runOnMainSync {
                prefs.edit()
                    .putBoolean(Prefs.KEY_SUGGESTIONS, true)
                    .putBoolean(ThemePrefs.KEY_TRAIL, trace)
                    .putBoolean(ThemePrefs.KEY_TRAIL_TRACE, trace)
                    .putBoolean(Prefs.KEY_SWIPE, false)
                    .putBoolean(Prefs.KEY_DYNAMIC_KEYS, false)
                    .apply()
            }
        }

        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()

        // Referenz: Trail aus, Nicht-Wort tippen, Screenshot. Der
        // likely-next-key-Highlight ist default GRUEN und ein anderer
        // Schalter — deshalb zaehlen wir BLAU-dominante Pixel: das ist
        // die Default-Trail-Farbe 0xFF2196F3.
        setTrail(false)
        typeWord("zau")
        val off = trailPixelCount("trail_off")

        // Messung: Trail an, Wort tippen — die zuletzt getippten Tasten
        // muessen blau getintet sein.
        setTrail(true)
        typeWord("haus")
        val on = trailPixelCount("trail_on")

        assertTrue("Mit Trail nur $on blau-dominante Pixel (ohne: $off) — der Trail wird nicht (kaum) gerendert", on > 40)
        assertTrue("Mit Trail $on blau-dominante Pixel, ohne $off — kein verwertbarer Unterschied", on > off * 2)
    }

    // ---------------- Hilfen ----------------

    /** Blau-dominante Pixel (Default-Trail-Farbe) im Tastaturbereich zaehlen. */
    private fun trailPixelCount(tag: String): Int {
        val dir = instrumentation.targetContext.getExternalFilesDir(null)
            ?: instrumentation.targetContext.filesDir
        val shot = File(dir, "trail_$tag.png")
        device.takeScreenshot(shot)
        val bitmap: Bitmap = BitmapFactory.decodeFile(shot.absolutePath)
            ?: return -1
        // Tastatur: untere Bildhaelfte, ohne Navigationsleiste unten und
        // ohne Tab-/Vorschlagsbereich oben (dort sitzt die gruene Accent-Leiste).
        val y0 = (bitmap.height * 0.60).toInt()
        val y1 = (bitmap.height * 0.92).toInt()
        var blue = 0
        val pixels = IntArray((y1 - y0) * bitmap.width)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, y0, bitmap.width, y1 - y0)
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (b > 90 && b > r + 40 && b > g + 40) blue++
        }
        bitmap.recycle()
        return blue
    }

    private fun typeWord(word: String) {
        word.forEach { clickImeText(it.toString()) }
    }

    private fun focus(field: EditText) {
        val imm = instrumentation.targetContext
            .getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        instrumentation.runOnMainSync {
            field.requestFocus()
            imm.showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        instrumentation.waitForIdleSync()
    }

    private fun waitForKeyboard() {
        TestRunnerState.awaitResume()
        // 1) Schon sichtbar (egal welcher Tab)?
        if (device.wait(Until.findObject(By.res(
                instrumentation.targetContext.packageName, "key_space")), 2_000) != null) {
            return
        }
        // 2) Nein: Tastatur per echtem Touch aufs erste EditText holen —
        //    showSoftInput allein verliert das Rennen gegen den kalten
        //    IME-Prozess nach `am instrument` (siehe HeightTest.setUp).
        device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)?.click()
        device.waitForIdle()
        // 3) Die IME merkt sich den letzten Tab: im Files/Clip/Snip-Tab gibt es
        //    kein sichtbares key_space — auf ABC schalten.
        device.wait(Until.findObject(By.textStartsWith("ABC")), 3_000)?.click()
        val key = device.wait(Until.findObject(
            By.res(instrumentation.targetContext.packageName, "key_space")), 10_000)
        assertTrue("KeyTab keyboard did not become visible", key != null)
    }

    private fun clickImeText(text: String) {
        TestRunnerState.awaitResume()
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
