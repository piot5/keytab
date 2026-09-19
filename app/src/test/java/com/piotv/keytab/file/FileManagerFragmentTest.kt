package com.piotv.keytab.file

import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.R
import com.piotv.keytab.ime.KeyTabExecutors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Robolectric-Tests für den In-App-Dateimanager (`file`-Paket, bisher 0 %).
 * Das Fragment wird einer echten (Robolectric-)Activity hinzugefügt; die
 * asynchrone Verzeichnisliste (KeyTabExecutors.io + view.post) wird
 * deterministisch eingesammelt (siehe [drain]) — ohne Verzeichnis-Assertions,
 * die vom Sandbox-Storage abhängen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileManagerFragmentTest {

    /** Activity inkl. Start (setup), dann Fragment anhängen. */
    private fun attach(fragment: Fragment): AppCompatActivity {
        val activity: AppCompatActivity =
            Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(R.style.Theme_KeyTab)
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment)
            .commitNowAllowingStateLoss()
        ShadowLooper.idleMainLooper()
        return activity
    }

    private fun root(activity: AppCompatActivity): View =
        activity.findViewById<View>(android.R.id.content)

    private fun tabs(activity: AppCompatActivity): TabLayout =
        root(activity).findViewById(R.id.file_tabs)

    private fun path(activity: AppCompatActivity): TextView =
        root(activity).findViewById(R.id.current_path)

    /**
     * Sammelt die asynchrone Verzeichnisliste ein: I/O-Pool + `view.post`.
     *
     * Früher pollte dieser Helfer mit `Thread.sleep(20)` bis zu 3 s — das war
     * langsam und zeitabhängig (flaky unter CI-Last). Jetzt wird der I/O-Pool
     * einmal gezielt geleert: ein Marker-Task wird eingereiht und abgewartet,
     * danach laufen die `view.post`-Runnables über den Main-Looper.
     */
    private fun drain() {
        KeyTabExecutors.io.submit { }.get(10, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()
        ShadowLooper.idleMainLooper()
    }

    private lateinit var rootOwner: AppCompatActivity

    @Test
    fun `neues Fragment haengt Start-Tab an und zeigt Pfad an`() {
        val fragment = FileManagerFragment()
        rootOwner = attach(fragment)
        drain()
        assertEquals(1, tabs(rootOwner).tabCount)
        assertTrue("Pfad muss gesetzt sein", path(rootOwner).text.isNotEmpty())
        assertEquals("Tab-Beschriftung entspricht dem Verzeichnisnamen",
            File(path(rootOwner).text.toString()).name.ifEmpty { "/" },
            tabs(rootOwner).getTabAt(0)?.text?.toString())
    }

    @Test
    fun `neuer Tab zaehlt Tabs hoch, schliessen reduziert`() {
        val fragment = FileManagerFragment()
        rootOwner = attach(fragment)
        drain()
        root(rootOwner).findViewById<View>(R.id.btn_new_tab).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(2, tabs(rootOwner).tabCount)
        assertEquals("Neuer Tab wird selektiert", 1, tabs(rootOwner).selectedTabPosition)
        root(rootOwner).findViewById<View>(R.id.btn_close_tab).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(1, tabs(rootOwner).tabCount)
        assertEquals("Auswahl bleibt im gueltigen Bereich", 0, tabs(rootOwner).selectedTabPosition)
    }

    /** Der letzte Tab darf nicht schliessbar sein — sonst bliebe der Pfad leer. */
    @Test
    fun `letzter Tab laesst sich nicht schliessen`() {
        val fragment = FileManagerFragment()
        rootOwner = attach(fragment)
        drain()
        val pathBefore = path(rootOwner).text.toString()
        root(rootOwner).findViewById<View>(R.id.btn_close_tab).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals("Tab bleibt erhalten", 1, tabs(rootOwner).tabCount)
        assertEquals("Pfad bleibt erhalten", pathBefore, path(rootOwner).text.toString())
    }

    /** Jeder Tab merkt sich sein eigenes Verzeichnis (Kernversprechen des Tabs-Modells). */
    @Test
    fun `Tab-Wechsel stellt das Verzeichnis des Tabs wieder her`() {
        val fragment = FileManagerFragment()
        rootOwner = attach(fragment)
        drain()
        val firstPath = path(rootOwner).text.toString()

        // Neuer Tab erbt das aktuelle Verzeichnis, dann eine Ebene hoch
        root(rootOwner).findViewById<View>(R.id.btn_new_tab).performClick()
        ShadowLooper.idleMainLooper()
        drain()
        root(rootOwner).findViewById<View>(R.id.btn_up).performClick()
        ShadowLooper.idleMainLooper()
        drain()
        val secondPath = path(rootOwner).text.toString()

        // Zurueck auf Tab 0 → dessen Pfad muss unveraendert sein
        tabs(rootOwner).getTabAt(0)?.select()
        ShadowLooper.idleMainLooper()
        drain()
        assertEquals("Tab 0 behaelt sein Verzeichnis", firstPath, path(rootOwner).text.toString())

        // Und Tab 1 ebenfalls
        tabs(rootOwner).getTabAt(1)?.select()
        ShadowLooper.idleMainLooper()
        drain()
        assertEquals("Tab 1 behaelt sein Verzeichnis", secondPath, path(rootOwner).text.toString())
    }
}

