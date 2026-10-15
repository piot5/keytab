package com.piotv.keytab.sections

import android.content.SharedPreferences
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import com.piotv.keytab.ime.ThemePrefs
import com.piotv.keytab.R

class LikelyHighlightSection(
    private val activity: android.app.Activity,
    private val prefs: SharedPreferences,
    private val onChange: () -> Unit
) {
    private val dip = activity.resources.displayMetrics.density
    private var likelyToggle: Button? = null

    fun build(col: LinearLayout) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        likelyToggle = Button(activity).apply {
            text = activity.getString(R.string.theme_likely_toggle)
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                val current = ThemePrefs.likelyHighlighting(prefs)
                com.piotv.keytab.Prefs.of(activity)
                    .edit().putBoolean(ThemePrefs.KEY_LIKELY, !current).apply()
                updateButtons()
                onChange()
            }
        }
        row.addView(likelyToggle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = (4 * dip).toInt()
            height = (32 * dip).toInt()
        })

        col.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (2 * dip).toInt() })
        updateButtons()
    }

    fun updateButtons() {
        val likely = ThemePrefs.likelyHighlighting(prefs)
        likelyToggle?.setBackgroundColor(if (likely) Color.parseColor("#FF2196F3") else Color.TRANSPARENT)
    }
}
