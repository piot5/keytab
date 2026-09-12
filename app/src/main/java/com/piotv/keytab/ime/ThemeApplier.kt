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
        val v = root
        val context = ctx
        val defKbdBg = ContextCompat.getColor(context, R.color.kbd_bg)
        val defKeyBg = ContextCompat.getColor(context, R.color.key_bg)
        val defPressed = ContextCompat.getColor(context, R.color.key_pressed)
        val defText = ContextCompat.getColor(context, R.color.key_text)
        val defPrimary = ContextCompat.getColor(context, R.color.primary)
        val bg = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_BG, defKbdBg)
        val keyBg = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_KEY, defKeyBg)
        val hl = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_HL, defPressed)
        val text = ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_TEXT, defText)

        val dip = context.resources.displayMetrics.density
        fun rounded(color: Int) = GradientDrawable().apply {
            cornerRadius = 8f * context.resources.displayMetrics.density
            setColor(color)
        }
        fun keyBackground() = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(hl))
            addState(intArrayOf(), rounded(keyBg))
        }
        /** FLACHE (eckige) Variante ohne cornerRadius: für die obere Zeile
         *  (Tab-Zellen, ☾/☀-Taste, ⚙-Taste). Diese Elemente sollen die Zelle
         *  komplett ausfüllen und NICHT auf abgerundeten Feldern sitzen. */
        fun flatKeyState() = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(hl))
            addState(intArrayOf(), ColorDrawable(keyBg))
        }
        /** Vorschlagsleisten-Look: key_bg + flacher 3dp-Streifen oben (primär). */
        fun sugLayer() = LayerDrawable(
            arrayOf(ColorDrawable(keyBg), GradientDrawable().apply { setColor(defPrimary) }))
            .apply {
                setLayerGravity(1, Gravity.TOP)
                setLayerHeight(1, (3 * dip).toInt())
            }
        val defTabState = ContextCompat.getDrawable(context, R.drawable.tab_bg_flat)?.constantState
        val defSugState = ContextCompat.getDrawable(context, R.drawable.sug_top_bg)?.constantState

        // Root: Verlauf wenn konfiguriert, sonst Background-Farbe
        ThemePrefs.gradientDrawable(prefs,
            context.resources.displayMetrics.widthPixels)?.let { v.background = it }
            ?: run { v.background = ColorDrawable(bg) }
        forEachView(v) { view ->
            val state = view.background?.constantState
            when (state) {
                defTabState -> view.background = flatKeyState()
                defSugState -> view.background = sugLayer()
                else -> {
                    val d = view.background
                    // Tab-Hintergrund (tab_bg_inset = InsetDrawable um key_bg):
                    // constantState-Match greift nicht → Abgleich per Typ.
                    if (d is InsetDrawable) {
                        view.background = InsetDrawable(
                            keyBackground(),
                            (4 * dip).toInt(), (3 * dip).toInt(), (4 * dip).toInt(), (3 * dip).toInt())
                    }
                    // KeyTab-Tasten (KeyDark-Style, @drawable/key_bg): der constantState
                    // des inflaten Drawables weicht vom frisch aufgelösten ab (enthält
                    // Laufzeit-State) → Abgleich per Typ statt constantState. Alle
                    // Tastatur-Tasten sind Buttons mit StateListDrawable-Hintergrund.
                    else if (view is Button && d is StateListDrawable) {
                        view.background = keyBackground()
                    } else if (d is ColorDrawable) {
                        when (d.color) {
                            defKeyBg -> view.background = ColorDrawable(keyBg)
                            // Container mit kbd_bg (obere Tab-Zeile, Vorschlagsleiste):
                            // TRANSPARENT statt erneut bg. Sonst stapeln sich Root-bg +
                            // Zeilen-bg (+ TabLayout) und bei halbtransparentem BG wirken
                            // diese Zeilen undurchsichtiger als die Buchstabenreihen,
                            // die keinen eigenen Hintergrund haben. Außerdem bleibt ein
                            // konfigurierter Verlauf so in den oberen Zeilen sichtbar.
                            defKbdBg -> view.background = if (view === v) ColorDrawable(bg)
                                else ColorDrawable(android.graphics.Color.TRANSPARENT)
                            defPressed -> view.background = ColorDrawable(hl)
                        }
                    }
                }
            }
            if (view is TextView && view.currentTextColor == defText) {
                view.setTextColor(text)
        }
        }
        // Tab-Leiste (ABC/Notes/Files/Terminal): Material legt sein eigenes Drawable
        // (Ripple/tabBackground) um die TabViews → constantState-Match schlägt fehl.
        // Explizit pro TabView setzen (SlidingTabIndicator → TabView-Kinder).
        v.findViewById<TabLayout>(R.id.ime_tabs)?.let { tabs ->
            // Textfarben ZUERST: Material kann bei Text-/Tab-Updates das XML-
            // tabBackground erneut auf die TabViews legen – danach gesetzte
            // Hintergründe gewinnen dann immer.
            val dimText = androidx.core.graphics.ColorUtils.setAlphaComponent(
                text, (android.graphics.Color.alpha(text) * 0.6f).toInt().coerceAtMost(255))
            tabs.setTabTextColors(dimText, text)
            // Die Tab-Leiste SELBST trägt von Material colorSurface (= surface, grau)
            // als Hintergrund. Würde sie grau bleiben, schimmert sie durch
            // halbtransparente (Alpha-)Tab-Hintergründe → Tastenfarbe/Alpha wirkt
            // dort nicht. TRANSPARENT (nicht bg!): Der themierte Hintergrund kommt
            // ausschließlich vom Root. Ein zusätzliches bg hier würde sich mit dem
            // Root-bg stapeln und die obere Zeile bei Alpha undurchsichtiger machen
            // als die Buchstabenreihen (ein Verlauf wäre hier verdeckt).
            tabs.background = ColorDrawable(
                android.graphics.Color.TRANSPARENT)
            for (i in 0 until tabs.childCount) {
                val indicator = tabs.getChildAt(i)
                if (indicator is ViewGroup) {
                    indicator.background = ColorDrawable(
                        android.graphics.Color.TRANSPARENT)
                    for (j in 0 until indicator.childCount) {
                        // FLACH + eckig: Zelle komplett gefüllt, keine Rundung,
                        // keine Insets → keine Lücken links/rechts der Zellen.
                        indicator.getChildAt(j).background = flatKeyState()
                        // Material kann ein Ripple-Foreground darüberlegen
                        indicator.getChildAt(j).foreground = null
                    }
                }
            }
        }
        // Vorschlagsleiste: sug_1 bekommt sugLayer(), sug_2/3 soliden keyBg-Hintergrund.
        // Per ID (nicht über constantState-Match, der beim 2. Durchlauf unter Alpha nicht trifft).
        // Alpha + Textfarbe immer aktiv setzen, damit Änderungen sofort wirksam sind.
        // Hinweis: sug_* sind TextView (nicht Button), key_theme/key_settings sind Buttons.
        // ☾/☀ und ⚙ FLACH/eckig (flatKeyState) statt keyBackground(): die abgerundete
        // Variante ließ die Taste „abgerundet" aussehen und an den Rändern der obersten
        // Zeile Lücken entstehen. Beide Tasten gleich behandeln → konsistenter Look.
        for (id in intArrayOf(R.id.key_theme, R.id.key_settings)) {
            val btn = v.findViewById<Button>(id) ?: continue
            btn.setTextColor(text)
            btn.background = flatKeyState()
        }
        for (id in intArrayOf(R.id.sug_1, R.id.sug_2, R.id.sug_3)) {
            val sug = v.findViewById<View>(id) ?: continue
            sug.background = if (id == R.id.sug_1) sugLayer() else ColorDrawable(keyBg)
            (sug as? TextView)?.setTextColor(text)
        }
    }
}