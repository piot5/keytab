package com.piotv.keytab.ime

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap

/**
 * I/O-Schicht für den SAF-Baum (Storage Access Framework) — direkt über
 * `DocumentsContract` + `ContentResolver`, **absichtlich ohne die
 * `androidx.documentfile`-Abhängigkeit**: das Build verifiziert jede Abhängigkeit
 * per Checksumme (`gradle/verification-metadata.xml`), und für die hier genutzten
 * Abfragen braucht es kein zusätzliches Artefakt.
 *
 * Alle Methoden sind defensiv: ein fehlender/entzogener Baum oder ein abweichend
 * antwortender Provider liefert `null`/`false` statt einer Exception — der
 * Files-Tab zeigt dann den bekannten „Kein Speicherzugriff“-Hinweis.
 *
 * Das **Pfad-Feature** (Einfügen echter Pfade für Termux) lebt in [SafPaths]:
 * Einträge im Primär-Volume bekommen ihren echten Pfad mitgeliefert
 * ([SafEntry.realPath]), andere Volumes fallen auf den Dateinamen zurück.
 */
internal data class SafEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
    val docId: String,
    val docUri: Uri,
    /** Echter Pfad im Primär-Volume, sonst `null` (siehe [SafPaths]). */
    val realPath: String?
)

internal object SafFiles {

