package com.piotv.keytab.ime

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.R

/**
 * Theme-Verlauf + Farb-Overrides (Background/Highlight/Text, mit Alpha) auf den
 * View-Baum anwenden. Erkennt die Original-Drawables über deren constantState
 * (key_bg-Selector, tab_bg_flat) und baut sie mit den neuen Farben neu. Ohne
 * Overrides ergibt sich der 0.9.4-Look 1:1.
 *
 * Ausgelagert aus [KeyTabImeService] (God-Class-Reduktion): Die Funktion
 * [apply] und das rekursive [forEachView] sind reine View-Tree-/Drawable-
 * Utilities und damit unabhängig vom Service.
 */
object ThemeApplier {

    private const val KEY_RADIUS_DP = 8f
    private const val KEY_INSET_H_DP = 4
    private const val KEY_INSET_V_DP = 3
    private const val FULL_ALPHA = 255
    private const val TAB_TEXT_DIM_FACTOR = 0.6f

    /** Höhe des Akzentbalkens am aktiven Tab (dp) — siehe [ThemeState.tabKeyState]. */
    private const val TAB_INDICATOR_HEIGHT_DP = 3f

    /** Rekursive View-Hierarchie-Traversierung (Action auf jedem View).
     *  Besucht ALLE Views (auch ViewGroups): Tab-Views (LinearLayout) und andere
     *  Container tragen eigene Hintergründe und müssen mit umgefärbt werden. */
    fun forEachView(root: View, action: (View) -> Unit) {
        action(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) forEachView(root.getChildAt(i), action)
        }
    }

    /**
     * Verlauf + Farb-Overrides anwenden.
     *
     * @param prefs SharedPreferences mit den Theme-Werten (keytab_prefs)
     * @param dark Zu verwendender Night-Mode (false = Light-Override)
     * @param root Inflated Tastatur-Root
     * @param ctx Kontext im übersteuerten Theme (Standard-Farben hieraus)
     */
    fun apply(prefs: SharedPreferences, dark: Boolean, root: View, ctx: Context) {
        val s = resolveTheme(prefs, dark, ctx)
        ThemePrefs.gradientDrawable(prefs, dark, ctx.resources.displayMetrics.widthPixels)
            ?.let { root.background = it }
            ?: run { root.background = ColorDrawable(s.bg) }
        forEachView(root) { view -> recolourView(view, root, s) }
        applyTabBar(root, s)
        applySuggestionBar(root, s)
        BackgroundImage.apply(root, ctx, root.background ?: ColorDrawable(s.bg))
    }

    private fun resolveTheme(prefs: SharedPreferences, dark: Boolean, ctx: Context): ThemeState {
        val baseKbdBg = ContextCompat.getColor(ctx, R.color.kbd_bg)
        val baseKeyBg = ContextCompat.getColor(ctx, R.color.key_bg)
        val basePressed = ContextCompat.getColor(ctx, R.color.key_pressed)
        val baseText = ContextCompat.getColor(ctx, R.color.key_text)
        return ThemeState().apply {
            bg = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_BG, baseKbdBg)
            keyBg = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_KEY, baseKeyBg)
            hl = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_HL, basePressed)
            text = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_TEXT, baseText)
            defKeyBg = baseKeyBg
            defKbdBg = baseKbdBg
            defPressed = basePressed
            defText = baseText
            defTabState = ContextCompat.getDrawable(ctx, R.drawable.tab_bg_flat)?.constantState
            accent = ContextCompat.getColor(ctx, R.color.primary)
            dip = ctx.resources.displayMetrics.density
        }
    }

    private fun recolourView(view: View, root: View, s: ThemeState) {
        val state = view.background?.constantState
        if (state == s.defTabState) {
            view.background = s.flatKeyState()
        } else {
            recolourByType(view, root, s)
        }
        if (view is TextView && view.currentTextColor == s.defText) {
            view.setTextColor(s.text)
        }
    }

    private fun recolourByType(view: View, root: View, s: ThemeState) {
        val d = view.background
        if (d is InsetDrawable) {
            view.background = InsetDrawable(
                s.keyBackground(),
                (KEY_INSET_H_DP * s.dip).toInt(), (KEY_INSET_V_DP * s.dip).toInt(),
                (KEY_INSET_H_DP * s.dip).toInt(), (KEY_INSET_V_DP * s.dip).toInt())
        } else if (view is Button && d is StateListDrawable) {
            view.background = s.keyBackground()
        } else if (d is ColorDrawable) {
            when (d.color) {
                s.defKeyBg -> view.background = ColorDrawable(s.keyBg)
                s.defKbdBg -> view.background =
                    if (view === root) ColorDrawable(s.bg)
                    else ColorDrawable(android.graphics.Color.TRANSPARENT)
                s.defPressed -> view.background = ColorDrawable(s.hl)
            }
        }
    }

    private fun applyTabBar(root: View, s: ThemeState) {
        val tabs = root.findViewById<TabLayout>(R.id.ime_tabs) ?: return
        val dimText = androidx.core.graphics.ColorUtils.setAlphaComponent(
            s.text, (android.graphics.Color.alpha(s.text) * TAB_TEXT_DIM_FACTOR).toInt().coerceAtMost(FULL_ALPHA))
        tabs.setTabTextColors(dimText, s.text)
        tabs.background = ColorDrawable(android.graphics.Color.TRANSPARENT)
        disableSlidingIndicator(tabs)
        for (i in 0 until tabs.childCount) {
            val indicator = tabs.getChildAt(i)
            if (indicator is ViewGroup) {
                indicator.background = ColorDrawable(android.graphics.Color.TRANSPARENT)
                for (j in 0 until indicator.childCount) {
                    val cell = indicator.getChildAt(j)
                    // Akzentbalken des aktiven Tabs (3 dp, grün) liegt im
                    // BACKGROUND der Zelle, nicht im Foreground: Das Framework
                    // führt den Zustand nur des Background-Drawables bei jedem
                    // Selektionswechsel nach (refreshDrawableState →
                    // mBackground.setState). View.foreground wird dagegen nur
                    // einmal beim Setzen aufgelöst und beim Tab-Wechsel NICHT
                    // nachgeführt — ein Balken im Foreground bliebe unsichtbar.
                    cell.background = s.tabKeyState()
                    // Das Ripple-Foreground von Material wird entfernt (der
                    // Akzentbalken aus dem Background ersetzt es). Pro Zelle ein
                    // eigenes Drawable, weil ein geteiltes Drawable sich die
                    // Bounds mit dem nächsten Zeichenvorgang teilen würde.
                    cell.foreground = null
                }
            }
        }
    }

    /**
     * Den Material-eigenen (gleitenden) Indikator des TabLayout abschalten:
     * Farbe transparent, Höhe 0.
     *
     * Der Akzentbalken kommt aus dem **Background** der Tab-Zelle
     * ([ThemeState.tabKeyState]) — siehe `keyboard_view.xml`. Der
     * Indikator des TabLayout liegt zeichentechnisch *unter* den Tab-Zellen
     * (TabLayout.onDraw → SlidingTabIndicator → TabView.background), wäre also
     * hinter der flachen Zellenfarbe unsichtbar; ohne das Abschalten läge
     * zusätzlich ein zweiter Balken darunter.
     */
    private fun disableSlidingIndicator(tabs: TabLayout) {
        tabs.setSelectedTabIndicatorColor(android.graphics.Color.TRANSPARENT)
        tabs.setSelectedTabIndicatorHeight(0)
    }

    /**
     * Farbbalken am oberen Rand eines Views (Höhe in Pixeln) — der grüne Akzent
     * über dem aktiven Tab.
     *
     * Als oberster Layer des Zellen-Backgrounds ([ThemeState.tabKeyState])
     * eingesetzt. Eigene Drawable-Klasse statt `GradientDrawable`/`setLayerSize`
     * als Balken: dort sind Layer-Reihenfolge und `setLayerSize(-1, h)`
     * (Intrinsic vs. Match-Parent) versionsabhängig; hier ist das Zeichnen
     * eindeutig (Rechteck am oberen Rand, Höhe [heightPx]). `internal` (statt
     * private), damit Tests die Klasse direkt prüfen können.
     */
    internal class TabAccentBarDrawable(val color: Int, val heightPx: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

        override fun draw(canvas: Canvas) {
            val h = heightPx.coerceAtMost(bounds.height())
            if (h > 0 && bounds.width() > 0) {
                canvas.drawRect(
                    bounds.left.toFloat(), bounds.top.toFloat(),
                    bounds.right.toFloat(), (bounds.top + h).toFloat(), paint)
            }
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter; invalidateSelf()
        }
        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun applySuggestionBar(root: View, s: ThemeState) {
        for (id in intArrayOf(R.id.key_theme, R.id.key_settings, R.id.key_maximize)) {
            val btn = root.findViewById<Button>(id) ?: continue
            btn.setTextColor(s.text)
            btn.background = s.flatKeyState()
        }
        for (id in intArrayOf(R.id.sug_emoji, R.id.sug_back, R.id.sug_grid,
                R.id.sug_1, R.id.sug_2, R.id.sug_3, R.id.sug_4, R.id.sug_5,
                R.id.sug_hide)) {
            val sug = root.findViewById<View>(id) ?: continue
            // Alle Chips flach in der Tastenfarbe — auch sug_1: der frühere
            // 3-dp-Akzentbalken in `primary` (grün) über dem Top-Vorschlag ist
            // entfernt (samt `drawable/sug_top_bg.xml`), die Vorschlagszeile
            // trägt keine Leiste mehr.
            sug.background = ColorDrawable(s.keyBg)
            (sug as? TextView)?.setTextColor(s.text)
        }
    }

    /** Aufgeloeste Theme-Farben + Drawable-Fabriken (ein Kontext fuer den Apply-Durchlauf). */
    private class ThemeState {
        var bg = 0
        var keyBg = 0
        var hl = 0
        var text = 0
        var defKeyBg = 0
        var defKbdBg = 0
        var defPressed = 0
        var defText = 0
        var defTabState: android.graphics.drawable.Drawable.ConstantState? = null
        /** Akzentfarbe für den Balken über dem aktiven Tab (`primary`, grün). */
        var accent = 0
        var dip = 0f

        fun rounded(color: Int): GradientDrawable = GradientDrawable().apply {
            cornerRadius = KEY_RADIUS_DP * dip
            setColor(color)
        }

        fun keyBackground(): StateListDrawable = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(hl))
            addState(intArrayOf(), rounded(keyBg))
        }

        fun flatKeyState(): StateListDrawable = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(hl))
            addState(intArrayOf(), ColorDrawable(keyBg))
        }

        /**
         * Zellen-Hintergrund einer Tab-Zelle: im ausgewählten Zustand die flache
         * Tastenfarbe PLUS den grünen Akzentbalken (3 dp, `primary`) am oberen
         * Rand (LayerDrawable); sonst nur die flache Tastenfarbe.
         *
         * Der Balken liegt im **Background** statt im Foreground, weil das
         * Framework den Zustand nur des Background-Drawables bei jedem
         * Selektionswechsel nachführt (`refreshDrawableState` → `mBackground.
         * setState`). Ein `View.foreground` wird dagegen nur einmal beim Setzen
         * aufgelöst und beim Tab-Wechsel nicht aktualisiert — ein Akzentbalken im
         * Foreground bliebe dadurch unsichtbar (Gerätebefund 2026-11-07).
         */
        fun tabKeyState(): StateListDrawable = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_selected),
                LayerDrawable(
                    arrayOf(
                        ColorDrawable(keyBg),
                        TabAccentBarDrawable(accent, (TAB_INDICATOR_HEIGHT_DP * dip).toInt()),
                    )
                ).apply { setLayerGravity(1, Gravity.TOP) }
            )
            addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(hl))
            addState(intArrayOf(), ColorDrawable(keyBg))
        }
    }
}
