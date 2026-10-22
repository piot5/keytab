package com.piotv.keytab.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.piotv.keytab.Prefs
import java.io.File

/**
 * Optionaler Watch-Mechanismus für den Cline companion mode: jede reguläre
 * Datei, die im zugewachten Verzeichnis erscheint (oder ihr Inhalt sich ändert),
 * wird über [onNewFile] an den Editor weitergeleitet.
 *
 * Der Watcher ist rein optional und wird nur gestartet, wenn
 * [Prefs.KEY_CLINE_COMPANION_MODE] UND [Prefs.KEY_CLINE_AUTO_LOAD] aktiv sind.
 * Weitere Dateien in diesem Verzeichnis bleiben unbeobachtet (kein globales
 * Dateisystem-Overwatch) — der App muss eine Tastaturebene mit geöffnetem
 * Editor-Tab vorliegen, damit der Watch-Verzeichnis-Pfad bekannt ist.
 */
class ClineFileWatcher(
    private val context: Context,
    private val directory: File,
    private val pollMs: Long,
    private val onNewFile: (File) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = Runnable { poll() }
    private val knownPaths = mutableSetOf<String>()
    private val lastModified = mutableMapOf<String, Long>()
    private var started = false

    /**
     * Übernimmt den aktuellen Verzeichnisinhalt als "bekannt".
     *
     * Ohne dieses Seeding meldet der erste Poll **alle** bereits vorhandenen
     * Dateien als neu — beim Start des Watchers würden dadurch auf einmal
     * Dutzende Dateien in den Editor geladen. Nach dem Seeding meldet der
     * Watcher nur noch Dateien, die **nach** dem Start dazukommen oder
     * verändert werden (das ist der eigentliche Cline-Fall).
     */
    fun seedExistingFiles() {
        val files = directory.listFiles() ?: return
        for (f in files) {
            if (!f.isFile) continue
            knownPaths += f.absolutePath
            lastModified[f.absolutePath] = f.lastModified()
        }
    }

    /** Startet den Nachfrage-Polling (Polling, kein Push/FileObserver). */
    fun start() {
        if (started) return
        started = true
        handler.post(pollRunnable)
    }

    /** Stoppt den Polling bis zum nächsten [start]. */
    fun stop() {
        started = false
        handler.removeCallbacks(pollRunnable)
    }

    private fun poll() {
        val files = directory.listFiles() ?: return
        for (f in files) {
            if (!f.isFile) continue
            val path = f.absolutePath
            if (!knownPaths.contains(path)) {
                knownPaths += path
                lastModified[path] = f.lastModified()
                onNewFile(f)
            } else if (lastModified[path] != f.lastModified()) {
                // Geändert: erneut laden (z. B. cline überschreibt dieselbe Datei).
                lastModified[path] = f.lastModified()
                onNewFile(f)
            }
        }
        // Hebt aufgehobene Dateien nicht auf (letzte bekannte Pfade bleiben als
        // Historie; bei Wiederholung mit anderer Modification-Time wird neu geöffnet).
        if (started) {
            handler.postDelayed(pollRunnable, pollMs)
        }
    }

    fun isRunning(): Boolean = started
}
