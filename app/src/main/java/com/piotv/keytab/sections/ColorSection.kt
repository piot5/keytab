package com.piotv.keytab.sections

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.SeekBar
import com.piotv.keytab.ColorWheelView
import com.piotv.keytab.ime.ThemePrefs

class ColorSection(
    private val activity: android.app.Activity,
    private val prefs: SharedPreferences,
    private val getCurrentTarget: () -> String,
    private val onChange: () -> Unit
) {
    private val dip = activity.resources.displayMetrics.density

    private var colorWheel: ColorWheelView? = null
    private var brightnessBar: SeekBar? = null
    private var alphaSlider: SeekBar? = null
    private var currentHue = 0f
    private var currentSat = 1f

    fun build(col: LinearLayout) {
        colorWheel = ColorWheelView(activity).apply {
            onColorPicked = { color ->
                // Update internal HSV from picked color
                val hsv = FloatArray(3)
                Color.colorToHSV(color, hsv)
                currentHue = hsv[0]
                currentSat = hsv[1]
                applyColorToCurrentTarget(color)
            }
        }
        col.addView(colorWheel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (200 * dip).toInt()
        ).apply { topMargin = (8 * dip).toInt() })

        brightnessBar = SeekBar(activity).apply {
            max = 100
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { updateColorFromWheel() }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        col.addView(brightnessBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        alphaSlider = SeekBar(activity).apply {
            max = 255
            progress = 255
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { updateColorFromWheel() }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        col.addView(alphaSlider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    fun updateControls(target: String) {
        val color = ThemePrefs.getColor(prefs, ThemePrefs.isDarkMode(activity), target, Color.GRAY)
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        currentHue = hsv[0]
        currentSat = hsv[1]
        brightnessBar?.progress = (hsv[2] * 100).toInt()
        alphaSlider?.progress = Color.alpha(color)
        colorWheel?.setArgb(color)
    }

    private fun updateColorFromWheel() {
        val value = (brightnessBar?.progress ?: 50) / 100f
        val color = Color.HSVToColor(alphaSlider?.progress ?: 255, floatArrayOf(currentHue, currentSat, value))
        applyColorToCurrentTarget(color)
    }

    private fun applyColorToCurrentTarget(color: Int) {
        val target = getCurrentTarget()
        val dark = ThemePrefs.isDarkMode(activity)
        ThemePrefs.setColor(prefs, dark, target, color)
        ThemePrefs.bumpVersion(prefs)
        onChange()
    }
}
