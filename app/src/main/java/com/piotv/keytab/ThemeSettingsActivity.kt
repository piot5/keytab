package com.piotv.keytab

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.piotv.keytab.ime.KeyTabImeService
import com.piotv.keytab.ime.ThemePrefs

/**
 * Zweite Einstellungsseite: Theme-Einstellungen (geöffnet per Long-Press auf
 * Mond/Sonne oder über die Haupteinstellungen). Auswahl Dark (☾) / Light (☀)
 * mit Verlauf im Icon, Verlauf-Presets + eigene Farben und Modus, Farb- und
 * Alpha-Regler für Background/Highlight/Schriftfarbe (je Theme getrennt),
 * Live-Vorschau und Reset. Änderungen zählen die Theme-Version hoch → die
 * Tastatur baut beim nächsten Aufbau automatisch neu.
 */
class ThemeSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var editingDark = false

    private var themeIconDark: TextView? = null
    private var themeIconLight: TextView? = null
    private val chipRows = mutableMapOf<String, LinearLayout>()
    private val alphaViews = mutableMapOf<String, TextView>()
    private val alphaBars = mutableMapOf<String, SeekBar>()
    private var modeButtons: List<Pair<String, Button>> = emptyList()

    // Live-Vorschau
    private var previewRow: LinearLayout? = null
    private var previewKey: TextView? = null
    private var previewSug: TextView? = null
    private var previewHl: View? = null

    private companion object {
        const val SUN_SYMBOL = "\u2600\uFE0E"  // ☀
        const val MOON_SYMBOL = "\u263E\uFE0E" // ☾
    }

    private val dip: Float by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        editingDark = ThemePrefs.isDarkMode(this)
        val scroll = android.widget.ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        scroll.setBackgroundColor(ContextCompat.getColor(this, R.color.kbd_bg))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dip).toInt(), (12 * dip).toInt(),
                (20 * dip).toInt(), (16 * dip).toInt())
        }
        scroll.addView(col)
        col.addView(TextView(this).apply {
            text = getString(R.string.theme_settings_title)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        })
        buildTop(col)
        buildGradientSection(col)
        sectionLabel(col, getString(R.string.theme_section_colors))
        colorSection(col)
        sectionLabel(col, getString(R.string.theme_section_preview))
        col.addView(buildPreview(), rowParams())
        col.addView(actionRow(), rowParams())
        setContentView(scroll)
        updatePreview()
        refreshThemeIcons()
    }

    // ---------- Theme-Auswahl ----------

    private fun buildTop(col: LinearLayout) {
        sectionLabel(col, getString(R.string.theme_section_theme))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(themeIcon(SUN_SYMBOL, getString(R.string.theme_pick_sun), dark = false))
        row.addView(themeIcon(MOON_SYMBOL, getString(R.string.theme_pick_moon), dark = true))
        col.addView(row, rowParams())
        col.addView(TextView(this).apply {
            textSize = 11f
            setTextColor(ContextCompat.getColor(this@ThemeSettingsActivity, R.color.text_secondary))
            text = getString(R.string.theme_longpress_hint)
        })
    }

    /** Großes ☀/☾-Icon mit Verlauf im Icon; Klick wechselt Dark/Light. */
    private fun themeIcon(symbol: String, label: String, dark: Boolean): View {
        val textCol = ContextCompat.getColor(this, R.color.popup_text)
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((16 * dip).toInt(), (8 * dip).toInt(), (16 * dip).toInt(), (8 * dip).toInt())
            setOnClickListener { switchTheme(dark) }
        }
        val icon = TextView(this).apply {
            text = symbol
            textSize = 36f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(textCol)
            post {
                paint.shader = gradientShader(width, height,
                    ThemePrefs.gradientColor1(prefs), ThemePrefs.gradientColor2(prefs),
                    ThemePrefs.gradientMode(prefs))
                invalidate()
            }
        }
        cell.addView(icon, LinearLayout.LayoutParams((48 * dip).toInt(), (48 * dip).toInt()))
        cell.addView(TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(textCol)
        })
        if (dark) themeIconDark = icon else themeIconLight = icon
        return cell
    }

    private fun refreshThemeIcons() {
        val active = if (editingDark) themeIconDark else themeIconLight
        val inactive = if (editingDark) themeIconLight else themeIconDark
        active?.setTypeface(null, Typeface.BOLD)
        active?.alpha = 1f
        inactiveIcon(inactive)
    }

    private fun inactiveIcon(icon: TextView?) {
        icon?.setTypeface(null, Typeface.NORMAL)
        icon?.alpha = 0.5f
    }

    private fun switchTheme(dark: Boolean) {
        if (editingDark == dark) return
        prefs.edit().putBoolean("dark_mode", dark).apply()
        ThemePrefs.bumpVersion(prefs)
        editingDark = dark
        refreshThemeIcons()
        refreshAllUi()
    }

    /** Verlauf-Shader passend zum Modus; null wenn Größe fehlt. */
    private fun gradientShader(w: Int, h: Int, c1: Int, c2: Int, mode: String) = when (mode) {
        ThemePrefs.GRADIENT_INVERT ->
            android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), c2, c1,
                android.graphics.Shader.TileMode.CLAMP)
        ThemePrefs.GRADIENT_RADIAL ->
            android.graphics.RadialGradient(w / 2f, h / 2f, maxOf(w, h) / 2f, c1, c2,
                android.graphics.Shader.TileMode.CLAMP)
        else -> android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), c1, c2,
            android.graphics.Shader.TileMode.CLAMP)
    }

    // ---------- Verlauf ----------

    private fun buildGradientSection(col: LinearLayout) {
        sectionLabel(col, getString(R.string.theme_section_gradient))
        col.addView(presetRow(), rowParams())
        miniLabel(col, getString(R.string.settings_gradient_color1))
        col.addView(chipsRow("grad1"), rowParams())
        miniLabel(col, getString(R.string.settings_gradient_color2))
        col.addView(chipsRow("grad2"), rowParams())
        col.addView(modeRow(), rowParams())
    }

    /** Preset-Verläufe als Mini-Swatches; Klick übernimmt Farben + Modus. */
    private fun presetRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        ThemePrefs.GRADIENTS.forEach { p ->
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (46 * dip).toInt(), (28 * dip).toInt()
                ).apply { marginEnd = (6 * dip).toInt() }
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 6f * dip
                    orientation = when (p.mode) {
                        ThemePrefs.GRADIENT_INVERT ->
                            android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP
                        else ->
                            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
                    }
                    if (p.mode == ThemePrefs.GRADIENT_RADIAL) {
                        setGradientType(android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT)
                        setGradientCenter(0.5f, 0.5f)
                        setGradientRadius(26 * dip)
                    }
                    colors = intArrayOf(p.c1, p.c2)
                }
                setOnClickListener {
                    prefs.edit()
                        .putInt(ThemePrefs.KEY_GRADIENT_COLOR1, p.c1)
                        .putInt(ThemePrefs.KEY_GRADIENT_COLOR2, p.c2)
                        .putString(ThemePrefs.KEY_GRADIENT_MODE, p.mode).apply()
                    ThemePrefs.bumpVersion(prefs)
                    rebuildChips()
                    refreshModeButtons()
                    updatePreview()
                }
            })
        }
        return row
    }

    /** Modus-Umschalter (Oben→Unten / Umgekehrt / Radial) als Segmente. */
    private fun modeRow(): View {
        val modes = listOf(
            ThemePrefs.GRADIENT_TOP_DOWN to getString(R.string.gradient_mode_top_down),
            ThemePrefs.GRADIENT_INVERT to getString(R.string.gradient_mode_invert),
            ThemePrefs.GRADIENT_RADIAL to getString(R.string.gradient_mode_radial)
        )
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modeButtons = modes.map { (mode, label) ->
            val b = Button(this).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                minimumHeight = 0
                setPadding((8 * dip).toInt(), (4 * dip).toInt(), (8 * dip).toInt(), (4 * dip).toInt())
                setOnClickListener {
                    prefs.edit().putString(ThemePrefs.KEY_GRADIENT_MODE, mode).apply()
                    ThemePrefs.bumpVersion(prefs)
                    refreshModeButtons()
                    updatePreview()
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

    // ---------- Farben (Background/Highlight/Text mit Alpha) ----------

    private fun colorSection(col: LinearLayout) {
        listOf(
            ThemePrefs.KIND_BG to getString(R.string.theme_color_bg),
            ThemePrefs.KIND_HL to getString(R.string.theme_color_hl),
            ThemePrefs.KIND_TEXT to getString(R.string.theme_color_text)
        ).forEach { (kind, label) ->
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@ThemeSettingsActivity, R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val alphaView = TextView(this).apply {
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@ThemeSettingsActivity, R.color.text_secondary))
                text = alphaText(kind)
            }
            alphaViews[kind] = alphaView
            head.addView(alphaView)
            col.addView(head)
            col.addView(chipsRow(kind), rowParams())
            col.addView(alphaBar(kind), rowParams())
        }
    }

    /** Alpha-Slider; ändert nur den Alpha-Anteil der jeweiligen Farbe. */
    private fun alphaBar(kind: String): SeekBar = SeekBar(this).apply {
        max = 255
        progress = Color.alpha(currentColor(kind))
        alphaBars[kind] = this
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                val rgb = currentColor(kind) and 0xFFFFFF
                ThemePrefs.setColor(prefs, editingDark, kind, ThemePrefs.withAlpha(rgb, value))
                alphaViews[kind]?.text = alphaText(kind)
                ThemePrefs.bumpVersion(prefs)
                updatePreview()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    /** Aktuelle Farbe (Pref oder Theme-Default) für eine Farb-Art. */
    private fun currentColor(kind: String): Int =
        ThemePrefs.getColor(prefs, editingDark, kind,
            ThemePrefs.defaultColor(this, editingDark, kind))

    private fun alphaText(kind: String): String =
        (Color.alpha(currentColor(kind)) * 100 / 255).toString() + " %"

    /** Reihe von Farb-Chips (Palette) mit Auswahl-Markierung. */
    private fun chipsRow(key: String): HorizontalScrollView {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipRows[key] = row
        fillChips(row, key)
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
        return scroll
    }

    private fun fillChips(row: LinearLayout, key: String) {
        row.removeAllViews()
        val isKind = key != "grad1" && key != "grad2"
        val current = if (isKind) currentColor(key)
            else if (key == "grad1") ThemePrefs.gradientColor1(prefs)
            else ThemePrefs.gradientColor2(prefs)
        ThemePrefs.PALETTE.forEach { color ->
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (34 * dip).toInt(), (34 * dip).toInt()
                ).apply { marginEnd = (8 * dip).toInt() }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                    setStroke(
                        ((if (color == current) 3f else 1f) * dip).toInt(),
                        if (color == current) 0xFF4CAF50.toInt() else 0xFF888888.toInt()
                    )
                }
                setOnClickListener {
                    if (isKind) {
                        ThemePrefs.setColor(prefs, editingDark, key,
                            ThemePrefs.withAlpha(color, Color.alpha(currentColor(key))))
                    } else {
                        prefs.edit().putInt(
                            if (key == "grad1") ThemePrefs.KEY_GRADIENT_COLOR1
                            else ThemePrefs.KEY_GRADIENT_COLOR2, color).apply()
                    }
                    ThemePrefs.bumpVersion(prefs)
                    rebuildChips()
                    updatePreview()
                }
            })
        }
    }

    private fun rebuildChips() {
        chipRows.forEach { (key, row) -> fillChips(row, key) }
        alphaBars.forEach { (kind, bar) ->
            bar.progress = Color.alpha(currentColor(kind))
            alphaViews[kind]?.text = alphaText(kind)
        }
        refreshModeButtons()
    }

    private fun refreshAllUi() {
        rebuildChips()
        updatePreview()
    }

    // ---------- Vorschau & Aktionen ----------

    /** Beispielzeile: Taste „A", Vorschlagswort, Highlight-Block. */
    private fun buildPreview(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * dip).toInt(), (8 * dip).toInt(), (10 * dip).toInt(), (8 * dip).toInt())
        }
        val key = TextView(this).apply {
            text = "A"
            textSize = 18f
            gravity = Gravity.CENTER
        }
        row.addView(key, LinearLayout.LayoutParams((44 * dip).toInt(), (44 * dip).toInt())
            .apply { marginEnd = (10 * dip).toInt() })
        val sug = TextView(this).apply {
            text = getString(R.string.theme_preview_word)
            textSize = 15f
        }
        row.addView(sug, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val hl = View(this)
        row.addView(hl, LinearLayout.LayoutParams((28 * dip).toInt(), (28 * dip).toInt())
            .apply { marginStart = (10 * dip).toInt() })
        previewRow = row
        previewKey = key
        previewSug = sug
        previewHl = hl
        return row
    }

    /** Vorschau-Farben + Verlauf-Hintergrund aktualisieren. */
    private fun updatePreview() {
        val row = previewRow ?: return
        val bg = currentColor(ThemePrefs.KIND_BG)
        val hl = currentColor(ThemePrefs.KIND_HL)
        val text = currentColor(ThemePrefs.KIND_TEXT)
        ThemePrefs.gradientDrawable(prefs,
            resources.displayMetrics.widthPixels)?.let { row.background = it }
            ?: run { row.background = android.graphics.drawable.ColorDrawable(bg) }
        previewKey?.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 6f * dip; setColor(currentColor(ThemePrefs.KIND_BG))
        }
        previewKey?.setTextColor(text)
        previewSug?.setTextColor(text)
        previewHl?.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 4f * dip; setColor(hl)
        }
    }

    private fun actionRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val reset = Button(this).apply {
            text = getString(R.string.theme_reset)
            isAllCaps = false
            setOnClickListener {
                ThemePrefs.resetAll(prefs)
                ThemePrefs.bumpVersion(prefs)
                refreshAllUi()
            }
        }
        row.addView(reset, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun sectionLabel(col: LinearLayout, text: String) {
        col.addView(TextView(this).apply {
            this.text = text
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@ThemeSettingsActivity, R.color.text_primary))
            setPadding(0, (18 * dip).toInt(), 0, (4 * dip).toInt())
        })
    }

    private fun miniLabel(col: LinearLayout, text: String) {
        col.addView(TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@ThemeSettingsActivity, R.color.text_secondary))
            setPadding(0, (6 * dip).toInt(), 0, (2 * dip).toInt())
        })
    }

    private fun rowParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = (2 * dip).toInt() }
}
