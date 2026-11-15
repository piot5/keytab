package com.piotv.keytab.ime

/**
 * Reine Mapping-Logik für den SAF-Baum (Storage Access Framework) — bewusst
 * **ohne Android-Importe**, damit sie per JUnit testbar ist ([SafPathsTest]).
 *
 * Ein Tree-URI besteht aus der Baum-ID (z. B. `primary:Download`) und den
 * Dokument-IDs darunter (`primary:Download/notizen/a.txt`). Für das
 * Primär-Volume (`primary:`) ist die Zuordnung zu echten Pfaden deterministisch:
 * `primary:Download/a.txt` → `/storage/emulated/0/Download/a.txt`. Genau darauf
 * beruht das Kernfeature des Files-Tabs — **das Einfügen echter Pfade** in die
 * Termux/proot-Kommandozeile. Für andere Volumes (SD-Karte `XXXX-XXXX:`,
 * `home:`) gibt es kein belastbares Pfad-Mapping → `null` (der Aufrufer fällt
 * auf den Dateinamen zurück).
 *
 * Baum- und Dokument-IDs werden als Strings übergeben; die Dekodierung aus
 * `Uri`-Objekten (RFC 3986, `:` → `%3A`) macht [SafFiles] über
 * `DocumentsContract.getTreeDocumentId()`/`getDocumentId()`.
 */
internal object SafPaths {

    /** Volume-Präfix des internen Speichers (Shared Storage). */
    const val PRIMARY_VOLUME = "primary"

    /** Echter Pfad des Primär-Volumes (auf allen Geräten gleich). */
    const val PRIMARY_ROOT = "/storage/emulated/0"

    /**
     * Echter Dateisystem-Pfad für ein Dokument im Primär-Volume, sonst `null`.
     * [treeId] ist die Baum-ID (Wurzel), [docId] die ID des Dokuments; beides
     * **dekodiert** (echte `:`-Trenner, wie `DocumentsContract` sie liefert).
     */
    fun realPath(treeId: String, docId: String): String? {
        val rel = relativePath(treeId, docId) ?: return null
        val treePath = treeId.substringAfter(':', "")
        val joined = listOf(treePath, rel).filter { it.isNotEmpty() }.joinToString("/")
        return if (joined.isEmpty()) PRIMARY_ROOT else "$PRIMARY_ROOT/$joined"
    }

    /**
     * Pfad **unterhalb** des Baums (`""` = die Baumwurzel selbst), `null`, wenn
     * [docId] nicht im Baum liegt oder das Volume nicht [PRIMARY_VOLUME] ist.
     */
    fun relativePath(treeId: String, docId: String): String? {
        val treeVolume = treeId.substringBefore(':')
        if (treeVolume != PRIMARY_VOLUME || docId.substringBefore(':') != treeVolume) return null
        val treePath = treeId.substringAfter(':', "")
        val docPath = docId.substringAfter(':', "")
        if (docPath == treePath) return ""
        return if (treePath.isEmpty()) docPath
        else docPath.takeIf { it.startsWith("$treePath/") }?.removePrefix("$treePath/")
    }

    /** true, wenn [childDocId] identisch mit [parentDocId] oder darin liegt. */
    fun isSameOrInside(parentDocId: String, childDocId: String): Boolean =
        childDocId == parentDocId || childDocId.startsWith("$parentDocId/")

    /**
     * Dokument-ID des Elternverzeichnisses, `null` an der Baumwurzel (dort ist
     * „nach oben“ bewusst deaktiviert — der Baum endet bei der Freigabe).
     */
    fun parentDocId(docId: String): String? {
        val cut = docId.lastIndexOf('/')
        return if (cut <= 0) null else docId.substring(0, cut)
    }

    /** Dokument-ID eines Kindes von [parentDocId] (nur String-Mathematik). */
    fun childDocId(parentDocId: String, name: String): String = "$parentDocId/$name"

    /**
     * Anzeige-Pfad für die Verzeichniszeile: echter Pfad im Primär-Volume,
     * sonst die Dokument-ID (ehrlich — ein Pfad dahinter existiert nicht).
     */
    fun pseudoPath(treeId: String, docId: String): String =
        realPath(treeId, docId) ?: docId
}
