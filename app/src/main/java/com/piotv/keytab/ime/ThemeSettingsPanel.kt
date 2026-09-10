package com.piotv.keytab.ime

import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.piotv.keytab.R

/**
 * Theme-Einstellungen (Long-Press auf Mond/Sonne): Auswahl Dark (☾) / Light (☀)
 * mit Verlauf im Icon, Verlauf-Presets + eigene Verlauf-Farben und Modus,
 * sowie Farb- und Alpha-Regler für Background, Highlight und Schriftfarbe
 * (je Theme getrennt). Änderungen wirken LIVE auf die laufende Tastatur
 * ([onLiveApply]); Theme-Wechsel (dark/light) baut die Tastatur neu auf
 * ([onSwitchTheme]) und öffnet das Panel neu.
 */
class ThemeSettingsPanel(
    private val service: KeyTabImeService,
    private val onSwitchTheme: (dark: Boolean) -> Unit,
    private val onLiveApply: () -> Unit
) {

    private var activePopup: PopupWindow? = null
    private var editingDark = false

    // Live-Vorschau (Beispielzeile innerhalb des Panels)
    private var previewRow: LinearLayout? = null
    private var previewKey: TextView? = null
    private var previewSug: TextView? = null
    private var previewHl: View? = null
    private val chipRows = mutableMapOf<String, HorizontalScrollView>()
    private var modeButtons: List<Pair<String, Button>> = emptyList()
    private val chipRowKeys = listOf(ThemePrefs.KIND_BG, ThemePrefs.KIND_HL, ThemePrefs.KIND_TEXT)

    private companion object {
        // Monochrome Text-Präsentation (identisch zu KeyTabImeService)
        const val SUN_SYMBOL = "\u2600\uFE0E"  // ☀
        const val MOON_SYMBOL = "\u263E\uFE0E" // ☾
    }

    private val dip: Float get() = service.resources.displayMetrics.density
    private val prefs
        get() = service.getSharedPreferences(
            com.piotv.keytab.MainActivity.PREFS, Context.MODE_PRIVATE)

    /** Blendet ein aktives Panel aus, falls vorhanden. */
    fun dismiss() {
        activePopup?.dismiss()
        activePopup = null
    }

    /** Zeigt das Theme-Einstellungs-Panel über der Tastatur. */
    fun show(anchor: View) {
        dismiss()
        editingDark = service.isDarkMode()
        val ctx = anchor.context
        val content = buildContent(ctx)
        val popup = PopupWindow(content, LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            isOutsideTouchable = true
            isFocusable = false
            setBackgroundDrawable(GradientDrawable())
        }
        activePopup = popup
        // Breite/Höhe: komplette Tastaturbreite, maximal ~85 % der Höhe
        val rootW = service.keyboardRoot?.width ?: (360 * dip).toInt()
        val rootH = service.keyboardRoot?.height ?: (300 * dip).toInt()
        val w = (rootW - 8 * dip).toInt().coerceAtLeast((240 * dip).toInt())
        val h = (rootH * 0.9f).toInt().coerceAtMost((380 * dip).toInt())
        val loc = IntArray(2)
        anchor.getLocationInWindow(loc)
        val x = (loc[0] + anchor.width / 2 - w / 2).coerceAtLeast((4 * dip).toInt())
        val y = loc[1] - h - (4 * dip).toInt()
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
    }

    // ---------- Aufbau ----------

    private fun buildContent(ctx: Context): View {
        val scroll = android.widget.ScrollView(ctx)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(ctx, R.color.popup_bg))
                cornerRadius = 12f * dip
            }
            setPadding((10 * dip).toInt(), (8 * dip).toInt(),
                (10 * dip).toInt(), (8 * dip).toInt())
        }
        scroll.addView(col)
        buildTop(ctx, col)
        buildGradientSection(ctx, col)
        col.addView(sectionLabel(ctx, ctx.getString(R.string.theme_section_colors)))
        colorSection(ctx, col)
        col.addView(sectionLabel(ctx, ctx.getString(R.string.theme_section_preview)))
        col.addView(buildPreview(ctx), rowParams())
        col.addView(actionRow(ctx), rowParams())
        updatePreview()
        return scroll
    }

    // ---------- Theme-Auswahl ----------

    private fun buildTop(ctx: Context, col: LinearLayout) {
        col.addView(sectionLabel(ctx, ctx.getString(R.string.theme_section_theme)))
        val themeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        themeRow.addView(themeIcon(ctx, SUN_SYMBOL,
            ctx.getString(R.string.theme_pick_sun), dark = false))
        themeRow.addView(themeIcon(ctx, MOON_SYMBOL,
            ctx.getString(R.string.theme_pick_moon), dark = true))
        col.addView(themeRow, rowParams())
    }

    /** Großes ☀/☾-Icon mit Verlauf im Icon (Shader je Modus). */
    private fun themeIcon(ctx: Context, symbol: String, label: String, dark: Boolean): View {
        val cell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((14 * dip).toInt(), (6 * dip).toInt(), (14 * dip).toInt(), (6 * dip).toInt())
            if (editingDark == dark) {
                background = GradientDrawable().apply {
                    cornerRadius = 10f * dip
                    setColor(ContextCompat.getColor(ctx, R.color.popup_focus_bg))
                }
            }
            setOnClickListener {
                if (editingDark != dark) onSwitchTheme(dark)
            }
        }
        val icon = TextView(ctx).apply {
            text = symbol
            textSize = 34f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(ctx, R.color.popup_text))
            // Verlauf-Shader braucht die endgültige Größe → nach Layout setzen
            post {
                val c1 = ThemePrefs.gradientColor1(prefs)
                val c2 = ThemePrefs.gradientColor2(prefs)
                paint.shader = gradientShader(width, height, c1, c2, ThemePrefs.gradientMode(prefs))
                invalidate()
            }
        }
        cell.addView(icon, LinearLayout.LayoutParams((46 * dip).toInt(), (46 * dip).toInt()))
        cell.addView(TextView(ctx).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(ctx, R.color.popup_text))
        })
        return cell
    }

    /** Verlauf-Shader passend zum Modus; null wenn Größe fehlt. */
    private fun gradientShader(w: Int, h: Int, c1: Int, c2: Int, mode: String): Shader? {
        if (w <= 0 || h <= 0) return null
        return when (mode) {
            ThemePrefs.GRADIENT_INVERT ->
                LinearGradient(0f, 0f, 0f, h.toFloat(), c2, c1, Shader.TileMode.CLAMP)
            ThemePrefs.GRADIENT_RADIAL ->
                RadialGradient(w / 2f, h / 2f, maxOf(w, h) / 2f, c1, c2, Shader.TileMode.CLAMP)
            else -> LinearGradient(0f, 0f, 0f, h.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        }
    }

    // ---------- Verlauf-Sektion ----------

    private fun buildGradientSection(ctx: Context, col: LinearLayout) {
        col.addView(sectionLabel(ctx, ctx.getString(R.string.theme_section_gradient)))
        col.addView(presetRow(ctx), rowParams())
        col.addView(miniLabel(ctx, ctx.getString(R.string.settings_gradient_color1)))
        col.addView(chipsRow(ctx, gradient = 1), rowParams())
        col.addView(miniLabel(ctx, ctx.getString(R.string.settings_gradient_color2)))
        col.addView(chipsRow(ctx, gradient = 2), rowParams())
        col.addView(modeRow(ctx), rowParams())
    }

    /** Preset-Verläufe als Mini-Swatches; Klick übernimmt Farben + Modus. */
    private fun presetRow(ctx: Context): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        ThemePrefs.GRADIENTS.forEach { p ->
            row.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (44 * dip).toInt(), (26 * dip).toInt()
                ).apply { marginEnd = (6 * dip).toInt() }
                background = GradientDrawable().apply {
                    cornerRadius = 6f * dip
                    orientation = when (p.mode) {
                        ThemePrefs.GRADIENT_INVERT ->
                            GradientDrawable.Orientation.BOTTOM_TOP
                        else -> GradientDrawable.Orientation.TOP_BOTTOM
                    }
                    if (p.mode == ThemePrefs.GRADIENT_RADIAL) {
                        setGradientType(GradientDrawable.RADIAL_GRADIENT)
                        setGradientCenter(0.5f, 0.5f)
                        setGradientRadius(24 * dip)
                    }
                    colors = intArrayOf(p.c1, p.c2)
                }
                setOnClickListener {
                    prefs.edit()
                        .putInt(ThemePrefs.KEY_GRADIENT_COLOR1, p.c1)
                        .putInt(ThemePrefs.KEY_GRADIENT_COLOR2, p.c2)
                        .putString(ThemePrefs.KEY_GRADIENT_MODE, p.mode).apply()
                    rebuildChipRows()
                    refreshModeButtons()
                    updatePreview()
                    onLiveApply()
                }
            })
        }
        return row
    }

    /** Modus-Umschalter (Oben→Unten / Umgekehrt / Radial) als Segmente. */
    private fun modeRow(ctx: Context): View {
        val modes = listOf(
            ThemePrefs.GRADIENT_TOP_DOWN to ctx.getString(R.string.gradient_mode_top_down),
            ThemePrefs.GRADIENT_INVERT to ctx.getString(R.string.gradient_mode_invert),
            ThemePrefs.GRADIENT_RADIAL to ctx.getString(R.string.gradient_mode_radial)
        )
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        modeButtons = modes.map { (mode, label) ->
            val b = Button(ctx).apply {
                text = label
                textSize = 10f
                isAllCaps = false
                minimumHeight = 0
                setPadding((8 * dip).toInt(), (4 * dip).toInt(),
                    (8 * dip).toInt(), (4 * dip).toInt())
                setOnClickListener {
                    prefs.edit().putString(ThemePrefs.KEY_GRADIENT_MODE, mode).apply()
                    refreshModeButtons()
                    updatePreview()
                    onLiveApply()
                }
            }
            row.addView(b, LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (4 * dip).toInt()
            })
            mode to b
        }
        refreshModeButtons()
        return row
    }

    private fun refreshModeButtons() {
        val active = ThemePrefs.gradientMode(prefs)
        modeButtons.forEach { (mode, b) ->
            b.setTypeface(null, if (mode == active) Typeface.BOLD else Typeface.NORMAL)
            b.alpha = if (mode == active) 1f else 0.65f
        }
    }

    // ---------- Farb-Sektion (Background/Highlight/Text mit Alpha) ----------

    private fun colorSection(ctx: Context, col: LinearLayout) {
        listOf(
            ThemePrefs.KIND_BG to ctx.getString(R.string.theme_color_bg),
            ThemePrefs.KIND_HL to ctx.getString(R.string.theme_color_hl),
            ThemePrefs.KIND_TEXT to ctx.getString(R.string.theme_color_text)
        ).forEach { (kind, label) ->
            val alphaView = TextView(ctx).apply {
                textSize = 11f
                setTextColor(ContextCompat.getColor(ctx, R.color.popup_text))
                text = alphaText(kind)
            }
            val head = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(miniLabel(ctx, label, weight = 1f))
            head.addView(alphaView)
            col.addView(head)
            col.addView(chipsRow(ctx, kind = kind), rowParams())
            col.addView(alphaBar(ctx, kind, alphaView))
        }
    }

    /** Alpha-Slider; ändert nur den Alpha-Anteil der jeweiligen Farbe. */
    private fun alphaBar(ctx: Context, kind: String, alphaView: TextView): SeekBar =
        SeekBar(ctx).apply {
            max = 255
            progress = Color.alpha(currentColor(kind))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, value: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val rgb = currentColor(kind) and 0xFFFFFF
                    ThemePrefs.setColor(prefs, editingDark, kind,
                        ThemePrefs.withAlpha(rgb, value))
                    alphaView.text = alphaText(kind)
                    updatePreview()
                    onLiveApply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

    /** Aktuelle Farbe (Pref oder Theme-Default) für eine Farb-Art. */
    private fun currentColor(kind: String): Int =
        ThemePrefs.getColor(prefs, editingDark, kind, service.defaultThemeColor(editingDark, kind))

    private fun alphaText(kind: String): String =
        (Color.alpha(currentColor(kind)) * 100 / 255).toString() + " %"

    // ---------- Farb-Chips ----------

    /** Horizontale Reihe von Farb-Chips (Palette); [gradient] 1/2 = Verlauf-Farben. */
    private fun chipsRow(ctx: Context, gradient: Int = 0, kind: String? = null): HorizontalScrollView {
        val key = kind ?: "grad$gradient"
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
        chipRows[key] = scroll
        fillChips(ctx, row, gradient, kind)
        return scroll
    }

    private fun fillChips(ctx: Context, row: LinearLayout, gradient: Int, kind: String?) {
        row.removeAllViews()
        val current = if (kind != null) currentColor(kind)
            else if (gradient == 1) ThemePrefs.gradientColor1(prefs)
            else ThemePrefs.gradientColor2(prefs)
        ThemePrefs.PALETTE.forEach { color ->
            row.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (30 * dip).toInt(), (30 * dip).toInt()
                ).apply { marginEnd = (6 * dip).toInt() }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(
                        ((if (color == current) 3f else 1f) * dip).toInt(),
                        if (color == current) 0xFF4CAF50.toInt() else 0xFF888888.toInt()
                    )
                }
                setOnClickListener {
                    if (kind != null) {
                        ThemePrefs.setColor(prefs, editingDark, kind,
                            ThemePrefs.withAlpha(color, Color.alpha(currentColor(kind))))
                    } else {
                        prefs.edit().putInt(
                            if (gradient == 1) ThemePrefs.KEY_GRADIENT_COLOR1
                            else ThemePrefs.KEY_GRADIENT_COLOR2, color).apply()
                    }
                    rebuildChipRows()
                    updatePreview()
                    onLiveApply()
                }
            })
        }
    }

    /** Chip-Reihen neu zeichnen (Auswahl-Markierung aktualisieren). */
    private fun rebuildChipRows() {
        chipRows.forEach { (key, scroll) ->
            val row = scroll.getChildAt(0) as? LinearLayout ?: return@forEach
            val kind = if (key == ThemePrefs.KIND_BG || key == ThemePrefs.KIND_HL ||
                key == ThemePrefs.KIND_TEXT) key else null
            val gradient = when (kind) {
                null -> if (key == "grad1") 1 else 2
                else -> 0
            }
            fillChips(scroll.context, row, gradient, kind)
        }
        refreshModeButtons()
    }

    // ---------- Live-Vorschau ----------

    /** Beispielzeile: Taste „A", Vorschlagswort, Highlight-Block. */
    private fun buildPreview(ctx: Context): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dip).toInt(), (6 * dip).toInt(), (8 * dip).toInt(), (6 * dip).toInt())
        }
        val key = TextView(ctx).apply {
            text = "A"
            textSize = 16f
            gravity = Gravity.CENTER
        }
        row.addView(key, LinearLayout.LayoutParams((40 * dip).toInt(), (40 * dip).toInt())
            .apply { marginEnd = (8 * dip).toInt() })
        val sug = TextView(ctx).apply {
            text = ctx.getString(R.string.theme_preview_word)
            textSize = 14f
        }
        row.addView(sug, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val hl = View(ctx)
        row.addView(hl, LinearLayout.LayoutParams((26 * dip).toInt(), (26 * dip).toInt())
            .apply { marginStart = (8 * dip).toInt() })
        previewRow = row
        previewKey = key
        previewSug = sug
        previewHl = hl
        updatePreview()
        return row
    }

    /** Vorschau-Farben + Verlauf-Hintergrund aktualisieren. */
    private fun updatePreview() {
        val row = previewRow ?: return
        val bg = currentColor(ThemePrefs.KIND_BG)
        val hl = currentColor(ThemePrefs.KIND_HL)
        val text = currentColor(ThemePrefs.KIND_TEXT)
        ThemePrefs.gradientDrawable(prefs, row.width.takeIf { it > 0 }
            ?: row.resources.displayMetrics.widthPixels)?.let { row.background = it }
            ?: run { row.background = android.graphics.drawable.ColorDrawable(bg) }
        previewKey?.background = GradientDrawable().apply {
            cornerRadius = 6f * dip; setColor(bg)
        }
        previewKey?.setTextColor(text)
        previewSug?.setTextColor(text)
        previewHl?.background = GradientDrawable().apply {
            cornerRadius = 4f * dip; setColor(hl)
        }
    }

    // ---------- Aktionen & kleine Helfer ----------

    private fun actionRow(ctx: Context): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        row.addView(smallButton(ctx, ctx.getString(R.string.theme_reset)) {
            ThemePrefs.resetAll(prefs)
            rebuildChipRows()
            updatePreview()
            onLiveApply()
        })
        row.addView(smallButton(ctx, ctx.getString(R.string.theme_done)) { dismiss() })
        return row
    }

    private fun smallButton(ctx: Context, label: String, onClick: () -> Unit): Button =
        Button(ctx).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            minimumHeight = 0
            setPadding((10 * dip).toInt(), (6 * dip).toInt(), (10 * dip).toInt(), (6 * dip).toInt())
            setOnClickListener { onClick() }
        }

    private fun sectionLabel(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        setTextColor(ContextCompat.getColor(ctx, R.color.popup_text))
        setPadding(0, (8 * dip).toInt(), 0, (2 * dip).toInt())
    }

    private fun miniLabel(ctx: Context, text: String, weight: Float = 0f): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 11f
            setTextColor(ContextCompat.getColor(ctx, R.color.popup_text))
            layoutParams = if (weight > 0f)
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            else LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

    private fun rowParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = (2 * dip).toInt() }
}
