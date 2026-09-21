package com.piotv.keytab.ime

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import com.piotv.keytab.Prefs
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Files-Panel: Navigation in Unterverzeichnisse, Back-Stack (Zurück-Button),
 * Up-Navigation, Tipp auf Datei → Pfad-Commit, Zustands-Persistenz in Prefs.
 * Startverzeichnis wird über die Prefs gesetzt (restoreSaved), damit die
 * Tests deterministisch sind und nicht vom echten Speicher abhängen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileManagerPanelTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var base: File
    private lateinit var sub: File

    /** legt <tmp>/fmp-<suffix>/ an, setzt fm_dir-Prefs und baut das Panel */
    private fun panelWithTree(suffix: String, committed: MutableList<String>? = null): Triple<View, FileManagerPanel, File> {
        base = File(app.filesDir, "fmp-$suffix").apply {
            deleteRecursively(); mkdirs()
        }
        sub = File(base, "sub").apply { mkdirs() }
        File(base, "a.txt").writeText("inhalt")
        Prefs.of(app).edit()
            .putString("fm_dir", base.absolutePath)
            .putString("fm_backstack", null)
            .commit()
        val root = inflateKeyboardRoot(app)
        val panel = FileManagerPanel(app, root, directExecutor, mainHandler) { committed?.add(it) }
        shadowOf(Looper.getMainLooper()).idle()
        return Triple(root, panel, base)
    }

    private fun labels(root: View): List<String> {
        val list = root.findViewById<ListView>(R.id.file_list)
        return (0 until list.adapter.count).map { list.adapter.getItem(it).toString() }
    }

    private fun click(list: ListView, position: Int) {
        list.performItemClick(list, position, list.getItemIdAtPosition(position))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun dirLabel(root: View) = root.findViewById<TextView>(R.id.file_dir).text.toString()

    @Test
    fun `Start zeigt das gespeicherte Verzeichnis mit Inhalt`() {
        val (root, _, _) = panelWithTree("start")
        assertEquals(base.absolutePath, dirLabel(root))
        val labels = labels(root)
        assertTrue("Unterordner sollte zuerst kommen (dirs zuerst)", labels.first().contains("sub"))
        assertTrue(labels.any { it.contains("a.txt") })
    }

    @Test
    fun `Tipp auf Ordner navigiert hinein und Zurueck kehrt zum Ausgangsort zurueck`() {
        val (root, _, _) = panelWithTree("nav")
        click(root.findViewById(R.id.file_list), 0)
        assertEquals(sub.absolutePath, dirLabel(root))
        val back = root.findViewById<Button>(R.id.btn_back_dir)
        assertEquals("Zurück-Button sichtbar, wenn BackStack gefüllt", View.VISIBLE, back.visibility)
        back.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(base.absolutePath, dirLabel(root))
        assertEquals("BackStack leer → Zurück ausgeblendet", View.GONE, back.visibility)
    }

    @Test
    fun `Up-Button navigiert zum Elternverzeichnis`() {
        val (root, _, _) = panelWithTree("up")
        click(root.findViewById(R.id.file_list), 0)
        root.findViewById<Button>(R.id.btn_up_dir).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(base.absolutePath, dirLabel(root))
    }

    @Test
    fun `Tipp auf Datei commitet den absoluten Pfad`() {
        val committed = mutableListOf<String>()
        val (root, _, _) = panelWithTree("commit", committed)
        // nur die Datei zählt: erst in sub navigieren (nur a.txt liegt in base;
        // darum navigieren wir in ein leeres Verzeichnis mit genau einer Datei)
        val only = File(base, "only").apply { mkdirs() }
        File(only, "note.md").writeText("x")
        Prefs.of(app).edit().putString("fm_dir", only.absolutePath).commit()
        val root2 = inflateKeyboardRoot(app)
        FileManagerPanel(app, root2, directExecutor, mainHandler) { committed.add(it) }
        shadowOf(Looper.getMainLooper()).idle()
        click(root2.findViewById(R.id.file_list), 0)
        assertEquals(listOf(File(only, "note.md").absolutePath), committed)
    }

    @Test
    fun `Navigation persistiert Verzeichnis und BackStack in den Prefs`() {
        val (root, _, _) = panelWithTree("persist")
        click(root.findViewById(R.id.file_list), 0)
        val prefs = Prefs.of(app)
        assertEquals(sub.absolutePath, prefs.getString("fm_dir", null))
        assertEquals(base.absolutePath, prefs.getString("fm_backstack", null))
        root.findViewById<Button>(R.id.btn_back_dir).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Nach Zurück ist fm_dir wieder base",
            base.absolutePath, prefs.getString("fm_dir", null))
    }
}
