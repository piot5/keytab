package com.piotv.keytab.ime

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
            "max_scale" -> copy(maxScale = v.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: maxScale)
            "mid_scale" -> copy(midScale = v.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: midScale)
            "hot_threshold" -> copy(hotThreshold = v.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..1.0 } ?: hotThreshold)
            "mid_threshold" -> copy(midThreshold = v.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..1.0 } ?: midThreshold)
            "min_neighbor_scale" -> copy(minNeighborScale = v.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: minNeighborScale)
            "mid_neighbor_scale" -> copy(midNeighborScale = v.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: midNeighborScale)
            else -> this
        }
    }

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
                if (line.startsWith("#")) continue
                val index = line.indexOf('=')
                if (index <= 0) continue
                val key = line.substring(0, index).trim().lowercase(Locale.ROOT)
                val value = line.substring(index + 1).trim()
                val comment = value.indices.firstOrNull { i ->
                    value[i] == '#' && (i == 0 || value[i - 1].isWhitespace()) &&
                        !(i == 0 && Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?(?=\\s|$)").containsMatchIn(value))
                }
                put(key, (comment?.let { value.substring(0, it) } ?: value).trim())
            }
        }

        /** Lädt die Config aus [file]; fehlt sie, werden die Defaults genutzt. */
        fun load(file: File?): KeyTabConfig {
            if (file == null || !file.isFile) return KeyTabConfig()
            return try { parse(file.readText()) } catch (_: Exception) { KeyTabConfig() }
        }

        /** Schreibt die Config nach [file] (nur wenn fehlend, außer [overwrite]). */
        fun writeDefault(file: File, overwrite: Boolean = false): Boolean = try {
            if (overwrite || !file.isFile) {
                file.parentFile?.mkdirs()
                file.writeText(KeyTabConfig().serialize())
                true
            } else false
        } catch (_: Exception) { false }
    }
}
