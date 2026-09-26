package com.piotv.keytab.ime

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.tabs.TabLayout
import com.piotv.keytab.Prefs
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TabControllerTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()
    private class Host(val ctx: Context) : TabHost {
        override val context get() = ctx
        override fun hideKeyboard() = Unit
        override val inputRouter = InputRouter(Target(), Target())
        override val letterPopup = LetterPopup(Robolectric.buildService(KeyTabImeService::class.java).get())
        override val fileManagerPanel: FileManagerPanel? = null
        override val clipboardPanel: ClipboardPanel? = null
        override val snippetPanel: SnippetPanel? = null
    }
    private class Target : InputTarget {
        override fun insert(text: String) = Unit
        override fun deleteBackspace() = Unit
        override fun deleteWord() = Unit
        override fun deleteBefore(count: Int) = Unit
        override fun deleteBeforeKeys(count: Int) = Unit
        override fun textBefore(count: Int) = ""
        override fun onEnter() = Unit
        override fun onTab() = Unit
    }
    private lateinit var host: Host
    private lateinit var controller: TabController
    @Before fun setUp() { Prefs.of(app).edit().clear().commit(); host = Host(app); controller = TabController(host) }
    private fun root(): View = LayoutInflater.from(ContextThemeWrapper(app, R.style.Theme_KeyTab)).inflate(R.layout.keyboard_view, null)
    private fun setup(clip: Boolean, snip: Boolean): View {
        Prefs.of(app).edit().putBoolean(Prefs.KEY_CLIP_TAB, clip).putBoolean(Prefs.KEY_SNIPPET_TAB, snip).commit()
        return root().also { controller.setup(it) }
    }
    @Test fun `Pflicht und optionale Tabs werden ohne Terminal aufgebaut`() {
        for (mask in 0..3) {
            val r = setup(mask and 1 != 0, mask and 2 != 0)
            assertEquals(3 + Integer.bitCount(mask), r.findViewById<TabLayout>(R.id.ime_tabs).tabCount)
        }
    }
    @Test fun `Editor und Files routen korrekt`() {
        val r = setup(false, false); val tabs = r.findViewById<TabLayout>(R.id.ime_tabs)
        tabs.getTabAt(1)!!.select()
        assertEquals(TabController.TabKind.EDITOR, controller.currentTabKind())
        assertEquals(InputKind.EDITOR, host.inputRouter.kind)
        tabs.getTabAt(2)!!.select()
        assertEquals(TabController.TabKind.FILES, controller.currentTabKind())
        assertEquals(InputKind.APP, host.inputRouter.kind)
    }
    @Test fun `Snippet ist optional und Hide nur im Editor sichtbar`() {
        val r = setup(true, true); val tabs = r.findViewById<TabLayout>(R.id.ime_tabs)
        tabs.getTabAt(3)!!.select(); assertEquals(TabController.TabKind.CLIP, controller.currentTabKind())
        tabs.getTabAt(4)!!.select(); assertEquals(TabController.TabKind.SNIPPET, controller.currentTabKind())
        tabs.getTabAt(1)!!.select(); assertEquals(View.VISIBLE, r.findViewById<View>(R.id.sug_hide).visibility)
        tabs.getTabAt(0)!!.select(); assertEquals(View.GONE, r.findViewById<View>(R.id.sug_hide).visibility)
    }
    @Test fun `Symbol Toggle schaltet Ebenen um`() {
        val r = setup(false, false)
        controller.toggleSymbols(r)
        assertEquals(View.GONE, r.findViewById<View>(R.id.kb_panel).visibility)
        controller.toggleSymbols(r)
        assertEquals(View.VISIBLE, r.findViewById<View>(R.id.kb_panel).visibility)
    }
}
