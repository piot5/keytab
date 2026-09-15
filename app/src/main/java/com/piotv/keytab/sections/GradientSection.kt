package com.piotv.keytab.sections

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.piotv.keytab.ime.ThemePrefs

class GradientSection(
    private val activity: android.app.Activity,
    private val prefs: SharedPreferences,
    private val onChange: () -> Unit
) {
    private val dip = activity.resources.displayMetrics.density
    private var gradientPreview: View? = null

    fun build(col: LinearLayout) {
        gradientPreview = View(activity)
        updateGradient()
        col.addView(gradientPreview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (40 * dip).toInt()
        ).apply { topMargin = (8 * dip).toInt() })

        val info = TextView(activity).apply {
            text = "Verlauf"
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, (4 * dip).toInt(), 0, (4 * dip).toInt())
        }
        col.addView(info, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    fun updateGradient() {
        if (ThemePrefs.hasGradient(prefs)) {
            gradientPreview?.background = ThemePrefs.gradientDrawable(prefs, 1000)
        } else {
            gradientPreview?.background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF2196F3"))
            }
        }
    }
}
