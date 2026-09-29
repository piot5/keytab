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

    companion object {
        private const val ICON_TEXT_SP = 28f
        private const val ICON_PADDING_H_DP = 12
        private const val ICON_PADDING_V_DP = 8
        private const val BUTTON_MARGIN_DP = 4
        private const val BUTTON_WIDTH_DP = 56
        private const val BUTTON_HEIGHT_DP = 32
    }

    fun build(col: LinearLayout) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        themeIconDark = TextView(activity).apply {
            text = "☾︎"
            textSize = ICON_TEXT_SP
            gravity = Gravity.CENTER
            setPadding(
                (ICON_PADDING_H_DP * dip).toInt(), (ICON_PADDING_V_DP * dip).toInt(),
                (ICON_PADDING_H_DP * dip).toInt(), (ICON_PADDING_V_DP * dip).toInt()
            )
            setOnClickListener {
                com.piotv.keytab.Prefs.of(activity)
                    .edit().putBoolean(ThemePrefs.KEY_DARK, true).apply()
                onThemeChanged()
            }
        }
        themeIconLight = TextView(activity).apply {
            text = "☀︎"
            textSize = ICON_TEXT_SP
            gravity = Gravity.CENTER
            setPadding(
                (ICON_PADDING_H_DP * dip).toInt(), (ICON_PADDING_V_DP * dip).toInt(),
                (ICON_PADDING_H_DP * dip).toInt(), (ICON_PADDING_V_DP * dip).toInt()
            )
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
        ).apply { topMargin = (ICON_PADDING_V_DP * dip).toInt() })

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
                    val dark = ThemePrefs.isDarkMode(activity)
                    com.piotv.keytab.Prefs.of(activity)
                        .edit()
                        .putInt(ThemePrefs.colorKey(dark, ThemePrefs.KIND_GRADIENT1), preset.c1)
                        .putInt(ThemePrefs.colorKey(dark, ThemePrefs.KIND_GRADIENT2), preset.c2)
                        .putString(ThemePrefs.gradientModeKey(dark), preset.mode)
                        .remove(ThemePrefs.colorKey(dark, ThemePrefs.KIND_BG))
                        .putBoolean(ThemePrefs.gradientOffKey(dark), false)
                        .apply()
                    ThemePrefs.bumpVersion(prefs)
                    onThemeChanged()
                }
            }
            presetRow.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (BUTTON_MARGIN_DP * dip).toInt()
                width = (BUTTON_WIDTH_DP * dip).toInt()
                height = (BUTTON_HEIGHT_DP * dip).toInt()
            })
        }
        col.addView(presetRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (ICON_PADDING_V_DP * dip).toInt() })
    }

    fun updateThemeIcons(editingDark: Boolean) {
        themeIconDark?.setBackgroundColor(if (editingDark) Color.parseColor("#33000000") else Color.TRANSPARENT)
        themeIconLight?.setBackgroundColor(if (!editingDark) Color.parseColor("#33000000") else Color.TRANSPARENT)
    }
}
