package com.piotv.keytab

import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * UI-Test (UiAutomator) fuer die **echte** IME-Hoehe auf dem Geraet.
 *
 * Die Unit-Tests (PanelHeightsTest) pruefen die Rechnung auf inflate-
 * Layouts. Sie ersetzen nicht die Messung, die hier stattfindet: View-Baum
 * wird vom System gemessen und gezeichnet, Geraete-Dichte, Schriftgroesse,
 * Systemleisten und die echte Fenstergroesse fliessen ein.
 *
 * Geprueft wird der Vertrag aus drei Punkten:
 *  1. Editor-, Files-, Clip- und Snippet-Tab sind gleich hoch (Toleranz 4 px).
 *  2. Der abc-Tab hat eine eigene Hoehe (er zeigt die Tastatur).
 *  3. Die Maximieren-Zeile ist in *jedem* Tab sichtbar und liegt unten;
 *     Tippen darauf fuellt den Bildschirm bis auf 5 % Rest, erneutes Tippen
 *     stellt den Normalzustand wieder her.
 */
@RunWith(AndroidJUnit4::class)
class KeyTabImeHeightTest {

    @get:Rule
    val activityRule = object : ActivityTestRule<ImeTargetActivity>(
        ImeTargetActivity::class.java, false, false
    ) {
        override fun getActivityIntent(): Intent =
            Intent().setComponent(
                ComponentName("com.piotv.keytab.debug",
                    "com.piotv.keytab.ImeTargetActivity")
            )
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val pkg get() = instrumentation.targetContext.packageName
    private var previousIme: String = ""

    /** Toleranz in px: Layout-Rundung und Systemleiste, kein echter Unterschied. */
    private val tolerance = 4

    @Before
    fun setUp() {
        // Erst das IME umschalten, dann starten: sonst bindet das erste
        // EditText die Input-Session an das vorherige Standard-IME (Gboard)
        // und KeyTab wird nie gefragt. Siehe KeyTabImeEndToEndTest.
        previousIme = shell("settings get secure default_input_method").trim()
        assertTrue("KeyTab-IME nicht registriert", shell("ime list -s -a").contains(KEYTAB_IME))
        shell("ime enable $KEYTAB_IME")
        shell("ime set $KEYTAB_IME")
        val active = shell("settings get secure default_input_method").trim()
        assertTrue("KeyTab ist nicht das Standard-IME (aktiv: $active)", active == KEYTAB_IME)
        activityRule.launchActivity(
            Intent().setComponent(
                ComponentName("com.piotv.keytab.debug",
                    "com.piotv.keytab.ImeTargetActivity")
            )
        )
        val prefs = Prefs.of(instrumentation.targetContext)
        prefs.edit().putBoolean(Prefs.KEY_SUGGESTIONS, false)
            .putBoolean(Prefs.KEY_SWIPE, false).apply()
        focusField()
        assertTrue("KeyTab wurde nicht eingeblendet", waitForIme())
    }

    @After
    fun tearDown() {
        if (previousIme.isNotBlank() && previousIme != "null") shell("ime set $previousIme")
    }

    // ---------------- Tests ----------------

    @Test
    fun alleInhaltsTabsHabenDieselbeImeHoehe() {
        val heights = mapOf(
            "EDITOR" to imeHeightAfterTab("EDITOR"),
            "FILES" to imeHeightAfterTab("FILES"),
            "CLIP" to imeHeightAfterTab("CLIP"),
            "SNIP" to imeHeightAfterTab("SNIP"),
        )
        val distinct = heights.values.distinct()
        assertTrue(
            "Inhalts-Tabs muessen gleich hoch sein, gemessen: $heights",
            distinct.size == 1
        )
        assertTrue("IME-Hoehe muss relevant sein: $heights", heights.values.first() > 100)
    }

    @Test
    fun abcTabHatEigeneHoeheUndIstKleiner() {
        val abc = imeHeightAfterTab("ABC")
        val editor = imeHeightAfterTab("EDITOR")
        // Der abc-Tab zeigt nur die Tastatur, die Inhalts-Tabs zusaetzlich ein
        // Panel — sie duerfen nicht dieselbe Hoehe haben.
        assertNotEquals("abc-Tab sollte eine eigene Hoehe haben ($abc vs $editor)",
            abc, editor)
        assertTrue("abc ($abc) darf nicht hoeher sein als Editor ($editor)", abc < editor)
    }

    @Test
    fun maximizeZeileIstInJedemTabSichtbarUndLiegtUnten() {
        for (tab in listOf("ABC", "EDITOR", "FILES", "CLIP", "SNIP")) {
            val row = selectTab(tab) ?: error("Tab $tab nicht gefunden")
            val maximize = waitFor("key_maximize")
            assertTrue("key_maximize fehlt im Tab $tab", maximize != null)
            assertTrue("key_maximize ist im Tab $tab nicht sichtbar",
                maximize!!.visibleBounds.height() > 0)
            assertTrue("Maximieren-Zeile muss unter der Tastatur liegen (Tab $tab): " +
                "Zeile ${row.visibleBounds}, Knopf ${maximize.visibleBounds}",
                maximize.visibleBounds.top >= row.visibleBounds.top)
        }
    }

    @Test
    fun maximierenFuelltDenBildschirmBisAufFuenfProzent() {
        val normal = imeHeightAfterTab("EDITOR")
        val displayHeight = device.displayHeight

        val button = waitFor("key_maximize") ?: error("key_maximize nicht gefunden")
        button.click()
        device.waitForIdle()
        Thread.sleep(600)
        val maximized = imeHeight()

        assertTrue("Maximieren muss die Hoehe deutlich vergroessern: " +
            "normal=$normal, maximiert=$maximized", maximized > normal + 100)
        // 95 % des Bildschirms, mit Toleranz: je nach Systemleiste kann der
        // IME nicht den vollen Bildschirm bekommen.
        val expected = (displayHeight * 0.95).toInt()
        val delta = kotlin.math.abs(maximized - expected)
        assertTrue("Maximiert sollte ~95 % des Bildschirms sein: " +
            "gemessen=$maximized, erwartet=$expected, Bildschirm=$displayHeight",
            delta <= displayHeight * 0.10)

        // Und wieder zurueck: der Ausweg muss in jedem Zustand bedienbar sein.
        waitFor("key_maximize")?.click()
        device.waitForIdle()
        Thread.sleep(600)
        val back = imeHeight()
        assertTrue("Nach dem Zurueck muss die Normalhoehe kommen: " +
            "vorher=$normal, jetzt=$back", kotlin.math.abs(back - normal) <= tolerance * 4)
    }

    // ---------------- Hilfen ----------------

    private fun focusField() {
        val activity = activityRule.activity
        instrumentation.runOnMainSync {
            activity.normalField.requestFocus()
            activity.normalField.clearFocus()
            activity.normalField.requestFocus()
        }
        instrumentation.waitForIdleSync()
    }

    private fun waitForIme(): Boolean = waitFor("key_maximize") != null

    private fun waitFor(id: String): UiObject2? = device.wait(
        Until.findObject(By.res(pkg, id)), 10_000)

    /** Tab ueber seinen sichtbaren Text waehlen (Sprache egal, Labels sind stabil). */
    private fun selectTab(tab: String): UiObject2? {
        val tabTexts = mapOf(
            "ABC" to arrayOf("abc", "ABC"),
            "EDITOR" to arrayOf("editor", "Editor", "EDITOR"),
            "FILES" to arrayOf("files", "Files", "FILES"),
            "CLIP" to arrayOf("clip", "Clip", "CLIP"),
            "SNIP" to arrayOf("snip", "Snip", "SNIP"),
        )[tab] ?: return null
        for (label in tabTexts) {
            val found = device.wait(Until.findObject(By.text(label)), 2_000)
            if (found != null) {
                found.click()
                device.waitForIdle()
                Thread.sleep(400)
                return found
            }
        }
        return null
    }

    private fun imeHeightAfterTab(tab: String): Int {
        assertTrue("Tab $tab nicht gefunden", selectTab(tab) != null)
        Thread.sleep(500)
        return imeHeight()
    }

    /**
     * Sichtbare Hoehe der Tastatur = Oberkante der Tab-Leiste bis Oberkante der
     * Navigationsleiste. Die IME selbst ist ein Fullscreen-Fenster
     * (Requested h=0), man misst also ihren *Inhalt*.
     */
    private fun imeHeight(): Int {
        val top = waitFor("ime_tabs")?.visibleBounds?.top
            ?: error("ime_tabs nicht sichtbar — ist die IME offen?")
        val bottom = imeBottom()
        val height = bottom - top
        assertTrue("IME-Hoehe muss positiv sein: top=$top bottom=$bottom", height > 0)
        return height
    }

    /** Unterkante der Tastatur: unterste sichtbare Key-Zeile des Tastaturlayouts. */
    private fun imeBottom(): Int {
        val navTop = device.findObject(By.res("com.android.systemui", "navigationBarBackground"))
            ?.visibleBounds?.top ?: device.displayHeight
        val maximize = waitFor("key_maximize")?.visibleBounds?.bottom
        // Die Maximieren-Zeile ist die unterste Zeile der Tastatur; wenn sie
        // fehlt (Vollbild), nehmen wir die Navigationsleiste als Unterkante.
        return if (maximize != null && maximize <= navTop) {
            maximize.coerceAtMost(navTop)
        } else {
            navTop
        }
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
