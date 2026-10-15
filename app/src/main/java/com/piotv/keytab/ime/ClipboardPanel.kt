package com.piotv.keytab.ime

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.piotv.keytab.R
import java.io.File

/**
 * Ablage-Panel der IME: Clipboard-Historie (max. [MAX_ENTRIES] Einträge),
 * persistiert in clipboard_history.txt. Persistenz läuft coroutine-basiert auf
 * dem übergebenen Scope; Datei-I/O nutzt [ioDispatcher], UI-Callbacks laufen
 * auf dem Main-Scope.
 *
 * Hinweis Android 10+: Das Lesen fremder Clipboards ist nur der fokussierten App
 * bzw. dem aktiven IME erlaubt. Auto-Capture passiert daher nur, wenn
 * [canAutoCapture] true liefert (Input-View sichtbar = IME fokussiert).
 */
class ClipboardPanel(
    private val context: Context,
    private val mainHandler: Handler,
    private val coroutineScope: CoroutineScope,
    private val callbacks: Callbacks,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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
    private val persistMutex = Mutex()

    /** Die Datei-Historie ist gelesen und in [history] angewendet (nur Main-Thread). */
    private var historyLoaded = false

    /** Vor dem Laden wurde die Historie geändert — danach wird nachgeschrieben. */
    private var writePendingBeforeLoad = false

    /** Vor dem Laden explizit geleert: der Load darf die alte Liste nicht zurückholen. */
    private var clearedBeforeLoad = false

    init {
        // Konstruktor läuft im IME-Aufbau auf dem Main-Thread
        // (KeyboardViewFactory.create → onCreateInputView) — Datei-I/O gehört
        // deshalb auf den ioDispatcher. Das Ergebnis wird auf dem Main-Thread
        // angewendet, damit `history` weiterhin nur dort verändert wird.
        //
        // Geschrieben wird erst, nachdem die Datei gelesen wurde
        // ([historyLoaded], siehe [persistAsync]): sonst überschrieb ein früher
        // Capture (Clip-Tab mit canAutoCapture) die Datei, *bevor* dieser Load sie
        // gelesen hatte — die gespeicherte Historie war dann dauerhaft weg. Ein
        // Load wird außerdem nicht mehr verworfen, sondern mit dem, was inzwischen
        // dazukam, zusammengeführt.
        coroutineScope.launch {
            val loaded = withContext(ioDispatcher) {
                runCatching { readHistoryFile() }.getOrDefault(emptyList())
            }
            mainHandler.post { applyLoaded(loaded) }
        }
    }

    /**
     * Wendet die geladene Historie an (Main-Thread): neue Einträge bleiben vorne,
     * die geladenen folgen ohne Duplikate, der Deckel gilt weiter. Ein vor dem
     * Laden ausgelöstes Schreiben wird danach nachgeholt.
     */
    private fun applyLoaded(loaded: List<String>) {
        if (!clearedBeforeLoad) {
            val known = history.toMutableSet()
            for (entry in loaded) if (known.add(entry)) history.add(entry)
            while (history.size > MAX_ENTRIES) history.removeAt(history.lastIndex)
        }
        historyLoaded = true
        if (writePendingBeforeLoad) {
            writePendingBeforeLoad = false
            persistAsync()
        }
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
        // Größen-Gate: ein einzelner riesiger Clipboard-Inhalt würde Historie,
        // Persistenz-Datei und Speicher fluten (siehe [TextEditLogic]).
        if (TextEditLogic.exceedsStoredTextLimit(text)) {
            callbacks.onError(context.getString(R.string.clip_entry_too_large))
            return
        }
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
                val dialog = android.app.AlertDialog.Builder(context)
                    .setTitle(R.string.clip_entry_actions)
                    .setItems(arrayOf(context.getString(R.string.clip_add_to_snippet))) { _, which ->
                        if (which == 0) callbacks.onAddToSnippet(entry)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
                // IME ohne Activity: Fenster-Token nötig (sonst BadTokenException).
                if (!ImeDialog.show(dialog, root.windowToken)) {
                    Toast.makeText(context, R.string.dialog_unavailable, Toast.LENGTH_SHORT).show()
                }
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
        // Vor dem ersten Load darf der Load die Liste nicht zurückholen.
        if (!historyLoaded) clearedBeforeLoad = true
        history.clear()
        persistAsync()
    }

    /**
     * Zeigt alle Clipboard-Einträge als Picker-Dialog an; [onPick] liefert den
     * gewählten Eintrag zurück (z. B. in das aktive Eingabefeld einsetzen).
     *
     * [windowToken] ist das Token des IME-Input-Views – nötig in einer IME ohne
     * Activity, sonst wirft ein AlertDialog eine BadTokenException und zeigt
     * nichts. Fehlt es, erscheint ein Hinweis statt eines Absturzes.
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
        if (!ImeDialog.show(dialog, windowToken)) {
            Toast.makeText(context, R.string.dialog_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun historyFile(): File = File(context.filesDir, "clipboard_history.txt")

    private fun persistAsync() {
        // Solange die Datei nicht gelesen ist, darf sie nicht überschrieben
        // werden: der Load würde sonst eine Liste lesen, die wir gerade ersetzt
        // haben, und die alte Historie wäre endgültig weg. Stattdessen wird das
        // Schreiben gemerkt und nach dem Load nachgeholt ([applyLoaded]).
        if (!historyLoaded) {
            writePendingBeforeLoad = true
            return
        }
        // Snapshot auf dem Main-Thread (nur die Liste kopieren), Serialisieren und
        // Schreiben auf dem ioDispatcher: der Join über die ganze Historie lief
        // vorher auf dem Main-Thread und konnte bei großen Einträgen stocken.
        val snapshot = history.toList()
        val f = historyFile()
        coroutineScope.launch {
            try {
                persistMutex.withLock {
                    withContext(ioDispatcher) {
                        f.writeText(TextEditLogic.encodeClipHistory(snapshot))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Clipboard history could not be persisted", e)
                callbacks.onError(context.getString(R.string.clip_history_save_failed))
            }
        }
        mainHandler.post { refresh() }
    }

    /** Liest die persistierte Historie (reines Lesen, kein Zustandswechsel). */
    private fun readHistoryFile(): List<String> {
        val f = historyFile()
        return if (f.exists()) TextEditLogic.decodeClipHistory(f.readText()) else emptyList()
    }
}