    private val PROJECTION = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED
    )

    // Spaltenindizes passend zu [PROJECTION] — benannt statt Rohzahlen (detekt MagicNumber).
    private const val COL_DOC_ID = 0
    private const val COL_DISPLAY_NAME = 1
    private const val COL_MIME = 2
    private const val COL_SIZE = 3
    private const val COL_MODIFIED = 4

    /** Stream-Puffer für Kopiervorgänge (8 KiB, gängige FS-Blockgröße ×2). */
    private const val COPY_BUFFER = 8_192

    /** Baum-ID eines Tree-URI (dekodiert), defensiv `"primary"` als Fallback. */
    fun treeId(treeUri: Uri): String =
        runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrDefault("primary")

    /** Dokument-ID der Baumwurzel (= die Baum-ID selbst). */
    fun rootDocId(treeUri: Uri): String = treeId(treeUri)

    /** Eintrags-Label für den Editor-Picker (gleiche Form wie der File-Modus). */
    fun label(e: SafEntry): String =
        if (e.isDir) "\uD83D\uDCC1 ${e.name}/" else "\uD83D\uDCC4 ${e.name}"

    /** true, wenn [docId] im Baum ein Verzeichnis ist (für die Zustands-Wiederherstellung). */
    fun isDir(context: Context, treeUri: Uri, docId: String): Boolean = try {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        context.contentResolver
            .query(uri, arrayOf(Document.COLUMN_MIME_TYPE), null, null, null)
            ?.use { c -> c.moveToFirst() && c.getString(0) == Document.MIME_TYPE_DIR } ?: false
    } catch (_: Exception) {
        false
    }

    /**
     * Kinder von [parentDocId] auflisten — **alle Dateitypen** (das ist der
     * Gewinn gegenüber dem Medien-Fallback). Sortierung identisch zum File-Modus:
     * Ordner zuerst, dann Name (case-insensitive). `null` = kein Zugriff.
     */
    fun listChildren(context: Context, treeUri: Uri, parentDocId: String): List<SafEntry>? {
        val out = mutableListOf<SafEntry>()
        return try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            context.contentResolver.query(children, PROJECTION, null, null, null)?.use { c ->
                val tree = treeId(treeUri)
                while (c.moveToNext()) {
                    val id = c.getString(COL_DOC_ID) ?: continue
                    val name = c.getString(COL_DISPLAY_NAME) ?: id
                    val mime = c.getString(COL_MIME)
                    out += SafEntry(
                        name = name,
                        isDir = mime == Document.MIME_TYPE_DIR,
                        size = c.getLong(COL_SIZE),
                        lastModified = c.getLong(COL_MODIFIED),
                        docId = id,
                        docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                        realPath = SafPaths.realPath(tree, id)
                    )
                }
            } ?: return null
            out.sortedWith(
                compareByDescending<SafEntry> { it.isDir }.thenBy { it.name.lowercase() }
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Größe in Bytes (`-1` unbekannt) — für das Editor-Größen-Gate vor dem Lesen. */
    fun sizeOf(context: Context, docUri: Uri): Long = try {
        context.contentResolver
            .query(docUri, arrayOf(Document.COLUMN_SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1L } ?: -1L
    } catch (_: Exception) {
        -1L
    }

    /** Anzeigename eines Dokuments (`null`, wenn der Provider ihn nicht nennt). */
    fun displayName(context: Context, docUri: Uri): String? = try {
        context.contentResolver
            .query(docUri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    /** Text eines Dokuments lesen (UTF-8); wirft bei I/O-Fehlern wie `File.readText`. */
    fun readText(context: Context, docUri: Uri): String =
        context.contentResolver.openInputStream(docUri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw java.io.IOException("no stream for $docUri")

    /** Text in ein Dokument schreiben (truncate, „wt“). `false` bei I/O-Fehlern. */
    fun writeText(context: Context, docUri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(docUri, "wt")?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
            it.flush()
            true
        } ?: false
    } catch (_: Exception) {
        false
    }

    /** docId eines Kindes mit [name] in [parentDocId], `null` wenn nicht vorhanden. */
    fun findChild(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        name: String
    ): String? = try {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        context.contentResolver
            .query(
                children,
                arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )
            ?.use { scanForName(it, name) }
    } catch (_: Exception) {
        null
    }

    /** Zeilenscan eines Kinder-Cursors nach Anzeigename (ent-Verschachtelung). */
    private fun scanForName(c: android.database.Cursor, name: String): String? {
        while (c.moveToNext()) {
            if (c.getString(COL_DISPLAY_NAME) == name) return c.getString(COL_DOC_ID)
        }
        return null
    }

    /**
     * Kopiert [src] (Datei **oder** Verzeichnis, rekursiv) nach
     * [destParentDocId] im Baum [destTree]. Mit [replaceDocId] wird ein
     * vorhandenes Ziel überschrieben (Datei: truncate; Ordner: zusammenführen,
     * wie `copyRecursively(overwrite = true)` im File-Modus).
     *
     * Quelle und Ziel liegen im selben Baum — der File-Manager merkt sich eine
     * Kopie nur innerhalb der aktiven Sitzung/des aktiven Modus.
     */
    fun copyInto(
        context: Context,
        destTree: Uri,
        destParentDocId: String,
        src: SafEntry,
        replaceDocId: String?
    ): Boolean {
        return try {
            val destId: String = replaceDocId
                ?: createDocument(context, destTree, destParentDocId, src.name, src.isDir)
                ?: return false
            if (src.isDir) copyDirInto(context, destTree, src, destId)
            else copyFileTo(context, destTree, src, destId)
        } catch (_: Exception) {
            false
        }
    }

    /** Ordner rekursiv kopieren — mit Selbst-/Innen-Schutz (wie FileCopyRules). */
    private fun copyDirInto(
        context: Context,
        destTree: Uri,
        src: SafEntry,
        destId: String
    ): Boolean {
        // Schutz vor Selbst-/Innere-Kopie: ein Ordner in sich selbst würde die
        // Quelle beim Rekursionslauf zerstören.
        if (SafPaths.isSameOrInside(src.docId, destId)) return false
        return listChildren(context, destTree, src.docId)
            ?.all { child -> copyInto(context, destTree, destId, child, null) } ?: false
    }

    /** Datei per Stream kopieren (truncate, „wt“ am Ziel). */
    private fun copyFileTo(
        context: Context,
        destTree: Uri,
        src: SafEntry,
        destId: String
    ): Boolean {
        val destUri = DocumentsContract.buildDocumentUriUsingTree(destTree, destId)
        return context.contentResolver.openInputStream(src.docUri)?.use { input ->
            context.contentResolver.openOutputStream(destUri, "wt")?.use { output ->
                input.copyTo(output, COPY_BUFFER)
                true
            } ?: false
        } ?: false
    }

    /** Neues Dokument/Verzeichnis anlegen; docId oder `null`. */
    private fun createDocument(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        name: String,
        isDir: Boolean
    ): String? = try {
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val mime = if (isDir) Document.MIME_TYPE_DIR else mimeFor(name)
        DocumentsContract.createDocument(context.contentResolver, parent, mime, name)
            ?.let { DocumentsContract.getDocumentId(it) }
    } catch (_: Exception) {
        null
    }

    /** MIME-Typ aus der Dateiendung, Fallback `application/octet-stream`. */
    private fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }
}
