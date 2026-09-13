package com.piotv.keytab.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * FileManagerModel: Back-Stack-Logik, Persistierung und Sortierung.
 * Keine UI-Abhängigkeiten (reine File-Logik). FM_ROOT als Stub-Verzeichnis
 * (wird via Env gesetzt), weil das Model `root` aus System.getenv liest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileManagerModelTest {

    private val store = mutableMapOf<String, String>()
    private val model = FileManagerModel(
        prefs = { k, d -> store[k] ?: d },
        put = { k, v -> store[k] = v },
        dirs = { d -> dirCache[d] }
    )

    private companion object {
        // Stub-Verzeichnisstruktur: /root/[a-dir, b-dir, c.txt]
        val rootDir = File("/test_fm_root")
        val aDir = File("/test_fm_root/a-dir")
        val bDir = File("/test_fm_root/b-dir")
        val cFile = File("/test_fm_root/c.txt")
        val sub = File("/test_fm_root/a-dir/sub")

        val dirCache = mapOf(
            rootDir to listOf(aDir, bDir, cFile),
            aDir to listOf(sub),
            bDir to emptyList(),
        )
    }

    private fun modelWithRoot(): FileManagerModel {
        // root über Env "FM_ROOT" steuern (siehe Model.lazy)
        return FileManagerModel(
            prefs = { k, d -> store[k] ?: d },
            put = { k, v -> store[k] = v },
            dirs = { d -> dirCache[d] }
        )
    }

    @Test
    fun `Startverzeichnis ist Root`() {
        val m = FileManagerModel(
            prefs = { _, _ -> null },
            put = { _, _ -> },
            dirs = { dirCache[it] })
        // root aus Env, fallback "/"
        assertEquals("Root nicht initialisiert", m.dir)
    }

    @Test
    fun `navigate in Unterverzeichnis, BackStack wächst`() {
        val m = modelWithRoot().apply { }
        m.navigate(aDir)
        assertEquals(aDir, m.dir)
        assertEquals(1, m.stack.size)
        assertEquals(rootDir, m.stack.first())
        assertTrue(m.canGoBack)
    }

    @Test
    fun `goBack stellt Verzeichnis wieder her`() {
        val m = modelWithRoot()
        m.navigate(aDir)
        m.goBack()
        assertEquals(rootDir, m.dir)
        assertFalse(m.canGoBack)
    }

    @Test
    fun `goUp navigiert zum Elternverzeichnis`() {
        val m = modelWithRoot()
        m.navigate(aDir)
        m.goUp()
        assertEquals(rootDir, m.dir)
    }

    @Test
    fun `listEntries sortiert Ordner zuerst, dann nach Name`() {
        val entries = model.listEntries()
        assertTrue(entries[0].isDirectory)
        assertTrue(entries[1].isDirectory)
        assertFalse(entries[2].isDirectory)
        // a-dir, b-dir, c.txt
        assertEquals("a-dir", entries[0].name)
        assertEquals("b-dir", entries[1].name)
    }

    @Test
    fun `counts liefert Ordner-/Dateianzahl`() {
        val (dirs, files) = model.counts()
        assertEquals(2, dirs)
        assertEquals(1, files)
    }

    @Test
    fun `persist + restore erhält dir und BackStack`() {
        val m = modelWithRoot()
        m.navigate(aDir)
        m.persist()
        // neues Model mit persistiertem Store
        val restored = FileManagerModel(
            prefs = { k, d -> store[k] ?: d },
            put = { k, v -> store[k] = v },
            dirs = { dirCache[it] })
        restored.restore()
        assertEquals(aDir, restored.dir)
        assertEquals(1, restored.stack.size)
        assertEquals(rootDir, restored.stack.first())
    }

    @Test
    fun `restore ignoriert ungültige Pfade`() {
        store["fm_dir"] = "/nicht/existierend"
        store["fm_backstack"] = "/auch/nicht"
        val m = FileManagerModel(
            prefs = { k, d -> store[k] ?: d },
            put = { _, _ -> },
            dirs = { dirCache[it] })
        // root fallback (nicht "/nicht/existierend")
        assertEquals(rootDir, m.dir)
        assertFalse(m.canGoBack)
    }

    @Test
    fun `navigate in nicht-lesbares Verzeichnis wird ignoriert`() {
        val m = modelWithRoot()
        m.navigate(cFile) // cFile ist eine Datei, kein Verzeichnis
        assertEquals(rootDir, m.dir) // unverändert geblieben
    }

    @Test
    fun `canGoUp false für Root`() {
        val m = modelWithRoot()
        assertEquals(false, m.canGoUp)
    }

    @Test
    fun `goBack bei leerem Stack ist noop`() {
        val m = modelWithRoot()
        m.goBack()
        assertEquals(rootDir, m.dir)
    }
}
