package com.piotv.keytab.ime

import android.content.Context
import android.graphics.RectF
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.ColorWheelView
import com.piotv.keytab.Prefs
import com.piotv.keytab.R
import com.piotv.keytab.sections.ColorSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRegressionTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Test fun `Farbrad ignoriert Ecken und Ziehen ausserhalb`() {
        val wheel = ColorWheelView(app)
        wheel.layout(0, 0, 400, 200)
        var picked = 0
        wheel.onColorPicked = { picked++ }
        fun touch(action: Int, x: Float, y: Float): Boolean {
            val event = MotionEvent.obtain(0, 0, action, x, y, 0)
            return wheel.onTouchEvent(event).also { event.recycle() }
        }
        assertFalse(touch(MotionEvent.ACTION_DOWN, 0f, 0f))
        assertEquals(0, picked)
        assertTrue(touch(MotionEvent.ACTION_DOWN, 200f, 100f))
        assertEquals(1, picked)
        assertTrue(touch(MotionEvent.ACTION_MOVE, 399f, 100f))
        assertEquals(1, picked)
        assertTrue(touch(MotionEvent.ACTION_CANCEL, 399f, 100f))
        assertFalse(touch(MotionEvent.ACTION_MOVE, 200f, 100f))
    }

    @Test fun `Farbziel laden schreibt keine Einstellungen`() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val prefs = app.getSharedPreferences(Prefs.FILE, 0)
        prefs.edit().clear().apply()
        val section = ColorSection(activity, prefs, { ThemePrefs.KIND_BG }) { fail("Keine Änderung beim Laden") }
        section.build(LinearLayout(activity))
        section.updateControls(ThemePrefs.KIND_BG)
        section.updateControls(ThemePrefs.KIND_GRADIENT2)
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun `Zweite Verlaufsfarbe erreicht Drawable mit Alpha`() {
        val prefs = app.getSharedPreferences(Prefs.FILE, 0)
        prefs.edit().clear().apply()
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_BG, 123)
        ThemePrefs.setGradientOff(prefs, true, true)
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_GRADIENT2, 0x80123456.toInt())
        assertTrue(ThemePrefs.hasGradient(prefs, true))
        assertEquals(0x80123456.toInt(), ThemePrefs.gradientDrawable(prefs, true, 400)!!.colors!![1])
    }

    @Test fun `Alle optionalen Tab Kombinationen bleiben auch bei erneutem Setup korrekt`() {
        val ctx = ContextThemeWrapper(app, R.style.Theme_KeyTab)
        val service = Robolectric.buildService(KeyTabImeService::class.java).get()
        // P3: TabController hängt jetzt am Rollen-Interface TabHost statt am
        // früheren 25-Member-KeyboardHost. Der Proxy muss daher nur noch die
        // von TabController benutzten Member bedienen (context + letterPopup).
        val host = Proxy.newProxyInstance(TabHost::class.java.classLoader,
            arrayOf(TabHost::class.java)) { _, method, _ ->
            when (method.name) {
                "getContext" -> ctx
                "getLetterPopup" -> LetterPopup(service)
                else -> null
            }
        } as TabHost
        for (mask in 0..7) {
            val prefs = app.getSharedPreferences(Prefs.FILE, 0)
            prefs.edit().clear().putBoolean(Prefs.KEY_CLIP_TAB, mask and 1 != 0)
                .putBoolean(Prefs.KEY_TERM_TAB, mask and 2 != 0)
                .putBoolean(Prefs.KEY_SNIPPET_TAB, mask and 4 != 0).apply()
            val root = LayoutInflater.from(ctx).inflate(R.layout.keyboard_view, null)
            val controller = TabController(host)
            repeat(2) { controller.setup(root) }
            val tabs = root.findViewById<TabLayout>(R.id.ime_tabs)
            assertEquals(3 + Integer.bitCount(mask), tabs.tabCount)
            if (mask and 4 != 0) {
                tabs.getTabAt(tabs.tabCount - 1)!!.select()
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.snippet_panel).visibility)
                assertEquals(View.GONE, root.findViewById<View>(R.id.term_panel).visibility)
            }
        }
    }

    @Test fun `Bildmodi passen ein beschneiden oder strecken`() {
        assertEquals(RectF(0f, 25f, 100f, 75f), BackgroundImage.destination(200, 100, 100, 100, Prefs.FILL_FIT))
        assertEquals(RectF(-50f, 0f, 150f, 100f), BackgroundImage.destination(200, 100, 100, 100, Prefs.FILL_COVER))
        assertEquals(RectF(0f, 0f, 100f, 100f), BackgroundImage.destination(200, 100, 100, 100, Prefs.FILL_STRETCH))
        assertNull(BackgroundImage.decode(app, "/nonexistent/keytab-image.png"))
        assertNull(BackgroundImage.decode(app, "https://example.com/image.png"))
    }
}
