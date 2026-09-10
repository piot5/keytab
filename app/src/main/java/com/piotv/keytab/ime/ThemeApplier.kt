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
 * (key_bg-Selector, tab_bg_inset, sug_top_bg) und baut sie mit den neuen Farben
 * neu. Ohne Overrides ergibt sich der 0.9.4-Look 1:1.
 *
 * Ausgelagert aus [KeyTabImeService] (God-Class-Reduktion): Die Funktion
 * [apply] und das rekursive [forEachView] sind reine View-Tree-/Drawable-
 * Utilities und damit unabhängig vom Service.
 */
object ThemeApplier {

    /** Rekursive View-Hierarchie-Traversierung (Action auf jedem View). */
    fun forEachView(root: View, action: (View) -> Unit) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) forEachView(root.getChildAt(i), action)
        } else {
            action(root)
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
        val v = root
        val context = ctx
        val defKbdBg = ContextCompat.getColor(context, R.color.kbd_bg)
        val defKeyBg = ContextCompat.getColor(context, R.color.key_bg)
        val defPressed = ContextCompat.getColor(context, R.color.key_pressed)
        val defText = ContextCompat.getColor(context, R.color.key_text)
        val defPrimary = ContextCompat.getColor(context, R.color.primary)
        val bg = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_BG, defKbdBg)
        val keyBg = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_BG, defKeyBg)
        val hl = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_HL, defPressed)
        val text = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_TEXT, defText)

        fun rounded(color: Int) = GradientDrawable().apply {
            cornerRadius = 8f * context.resources.displayMetrics.density
            setColor(color)
        }
        fun keyBackground() = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(hl))
            addState(intArrayOf(), rounded(keyBg))
        }
        val defKeyBgState = ContextCompat.getDrawable(context, R.drawable.key_bg)?.constantState
        val defTabState = ContextCompat.getDrawable(context, R.drawable.tab_bg_inset)?.constantState
        val defSugState = ContextCompat.getDrawable(context, R.drawable.sug_top_bg)?.constantState
        val dip = context.resources.displayMetrics.density

        // Root: Verlauf wenn konfiguriert, sonst Background-Farbe
        ThemePrefs.gradientDrawable(prefs,
            context.resources.displayMetrics.widthPixels)?.let { v.background = it }
            ?: run { v.background = ColorDrawable(bg) }
        forEachView(v) { view ->
            val state = view.background?.constantState
            when (state) {
                defKeyBgState -> view.background = keyBackground()
                defTabState -> view.background = InsetDrawable(
                    keyBackground(),
                    (4 * dip).toInt(), (3 * dip).toInt(), (4 * dip).toInt(), (3 * dip).toInt())
                defSugState -> {
                    // Vorschlagsleiste: key_bg + flacher 3dp-Streifen oben (primär)
                    val strip = GradientDrawable().apply { setColor(defPrimary) }
                    val sugLayer = LayerDrawable(
                        arrayOf(ColorDrawable(keyBg), strip))
                    sugLayer.setLayerGravity(1, Gravity.TOP)
                    sugLayer.setLayerHeight(1, (3 * dip).toInt())
                    view.background = sugLayer
                }
                else -> {
                    val d = view.background
                    if (d is ColorDrawable) {
                        when (d.color) {
                            defKeyBg -> view.background = ColorDrawable(keyBg)
                            defKbdBg -> view.background = ColorDrawable(bg)
                            defPressed -> view.background = ColorDrawable(hl)
                        }
                    }
                }
            }
            if (view is TextView && view.currentTextColor == defText) {
                view.setTextColor(text)
            }
        }
        // Tabs (ABC/Notes/Files/Terminal): Textfarben normal/ausgewählt
        v.findViewById<TabLayout>(R.id.ime_tabs)?.let { tabs ->
            val dimText = androidx.core.graphics.ColorUtils.setAlphaComponent(
                text, (android.graphics.Color.alpha(text) * 0.6f).toInt().coerceAtMost(255))
            tabs.setTabTextColors(dimText, text)
        }
        // Theme-Button-Farbe explizit (wurde im Aufbau separat gesetzt)
        v.findViewById<Button>(R.id.key_theme)?.setTextColor(text)
    }
}