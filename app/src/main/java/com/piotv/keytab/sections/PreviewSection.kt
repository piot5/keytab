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
    private val getCurrentColor: (String) -> Int,
    private val onChange: () -> Unit
) {
    private val dip = activity.resources.displayMetrics.density

    private var previewRow: LinearLayout? = null
    private var previewKey: TextView? = null
    private var previewSug: TextView? = null
    private var previewHl: View? = null

    fun build(col: LinearLayout) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dip).toInt(), (12 * dip).toInt(), (12 * dip).toInt(), (12 * dip).toInt())
        }

        val key = TextView(activity).apply {
            text = "A"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding((16 * dip).toInt(), (8 * dip).toInt(), (16 * dip).toInt(), (8 * dip).toInt())
        }
        row.addView(key, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = (16 * dip).toInt() })

        val sug = TextView(activity).apply {
            text = "Suggestion"
            textSize = 14f
        }
        row.addView(sug, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = (16 * dip).toInt() })

        val hl = View(activity)
        row.addView(hl, LinearLayout.LayoutParams((28 * dip).toInt(), (28 * dip).toInt()))

        previewRow = row
        previewKey = key
        previewSug = sug
        previewHl = hl

        col.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * dip).toInt() })
    }

    fun updatePreview() {
        val row = previewRow ?: return
        val bg = getCurrentColor(ThemePrefs.KIND_BG)
        val hl = getCurrentColor(ThemePrefs.KIND_HL)
        val text = getCurrentColor(ThemePrefs.KIND_TEXT)
        ThemePrefs.gradientDrawable(prefs, activity.resources.displayMetrics.widthPixels)?.let {
            row.background = it
        } ?: run {
            row.background = GradientDrawable().apply { setColor(bg) }
        }
        previewKey?.background = GradientDrawable().apply {
            cornerRadius = 6f * dip
            setColor(getCurrentColor(ThemePrefs.KIND_KEY))
        }
        previewKey?.setTextColor(text)
        previewSug?.setTextColor(text)
        previewHl?.background = GradientDrawable().apply {
            cornerRadius = 4f * dip
            setColor(hl)
        }
    }
}
