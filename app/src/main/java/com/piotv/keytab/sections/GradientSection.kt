package com.piotv.keytab.sections

import android.content.SharedPreferences
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import com.piotv.keytab.R
import com.piotv.keytab.ime.ThemePrefs

class GradientSection(
    private val activity: android.app.Activity,
    private val prefs: SharedPreferences,
    private val onChange: () -> Unit,
    private val onPickColor: (String) -> Unit = {}
) {
    private val dip = activity.resources.displayMetrics.density
    private var gradientPreview: View? = null
    private var enabled: CheckBox? = null
    private var modes: Spinner? = null
    private var refreshing = false
    private var swatch1: View? = null
    private var swatch2: View? = null
    private val values = listOf(ThemePrefs.GRADIENT_TOP_DOWN, ThemePrefs.GRADIENT_INVERT, ThemePrefs.GRADIENT_RADIAL)

    fun build(col: LinearLayout) {
        enabled = CheckBox(activity).apply {
            setText(R.string.gradient_enabled)
            setOnCheckedChangeListener { _, checked ->
                if (!refreshing) {
                    val dark = ThemePrefs.isDarkMode(activity)
                    val edit = prefs.edit().putBoolean(ThemePrefs.gradientOffKey(dark), !checked)
                    if (checked) edit.remove(ThemePrefs.colorKey(dark, ThemePrefs.KIND_BG))
                    edit.apply()
                    changed()
                }
            }
        }
        col.addView(enabled)
        modes = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                listOf(R.string.gradient_mode_top_down, R.string.gradient_mode_invert,
                    R.string.gradient_mode_radial).map { activity.getString(it) })
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    val dark = ThemePrefs.isDarkMode(activity)
                    if (!refreshing && ThemePrefs.gradientMode(prefs, dark) != values[pos]) {
                        prefs.edit().putString(ThemePrefs.gradientModeKey(dark), values[pos]).apply()
                        changed()
                    }
                }
            override fun onNothingSelected(parent: AdapterView<*>?) { /* kein Bedarf: keine Neutralposition */ return }
            }
        }
        col.addView(modes)

        // Zwei direkte Farbauswahl-Swatches für den aktiven Modus (Dark ODER Light):
        // Klick öffnet den Farbwähler für Farbe 1 bzw. 2 des gerade aktiven Modus.
        val swatchRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val label1 = android.widget.TextView(activity).apply {
            text = activity.getString(R.string.settings_gradient_color1)
            textSize = 12f
            setPadding(0, 0, (6 * dip).toInt(), 0)
        }
        swatch1 = View(activity).apply {
            setOnClickListener { onPickColor(ThemePrefs.KIND_GRADIENT1) }
        }
        val label2 = android.widget.TextView(activity).apply {
            text = activity.getString(R.string.settings_gradient_color2)
            textSize = 12f
            setPadding((12 * dip).toInt(), 0, (6 * dip).toInt(), 0)
        }
        swatch2 = View(activity).apply {
            setOnClickListener { onPickColor(ThemePrefs.KIND_GRADIENT2) }
        }
        for (v in listOf(label1, swatch1, label2, swatch2)) {
            val lp = LinearLayout.LayoutParams(
                if (v is android.widget.TextView) LinearLayout.LayoutParams.WRAP_CONTENT
                else (36 * dip).toInt(),
                if (v is android.widget.TextView) LinearLayout.LayoutParams.WRAP_CONTENT
                else (36 * dip).toInt()
            )
            lp.bottomMargin = (4 * dip).toInt()
            swatchRow.addView(v, lp)
        }
        col.addView(swatchRow)

        gradientPreview = View(activity)
        col.addView(gradientPreview, LinearLayout.LayoutParams(-1, (40 * dip).toInt()))
        col.addView(TextView(activity).apply { setText(R.string.theme_gradient_hint) })
        updateGradient()
    }

    private fun changed() { ThemePrefs.bumpVersion(prefs); updateGradient(); onChange() }

    fun updateGradient() {
        val dark = ThemePrefs.isDarkMode(activity)
        refreshing = true
        enabled?.isChecked = ThemePrefs.hasGradient(prefs, dark)
        modes?.setSelection(values.indexOf(ThemePrefs.gradientMode(prefs, dark)).coerceAtLeast(0))
        refreshing = false
        gradientPreview?.background = ThemePrefs.gradientDrawable(prefs, dark,
            activity.resources.displayMetrics.widthPixels) ?: android.graphics.drawable.ColorDrawable(
            ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_BG,
                ThemePrefs.defaultColor(activity, dark, ThemePrefs.KIND_BG)))
        // Swatches mit den aktuellen Verlaufs-Farben des aktiven Modus füllen
        val c1 = ThemePrefs.gradientColor1(prefs, dark)
        val c2 = ThemePrefs.gradientColor2(prefs, dark)
        swatch1?.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 6f * dip; setColor(c1)
            setStroke((1 * dip).toInt(), android.graphics.Color.parseColor("#555555"))
        }
        swatch2?.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 6f * dip; setColor(c2)
            setStroke((1 * dip).toInt(), android.graphics.Color.parseColor("#555555"))
        }
    }
}
