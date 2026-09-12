package com.piotv.keytab.ime

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeApplierTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    private fun nightContext(): Context {
        val conf = Configuration(app.resources.configuration)
        conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            Configuration.UI_MODE_NIGHT_YES
        return app.createConfigurationContext(conf)
    }

    private fun inflateRoot(ctx: Context): View =
        LayoutInflater.from(ContextThemeWrapper(ctx, R.style.Theme_KeyTab))
            .inflate(R.layout.keyboard_view, null)

    private fun drawableKeyButtons(root: View): List<Button> {
        val out = mutableListOf<Button>()
        fun walk(v: View) {
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
            } else {
                if (v is Button && v.background is android.graphics.drawable.StateListDrawable) out.add(v)
            }
        }
        walk(root)
        return out
    }

    private fun insetBgViews(root: View): List<View> {
        val out = mutableListOf<View>()
        fun walk(v: View) {
            if (v.background is android.graphics.drawable.InsetDrawable) out.add(v)
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
        }
        walk(root)
        return out
    }

    /**
     * Drawable des Default-States (leeres State-Set) eines StateListDrawable.
     * ThemeApplier setzt für Tasten eine StateListDrawable (pressed = Highlight,
     * default = Taste-Farbe) → ein Cast auf ColorDrawable greift nicht. Die States
     * liegen in DrawableContainerState.mDrawables/mStateSets (Reflexion, nur Test).
     * Nicht-StateList-Drawables werden unverändert zurückgegeben.
     */
    private fun defaultStateDrawable(d: android.graphics.drawable.Drawable?)
        : android.graphics.drawable.Drawable? {
        if (d == null) return null
        val sld = d as? android.graphics.drawable.StateListDrawable ?: return d
        fun fieldOf(obj: Any, name: String): Any? {
            var c: Class<*>? = obj.javaClass
            while (c != null) {
                try {
                    val f = c.getDeclaredField(name)
                    f.isAccessible = true
                    return f.get(obj)
                } catch (_: NoSuchFieldException) {
                    c = c.superclass
                }
            }
            return null
        }
        val state = fieldOf(sld, "mDrawableContainerState") ?: return null
        val drawables = fieldOf(state, "mDrawables") as? Array<*> ?: return null
        val stateSets = fieldOf(state, "mStateSets") as? Array<*> ?: return null
        for (i in stateSets.indices) {
            val set = stateSets[i] as? IntArray ?: continue
            if (set.isEmpty()) return drawables[i] as? android.graphics.drawable.Drawable
        }
        return null
    }

    /** Farbe des Default-States (0 = null / nicht ermittelbar). */
    private fun defaultStateColor(d: android.graphics.drawable.Drawable?): Int? =
        when (val dd = defaultStateDrawable(d)) {
            is android.graphics.drawable.ColorDrawable -> dd.color
            is android.graphics.drawable.GradientDrawable -> dd.color?.defaultColor
            else -> null
        }

    /** Eckradius des Default-State-Drawables: 0f = flach/eckig (ColorDrawable). */
    private fun cornerRadiusOf(d: android.graphics.drawable.Drawable?): Float? =
        when (val dd = defaultStateDrawable(d)) {
            is android.graphics.drawable.GradientDrawable -> dd.cornerRadius
            is android.graphics.drawable.ColorDrawable -> 0f
            else -> null
        }

    @Test
    fun `Alpha der Taste-Farbe wird auf Mond-Taste und Vorschlaege angewendet`() {
        val cfg = nightContext()
        val root = inflateRoot(cfg)
        val prefs = app.getSharedPreferences(ThemePrefs.PREFS, android.content.Context.MODE_PRIVATE)
        prefs.edit().remove(ThemePrefs.colorKey(true, ThemePrefs.KIND_KEY)).apply()
        val halfAlpha = 0x80FF0000.toInt()
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_KEY, halfAlpha)

        ThemeApplier.apply(prefs, true, root, cfg)

        val moon = defaultStateColor(root.findViewById<Button>(R.id.key_theme).background)
        println("moon color=${moon?.toUInt()?.toString(16)}")
        assertTrue("Mond-Taste: Alpha-Farbe kam nicht an",
            moon == halfAlpha)
        val sug2 = defaultStateColor(root.findViewById<View>(R.id.sug_2).background)
        println("sug2 color=${sug2?.toUInt()?.toString(16)}")
        assertTrue("Vorschlag sug_2: Alpha-Farbe kam nicht an",
            sug2 == halfAlpha)
    }

    @Test
    fun `Export enthaelt alle Theme-Farben als JSON`() {
        val ctx = app
        val prefs = ctx.getSharedPreferences(ThemePrefs.PREFS, android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_KEY, 0x80FF0000.toInt())
        ThemePrefs.setColor(prefs, false, ThemePrefs.KIND_BG, 0xFF00FF00.toInt())
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_GAMING, 0xFFFF0000.toInt())
        prefs.edit().putBoolean(ThemePrefs.KEY_GAMING, true).apply()

        val json = ThemePrefs.exportColors(prefs)
        org.json.JSONObject(json).apply {
            assertEquals(0x80FF0000.toInt(), getJSONObject("dark").getInt(ThemePrefs.KIND_KEY))
            assertEquals(0xFF00FF00.toInt(), getJSONObject("light").getInt(ThemePrefs.KIND_BG))
            assertTrue(getJSONObject("gaming").getBoolean("enabled"))
            assertEquals(0xFFFF0000.toInt(), getJSONObject("dark").getInt(ThemePrefs.KIND_GAMING))
            assertTrue(has("version"))
        }
    }

    @Test
    fun `Taste-Farbe ueberfaerbt die Tab-Leiste (ABC-Notes-Files-Terminal)`() {
        val cfg = nightContext()
        val root = inflateRoot(cfg)
        val prefs = app.getSharedPreferences(ThemePrefs.PREFS, android.content.Context.MODE_PRIVATE)
        prefs.edit().remove(ThemePrefs.colorKey(true, ThemePrefs.KIND_KEY)).apply()
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_KEY, 0xFFAABBCC.toInt())

        val tabsLayout = root.findViewById<com.google.android.material.tabs.TabLayout>(R.id.ime_tabs)
        assertTrue("ime_tabs nicht gefunden", tabsLayout != null)
        val tabViews = mutableListOf<View>()
        for (i in 0 until tabsLayout.childCount) {
            val c = tabsLayout.getChildAt(i)
            if (c is ViewGroup) {
                for (j in 0 until c.childCount) tabViews.add(c.getChildAt(j))
            }
        }
        assertTrue("keine Tab-Views gefunden", tabViews.isNotEmpty())
        val before = tabViews.map { it.background?.constantState }
        println("Tab-Views gesamt=${tabViews.size} vor=Material-Ripple")

        ThemeApplier.apply(prefs, true, root, cfg)

        val unchanged = tabViews.filterIndexed { i, v -> v.background?.constantState == before[i] }.size
        println("nachher unveraendert=$unchanged")
        assertTrue(
            "Taste-Farbe muss die Tab-Leiste (ABC/Notes/Files/Terminal) ueberfaerben, aber " +
                "$unchanged von ${tabViews.size} behalten ihren Default-Hintergrund",
            unchanged == 0)
        // Vollständig ausgefüllt: flacher StateListDrawable-Hintergrund (kein Inset/Ripple)
        assertTrue(
            "Tabs muessen ihre Zelle voll ausfuellen (flacher Hintergrund ohne Inset)",
            tabViews.all { it.background is android.graphics.drawable.StateListDrawable })
    }

    @Test
    fun `Taste-Farbe ueberfaerbt alle Tasten im abc-Tab`() {
        val cfg = nightContext()
        val root = inflateRoot(cfg)
        val prefs = app.getSharedPreferences(ThemePrefs.PREFS, android.content.Context.MODE_PRIVATE)
        prefs.edit().remove(ThemePrefs.colorKey(true, ThemePrefs.KIND_KEY)).apply()
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_KEY, 0xFFAABBCC.toInt())

        val keys = drawableKeyButtons(root)
        assertTrue("keine KeyDark-Tasten gefunden", keys.isNotEmpty())
        val before = keys.map { it.background?.constantState }
        println("KeyDark-Tasten gesamt=${keys.size}")

        ThemeApplier.apply(prefs, true, root, cfg)

        val unchanged = keys.filterIndexed { i, b -> b.background?.constantState == before[i] }.size
        println("nachher unveraendert=$unchanged")
        assertTrue(
            "Taste-Farbe muss ALLE Tasten (abc-Buchstaben + Spezial) ueberfaerben, aber " +
                "$unchanged von ${keys.size} haben ihren Default-Hintergrund behalten",
            unchanged == 0)
    }

    @Test
    fun `Obere Zeilen lassen den Hintergrund bei Alpha durchscheinen`() {
        val cfg = nightContext()
        val root = inflateRoot(cfg)
        val prefs = app.getSharedPreferences(ThemePrefs.PREFS, android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_BG, 0x8000AA00.toInt())
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_KEY, 0x80FF0000.toInt())

        ThemeApplier.apply(prefs, true, root, cfg)

        // Nur der Root trägt die Background-Farbe – exakt eine Schicht, damit Alpha
        // in den oberen Zeilen genauso wirkt wie in den Buchstabenreihen.
        assertEquals("Root muss die themierte Background-Farbe tragen",
            0x8000AA00.toInt(),
            (root.background as? android.graphics.drawable.ColorDrawable)?.color ?: -1)

        val tabs = root.findViewById<com.google.android.material.tabs.TabLayout>(R.id.ime_tabs)
        fun isTransparent(v: View): Boolean {
            val d = v.background ?: return true
            return d is android.graphics.drawable.ColorDrawable &&
                d.color == android.graphics.Color.TRANSPARENT
        }
        // 1) TabLayout: kein Material-colorSurface-Grau, kein zweites bg
        println("tab-bar bg=${tabs.background}")
        assertTrue("TabLayout muss transparent sein (kein Grau, kein zweites bg)",
            isTransparent(tabs))
        // 2) Obere Zeile (Eltern-LinearLayout der Tab-Leiste) transparent
        assertTrue("Obere Tab-Zeile muss transparent sein",
            isTransparent(tabs.parent as View))
        // 3) Vorschlagsleiste (2. obere Zeile) transparent
        val sugBar = root.findViewById<View>(R.id.suggestion_bar)
        println("sug-bar bg=${sugBar?.background}")
        assertTrue("Vorschlagsleiste muss transparent sein", isTransparent(sugBar))
        // 4) Buchstabenreihe ohne eigenen Hintergrund → identische Compositing-Tiefe
        val numRow = root.findViewById<View>(R.id.num_row)
        assertTrue("Buchstabenreihe bleibt hintergrundfrei", isTransparent(numRow))

        // 5) SlidingTabIndicator transparent + Tab-Zellen flach mit Alpha-Tastenfarbe
        var cells = 0
        for (i in 0 until tabs.childCount) {
            val c = tabs.getChildAt(i)
            if (c is ViewGroup) {
                assertTrue("Indicator-Container darf kein graues Drawable behalten",
                    isTransparent(c))
                for (j in 0 until c.childCount) {
                    val cell = c.getChildAt(j)
                    assertTrue("Tab-Zelle ${j} muss flach gefuellt sein (kein Inset)",
                        cell.background is android.graphics.drawable.StateListDrawable)
                    assertEquals("Tab-Zelle ${j}: Alpha-Tastenfarbe im Default-State",
                        0x80FF0000.toInt(), defaultStateColor(cell.background) ?: -1)
                    cells++
                }
            }
        }
        assertTrue("keine Tab-Zellen gefunden", cells > 0)
        // 6) Wortvorschläge behalten die Alpha-Tastenfarbe (über transparentem Root)
        assertEquals("sug_2: Alpha-Tastenfarbe", 0x80FF0000.toInt(),
            defaultStateColor(root.findViewById<View>(R.id.sug_2).background) ?: -1)
    }

    @Test
    fun `Mond- und Einstellungs-Taste sind eckig und flach gefuellt`() {
        val cfg = nightContext()
        val root = inflateRoot(cfg)
        val prefs = app.getSharedPreferences(ThemePrefs.PREFS, android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_KEY, 0x80FF0000.toInt())

        ThemeApplier.apply(prefs, true, root, cfg)

        for (id in intArrayOf(R.id.key_theme, R.id.key_settings)) {
            val btn = root.findViewById<Button>(id)
            val radius = cornerRadiusOf(btn.background)
            println("btn $id bg=${btn.background} radius=$radius")
            assertEquals("Taste $id darf NICHT abgerundet sein", 0f, radius ?: -1f, 0.001f)
            assertEquals("Taste $id: Alpha-Tastenfarbe im Default-State",
                0x80FF0000.toInt(), defaultStateColor(btn.background) ?: -1)
        }
    }

    @Test
    fun `Tab-Zellen ohne Insets und ohne Rundung (keine Rundfelder, keine Luecken)`() {
        val cfg = nightContext()
        val root = inflateRoot(cfg)
        val tabs = root.findViewById<com.google.android.material.tabs.TabLayout>(R.id.ime_tabs)
        val cells = mutableListOf<View>()
        for (i in 0 until tabs.childCount) {
            val c = tabs.getChildAt(i)
            if (c is ViewGroup) for (j in 0 until c.childCount) cells.add(c.getChildAt(j))
        }
        assertTrue("keine Tab-Zellen gefunden", cells.isNotEmpty())

        // 1) Schon das LAYOUT muss flach sein: früher tab_bg_inset (InsetDrawable um
        //    key_bg mit 8dp-Rundung) → graue abgerundete Felder + Lücken links/rechts
        val insetsBefore = cells.count {
            it.background is android.graphics.drawable.InsetDrawable }
        println("Tab-Zellen=${cells.size} Insets im Layout=$insetsBefore " +
            "Insets gesamt=${insetBgViews(root).size}")
        assertEquals("Tab-Zellen dürfen im Layout kein Inset-Drawable haben (Lücken!)",
            0, insetsBefore)
        assertTrue("tab_bg_flat darf kein InsetDrawable sein",
            androidx.core.content.ContextCompat.getDrawable(cfg, R.drawable.tab_bg_flat)
                !is android.graphics.drawable.InsetDrawable)

        // 2) Nach apply: eckig, Alpha-Tastenfarbe, kein überlagerndes Ripple-Foreground
        val prefs = app.getSharedPreferences(ThemePrefs.PREFS, android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        ThemePrefs.setColor(prefs, true, ThemePrefs.KIND_KEY, 0x80FF0000.toInt())
        ThemeApplier.apply(prefs, true, root, cfg)
        for (i in cells.indices) {
            val cell = cells[i]
            assertEquals("Tab-Zelle $i muss eckig sein", 0f,
                cornerRadiusOf(cell.background) ?: -1f, 0.001f)
            assertEquals("Tab-Zelle $i: Alpha-Tastenfarbe im Default-State",
                0x80FF0000.toInt(), defaultStateColor(cell.background) ?: -1)
            assertNull("Tab-Zelle $i: kein Ripple-Foreground", cell.foreground)
        }
        assertEquals("keine Insets in der Tab-Leiste nach apply", 0, insetBgViews(tabs).size)
    }
}