package com.piotv.keytab.sections

import android.content.Intent
import android.content.SharedPreferences
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.piotv.keytab.Prefs
import com.piotv.keytab.R
import com.piotv.keytab.ime.ThemePrefs

class BackgroundSection(
    private val activity: AppCompatActivity,
    private val prefs: SharedPreferences,
    private val onChange: () -> Unit
) {
    private var sourceLabel: TextView? = null
    private var modes: Spinner? = null
    private val values = listOf(Prefs.FILL_FIT, Prefs.FILL_COVER, Prefs.FILL_STRETCH)
    private val picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit().putString(Prefs.KEY_BG_IMAGE_URI, uri.toString()).apply()
                changed()
            } catch (_: Exception) {
                Toast.makeText(activity, R.string.background_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun build(col: LinearLayout) {
        col.addView(TextView(activity).apply { setText(R.string.background_title); textSize = 18f })
        col.addView(Button(activity).apply {
            setText(R.string.background_choose)
            setOnClickListener { picker.launch(arrayOf("image/*")) }
        })
        sourceLabel = TextView(activity)
        col.addView(sourceLabel)
        col.addView(Button(activity).apply {
            setText(R.string.background_remove)
            setOnClickListener { prefs.edit().remove(Prefs.KEY_BG_IMAGE_URI).apply(); changed() }
        })
        modes = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                listOf(activity.getString(R.string.background_fit), activity.getString(R.string.background_cover),
                    activity.getString(R.string.background_stretch)))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                    val value = values[pos]
                    if (prefs.getString(Prefs.KEY_BG_IMAGE_FILL, Prefs.FILL_FIT) != value) {
                        prefs.edit().putString(Prefs.KEY_BG_IMAGE_FILL, value).apply()
                        changed()
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) { /* kein Bedarf: keine Neutralposition */ return }
            }
        }
        col.addView(modes)
        col.addView(TextView(activity).apply { setText(R.string.background_ratio) })
        refresh()
    }

    private fun changed() { ThemePrefs.bumpVersion(prefs); refresh(); onChange() }

    fun refresh() {
        sourceLabel?.text = prefs.getString(Prefs.KEY_BG_IMAGE_URI, "").orEmpty().ifBlank {
            activity.getString(R.string.background_none)
        }
        modes?.setSelection(values.indexOf(prefs.getString(Prefs.KEY_BG_IMAGE_FILL, Prefs.FILL_FIT)).coerceAtLeast(0))
    }
}
