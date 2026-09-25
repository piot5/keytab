package com.piotv.keytab.ime

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.piotv.keytab.R
import java.io.File
import java.util.concurrent.Executor

/**
 * Ablage-Panel der IME: Clipboard-Historie (max. [MAX_ENTRIES] Einträge),
 * persistiert in clipboard_history.txt. I/O auf [ioExecutor], UI über [mainHandler].
 *
 * Hinweis Android 10+: Das Lesen fremder Clipboards ist nur der fokussierten App
 * bzw. dem aktiven IME erlaubt. Auto-Capture passiert daher nur, wenn
 * [canAutoCapture] true liefert (Input-View sichtbar = IME fokussiert).
 */
class ClipboardPanel(
    private val context: Context,
    private val ioExecutor: Executor,
    private val mainHandler: Handler,
    private val callbacks: Callbacks
) {
    data class Callbacks(
        val onCommit: (String) -> Unit,
        val canAutoCapture: () -> Boolean,
        val onAddToSnippet: (String) -> Unit = {},
        val onError: (String) -> Unit = {}
    )
    private companion object {
        const val MAX_ENTRIES = 50
        const val TAG = "KeyTabClipboard"
    }

    private val history = mutableListOf<String>()

    init {
        loadHistory()
    }

    /** Öffentliche Historie (für die Picker-Dialoge und Tests). */
    fun entries(): List<String> = history.toList()

    /** Wird beim Wechsel auf den Ablage-/Editor-Tab aufgerufen. */
    fun onSelected() {
        if (callbacks.canAutoCapture()) capture()
        refresh()
    }

    private fun currentClipboardText(): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        return cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }

    fun capture() {
        val text = currentClipboardText() ?: return
        if (history.firstOrNull() == text) return
        history.removeAll { it == text }
        history.add(0, text)
        if (history.size > MAX_ENTRIES) history.removeAt(history.lastIndex)
        persistAsync()
    }

    private fun refresh() {
        // UI-Refresh übernimmt der Picker-Dialog beim Öffnen (lazy)
    }

    /** Clipboard-Historie in den ListView des Clip-Tabs rendern. */
    fun refreshList(root: View) {
        val list = root.findViewById<ListView>(R.id.clip_list) ?: return
        val items = history.map { TextEditLogic.clipDisplayText(it) }
        if (items.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.clip_hint_empty), Toast.LENGTH_SHORT).show()
        }
        list.adapter = themedAdapter(context, items)
        list.setOnItemClickListener { _, _, position, _ ->
            history.getOrNull(position)?.let { callbacks.onCommit(it) }
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            history.getOrNull(position)?.let { entry ->
                android.app.AlertDialog.Builder(context)
                    .setTitle(R.string.clip_entry_actions)
                    .setItems(arrayOf(context.getString(R.string.clip_add_to_snippet))) { _, which ->
                        if (which == 0) callbacks.onAddToSnippet(entry)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
                .also { dialog ->
                    dialog.window?.apply {
                        setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
                        attributes.token = root.windowToken
                    }
                }
                .show()
            }
            true
        }
        // Clear-Button
        root.findViewById<Button>(R.id.btn_clip_clear)?.setOnClickListener {
            clear()
            refreshList(root)
        }
    }

    fun clear() {
        history.clear()
        persistAsync()
    }

    /**
     * Zeigt alle Clipboard-Einträge als Picker-Dialog an; [onPick] liefert den
     * gewählten Eintrag zurück (z. B. in das aktive Eingabefeld einsetzen).
     *
     * [windowToken] ist das Token des IME-Input-Views – nötig in einer IME ohne
     * Activity, sonst wirft ein AlertDialog eine BadTokenException und zeigt nichts.
     */
    fun showPicker(windowToken: android.os.IBinder?, onPick: (String) -> Unit) {
        val items = history.map { TextEditLogic.clipDisplayText(it) }
        if (items.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.clip_hint_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = android.app.AlertDialog.Builder(context)
            .setTitle(R.string.clip_pick_title)
            .setItems(items.toTypedArray()) { _, which ->
                history.getOrNull(which)?.let(onPick)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        if (windowToken != null) {
            dialog.window?.apply {
                setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
                attributes.token = windowToken
            }
        }
        dialog.show()
    }

    private fun historyFile(): File = File(context.filesDir, "clipboard_history.txt")

    private fun persistAsync() {
        val encoded = TextEditLogic.encodeClipHistory(history)
        val f = historyFile()
        ioExecutor.execute {
            try {
                f.writeText(encoded)
            } catch (e: Exception) {
                Log.w(TAG, "Clipboard history could not be persisted", e)
                mainHandler.post { callbacks.onError(context.getString(R.string.clip_history_save_failed)) }
            }
        }
        mainHandler.post { refresh() }
    }

    private fun loadHistory() {
        try {
            val f = historyFile()
            if (f.exists()) {
                history.clear()
                history.addAll(TextEditLogic.decodeClipHistory(f.readText()))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard history could not be loaded", e)
        }
    }
}
