package com.piotv.keytab.ime

import android.content.Context
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import com.piotv.keytab.R
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * Interaktives Terminal der IME: eine eigene Shell (/system/bin/sh) läuft im
 * Hintergrund; Befehle aus der Eingabezeile werden in ihre Stdin geschrieben,
 * die Ausgabe landet live im Verlauf (TextView) ÜBER der Eingabezeile.
 *
 * Die Shell läuft in der App-Sandbox (kein Root, kein Termux-Umfeld) –
 * System-Befehle wie ls/pwd/cat/wc funktionieren, `pm`/`am` eingeschränkt.
 * Die Eingabezeile bekommt die Tastatur-Inputs (wie beim Editor), Enter/Send
 * führt die Zeile aus und leert das Feld.
 */
class TerminalPanel(
    private val context: Context,
    root: View,
    private val mainHandler: Handler
) {

    private val input: EditText? = root.findViewById(R.id.terminal_input)
    private val output: TextView? = root.findViewById(R.id.term_output)
    private var shell: Process? = null
    private var stdin: OutputStream? = null
    private var readerThread: Thread? = null
    private val history = StringBuilder()

    // Für den Standard-Prompt: user@host:pfad$
    private var userName: String = "user"
    private val hostName: String = android.os.Build.HOST.ifBlank { "localhost" }
    private var cwd: String = context.filesDir.absolutePath ?: "/"

    init {
        root.findViewById<Button>(R.id.btn_term_send)?.setOnClickListener { runLine() }
        // Enter direkt im Feld (Hardware-Tastatur) führt ebenfalls aus
        input?.setOnEditorActionListener { _, _, _ -> runLine(); true }
        resolveUserName()
        startShell()
        appendOut(prompt() + "\n")
    }

    /** Prompt im Standard-Stil: user@host:pfad$ (Home = ~) */
    private fun prompt(): String {
        val home = context.filesDir.absolutePath
        val shownDir = when {
            cwd == home -> "~"
            cwd.startsWith(home + "/") -> "~" + cwd.substring(home.length)
            else -> cwd
        }
        return "$userName@$hostName:$shownDir$ "
    }

    /** Shell-User für den Prompt ermitteln (Fallback: Android-UID-Name). */
    private fun resolveUserName() {
        Thread {
            val name = try {
                val p = ProcessBuilder("/system/bin/sh", "-c", "id -un")
                    .redirectErrorStream(true).start()
                p.inputStream.bufferedReader().readText().trim().ifBlank { null }.also { p.destroy() }
            } catch (_: Exception) { null }
            mainHandler.post {
                userName = name ?: "u" + android.os.Process.myUid() / 100000 + "a" +
                        (android.os.Process.myUid() % 100000)
            }
        }.apply { isDaemon = true; start() }
    }

    /** Text + Cursorposition (für die Wortvorhersage), null wenn nicht bereit. */
    fun cursorContext(): Pair<CharSequence, Int>? {
        val et = input ?: return null
        val t = et.text ?: return null
        return t to et.selectionEnd.coerceIn(0, t.length)
    }

    /** Löscht [count] Zeichen vor dem Cursor (Vorschlag ersetzt Teilwort). */
    fun deleteBefore(count: Int) {
        val et = input ?: return
        val editable = et.text ?: return
        val cursor = et.selectionEnd.coerceIn(0, editable.length)
        val start = (cursor - count.coerceAtLeast(0)).coerceAtLeast(0)
        if (start < cursor) editable.delete(start, cursor)
    }

    /** Fügt Text an der Cursorposition ein (ersetzt eine Selektion). */
    fun insert(text: String) {
        val et = input ?: return
        val editable = et.text ?: return
        var start = et.selectionStart.coerceIn(0, editable.length)
        val end = et.selectionEnd.coerceIn(start, editable.length)
        editable.replace(start, end, text)
        start += text.length
        et.setSelection(start)
    }

    /** Löscht in der Zeile: ein Zeichen oder bis zum Wortanfang ([word] = true). */
    fun delete(word: Boolean) {
        val et = input ?: return
        val editable = et.text ?: return
        val cursor = et.selectionEnd.coerceIn(0, editable.length)
        if (cursor == 0) return
        if (word) {
            val start = TextEditLogic.wordStartIndex(editable, cursor)
            if (start < cursor) {
                editable.delete(start, cursor)
                et.setSelection(start)
            } else {
                editable.delete(cursor - 1, cursor)
                et.setSelection(cursor - 1)
            }
        } else {
            editable.delete(cursor - 1, cursor)
            et.setSelection(cursor - 1)
        }
    }

    /** Öffentlicher Einstieg: aktuelle Zeile ausführen (Enter/Send). */
    fun send() = runLine()

    private fun runLine() {
        val et = input ?: return
        val cmd = et.text?.toString()?.trim() ?: return
        if (cmd.isEmpty()) return
        et.setText("")
        // Standard-Look: Zeile mit Prompt im Verlauf anzeigen
        appendOut(prompt() + cmd + "\n")
        trackCd(cmd)
        try {
            stdin?.write((cmd + "\n").toByteArray())
            stdin?.flush()
        } catch (e: Exception) {
            appendOut(context.getString(R.string.terminal_shell_dead) + "\n")
            appendOut(prompt() + "\n")
        }
    }

    /** Verfolgt `cd`-Befehle, damit der Prompt dem Verzeichnis folgt. */
    private fun trackCd(cmd: String) {
        val parts = cmd.trim().split(Regex("\\s+"))
        if (parts.firstOrNull()?.lowercase() != "cd") return
        val target = parts.getOrNull(1) ?: return
        val home = context.filesDir.absolutePath
        val newDir = when {
            target == "~" || target == "" -> home
            target.startsWith("~") -> home + target.substring(1)
            target.startsWith("/") -> target
            else -> cwd + "/" + target
        }
        cwd = File(newDir).normalize().absolutePath
        if (!File(cwd).isDirectory) cwd = home
    }

    private fun appendOut(text: String) {
        history.append(text)
        if (history.length > MAX_HISTORY) history.delete(0, history.length - MAX_HISTORY)
        output?.text = history
        (output?.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN)
    }

    /** Findet eine verfügbare Shell (Fallback-Chain). */
    private fun findShell(): String {
        val shells = listOf("/system/bin/sh", "/bin/sh", "/system/bin/ksh", "/vendor/bin/sh")
        return shells.firstOrNull { runCatching { File(it).canExecute() }.getOrDefault(false) } ?: "/system/bin/sh"
    }

    /** Startet die Hintergrund-Shell und den Ausgabe-Lesethread. */
    private fun startShell() {
        try {
            val p = ProcessBuilder(findShell()).redirectErrorStream(true)
            p.directory(context.filesDir)
            shell = p.start()
            stdin = shell?.outputStream
            readerThread = Thread {
                val reader = InputStreamReader(shell?.inputStream ?: return@Thread)
                val buf = CharArray(512)
                var n: Int
                try {
                    while (reader.read(buf).also { n = it } > 0) {
                        val chunk = String(buf, 0, n)
                        mainHandler.post { appendOut(chunk) }
                    }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; start() }
        } catch (e: Exception) {
            mainHandler.post {
                appendOut(context.getString(R.string.terminal_start_failed) + "\n")
            }
        }
    }

    /** Beendet die Shell (beim Zerstören der Tastatur aufrufen). */
    fun shutdown() {
        try { stdin?.close() } catch (_: Exception) {}
        readerThread?.interrupt()
        shell?.destroy()
        shell = null
        stdin = null
    }

    private companion object {
        // Verlauf begrenzen, damit die TextView bei langen Ausgaben nicht explodiert
        const val MAX_HISTORY = 12000
    }
}