package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KeyTabConfigTest {

    @Test
    fun defaults_matchKeyScaleLogicConstants() {
        val cfg = KeyTabConfig()
        assertEquals(KeyScaleLogic.MAX_SCALE, cfg.maxScale, 0.0001f)
        assertEquals(KeyScaleLogic.MID_SCALE, cfg.midScale, 0.0001f)
        assertEquals(KeyScaleLogic.HOT_THRESHOLD, cfg.hotThreshold, 0.0001)
        assertEquals(KeyScaleLogic.MID_THRESHOLD, cfg.midThreshold, 0.0001)
        assertEquals(KeyScaleLogic.MIN_NEIGHBOR_SCALE, cfg.minNeighborScale, 0.0001f)
        assertEquals(KeyScaleLogic.MID_NEIGHBOR_SCALE, cfg.midNeighborScale, 0.0001f)
    }

    @Test
    fun parse_readsValues() {
        val cfg = KeyTabConfig.parse(
            """
            # Kommentar
            max_scale = 1.5
            hot_threshold = 0.8
            move_enabled = false
            side_hints_enabled = false
            """.trimIndent()
        )
        assertEquals(1.5f, cfg.maxScale, 0.0001f)
        assertEquals(0.8, cfg.hotThreshold, 0.0001)
        // Move-/Hinweis-Keys existieren nicht mehr → ignoriert,
        // alle übrigen Felder bleiben auf den Defaults
        assertEquals(KeyTabConfig(maxScale = 1.5f, hotThreshold = 0.8), cfg)
    }

    @Test
    fun parse_ignoresUnknownKeysAndInvalidValues() {
        val cfg = KeyTabConfig.parse("foo = bar\nmax_scale = xyz\nmid_threshold = 0.9\n")
        assertEquals(KeyTabConfig().maxScale, cfg.maxScale, 0.0001f)
        assertEquals(0.9, cfg.midThreshold, 0.0001)
    }

    @Test
    fun serialize_roundTrip() {
        val cfg = KeyTabConfig(maxScale = 1.4f, midScale = 1.2f)
        val back = KeyTabConfig.parse(cfg.serialize())
        assertEquals(1.4f, back.maxScale, 0.0001f)
        assertEquals(1.2f, back.midScale, 0.0001f)
        assertEquals(cfg, back)
    }

    @Test
    fun load_missingFile_returnsDefaults() {
        val cfg = KeyTabConfig.load(File("/nonexistent/keytab_config.txt"))
        assertEquals(KeyTabConfig(), cfg)
    }

    @Test
    fun writeDefault_createsFileAndDoesNotOverwrite() {
        val f = File.createTempFile("keytab_config", ".txt")
        f.delete()
        assertTrue(KeyTabConfig.writeDefault(f))
        assertTrue(f.isFile)
        val edited = KeyTabConfig(maxScale = 1.6f)
        f.writeText(edited.serialize())
        // zweiter Aufruf darf bestehende Datei NICHT überschreiben
        assertFalse(KeyTabConfig.writeDefault(f))
        assertEquals(1.6f, KeyTabConfig.load(f).maxScale, 0.0001f)
        f.delete()
    }
}
