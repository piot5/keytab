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
 *
 * Zusätzlich (v0.9.8):
 *  - **Stufen**: Anzahl der Verblass-Schritte (3/5/7/10), Pref `trail_steps`.
 *  - **Treffer-Markierung**: färbt getippte Wörter grün, wenn sie exakt dem
 *    obersten Vorschlag entsprechen (reine Bestätigung; es wird nichts geändert).
 *
 * Der Trail erscheint nie in Passwort-Feldern (siehe [TrailLogic.isTrailAllowed]).
 */
class TrailSection(
    private val activity: android.app.Activity,
    private val prefs: SharedPreferences,
    private val onChange: () -> Unit
) {
    private val dip = activity.resources.displayMetrics.density
    private var trailToggle: Button? = null
    private var stepsToggle: Button? = null
    private var traceToggle: Button? = null

    /** Durchschaltbare Stufen – bewusst kurz und ohne Freitext-Eingabe. */
    private val stepChoices = listOf(3, 5, 7, 10)

    fun build(col: LinearLayout) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        trailToggle = toggleButton(activity.getString(R.string.theme_trail_toggle)) {
            val current = ThemePrefs.trailEnabled(prefs)
            putBoolean(ThemePrefs.KEY_TRAIL, !current)
            updateButton()
            onChange()
        }
        stepsToggle = toggleButton("") {
            val current = ThemePrefs.trailSteps(prefs)
            val next = stepChoices[(stepChoices.indexOf(current).takeIf { it >= 0 } ?: 0)
                .let { (it + 1) % stepChoices.size }]
            putInt(ThemePrefs.KEY_TRAIL_STEPS, next)
            updateButton()
            onChange()
        }
        traceToggle = toggleButton(activity.getString(R.string.theme_trail_trace_toggle)) {
            val current = ThemePrefs.trailTraceEnabled(prefs)
            putBoolean(ThemePrefs.KEY_TRAIL_TRACE, !current)
            updateButton()
            onChange()
        }

        for (btn in listOf(trailToggle, stepsToggle, traceToggle)) {
            row.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (4 * dip).toInt()
                height = (32 * dip).toInt()
            })
        }

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
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        col.addView(android.widget.TextView(activity).apply {
            text = activity.getString(R.string.theme_trail_trace_hint)
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#888888"))
            setPadding(0, 0, 0, (2 * dip).toInt())
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        updateButton()
    }

    private fun toggleButton(label: String, onClick: () -> Unit): Button =
        Button(activity).apply {
            text = label
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
        }

    private fun putBoolean(key: String, value: Boolean) {
        com.piotv.keytab.Prefs.of(activity)
            .edit().putBoolean(key, value).apply()
    }

    private fun putInt(key: String, value: Int) {
        com.piotv.keytab.Prefs.of(activity)
            .edit().putInt(key, value).apply()
    }

    fun updateButton() {
        val enabled = ThemePrefs.trailEnabled(prefs)
        trailToggle?.setBackgroundColor(
            if (enabled) Color.parseColor("#FF2196F3") else Color.TRANSPARENT
        )
        stepsToggle?.text = activity.getString(R.string.theme_trail_steps, ThemePrefs.trailSteps(prefs))
        stepsToggle?.setBackgroundColor(
            if (enabled) Color.parseColor("#FF3F51B5") else Color.TRANSPARENT
        )
        traceToggle?.setBackgroundColor(
            if (enabled && ThemePrefs.trailTraceEnabled(prefs)) Color.parseColor("#FF4CAF50")
            else Color.TRANSPARENT
        )
    }
}
