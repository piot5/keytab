package com.piotv.keytab.sections

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.piotv.keytab.ime.ThemePrefs

class PreviewSection(
    private val activity: android.app.Activity,
    private val prefs: SharedPreferences,
    private val getCurrentColor: (String) -> Int
) {
    private val dip = activity.resources.displayMetrics.density

    private var previewRow: LinearLayout? = null
    private var previewKey: TextView? = null
    private var previewSug: TextView? = null
    private var previewHl: View? = null

    companion object {
        private const val PADDING_DP = 12
        private const val KEY_TEXT_SP = 18f
        private const val KEY_SPACING_DP = 16
        private const val SMALL_SPACING_DP = 8
        private const val SUG_TEXT_SP = 14f
        private const val HIGHLIGHT_SIZE_DP = 28
        private const val KEY_RADIUS_DP = 6f
        private const val HIGHLIGHT_RADIUS_DP = 4f
    }

    fun build(col: LinearLayout) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((PADDING_DP * dip).toInt(), (PADDING_DP * dip).toInt(), (PADDING_DP * dip).toInt(), (PADDING_DP * dip).toInt())
        }

        val key = TextView(activity).apply {
            text = "A"
            textSize = KEY_TEXT_SP
            gravity = Gravity.CENTER
            setPadding(
                (KEY_SPACING_DP * dip).toInt(), (SMALL_SPACING_DP * dip).toInt(),
                (KEY_SPACING_DP * dip).toInt(), (SMALL_SPACING_DP * dip).toInt()
            )
        }
        row.addView(key, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = (KEY_SPACING_DP * dip).toInt() })

        val sug = TextView(activity).apply {
            text = "Suggestion"
            textSize = SUG_TEXT_SP
        }
        row.addView(sug, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = (KEY_SPACING_DP * dip).toInt() })

        val hl = View(activity)
        row.addView(hl, LinearLayout.LayoutParams((HIGHLIGHT_SIZE_DP * dip).toInt(), (HIGHLIGHT_SIZE_DP * dip).toInt()))

        previewRow = row
        previewKey = key
        previewSug = sug
        previewHl = hl

        col.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (SMALL_SPACING_DP * dip).toInt() })
    }

    fun updatePreview() {
        val row = previewRow ?: return
        val bg = getCurrentColor(ThemePrefs.KIND_BG)
        val hl = getCurrentColor(ThemePrefs.KIND_HL)
        val text = getCurrentColor(ThemePrefs.KIND_TEXT)
        val dark = ThemePrefs.isDarkMode(activity)
        ThemePrefs.gradientDrawable(prefs, dark, activity.resources.displayMetrics.widthPixels)?.let {
            row.background = it
        } ?: run {
            row.background = GradientDrawable().apply { setColor(bg) }
        }
        com.piotv.keytab.ime.BackgroundImage.apply(row, activity, row.background)
        previewKey?.background = GradientDrawable().apply {
            cornerRadius = KEY_RADIUS_DP * dip
            setColor(getCurrentColor(ThemePrefs.KIND_KEY))
        }
        previewKey?.setTextColor(text)
        previewSug?.setTextColor(text)
        previewHl?.background = GradientDrawable().apply {
            cornerRadius = HIGHLIGHT_RADIUS_DP * dip
            setColor(hl)
        }
    }
}
