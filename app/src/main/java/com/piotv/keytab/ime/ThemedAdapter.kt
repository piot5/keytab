package com.piotv.keytab.ime

import android.content.Context
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
 */
fun themedAdapter(context: Context, items: List<String>): ArrayAdapter<String> =
    object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, items) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            (super.getView(position, convertView, parent) as TextView).apply {
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            }
    }
