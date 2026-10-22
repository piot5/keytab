package com.piotv.keytab.ime

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
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
 * (key_bg-Selector, tab_bg_flat, sug_top_bg) und baut sie mit den neuen Farben
 * neu. Ohne Overrides ergibt sich der 0.9.4-Look 1:1.
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
        val basePrimary = ContextCompat.getColor(ctx, R.color.primary)
        return ThemeState().apply {
            bg = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_BG, baseKbdBg)
            keyBg = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_KEY, baseKeyBg)
            hl = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_HL, basePressed)
            text = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_TEXT, baseText)
            defKeyBg = baseKeyBg
            defKbdBg = baseKbdBg
            defPressed = basePressed
            defText = baseText
            defPrimary = basePrimary
            defTabState = ContextCompat.getDrawable(ctx, R.drawable.tab_bg_flat)?.constantState
            defSugState = ContextCompat.getDrawable(ctx, R.drawable.sug_top_bg)?.constantState
            dip = ctx.resources.displayMetrics.density
        }
    }

    private fun recolourView(view: View, root: View, s: ThemeState) {
        val state = view.background?.constantState
        if (state == s.defTabState) {
            view.background = s.flatKeyState()
        } else if (state == s.defSugState) {
            view.background = s.sugLayer()
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
        for (i in 0 until tabs.childCount) {
            val indicator = tabs.getChildAt(i)
            if (indicator is ViewGroup) {
                indicator.background = ColorDrawable(android.graphics.Color.TRANSPARENT)
                for (j in 0 until indicator.childCount) {
                    indicator.getChildAt(j).background = s.flatKeyState()
                    indicator.getChildAt(j).foreground = null
                }
            }
        }
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
            sug.background = if (id == R.id.sug_1) s.sugLayer() else ColorDrawable(s.keyBg)
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
        var defPrimary = 0
        var defTabState: android.graphics.drawable.Drawable.ConstantState? = null
        var defSugState: android.graphics.drawable.Drawable.ConstantState? = null
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

        fun sugLayer(): LayerDrawable = LayerDrawable(
            arrayOf(ColorDrawable(keyBg), GradientDrawable().apply { setColor(defPrimary) })
        ).apply {
            setLayerGravity(1, Gravity.TOP)
            setLayerHeight(1, (3 * dip).toInt())
        }
    }
}
