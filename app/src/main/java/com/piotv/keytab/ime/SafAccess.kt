package com.piotv.keytab.ime

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.piotv.keytab.Prefs

/**
 * Zentraler Zugriffspunkt für den SAF-Ordnerzugriff (Storage Access Framework,
 * `ACTION_OPEN_DOCUMENT_TREE`) — **Ersatz für `MANAGE_EXTERNAL_STORAGE`** (v0.24).
 * App-Einstellungen (`MainActivity`) und Files-Tab (`FileManagerPanel`) müssen
 * dieselbe Antwort bekommen, sonst widersprechen sich Statuszeile und Listen-Hinweis.
 *
 * Funktionweise: die gewählte Baum-URI wird mit persistierbarer Lese-/Schreib-
 * Erlaubnis versehen und in den Prefs gespeichert. [treeUri] liefert sie nur
 * zurück, solange die Plattform die Erlaubnis **noch** führt (persistedUri-
 * Permissions) — nach einem Revoke im System-UI fällt der Files-Tab sauber in
 * den Medien-Fallback zurück.
 *
 * [override] ist der dokumentierte Testhaken (wie früher bei `StorageAccess`):
 * Robolectric verwaltet keine echten Dokument-Provider, der gewährte Zustand ist
 * in JVM-Tests sonst nicht prüfbar. Produktion lässt den Haken auf `null`.
 *
 * Der Zugriff ist bewusst **optional**: nie beim Start, nie im Hintergrund,
 * nur über den Button „Ordner wählen“ in den Einstellungen. Ohne ihn bleibt der
 * Medien-Weg (Ordner + Bilder) vollständig nutzbar. Die App hat kein INTERNET —
 * gelesene Dateien können das Gerät nicht verlassen.
 */
internal object SafAccess {

    /** Prefs-Schlüssel für die persistierte Baum-URI. */
    const val KEY_TREE = "saf_tree_uri"

    /** Stellvertreter-URI für Tests (Listing schlägt fehl → sauberer Hinweis). */
    internal const val TEST_URI = "content://keytab.test/tree/primary"

    /** Testhaken: `null` = echte Plattform-Abfrage. */
    internal var override: Boolean? = null

    /**
     * Übernimmt [uri] als Arbeitsordner: persistierbare Lese-/Schreib-Erlaubnis
     * nehmen (wenn der Provider sie anbietet) und URI speichern. Auch eine nur
     * session-weite Freigabe wird gespeichert — [treeUri] prüft die tatsächlich
     * geltende Erlaubnis und fällt sonst sauber auf den Medien-Fallback zurück.
     */
    fun grant(context: Context, uri: Uri): Boolean {
        val persisted = try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            true
        } catch (_: Exception) {
            false
        }
        Prefs.of(context).edit().putString(KEY_TREE, uri.toString()).apply()
        return persisted
    }

    /**
     * Die aktive Baum-URI — nur solange die Plattform die Lese-Erlaubnis **noch**
     * hält. Defensiv verpackt: die Abfrage geht über den ContentResolver und darf
     * Statuszeile und Files-Tab nie zum Absturz bringen (gleiche Regel wie beim
     * alten `StorageAccess`). Ein nicht mehr gültiger Baum ist `null`.
     */
    fun treeUri(context: Context): Uri? {
        override?.let { granted ->
            // Tests: Stellvertreter (gespeicherte URI hat Vorrang, sonst Test-URI)
            return if (granted) Uri.parse(
                Prefs.of(context).getString(KEY_TREE, null) ?: TEST_URI
            ) else null
        }
        val stored = Prefs.of(context).getString(KEY_TREE, null) ?: return null
        val uri = runCatching { Uri.parse(stored) }.getOrNull() ?: return null
        val held = try {
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
        } catch (_: Exception) {
            false
        }
        return if (held) uri else null
    }

    /** true, wenn ein gültiger SAF-Ordnerzugriff besteht (Status-Zeile). */
    fun granted(context: Context): Boolean = treeUri(context) != null

    /** Ordnerzugriff entziehen (persistierbare Erlaubnis freigeben + Pref löschen). */
    fun revoke(context: Context) {
        val stored = Prefs.of(context).getString(KEY_TREE, null) ?: return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(stored),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        Prefs.of(context).edit().remove(KEY_TREE).apply()
    }

    /** Nur für Tests: Haken zurücksetzen. */
    internal fun resetForTests() {
        override = null
    }
}
