package com.piotv.keytab.sections

import android.content.SharedPreferences
import android.view.View
import android.widget.*
import com.piotv.keytab.R
import com.piotv.keytab.ime.ThemePrefs

class GradientSection(
    private val activity: android.app.Activity,
    private val prefs: SharedPreferences,
    private val onChange: () -> Unit
) {
    private val dip = activity.resources.displayMetrics.density
    private var gradientPreview: View? = null
    private var enabled: CheckBox? = null
    private var modes: Spinner? = null
    private var refreshing = false
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
                    if (!refreshing && ThemePrefs.gradientMode(prefs) != values[pos]) {
                        prefs.edit().putString(ThemePrefs.KEY_GRADIENT_MODE, values[pos]).apply()
                        changed()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        col.addView(modes)
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
        modes?.setSelection(values.indexOf(ThemePrefs.gradientMode(prefs)).coerceAtLeast(0))
        refreshing = false
        gradientPreview?.background = ThemePrefs.gradientDrawable(prefs, dark,
            activity.resources.displayMetrics.widthPixels) ?: android.graphics.drawable.ColorDrawable(
            ThemePrefs.getColor(prefs, dark, ThemePrefs.KIND_BG,
                ThemePrefs.defaultColor(activity, dark, ThemePrefs.KIND_BG)))
    }
}
