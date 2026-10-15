package com.piotv.keytab.ime

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.piotv.keytab.R

/**
 * ArrayAdapter mit Theme-korrekter Textfarbe: android.R.layout.simple_list_item_1
 * wird ohne App-Theme infaltet (Text fällt auf Schwarz zurück → im Night-Theme
 * auf dunklem Grund unlesbar). Hier wird text_primary explizit gesetzt –
 * resolved über [ContextCompat], funktioniert Day- UND Night-Theme.
 *
 * @param textSizeSp optionale Schriftgröße in sp. `null` = Plattform-Default von
 *   `simple_list_item_1` (die Dateilisten setzen hier einen kleineren Wert, damit
 *   deutlich mehr Einträge sichtbar sind).
 * @param compactRows enge Zeilen: `minHeight = 0` und kleines Padding. Ohne das
 *   bliebe die Zeilenhöhe am Plattform-Minimum hängen und eine kleinere Schrift
 *   wäre nicht zu sehen.
 */
fun themedAdapter(
    context: Context,
    items: List<String>,
    textSizeSp: Float? = null,
    compactRows: Boolean = false
): ArrayAdapter<String> =
    object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, items) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            (super.getView(position, convertView, parent) as TextView).apply {
                // parent.context ist der themed Context der Listenzeile (korrektes Day/Night)
                setTextColor(ContextCompat.getColor(parent.context, R.color.text_primary))
                textSizeSp?.let { setTextSize(TypedValue.COMPLEX_UNIT_SP, it) }
                if (compactRows) {
                    val padH = (COMPACT_PAD_H_DP * resources.displayMetrics.density).toInt()
                    val padV = (COMPACT_PAD_V_DP * resources.displayMetrics.density).toInt()
                    minHeight = 0
                    setPadding(padH, padV, padH, padV)
                }
            }
    }

private const val COMPACT_PAD_H_DP = 8
private const val COMPACT_PAD_V_DP = 2
