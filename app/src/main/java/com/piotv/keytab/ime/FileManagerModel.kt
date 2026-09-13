package com.piotv.keytab.ime

import java.io.File

/**
 * Rheinwertungsfreie Back-Stack-Logik des Dateimanagers.
 *
 * Kapselt Navigation (verzeichnis-abwärts, zurück, nach-oben) und Persistierung
 * (dir + backstack) als reine Kotlin-Klasse ohne View-Dependencies. Wird von
 * [FileManagerPanel] (View-Schicht) und [FileManagerModelTest] verwendet.
 *
 * Invariant: bei jeder navigate()-Bestätigung ist `currentDir` != null und
 * `currentDir` stets im Leseverzeichnis.
 */
class FileManagerModel(
    private val prefs: (String, String?) -> String?,
    private val put: (String, String) -> Unit,
    private val dirs: (File) -> List<File>?
) {
    /** Wurzel-Verzeichnis: externer Speicher, sonst Download, sonst "/". */
    val root: File by lazy {
        val pub = System.getenv("FM_ROOT")?.let { File(it) }
        if (pub?.isDirectory == true && pub.canRead()) pub else {
            System.getenv("FM_ALT_ROOT")?.let { File(it) }
                ?.takeIf { it.isDirectory && it.canRead() }
        } ?: File("/")
    }

    private var currentDir: File? = null
    private val backStack = mutableListOf<File>()

    val dir: File get() = currentDir ?: root
    val canGoBack: Boolean get() = backStack.isNotEmpty()
    val canGoUp: Boolean get() = dir.parentFile != null
    val stack: List<File> get() = backStack

    init { restore() }

    /** Wechsel zu [target]. */
    fun navigate(target: File) {
        if (!target.isDirectory || !target.canRead()) return
        val cur = currentDir
        if (cur != null && target.absolutePath != cur.absolutePath) backStack += cur
        currentDir = target
    }

    /** Eine Ebene zurück (Back-Button). */
    fun goBack() {
        if (backStack.isEmpty()) return
        currentDir = backStack.removeAt(backStack.lastIndex)
    }

    /** Eine Ebene nach oben (Up-Button). */
    fun goUp() {
        dir.parentFile?.let { navigate(it) }
    }

    /** Einträge des aktuellen Verzeichnisses (sortiert: Ordner zuerst, dann Name). */
    fun listEntries(): List<File> {
        val d = dir
        val raw = dirs(d) ?: emptyList()
        return raw.filter { !it.isHidden }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /** Zählt Ordner/Dateien im aktuellen Verzeichnis. */
    fun counts(): Pair<Int, Int> {
        val raw = dirs(dir) ?: return 0 to 0
        return raw.count { it.isDirectory } to raw.count { it.isFile }
    }

    /** Persistiert dir + backstack. */
    fun persist(keyDir: String = "fm_dir", keyStack: String = "fm_backstack") {
        val d = currentDir ?: return
        put(keyDir, d.absolutePath)
        put(keyStack, backStack.joinToString("\n") { it.absolutePath })
    }

    /** Stellt dir + backstack wieder her (validiert Lesbarkeit). */
    fun restore(keyDir: String = "fm_dir", keyStack: String = "fm_backstack") {
        currentDir = prefs(keyDir, null)?.let { p ->
            File(p).takeIf { it.isDirectory && it.canRead() }
        } ?: root
        val stack = prefs(keyStack, null)?.split("\n")
            ?.mapNotNull { File(it).takeIf { f -> f.isDirectory && f.canRead() } }
        if (!stack.isNullOrEmpty()) {
            backStack.clear()
            backStack.addAll(stack)
        }
    }
}
