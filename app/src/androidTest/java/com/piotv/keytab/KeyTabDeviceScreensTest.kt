package com.piotv.keytab

import android.content.ComponentName
import android.content.Intent
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
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
import kotlin.concurrent.thread

/**
 * ECHTE Screenshots + GIF-Frames vom laufenden KeyTab-IME:
 *
 *  1. Alle Tabs (abc/Editor/Files/Clip/Snip) im Normal- UND Maximier-
 *     Zustand abfotografieren.
 *  2. GIF-Frames: Swipe-Trail (Glide-Typing) über die Tastatur.
 *  3. GIF-Frames: Scrollen im Files-Panel.
 *
 * Die PNGs landen im App-Außenspeicher (getExternalFilesDir) unter
 * `device_screens/`, die Trail-/Scroll-Frames zusätzlich unter
 * /data/local/tmp/keytab_frames/ (dorthin darf das shell-UID screencap
 * schreiben; /sdcard/Android/data darf es auf neueren Androiden nicht).
 * Das Host-Skript scripts/device_screens.sh baut daraus die GIFs.
 *
 * Bewusst weich: fehlende Tabs (Clip/Snip deaktiviert) oder eine fehlende
 * Max-Zeile überspringen einzelne Aufnahmen, statt den Test rot zu machen —
 * er ist Dokumentationswerkzeug, kein Contract-Test.
 */
@RunWith(AndroidJUnit4::class)
class KeyTabDeviceScreensTest {
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
    private var previousIme: String = ""
    private lateinit var outDir: File
    private val framesDir = File("/data/local/tmp/keytab_frames")

    @Before
    fun activateKeyTab() {
        previousIme = shell("settings get secure default_input_method").trim()
        assertTrue("Kein KeyTab-IME registriert", shell("ime list -s -a").contains(KEYTAB_IME))
        shell("ime enable $KEYTAB_IME")
        shell("ime set $KEYTAB_IME")
        activityRule.launchActivity(
            Intent().setComponent(
                ComponentName("com.piotv.keytab.debug", "com.piotv.keytab.ImeTargetActivity")
            )
        )
        outDir = instrumentation.targetContext.getExternalFilesDir(null)
            ?.let { File(it, "device_screens") }
            ?: File(instrumentation.targetContext.filesDir, "device_screens")
        outDir.mkdirs()
        framesDir.mkdirs()
        framesDir.listFiles()?.forEach { it.delete() }
        shell("rm -f ${framesDir.absolutePath}/*.png")
        wake()
    }

    @After
    fun restoreIme() {
        if (previousIme.isNotBlank() && previousIme != "null") shell("ime set $previousIme")
    }

