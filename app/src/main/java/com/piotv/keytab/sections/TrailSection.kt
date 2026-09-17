package com.piotv.keytab.sections

import android.content.SharedPreferences
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import com.piotv.keytab.R
import com.piotv.keytab.ime.ThemePrefs

/**
 * UI-Sektion für den Tippspur-Effekt (Trail) in den Theme-Einstellungen.
 * Optional, mit Farbe (über Farbkreis auswählbar) und An/Aus-Schalter.
 * Standard-Farbe: Blau (#2196F3).
 */
class TrailSection(
    private val activity: android.app.Activity,
    private val prefs: SharedPreferences,
    private val onChange: () -> Unit
) {
    private val dip = activity.resources.displayMetrics.density
    private var trailToggle: Button? = null

    fun build(col: LinearLayout) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        trailToggle = Button(activity).apply {
            text = activity.getString(R.string.theme_trail_toggle)
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                val current = ThemePrefs.trailEnabled(prefs)
                activity.getSharedPreferences(ThemePrefs.PREFS, android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(ThemePrefs.KEY_TRAIL, !current).apply()
                updateButton()
                onChange()
            }
        }

        row.addView(trailToggle, LinearLayout.LayoutParams(
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

        // Hinweis-Text
        col.addView(android.widget.TextView(activity).apply {
            text = activity.getString(R.string.theme_trail_hint)
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#888888"))
            setPadding(0, (4 * dip).toInt(), 0, (2 * dip).toInt())
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        updateButton()
    }

    fun updateButton() {
        val enabled = ThemePrefs.trailEnabled(prefs)
        trailToggle?.setBackgroundColor(
            if (enabled) Color.parseColor("#FF2196F3") else Color.TRANSPARENT
        )
    }
}