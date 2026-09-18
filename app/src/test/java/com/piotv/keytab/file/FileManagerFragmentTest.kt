package com.piotv.keytab.file

import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * Robolectric-Tests für den In-App-Dateimanager (`file`-Paket, bisher 0 %).
 * Das Fragment wird einer echten (Robolectric-)Activity hinzugefügt; die
 * asynchrone Verzeichnisliste (KeyTabExecutors.io + view.post) wird über
 * Main-Looper-Idle mit Timeout eingesammelt — ohne Verzeichnis-Assertions,
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

    /** Sammelt Main-Looper-Tasks bis zum Timeout (I/O-Pool + view.post). */
    private fun drain() {
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            if (path(rootOwner).text.isNotEmpty()) return
            Thread.sleep(20)
        }
        ShadowLooper.idleMainLooper()
    }

    private lateinit var rootOwner: AppCompatActivity

    @Test
    fun `neues Fragment haengt Start-Tab an und zeigt Pfad an`() {
        val fragment = FileManagerFragment()
        rootOwner = attach(fragment)
        drain()
        assertEquals(1, tabs(rootOwner).tabCount)
        assertTrue(path(rootOwner).text.isNotEmpty())
    }

    @Test
    fun `neuer Tab zaehlt Tabs hoch, schliessen reduziert`() {
        val fragment = FileManagerFragment()
        rootOwner = attach(fragment)
        drain()
        root(rootOwner).findViewById<View>(R.id.btn_new_tab).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(2, tabs(rootOwner).tabCount)
        root(rootOwner).findViewById<View>(R.id.btn_close_tab).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(1, tabs(rootOwner).tabCount)
    }

    @Test
    fun `Up ohne Elternteil aendert nichts, Tab-Wechsel laedt neu`() {
        val fragment = FileManagerFragment()
        rootOwner = attach(fragment)
        drain()
        val before = path(rootOwner).text.toString()
        root(rootOwner).findViewById<View>(R.id.btn_up).performClick()
        ShadowLooper.idleMainLooper()
        drain()
        // "/" hat kein parentFile → Pfad unverändert
        assertEquals(before, path(rootOwner).text.toString())
        // Tab-Wechsel (reselekt denselben Tab) → reload läuft, Pfad bleibt
        tabs(rootOwner).getTabAt(0)?.select()
        ShadowLooper.idleMainLooper()
        drain()
        assertEquals(1, tabs(rootOwner).tabCount)
    }
}