    /** Sicherheitsnetz: Display an, Keyguard weg (no-op ohne Lock). */
    private fun wake() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        device.waitForIdle()
    }

    private fun res(id: String): UiObject2? =
        device.wait(Until.findObject(By.res(pkg, id)), 5_000)

    private fun text(t: String): UiObject2? =
        device.wait(Until.findObject(By.textStartsWith(t)), 3_000)

    private fun shot(name: String) {
        device.waitForIdle()
        val f = File(outDir, "$name.png")
        device.takeScreenshot(f)
        // Zwischenlager für den Host (shell-UID darf Android/data evtl. nicht
        // lesen, /data/local/tmp aber immer).
        shell("cp -f ${f.absolutePath} ${framesDir.absolutePath}/$name.png")
        log("📸 $name.png")
    }

    private fun log(msg: String) = println("DeviceScreens: $msg")

    // ---------------- 1. Alle Tabs: normal + maximiert ----------------

    @Test
    fun alleTabs_normalUndMaximiert() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()

        val tabs = listOf("abc", "Editor", "Files", "Clip", "Snip")
        for (t in tabs) {
            val tab = text(t)
            if (tab == null) { log("⚠ Tab $t nicht sichtbar — übersprungen"); }
            else {
                tab.click()
            device.waitForIdle()
            device.wait(Until.findObject(By.res(pkg, "ime_tabs")), 2_000)
            Thread.sleep(600)

            shot("tab_${t}_normal")

            // abc hat keine Max-Zeile; sug_hide (⇲ in der Vorschlagsleiste)
            // als Fallback für den Vollbild-Toggle.
            val maxId = if (res("key_maximize") != null) "key_maximize" else "sug_hide"
            if (res(maxId) == null) {
                log("⚠ kein Maximize-Key im Tab $t — maximiert übersprungen")
                continue
            }
            res(maxId)!!.click()
            device.waitForIdle()
            Thread.sleep(800)
            shot("tab_${t}_max")

            // zurück in den Normalzustand (Symbol ist jetzt ⇱)
            device.wait(Until.findObject(By.res(pkg, maxId)), 3_000)?.click()
            device.waitForIdle()
            Thread.sleep(600)
            }   // else (Tab gefunden)
        }
    }

    // ---------------- 2. GIF: Swipe-Trail ----------------

    @Test
    fun trailGifFrames() {
        val activity = activityRule.activity
        val prefs = Prefs.of(instrumentation.targetContext)
        instrumentation.runOnMainSync {
            prefs.edit()
                .putBoolean(Prefs.KEY_SUGGESTIONS, true)
                .putBoolean(Prefs.KEY_SWIPE, true)          // Glide-Typing an
                .putBoolean(ThemePrefs.KEY_TRAIL, true)     // Trail sichtbar
                .putBoolean(ThemePrefs.KEY_TRAIL_TRACE, true)
                .putBoolean(Prefs.KEY_DYNAMIC_KEYS, false)
                .apply()
        }
        focus(activity.normalField)
        waitForKeyboard()
        text("abc")?.click()
        device.waitForIdle()

        val space = res("key_space") ?: return log("⚠ Tastatur nicht gefunden — Trail übersprungen")
        val cb = space.visibleBounds
        val cx = cb.centerX()
        val cy = cb.centerY()
        device.waitForIdle()

        captureFrames("trail") {
            // Zickzack-Swipes über die Tastatur — TrailLogic zeichnet die Spur
            device.swipe(cx - 420, cy - 250, cx + 300, cy - 120, 90)
            device.swipe(cx + 300, cy - 120, cx - 350, cy - 50, 90)
            device.swipe(cx - 350, cy - 50, cx + 350, cy - 200, 90)
            device.swipe(cx + 350, cy - 200, cx - 420, cy + 60, 100)
        }
        log("Trail-Frames: ${countFrames("trail")}")
    }

    // ---------------- 3. GIF: Scrollen (Files-Panel) ----------------

    @Test
    fun scrollGifFrames() {
        val activity = activityRule.activity
        focus(activity.normalField)
        waitForKeyboard()

        val tab = text("Files") ?: return log("⚠ Files-Tab nicht sichtbar — übersprungen")
        tab.click()
        device.waitForIdle()
        val list = res("file_list") ?: return log("⚠ file_list nicht gefunden — übersprungen")
        val lb = list.visibleBounds
        val lx = lb.centerX()

        captureFrames("scroll") {
            device.swipe(lx, lb.bottom - 60, lx, lb.top + 60, 40)
            Thread.sleep(400)
            device.swipe(lx, lb.bottom - 60, lx, lb.top + 60, 40)
            Thread.sleep(800)
            device.swipe(lx, lb.top + 60, lx, lb.bottom - 60, 40)
        }
        log("Scroll-Frames: ${countFrames("scroll")}")
    }

    // ---------------- Frame-Capture im Hintergrund ----------------

    private fun countFrames(prefix: String): Int =
        framesDir.listFiles()?.count { it.name.startsWith(prefix) } ?: 0

    /**
     * screencap-Lauf im Hintergrund-Thread (shell-UID), während der
     * Haupt-Thread die Eingaben macht. screencap braucht je Frame ~300-500 ms
     * — die GIF-Framerate ergibt sich dadurch von selbst (~2-3 fps).
     */
    private fun captureFrames(prefix: String, block: () -> Unit) {
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val captor = thread(name = "frame-capture") {
            var i = 0
            while (!stop.get()) {
                shell("screencap -p ${framesDir.absolutePath}/${prefix}_%04d.png".format(i))
                i++
            }
            log("$prefix: $i Frames")
        }
        Thread.sleep(700)   // ersten Frame sicher drin haben
        block()
        Thread.sleep(700)
        stop.set(true)
        captor.join()
    }

    // ---------------- Hilfen (wie in KeyTabImeEndToEndTest) ----------------

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
        if (device.wait(Until.findObject(By.res(pkg, "key_space")), 2_000) != null) return
        device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)?.click()
        device.waitForIdle()
        device.wait(Until.findObject(By.textStartsWith("abc")), 3_000)?.click()
        val key = device.wait(Until.findObject(By.res(pkg, "key_space")), 10_000)
        assertTrue("KeyTab keyboard did not become visible", key != null)
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
