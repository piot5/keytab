package com.piotv.keytab.ime

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.Prefs
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * TabController: alle 8 Clip/Term/Snip-Kombinationen, Panel-Visibility,
 * InputRouter-Umschaltung, Idempotenz, Symbol-Toggle. Host als schmaler
 * FakeTabHost (kein Proxy, kein Service nötig ausser LetterPopup).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TabControllerTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    private class FakeHost(val ctx: Context) : TabHost {
        override val context: Context get() = ctx
        override fun hideKeyboard() {}
        var router: InputRouter? = null
        override val inputRouter get() = router
        override val letterPopup = LetterPopup(
            Robolectric.buildService(KeyTabImeService::class.java).get())
        override val fileManagerPanel: FileManagerPanel? = null
        override val clipboardPanel: ClipboardPanel? = null
        override val snippetPanel: SnippetPanel? = null
    }

    private class FakeTarget : InputTarget {
        override fun insert(text: String) {}
        override fun deleteBackspace() {}
        override fun deleteWord() {}
        override fun deleteBefore(count: Int) {}
        override fun deleteBeforeKeys(count: Int) {}
        override fun textBefore(count: Int) = ""
        override fun onEnter() {}
        override fun onTab() {}
    }

    private lateinit var host: FakeHost
    private lateinit var controller: TabController

    @Before
    fun setUp() {
        Prefs.of(app).edit().clear().commit()
        host = FakeHost(app)
        host.router = InputRouter(FakeTarget(), FakeTarget(), FakeTarget())
        controller = TabController(host)
    }

    private fun themedRoot(): View = LayoutInflater
        .from(ContextThemeWrapper(app, R.style.Theme_KeyTab))
        .inflate(R.layout.keyboard_view, null)

    private fun tabs(root: View) = root.findViewById<TabLayout>(R.id.ime_tabs)

    private fun vis(root: View, id: Int) = root.findViewById<View>(id).visibility

    private fun setupWith(clip: Boolean, term: Boolean, snip: Boolean): View {
        Prefs.of(app).edit()
            .putBoolean(Prefs.KEY_CLIP_TAB, clip)
            .putBoolean(Prefs.KEY_TERM_TAB, term)
            .putBoolean(Prefs.KEY_SNIPPET_TAB, snip)
            .commit()
        val root = themedRoot()
        controller.setup(root)
        return root
    }

    @Test
    fun `alle 8 Kombinationen haben 3 plus aktive Optionals`() {
        for (mask in 0..7) {
            val clip = mask and 1 != 0
            val term = mask and 2 != 0
            val snip = mask and 4 != 0
            val root = setupWith(clip, term, snip)
            assertEquals("mask=$mask", 3 + Integer.bitCount(mask), tabs(root).tabCount)
        }
    }

    @Test
    fun `doppeltes setup ist idempotent (keine Tabs dazu)`() {
        val root = setupWith(true, true, true)
        controller.setup(root)
        assertEquals(6, tabs(root).tabCount)
    }

    @Test
    fun `Start zeigt Buchstaben-Tastatur, Rest versteckt`() {
        val root = setupWith(true, true, true)
        assertEquals(TabController.TabKind.ABC, controller.currentTabKind())
        assertEquals(View.VISIBLE, vis(root, R.id.kb_panel))
        assertEquals(View.GONE, vis(root, R.id.editor_panel))
        assertEquals(View.GONE, vis(root, R.id.file_panel))
        assertEquals(View.GONE, vis(root, R.id.term_panel))
    }

    @Test
    fun `Editor-Tab zeigt Editor und routet EDITOR`() {
        val root = setupWith(false, false, false)
        tabs(root).getTabAt(1)!!.select()
        assertEquals(TabController.TabKind.EDITOR, controller.currentTabKind())
        assertEquals(View.VISIBLE, vis(root, R.id.editor_panel))
        assertEquals(InputKind.EDITOR, host.router!!.kind)
    }

    @Test
    fun `Files-Tab zeigt Dateimanager und routet APP`() {
        val root = setupWith(false, false, false)
        tabs(root).getTabAt(2)!!.select()
        assertEquals(TabController.TabKind.FILES, controller.currentTabKind())
        assertEquals(View.VISIBLE, vis(root, R.id.file_panel))
        assertEquals(View.GONE, vis(root, R.id.kb_panel))
        assertEquals(InputKind.APP, host.router!!.kind)
    }

    @Test
    fun `Terminal-Tab zeigt Shell und routet TERMINAL`() {
        val root = setupWith(false, true, false)
        // Reihenfolge: abc=0, editor=1, files=2, term=3 (clip aus, snip aus)
        tabs(root).getTabAt(3)!!.select()
        assertEquals(TabController.TabKind.TERMINAL, controller.currentTabKind())
        assertEquals(View.VISIBLE, vis(root, R.id.term_panel))
        assertEquals(View.VISIBLE, vis(root, R.id.kb_panel))
        assertEquals(InputKind.TERMINAL, host.router!!.kind)
    }

    @Test
    fun `Snippet-Tab zeigt Snippets, letzter Tab selektierbar`() {
        val root = setupWith(true, true, true)
        tabs(root).getTabAt(5)!!.select()
        assertEquals(TabController.TabKind.SNIPPET, controller.currentTabKind())
        assertEquals(View.VISIBLE, vis(root, R.id.snippet_panel))
        assertEquals(View.GONE, vis(root, R.id.term_panel))
    }

    @Test
    fun `toggleSymbols blendet Buchstaben aus und Symbole ein`() {
        val root = setupWith(false, false, false)
        controller.toggleSymbols(root)
        assertEquals(View.GONE, vis(root, R.id.kb_panel))
        assertEquals(View.VISIBLE, vis(root, R.id.sym_panel))
        controller.toggleSymbols(root)
        assertEquals(View.VISIBLE, vis(root, R.id.kb_panel))
        assertEquals(View.GONE, vis(root, R.id.sym_panel))
    }

    @Test
    fun `resetSymbols stellt Buchstaben-Layer wieder her`() {
        val root = setupWith(false, false, false)
        controller.toggleSymbols(root)
        controller.resetSymbols()
        // Nach Reset zeigt der naechste Toggle wieder Buchstaben.
        controller.toggleSymbols(root)
        assertEquals(View.GONE, vis(root, R.id.kb_panel))
    }

    @Test
    fun `Hide-Button nur in Editor und Terminal sichtbar`() {
        val root = setupWith(false, true, false)
        tabs(root).getTabAt(1)!!.select()
        assertEquals(View.VISIBLE, vis(root, R.id.sug_hide))
        tabs(root).getTabAt(2)!!.select()
        assertEquals(View.GONE, vis(root, R.id.sug_hide))
        tabs(root).getTabAt(3)!!.select()
        assertEquals(View.VISIBLE, vis(root, R.id.sug_hide))
    }

    @Test
    fun `alle Tabs haben einzeilige 8sp-Beschriftung`() {
        val root = setupWith(true, true, true)
        val strip = tabs(root).getChildAt(0) as android.view.ViewGroup
        assertTrue(strip.childCount > 0)
        for (i in 0 until strip.childCount) {
            val cell = strip.getChildAt(i) as? android.view.ViewGroup ?: continue
            for (j in 0 until cell.childCount) {
                val tv = cell.getChildAt(j) as? android.widget.TextView ?: continue
                assertEquals(8f, tv.textSize / app.resources.displayMetrics.scaledDensity,
                    0.6f)
                assertEquals(1, tv.maxLines)
            }
        }
    }
}
