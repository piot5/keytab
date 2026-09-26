package com.piotv.keytab.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * KeyboardBinder: Tasten-Verdrahtung ohne Service-Mock.
 * FakeKeyboardInputHost implementiert nur das schmale KeyboardInputHost-
 * Interface; LetterPopup kommt aus einem echten (Robolectric-)Service.
 * Tab/Theme/Suggestion-Controller sind echte Instanzen mit schmalen
 * Fake-Hosts (nur als Typ im Konstruktor noetig; hook() ruft sie nie).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardBinderTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    private class FakeHost(val ctx: Context, val popup: LetterPopup) : KeyboardInputHost {
        override val context: Context get() = ctx
        override fun hideKeyboard() = Unit
        override val letterPopup: LetterPopup get() = popup
        override val longPressHandler = Handler(Looper.getMainLooper())
        var router: InputRouter? = null
        override val inputRouter get() = router
        var pm: WordPredictionManager? = null
        override val predictionManager get() = pm
        val letters = mutableMapOf<Button, Char>()
        override val baseLetters get() = letters
        var root: View? = null
        override val keyboardRoot get() = root
        val commits = mutableListOf<String>()
        override fun haptic() = Unit
        override fun commitText(text: String) { commits += text }
        var deletedWords = 0
        override fun deleteLastWord() { deletedWords++ }
        override fun openSettings() = Unit
        var shifted = false
        var caps = false
        override fun isShifted() = shifted
        override fun isCapsLock() = caps
        var shifts = 0
        override fun consumeSingleShift() { shifts++; shifted = false }
        val shiftCtl = ShiftController(300L)
        override fun tapShift(now: Long): ShiftController.ShiftState = shiftCtl.tapShift(now)
        var letterCase = 0
        var shiftVisual = 0
        override fun applyLetterCase(root: View?) { letterCase++ }
        override fun updateShiftVisual(root: View?) { shiftVisual++ }
        override val letterExtras: Map<Char, List<String>> = mapOf('a' to listOf("\u00E4"))
    }

    private class FakeRouterTarget : InputTarget {
        val ops = mutableListOf<String>()
        override fun insert(text: String) { ops += "ins:$text" }
        override fun deleteBackspace() { ops += "del" }
        override fun deleteWord() { ops += "delWord" }
        override fun deleteBefore(count: Int) = Unit
        override fun deleteBeforeKeys(count: Int) = Unit
        override fun textBefore(count: Int) = ""
        override fun onEnter() { ops += "enter" }
        override fun onTab() { ops += "tab" }
    }

    private class Found(val b: Button) : RuntimeException()

    private lateinit var host: FakeHost
    private lateinit var root: View
    private lateinit var binder: KeyboardBinder

    private class FakeTabHost(val ctx: Context, popup: LetterPopup) : TabHost {
        override val context: Context get() = ctx
        override fun hideKeyboard() = Unit
        override val inputRouter: InputRouter? = null
        override val letterPopup: LetterPopup = popup
        override val fileManagerPanel: FileManagerPanel? = null
        override val clipboardPanel: ClipboardPanel? = null
        override val snippetPanel: SnippetPanel? = null
    }

    private class FakeThemeHost(val ctx: Context, popup: LetterPopup) : ThemeHost {
        override val context: Context get() = ctx
        override fun hideKeyboard() = Unit
        override fun isDarkMode() = true
        override fun rebuildInputView(): View = View(ctx)
        override fun setInputView(view: View) = Unit
        override val keyboardRoot: View? = null
        override val longPressHandler = Handler(Looper.getMainLooper())
        override val letterPopup: LetterPopup = popup
        override fun haptic() = Unit
    }

    private class FakeSuggHost(val ctx: Context) : SuggestionHost {
        override val context: Context get() = ctx
        override fun hideKeyboard() = Unit
        override val predictionManager: WordPredictionManager? = null
        override val keyScaler: DynamicKeyScaler? = null
        override val baseLetters = mutableMapOf<android.widget.Button, Char>()
        override val keyboardRoot: View? = null
        override fun isDarkMode() = true
        override fun haptic() = Unit
        override fun isShifted() = false
        override fun isCapsLock() = false
        override fun isEditorTab() = false
        override fun consumeSingleShift() = Unit
    }

    @Before
    fun setUp() {
        host = FakeHost(app,
            LetterPopup(Robolectric.buildService(KeyTabImeService::class.java).get()))
        host.router = InputRouter(FakeRouterTarget(), FakeRouterTarget())
        root = LayoutInflater.from(ContextThemeWrapper(app, R.style.Theme_KeyTab))
            .inflate(R.layout.keyboard_view, null)
        host.root = root
        val popup = host.popup
        binder = KeyboardBinder(host, 50L,
            TabController(FakeTabHost(app, popup)),
            ThemeController(FakeThemeHost(app, popup)),
            SuggestionController(FakeSuggHost(app)),
            null, null)
        binder.hook(root)
    }

    private fun touch(v: View, action: Int) {
        val e = MotionEvent.obtain(0, 0, action, 10f, 10f, 0)
        v.dispatchTouchEvent(e)
        e.recycle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun letterButton(c: Char): Button {
        ThemeApplier.forEachView(root) { v ->
            val b = v as? Button ?: return@forEachView
            if (b.tag == "letter" && host.baseLetters[b] == c) throw Found(b)
        }
        throw AssertionError("keine letter-Taste fuer $c")
    }

    @Test
    fun `hook registriert alle Buchstaben-Tasten in baseLetters`() {
        assertTrue("Buchstaben gefunden", host.baseLetters.isNotEmpty())
        assertTrue(host.baseLetters.values.any { it.lowercaseChar() == 'a' })
    }

    @Test
    fun `Tap auf Buchstabe committet Kleinbuchstabe`() {
        val btn = try { letterButton('a'); throw AssertionError("unreachable") }
        catch (f: Found) { f.b }
        touch(btn, MotionEvent.ACTION_DOWN)
        touch(btn, MotionEvent.ACTION_UP)
        assertEquals(listOf("a"), host.commits)
    }

    @Test
    fun `Tap mit Shift committet Grossbuchstabe`() {
        host.shifted = true
        val btn = try { letterButton('a'); throw AssertionError("unreachable") }
        catch (f: Found) { f.b }
        touch(btn, MotionEvent.ACTION_DOWN)
        touch(btn, MotionEvent.ACTION_UP)
        assertEquals(listOf("A"), host.commits)
    }

    @Test
    fun `Eszett mit Shift wird zu ẞ (Sonderfall ohne uppercaseChar)`() {
        host.shifted = true
        val btn = try { letterButton('\u00DF'); throw AssertionError("unreachable") }
        catch (f: Found) { f.b }
        touch(btn, MotionEvent.ACTION_DOWN)
        touch(btn, MotionEvent.ACTION_UP)
        assertEquals(listOf("\u1E9E"), host.commits)
    }

    @Test
    fun `applyLetterCase setzt Hinweis-Zeichen und Shift-Visual`() {
        // binder.applyLetterCase schreibt direkt die Button-Texte.
        // Hinweis: binder.updateShiftVisual nutzt host.isShifted (FakeHost),
        // NICHT host.updateShiftVisual — der Zähler bleibt 0, geprüft wird
        // der sichtbare Effekt (Alpha). Host-Callbacks sind Service-Sache.
        binder.applyLetterCase(root)
        val btn = try { letterButton('a'); throw AssertionError("unreachable") }
        catch (f: Found) { f.b }
        val text = btn.text.toString()
        assertTrue("Hauptbuchstabe + Hinweis (ist '$text')",
            text.startsWith("a") && text.contains("ä"))
        binder.updateShiftVisual(root)
        val shift = root.findViewById<Button>(R.id.key_shift)
        assertEquals("ohne Shift halbtransparent", 0.6f, shift.alpha, 0.001f)
        host.shifted = true
        binder.updateShiftVisual(root)
        assertEquals(1f, shift.alpha, 0.001f)
    }

    @Test
    fun `TAB-Taste routet onTab, Enter routet onEnter`() {
        root.findViewById<Button>(R.id.key_tab).performClick()
        root.findViewById<Button>(R.id.key_enter).performClick()
        val appT = (host.router as InputRouter).active as FakeRouterTarget
        assertEquals(listOf("tab", "enter"), appT.ops)
    }

    @Test
    fun `Space und Punkt committen direkt`() {
        root.findViewById<Button>(R.id.key_space).performClick()
        root.findViewById<Button>(R.id.key_dot).performClick()
        assertEquals(listOf(" ", "."), host.commits)
    }

    @Test
    fun `Del-Tap loescht einmal, kein Wort-Repeat ohne Long-Press`() {
        val del = root.findViewById<Button>(R.id.key_del)
        touch(del, MotionEvent.ACTION_DOWN)
        touch(del, MotionEvent.ACTION_UP)
        val appT = (host.router as InputRouter).active as FakeRouterTarget
        assertEquals(listOf("del"), appT.ops)
        assertEquals("kein Wort-Delete bei Tap", 0, host.deletedWords)
    }

    @Test
    fun `Del-Long-Press loescht Wort (Repeat-Scheduler-Pfad)`() {
        // Echter Binder nutzt longPressTimeout=50ms: nach DOWN + idle
        // feuert der Long-Press-Runnable → deleteLastWord am Host.
        // Hinweis: touch() idled bereits 1× — für 50ms braucht es
        // zusätzlich den Scheduler-Idle (postDelayed abarbeiten).
        val del = root.findViewById<Button>(R.id.key_del)
        touch(del, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()
        touch(del, MotionEvent.ACTION_UP)
        assertTrue("Long-Press Wort-Delete (waren ${host.deletedWords})",
            host.deletedWords >= 1)
    }

    @Test
    fun `Shift-Taste tippt Shift-State und aktualisiert View`() {
        // Echter Binder-Pfad: host.tapShift (FakeHost→ShiftController),
        // dann host.updateShiftVisual + host.applyLetterCase (FakeHost-Zähler).
        root.findViewById<Button>(R.id.key_shift).performClick()
        assertEquals("updateShiftVisual-Callback", 1, host.shiftVisual)
        assertEquals("applyLetterCase-Callback", 1, host.letterCase)
    }

    @Test
    fun `setEditorInfo mit null crasht nicht`() {
        binder.setEditorInfo(null)
    }

    @Test
    fun `applyLetterCase mit null crasht nicht`() {
        binder.applyLetterCase(null)
        binder.updateShiftVisual(null)
    }
}
