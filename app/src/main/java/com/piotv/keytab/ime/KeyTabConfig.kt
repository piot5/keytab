package com.piotv.keytab.ime

import com.piotv.keytab.AtomicWrites
import java.io.File
import java.util.Locale

/** Android-free scale configuration and shared key/value syntax. UI import lives in SettingsConfig. */
data class KeyTabConfig(
    val maxScale: Float = 1.21f,
    val midScale: Float = 1.15f,
    val hotThreshold: Double = 0.75,
    val midThreshold: Double = 0.55,
    val minNeighborScale: Float = 0.812f,
    val midNeighborScale: Float = 0.925f
) {

    /** Wertet eine Zeile "key = value" auf dieses Config-Objekt aus. */
    private fun with(key: String, value: String): KeyTabConfig {
        val v = value.trim()
        return when (key) {
            "max_scale" -> copy(maxScale = scaleOf(v) ?: maxScale)
            "mid_scale" -> copy(midScale = scaleOf(v) ?: midScale)
            "hot_threshold" -> copy(hotThreshold = thresholdOf(v) ?: hotThreshold)
            "mid_threshold" -> copy(midThreshold = thresholdOf(v) ?: midThreshold)
            "min_neighbor_scale" -> copy(minNeighborScale = scaleOf(v) ?: minNeighborScale)
            "mid_neighbor_scale" -> copy(midNeighborScale = scaleOf(v) ?: midNeighborScale)
            else -> this
        }
    }

    private fun scaleOf(v: String): Float? =
        v.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }

    private fun thresholdOf(v: String): Double? =
        v.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..1.0 }

    /** Serialisiert die sechs Scale-Keys; vollständiger UI-Export über SettingsConfig. */
    fun serialize(): String = buildString {
        appendLine("# KeyTab Konfiguration — Werte direkt anpassen,")
        appendLine("# Wirkung beim nächsten Öffnen der Tastatur.")
        appendLine()
        appendLine("# Skalierung der wahrscheinlichen Tasten")
        appendLine("max_scale = $maxScale")
        appendLine("mid_scale = $midScale")
        appendLine("hot_threshold = $hotThreshold")
        appendLine("mid_threshold = $midThreshold")
        appendLine("min_neighbor_scale = $minNeighborScale")
        appendLine("mid_neighbor_scale = $midNeighborScale")

    }

    companion object {
        /** Dateiname der Konfigurationsdatei. */
        const val FILE_NAME = "keytab_config.txt"

        /** Parst Config-Text; unbekannte Keys/Zeilen werden ignoriert. */
        fun parse(text: String): KeyTabConfig {
            var cfg = KeyTabConfig()
            for ((key, value) in entries(text)) cfg = cfg.with(key, value)
            return cfg
        }

        /** Comments start at a whitespace-separated #, except a leading hex color.
         * URI fragments (image.png#fragment) remain intact. Last duplicate wins.
         */
        fun entries(text: String): Map<String, String> = buildMap {
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                val index = line.indexOf('=')
                if (line.startsWith("#") || index <= 0) continue
                val key = line.substring(0, index).trim().lowercase(Locale.ROOT)
                val value = line.substring(index + 1).trim()
                val comment = value.indices.firstOrNull { i ->
                    value[i] == '#' && (i == 0 || value[i - 1].isWhitespace()) &&
                        !(i == 0 && Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?(?=\\s|$)").containsMatchIn(value))
                }
                put(key, (comment?.let { value.substring(0, it) } ?: value).trim())
            }
        }

        /**
         * Lädt die Config aus [file]; fehlt sie, werden die Defaults genutzt.
         *
         * N4: Das Lesen passiert nicht bei jedem Aufruf. Gecacht wird anhand
         * von `mtime` + Dateilänge — dieselbe Technik wie in
         * [SettingsConfig.importIfChanged]. Der Cache wird nur neu aufgebaut,
         * wenn sich die Datei wirklich geändert hat; dadurch entfällt das
         * Datei-I/O im Hot-Path (Tastatur-Rebuild bei jedem Theme-Wechsel).
         *
         * Der Cache ist prozessweit; [invalidateCache] leert ihn (z. B. wenn die
         * Datei von aussen geschrieben wurde und die Aenderung nicht über
         * `mtime`/Länge auffaellt — etwa bei gleicher Groesse innerhalb
         * derselben Sekunde).
         */
        fun load(file: File?): KeyTabConfig {
            if (file == null) return KeyTabConfig()
            val stat = runCatching { "${file.lastModified()}:${file.length()}" }.getOrNull()
            val cached = cache
            if (cached != null && cached.file == file.absolutePath && cached.stat == stat) {
                return cached.config
            }
            if (!file.isFile) {
                cache = Cache(file.absolutePath, stat, KeyTabConfig())
                return KeyTabConfig()
            }
            val config = try { parse(file.readText()) } catch (_: Exception) { KeyTabConfig() }
            cache = Cache(file.absolutePath, stat, config)
            return config
        }

        /** Verwirft den Lese-Cache (siehe [load]). */
        fun invalidateCache() {
            cache = null
        }

        private data class Cache(
            val file: String,
            val stat: String?,
            val config: KeyTabConfig
        )

        @Volatile
        private var cache: Cache? = null

        /** Schreibt die Config nach [file] (nur wenn fehlend, außer [overwrite]). */
        fun writeDefault(file: File, overwrite: Boolean = false): Boolean = try {
            if (overwrite || !file.isFile) {
                AtomicWrites.writeText(file, KeyTabConfig().serialize())
                true
            } else false
        } catch (_: Exception) { false }
    }
}
