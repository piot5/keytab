package com.piotv.keytab.sections

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.piotv.keytab.ime.ThemePrefs

class TopSection(
    private val activity: android.app.Activity,
    private val prefs: SharedPreferences,
    private val onThemeChanged: () -> Unit
) {
    private val dip = activity.resources.displayMetrics.density
    private var themeIconDark: TextView? = null
    private var themeIconLight: TextView? = null

    fun build(col: LinearLayout) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        themeIconDark = TextView(activity).apply {
            text = "☾︎"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding((12 * dip).toInt(), (8 * dip).toInt(), (12 * dip).toInt(), (8 * dip).toInt())
            setOnClickListener {
                com.piotv.keytab.Prefs.of(activity)
                    .edit().putBoolean(ThemePrefs.KEY_DARK, true).apply()
                onThemeChanged()
            }
        }
        themeIconLight = TextView(activity).apply {
            text = "☀︎"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding((12 * dip).toInt(), (8 * dip).toInt(), (12 * dip).toInt(), (8 * dip).toInt())
            setOnClickListener {
                com.piotv.keytab.Prefs.of(activity)
                    .edit().putBoolean(ThemePrefs.KEY_DARK, false).apply()
                onThemeChanged()
            }
        }
        row.addView(themeIconDark)
        row.addView(themeIconLight)
        col.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * dip).toInt() })

        val presetRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        ThemePrefs.GRADIENTS.forEach { preset ->
            val btn = Button(activity).apply {
                text = preset.name
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    com.piotv.keytab.Prefs.of(activity)
                        .edit()
                        .putInt(ThemePrefs.KEY_GRADIENT_COLOR1, preset.c1)
                        .putInt(ThemePrefs.KEY_GRADIENT_COLOR2, preset.c2)
                        .putString(ThemePrefs.KEY_GRADIENT_MODE, preset.mode)
                        .remove(ThemePrefs.colorKey(ThemePrefs.isDarkMode(activity), ThemePrefs.KIND_BG))
                        .putBoolean(ThemePrefs.gradientOffKey(ThemePrefs.isDarkMode(activity)), false)
                        .apply()
                    ThemePrefs.bumpVersion(prefs)
                    onThemeChanged()
                }
            }
            presetRow.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (4 * dip).toInt()
                width = (56 * dip).toInt()
                height = (32 * dip).toInt()
            })
        }
        col.addView(presetRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * dip).toInt() })
    }

    fun updateThemeIcons(editingDark: Boolean) {
        themeIconDark?.setBackgroundColor(if (editingDark) Color.parseColor("#33000000") else Color.TRANSPARENT)
        themeIconLight?.setBackgroundColor(if (!editingDark) Color.parseColor("#33000000") else Color.TRANSPARENT)
    }
}
