package com.piotv.keytab.ime

import java.io.File

/**
 * KeyTab-Konfigurationsdatei (android-frei, JUnit-testbar).
 *
 * Einfaches "schlüssel = wert"-Format, direkt auf dem Gerät editierbar:
 *   <externes Files-Dir>/keytab_config.txt
 * (über die KeyTab-App: „Config schreiben/aktualisieren“ erzeugt eine
 *  vorbefüllte Datei mit allen Keys und dem Pfad als Toast/Hinweis.)
 *
 * Bekannte Keys (unbekannte Keys und ungültige Werte werden ignoriert,
 * Defaults greifen dann weiter):
 *
 *   # Skalierung der wahrscheinlichen Tasten
 *   max_scale           = 1.30   # größte Stufe
 *   mid_scale           = 1.15   # zweite Stufe
 *   hot_threshold       = 0.75   # Schwelle Stufe 3 (relativ)
 *   mid_threshold       = 0.55   # Schwelle Stufe 2 (relativ)
 *   min_neighbor_scale  = 0.85   # Verkleinerung neben Stufe-3-Taste
 *   mid_neighbor_scale  = 0.925  # Verkleinerung neben Stufe-2-Taste
 */
data class KeyTabConfig(
    val maxScale: Float = 1.30f,
    val midScale: Float = 1.15f,
    val hotThreshold: Double = 0.75,
    val midThreshold: Double = 0.55,
    val minNeighborScale: Float = 0.85f,
    val midNeighborScale: Float = 0.925f
) {

    /** Wertet eine Zeile "key = value" auf dieses Config-Objekt aus. */
    private fun with(key: String, value: String): KeyTabConfig {
        val v = value.trim()
        return when (key) {
            "max_scale" -> copy(maxScale = v.toFloatOrNull() ?: maxScale)
            "mid_scale" -> copy(midScale = v.toFloatOrNull() ?: midScale)
            "hot_threshold" -> copy(hotThreshold = v.toDoubleOrNull() ?: hotThreshold)
            "mid_threshold" -> copy(midThreshold = v.toDoubleOrNull() ?: midThreshold)
            "min_neighbor_scale" -> copy(minNeighborScale = v.toFloatOrNull() ?: minNeighborScale)
            "mid_neighbor_scale" -> copy(midNeighborScale = v.toFloatOrNull() ?: midNeighborScale)
            else -> this
        }
    }

    /** Serialisiert alle Keys (kommentiert), direkt wieder einlesbar. */
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
            for (rawLine in text.lines()) {
                val line = rawLine.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val idx = line.indexOf('=')
                if (idx <= 0) continue
                cfg = cfg.with(line.substring(0, idx).trim().lowercase(), line.substring(idx + 1))
            }
            return cfg
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
