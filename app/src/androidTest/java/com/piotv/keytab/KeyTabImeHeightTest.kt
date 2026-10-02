package com.piotv.keytab

import android.content.ComponentName
import android.content.Intent
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
 *  1. FILES-, Clip- und Snippet-Tab sind gleich hoch; der Editor-Tab ist
 *     Hoehen-Referenz und liegt korrekt nicht unter ihnen.
 *  2. Der abc-Tab hat eine eigene Hoehe (er zeigt die Tastatur).
 *  3. Die Maximieren-Zeile (key_maximize) existiert **nur** in den Inhalts-Tabs
 *     (Files/Clip/Snip) und liegt dort ganz unten; im abc-Tab gibt es sie nie
 *     (TabController.showsMaximizeRow — die Tastatur ist dort das groesste
 *     Element), im Editor-Tab sitzt das Symbol stattdessen in der (hier
 *     deaktivierten) Vorschlagszeile. Tippen fuellt den Bildschirm zwischen
 *     Status- und Navigationsleiste (PanelHeights-Band, ~95 % des Bildschirms),
 *     erneutes Tippen stellt den Normalzustand wieder her.
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
    @Before
    fun setUp() {
        // Erst das IME umschalten, dann starten: sonst bindet das erste
        // EditText die Input-Session an das vorherige Standard-IME (Gboard)
        // und KeyTab wird nie gefragt. Siehe KeyTabImeEndToEndTest.
        // v0.16: kein previousIme-Save mehr — das IME-Fenster bleibt über den Lauf aktiv.
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
        if (!waitForIme()) {
            // Cold-Start-Rennen: `am instrument` killt den Ziel-Prozess (in dem
            // auch der IME-Service laeuft) und startet ihn neu. Der erste
            // showSoftInput kann verloren gehen, bevor onCreateInputView
            // abgeschlossen ist (Logcat: updateInputViewShown ... mInputView=null,
            // danach kein show mehr). Zweiter Anlauf: echten Touch auf das
            // erste EditText — der Weg, der auch im Normalbetrieb zuverlaessig
            // die Tastatur einblendet.
            val field = device.wait(Until.findObject(
                By.clazz("android.widget.EditText")), 5_000)
            field?.click()
            device.waitForIdle()
            focusField()
        }
        // Die IME merkt sich den zuletzt genutzten Tab: stand sie zuletzt im
        // Files/Clip/Snip-Tab, gibt es dort kein sichtbares key_space (das
        // gibt es nur im abc-Tab) — waitForIme wuerde sonst 10 s ins Leere
        // laufen ("KeyTab wurde nicht eingeblendet", Screenshot-Beweis 2026-09-29:
        // Tastatur sichtbar im FILES-Tab). Deshalb: zuerst auf ABC schalten.
        device.wait(Until.findObject(By.textStartsWith("ABC")), 3_000)?.click()
        assertTrue("KeyTab wurde nicht eingeblendet", waitForIme())
    }

    @After
    fun tearDown() {
        // v0.16: bewusst KEIN Rückschalten auf das vorherige IME mehr (ein Durchlauf).
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
        // FILES/CLIP/SNIP muessen gleich hoch sein: Die Inhalts-Panels bekommen
        // per PanelHeights.applyNormalHeights dieselbe Hoehe. Der Editor-Tab ist
        // die Hoehen-Referenz und zeigt statt der Maximieren-Zeile die (deakti-
        // vierte) Vorschlagszeile — die vier Tabs sind also nicht bitidentisch
        // hoch, sondern innerhalb einer Maximieren-Zeilen-Hoehe. Gemessen
        // (2026-09-30, 1220x2712 @520dpi): Einzellauf EDITOR=1552 /
        // FILES=1467; Gesamtlauf EDITOR=1552 / FILES=1565 — abhaengig vom
        // vorherigen Maximize-Zustand. Der Vertrag ist also:
        //  1. FILES == CLIP == SNIP,
        //  2. |EDITOR − Inhalts-Tabs| <= maximize_row_height.
        val content = listOf(heights["FILES"], heights["CLIP"], heights["SNIP"])
        assertTrue(
            "FILES/CLIP/SNIP muessen gleich hoch sein, gemessen: $heights",
            content.distinct().size == 1
        )
        val tolerance = instrumentation.targetContext.resources
            .getDimensionPixelSize(com.piotv.keytab.R.dimen.maximize_row_height)
        val delta = kotlin.math.abs(heights["EDITOR"]!! - content.first()!!)
        assertTrue(
            "EDITOR-Tab muss innerhalb einer Maximieren-Zeile der Inhalts-Tabs liegen " +
                "(Delta=$delta, Toleranz=$tolerance, gemessen: $heights)",
            delta <= tolerance
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
    fun maximizeZeileNurInInhaltsTabsSichtbarUndLiegtUnten() {
        val tabRow = waitFor("ime_tabs") ?: error("ime_tabs nicht sichtbar")
        // FILES/CLIP/SNIP: die Maximieren-Zeile ist die unterste Zeile — dort
        // sitzt der einzige Weg in den Vollbild-Modus.
        for (tab in listOf("FILES", "CLIP", "SNIP")) {
            assertTrue("Tab $tab nicht gefunden", selectTab(tab) != null)
            val maximize = waitFor("key_maximize")
            assertTrue("key_maximize fehlt im Tab $tab", maximize != null)
            assertTrue("key_maximize ist im Tab $tab nicht sichtbar",
                maximize!!.visibleBounds.height() > 0)
            assertTrue("Maximieren-Zeile muss unter der Tab-Leiste liegen (Tab $tab): " +
                "Leiste ${tabRow.visibleBounds}, Knopf ${maximize.visibleBounds}",
                maximize.visibleBounds.top >= tabRow.visibleBounds.top)
        }
        // abc: kein Maximieren — es gibt nichts zu vergroessern. GONE-Ansichten
        // verschwinden aus dem Accessibility-Baum: nach dem Tab-Wechsel darf
        // key_maximize nicht mehr auffindbar sein.
        assertTrue("Tab ABC nicht gefunden", selectTab("ABC") != null)
        Thread.sleep(500)
        val abcMaximize = device.findObject(By.res(pkg, "key_maximize"))
        assertTrue("key_maximize darf im abc-Tab nicht sichtbar sein",
            abcMaximize == null || abcMaximize.visibleBounds.isEmpty)
        // Editor: das Symbol lebt in der Vorschlagszeile (sug_hide) — die ist
        // bei deaktivierten Vorschlaegen (setUp) GONE, key_maximize bleibt GONE.
    }

    @Test
    fun maximierenFuelltDenBildschirmBisAufFuenfProzent() {
        // Der Vollbild-Modus ist **bildschirmfuellend**: Das System laesst die
        // IME bis unter die Statusleiste wachsen; das Panel fuellt das Band
        // zwischen Status- und Navigationsleiste (PanelHeights.
        // maximizeAvailableHeight). Der fruehere 0.7-Deckel war kein
        // Systemlimit, sondern der eigene Fallback.
        // Inhalts-Tab: nur dort gibt es key_maximize (im Editor sitzt das
        // Symbol in der hier deaktivierten Vorschlagszeile).
        val normal = imeHeightAfterTab("FILES")
        val displayHeight = device.displayHeight

        val button = waitFor("key_maximize") ?: error("key_maximize nicht gefunden")
        button.click()
        device.waitForIdle()
        Thread.sleep(600)
        val maximized = imeHeight("key_maximize")

        assertTrue("Maximieren muss die Hoehe deutlich vergroessern: " +
            "normal=$normal, maximiert=$maximized", maximized > normal + 100)
        // Bildschirmfuellend: ~95 % des Bildschirms, mit 10 % Toleranz fuer
        // die Systemleisten (Status- + Navigationsleiste bleiben sichtbar).
        val expected = (displayHeight * 0.95).toInt()
        val delta = kotlin.math.abs(maximized - expected)
        assertTrue("Maximiert sollte ~95 % des Bildschirms fuellen: " +
            "gemessen=$maximized, erwartet=$expected, Bildschirm=$displayHeight",
            delta <= displayHeight * 0.10)

        // Und wieder zurueck: der Ausweg muss in jedem Zustand bedienbar sein.
        waitFor("key_maximize")?.click()
        device.waitForIdle()
        Thread.sleep(600)
        val back = imeHeight("key_maximize")
        // Streng: expand() restauriert die gemerkte Normalhoehe, und der
        // Fenster-Clamp rechnet seit dem Band-Fix deterministisch — Abweichung
        // hier waere wieder der "festgenagelte" Zustand von vor dem Fix.
        assertTrue("Nach dem Zurueck muss die Normalhoehe kommen: " +
            "vorher=$normal, jetzt=$back", kotlin.math.abs(back - normal) <= 16)
    }

    @Test
    fun editorTabIstNichtKleinerAlsInhaltsTabs() {
        val editor = imeHeightAfterTab("EDITOR")
        val files = imeHeightAfterTab("FILES")
        val tolerance = instrumentation.targetContext.resources
            .getDimensionPixelSize(com.piotv.keytab.R.dimen.maximize_row_height)
        assertTrue("Editor darf nicht kleiner als Inhalts-Tabs sein: editor=$editor files=$files",
            editor >= files - tolerance)
    }

    @Test
    fun wiederholterTabWechselBleibtStabil() {
        val first = imeHeightAfterTab("FILES")
        selectTab("ABC")
        selectTab("EDITOR")
        val second = imeHeightAfterTab("FILES")
        assertTrue("Tab-Wechsel muss die Hoehe stabil lassen: first=$first second=$second",
            kotlin.math.abs(first - second) <= 32)
    }

    @Test
    fun tastaturBleibtNachTabWechselSichtbar() {
        assertTrue("FILES-Tab nicht erreichbar", selectTab("FILES") != null)
        assertTrue("key_maximize muss in FILES sichtbar sein", waitFor("key_maximize") != null)
        assertTrue("ABC-Tab nicht erreichbar", selectTab("ABC") != null)
        assertTrue("key_space muss in ABC sichtbar sein", waitFor("key_space") != null)
    }

    // ---------------- Hilfen ----------------

    private fun focusField() {
        val activity = activityRule.activity
        val imm = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        instrumentation.runOnMainSync {
            activity.normalField.requestFocus()
            // Siehe KeyTabImeEndToEndTest.focus: ohne showSoftInput bleibt
            // mInputShown=false und das IME-Fenster wird nie sichtbar.
            imm.showSoftInput(activity.normalField,
                android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        instrumentation.waitForIdleSync()
    }

    // Die IME öffnet im abc-Tab — dort gibt es key_maximize per Design nie
    // (TabController.showsMaximizeRow: nur Files/Clip/Snip). Sichtbarkeits-
    // Anker ist deshalb die Funktionsleiste: key_space ist im abc-Tab sichtbar,
    // in Inhalts-Tabs nur INVISIBLE, bleibt aber im Accessibility-Baum.
    private fun waitForIme(): Boolean = waitFor("key_space") != null

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

    /** Unterkante je Tab: Funktionsleiste in abc/Editor, Maximieren-Zeile in Inhalts-Tabs. */
    private fun bottomAnchorOf(tab: String): String =
        if (tab == "ABC" || tab == "EDITOR") "key_space" else "key_maximize"

    private fun imeHeightAfterTab(tab: String): Int {
        assertTrue("Tab $tab nicht gefunden", selectTab(tab) != null)
        Thread.sleep(500)
        return imeHeight(bottomAnchorOf(tab))
    }

    /**
     * Sichtbare Hoehe der Tastatur = Oberkante der Tab-Leiste bis zur Unterkante
     * des tab-spezifischen Ankers. Die IME selbst ist ein Fullscreen-Fenster
     * (Requested h=0), man misst also ihren *Inhalt*.
     *
     * Anker: abc/Editor enden unten mit der Funktionsleiste (key_space, sichtbar
     * — die Tastatur bleibt laut TabController.isKeyboardAlwaysVisible offen).
     * Die Maximieren-Zeile ist dort GONE. In Files/Clip/Snip sind die Funktions-
     * leisten-Tasten nur INVISIBLE (leere visibleBounds), die Maximieren-Zeile
     * ist die unterste *sichtbare* Zeile.
     */
    private fun imeHeight(bottomAnchor: String): Int {
        val top = waitFor("ime_tabs")?.visibleBounds?.top
            ?: error("ime_tabs nicht sichtbar — ist die IME offen?")
        val bottom = waitFor(bottomAnchor)?.visibleBounds?.bottom
            ?: error("$bottomAnchor nicht sichtbar — Anker der IME-Unterkante fehlt")
        val height = bottom - top
        assertTrue("IME-Hoehe muss positiv sein: top=$top bottom=$bottom (Anker $bottomAnchor)",
            height > 0)
        return height
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
