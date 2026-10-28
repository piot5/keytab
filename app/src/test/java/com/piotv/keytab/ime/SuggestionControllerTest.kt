package com.piotv.keytab.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.piotv.keytab.Prefs
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * SuggestionController: Vorschlagsleiste, Wort-Uebernahme, Katalog-Browser,
 * Swipe-Kandidaten, Hide-Button, Likely-Highlights (an/aus).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuggestionControllerTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    private class FakeField : WordPredictionManager.InputOperations {
        var content = ""
        var cursor = 0
        override fun deleteBefore(count: Int) {
            val s = (cursor - count).coerceAtLeast(0)
            content = content.removeRange(s, cursor); cursor = s
        }
        override fun deleteBeforeKeys(count: Int) {
            repeat(count) {
                if (cursor > 0) { content = content.removeRange(cursor - 1, cursor); cursor-- }
            }
        }
        override fun textBefore(count: Int) =
            content.substring((cursor - count).coerceAtLeast(0), cursor)
        override fun insert(text: String) {
            content = content.substring(0, cursor) + text + content.substring(cursor)
            cursor += text.length
        }
        override fun commitToApp(text: String) = insert(text)
    }

    private class FakeHost(val ctx: Context) : SuggestionHost {
        override val context: Context get() = ctx
        var hidden = 0
        override fun hideKeyboard() { hidden++ }
        var pm: WordPredictionManager? = null
        override val predictionManager get() = pm
        var scaler: DynamicKeyScaler? = null
        override val keyScaler get() = scaler
        val letters = mutableMapOf<android.widget.Button, Char>()
        override val baseLetters get() = letters
        var root: View? = null
        override val keyboardRoot get() = root
        var dark = true
        override fun isDarkMode() = dark
        var haptics = 0
        override fun haptic() { haptics++ }
        var shifted = false
        var caps = false
        override fun isShifted() = shifted
        override fun isCapsLock() = caps
        var editorTab = false
        override fun isEditorTab() = editorTab
        var shifts = 0
        override fun consumeSingleShift() { shifts++; shifted = false }
    }

    private lateinit var host: FakeHost
    private lateinit var root: View
    private lateinit var controller: SuggestionController
    private lateinit var slots: Array<TextView?>
    private lateinit var field: FakeField

    @Before
    fun setUp() {
        Prefs.of(app).edit().clear().commit()
        host = FakeHost(app)
        root = LayoutInflater.from(ContextThemeWrapper(app, R.style.Theme_KeyTab))
            .inflate(R.layout.keyboard_view, null)
        host.root = root
        field = FakeField()
        // Vor-Kontext: Satzmitte ("ich sage ..."), damit matchCase NICHT
        // großschreibt — ausser Tests setzen gezielt Satzanfang.
        // Hinweis: Robustheit gegen Hooks, die Text aus dem Feld lesen.
        field.content = "ich sage "
        field.cursor = field.content.length
        host.pm = WordPredictionManager(app, { it.run() },
            Handler(Looper.getMainLooper()), arrayOfNulls(3), field)
        host.scaler = DynamicKeyScaler(emptyMap())
        slots = arrayOfNulls(3)
        controller = SuggestionController(host)
        controller.setup(root, slots, Languages.de)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `setup bindet Slots und zeigt die Leiste`() {
        assertTrue(slots[0] != null && slots[1] != null && slots[2] != null)
        assertEquals(View.VISIBLE,
            root.findViewById<View>(R.id.suggestion_bar).visibility)
    }

    @Test
    fun `setup mit deaktivierten Vorschlaegen versteckt die Leiste`() {
        Prefs.of(app).edit().putBoolean(Prefs.KEY_SUGGESTIONS, false).commit()
        controller.setup(root, arrayOfNulls(3), Languages.de)
        assertEquals(View.GONE,
            root.findViewById<View>(R.id.suggestion_bar).visibility)
    }

    @Test
    fun `Emoji-Katalog bleibt in gesperrten Feldern zu`() {
        // Katalog im Normalfeld oeffnen (☺-Button blaettert auf Seite 0)
        root.findViewById<TextView>(R.id.sug_emoji)?.performClick()
        assertEquals(
            "Gegenprobe: im Normalfeld ist der Katalog sichtbar",
            View.VISIBLE,
            root.findViewById<View>(R.id.suggestion_bar).visibility
        )

        // Feld wird gesperrt (Passwort- bzw. NO_PERSONALIZED_LEARNING-Feld)
        host.pm = WordPredictionManager(
            app, { it.run() }, Handler(Looper.getMainLooper()), slots, field,
            personalizedProcessingAllowed = { false })
        controller.update()
        assertEquals(
            "Katalog und Leiste muessen im gesperrten Feld zu sein",
            View.GONE,
            root.findViewById<View>(R.id.suggestion_bar).visibility
        )
    }

    @Test
    fun `Tap auf Vorschlag uebernimmt Wort mit Shift-Reset`() {
        val pm = host.pm!!
        for (c in "haus") pm.onCharacter(c.toString())
        field.content = "ich sage haus"; field.cursor = field.content.length
        shadowOf(Looper.getMainLooper()).idle()
        host.shifted = true
        // Slot-Tag direkt setzen (Engine-Load ist async) und Klick simulieren.
        // Satzmitte (setUp-Kontext) → matchCase klein: "haus " ist korrekt.
        slots[0]!!.tag = "haus"
        slots[0]!!.performClick()
        assertEquals("Wort + Space (Satzmitte→klein)", "ich sage haus ", field.content)
        assertEquals("Single-Shift verbraucht", 1, host.shifts)
        assertEquals(1, host.haptics)
    }

    @Test
    fun `applySuggestion mit CapsLock behaelt Shift`() {
        val pm = host.pm!!
        pm.onCharacter("a")
        // Satzmitte-Kontext: applySuggestion("aber") → matchCase klein.
        field.content = "ich sage a"; field.cursor = field.content.length
        host.shifted = true; host.caps = true
        controller.applySuggestion("aber")
        assertEquals("kein Shift-Reset bei CapsLock", 0, host.shifts)
    }

    @Test
    fun `Emoji-Katalog blaettert und kehrt zurueck`() {
        val emoji = root.findViewById<TextView>(R.id.sug_emoji)
        val pages = EmojiModule.pageCount(5)
        assertTrue(pages > 0)
        repeat(pages + 1) { emoji.performClick() }
        // Nach letzter Seite +1: zurueck bei Wortvorschlaegen (kein Crash).
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `sug_back kehrt aus dem Katalog zurueck`() {
        root.findViewById<TextView>(R.id.sug_emoji).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            "Gegenprobe: im Katalog ist das Raster-Symbol sichtbar",
            View.VISIBLE, root.findViewById<View>(R.id.sug_grid).visibility
        )
        root.findViewById<TextView>(R.id.sug_back).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            "nach ◀ ist der Katalog-Chrome wieder weg",
            View.GONE, root.findViewById<View>(R.id.sug_grid).visibility
        )
    }

    @Test
    fun `Emoji-Chips sug_4 und sug_5 sind antippbar`() {
        // v0.20-Fix: sug_4/sug_5 hatten keinen Klick-Listener — 2 von 5
        // Katalog-Chips pro Seite ließen sich nicht antippen (nur das ▦-Raster
        // bot diese Emojis an). Nach commitEmoji schließt sich der Katalog
        // (browsePage = -1), daher wird je Chip neu geblättert.
        val emojiBtn = root.findViewById<TextView>(R.id.sug_emoji)
        val sug4 = root.findViewById<TextView>(R.id.sug_4)
        val sug5 = root.findViewById<TextView>(R.id.sug_5)

        emojiBtn.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Gegenprobe: sug_4 ist im Katalog sichtbar", View.VISIBLE, sug4.visibility)
        val before4 = field.content
        sug4.performClick()
        assertTrue("Klick auf sug_4 fügt das Emoji ein (vorher='$before4', nachher='${field.content}')",
            field.content.length > before4.length && field.content.startsWith(before4))

        emojiBtn.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Gegenprobe: sug_5 ist im Katalog sichtbar", View.VISIBLE, sug5.visibility)
        val before5 = field.content
        sug5.performClick()
        assertTrue("Klick auf sug_5 fügt das Emoji ein (vorher='$before5', nachher='${field.content}')",
            field.content.length > before5.length && field.content.startsWith(before5))
    }

    @Test
    fun `showSwipeCandidates zeigt drei Woerter, Rest unsichtbar`() {
        // Swipe hat kein getipptes Teilwort (typed="") → matchCase groß per
        // Vertrag (typed.isEmpty→groß). Tag bleibt das Roh-Wort.
        controller.showSwipeCandidates(listOf("haus", "hand"))
        assertEquals("Haus", slots[0]!!.text.toString())
        assertEquals("Roh-Tag bleibt klein", "haus", slots[0]!!.tag as? String)
        assertEquals("Hand", slots[1]!!.text.toString())
        assertEquals(View.VISIBLE, slots[0]!!.visibility)
        assertEquals(View.INVISIBLE, slots[2]!!.visibility)
    }

    @Test
    fun `showSwipeCandidates mit getipptem Praefix bleibt klein`() {
        // Mit getipptem Teilwort (typed="hau") greift matchCase klein —
        // das ist der echte Satzmitte-Vertrag des Controllers.
        host.pm!!.onCharacter("h")
        host.pm!!.onCharacter("a")
        host.pm!!.onCharacter("u")
        controller.showSwipeCandidates(listOf("haus"))
        assertEquals("haus", slots[0]!!.text.toString())
        assertEquals("haus", slots[0]!!.tag as? String)
    }

    @Test
    fun `showSwipeCandidates mitten im Satz bleibt klein`() {
        // Das getippte Teilwort steuert matchCase, nicht der Feld-Kontext:
        // typed="hau" (klein) → kein Großschreiben. Swipe mit Teilwort =
        // der echte Satzmitte-Vertrag (ohne Teilwort gilt typed=""→groß).
        host.pm!!.onCharacter("h")
        controller.showSwipeCandidates(listOf("haus"))
        assertEquals("Satzmitte bleibt klein", "haus", slots[0]!!.text.toString())
    }

    @Test
    fun `showSwipeCandidates respektiert deaktivierte Vorschlaege`() {
        Prefs.of(app).edit().putBoolean(Prefs.KEY_SUGGESTIONS, false).commit()
        controller.showSwipeCandidates(listOf("haus"))
        shadowOf(Looper.getMainLooper()).idle()
        val texts = slots.map { it?.text?.toString().orEmpty() }
        val tags = slots.map { it?.tag as? String }
        assertTrue("Swipe-Kandidat darf nicht gerendert werden, war: $texts",
            texts.none { it.equals("haus", ignoreCase = true) })
        assertTrue("kein Swipe-Tag in der Leiste, war: $tags", tags.none { it == "haus" })
    }

    @Test
    fun `Prediction-Zeile bindet das Maximieren-Symbol an den Vollbild-Modus`() {
        // Der Knopf stand frueher rechts in der Vorschlagsleiste und war nur im
        // Editor-Tab sichtbar; jetzt steht er in der maximize_row am unteren
        // Rand und ist unabhängig vom Tab immer da.
        for (editorTab in listOf(true, false)) {
            host.editorTab = editorTab
            controller.setup(root, arrayOfNulls(3), Languages.de)
            assertEquals("Symbol vorhanden (editorTab=$editorTab)", View.VISIBLE,
                root.findViewById<View>(R.id.sug_hide).visibility)
            assertEquals("Max-Zeile vorhanden (editorTab=$editorTab)", View.VISIBLE,
                root.findViewById<View>(R.id.maximize_row).visibility)
        }
    }

    @Test
    fun `Prediction-Symbol loest Vollbild-Modus aus`() {
        host.editorTab = false
        controller.setup(root, arrayOfNulls(3), Languages.de)
        val btn = root.findViewById<TextView>(R.id.sug_hide)
        assertEquals("Start-Symbol", "⇲", btn.text.toString())
        btn.performClick()
        assertEquals("hideKeyboard aufgerufen", 1, host.hidden)
    }

    @Test
    fun `update ohne keyboardRoot crasht nicht`() {
        host.root = null
        controller.update()
    }

    /** Registriert alle 26 Buchstaben-Tasten am FakeHost (für Likely-Highlights). */
    private fun registerLetterButtons(): List<android.widget.Button> =
        ('a'..'z').map { c ->
            android.widget.Button(app).apply { text = c.toString() }.also { host.letters[it] = c }
        }

    @Test
    fun `update mit Likely-an markiert naechste Taste`() {
        Prefs.of(app).edit().putBoolean(ThemePrefs.KEY_LIKELY, true).commit()
        val buttons = registerLetterButtons()
        val before = buttons.map { it.background }

        host.pm!!.onCharacter("h")
        controller.update()
        shadowOf(Looper.getMainLooper()).idle()

        val changed = buttons.indices.filter { buttons[it].background !== before[it] }
        assertEquals("genau eine Taste ist hervorgehoben, war: $changed", 1, changed.size)
        assertTrue(
            "Hervorhebung ist eine Farbflaeche (GradientDrawable)",
            buttons[changed.first()].background is android.graphics.drawable.GradientDrawable
        )
    }

    @Test
    fun `update mit Likely-aus laesst die Tasten unveraendert`() {
        Prefs.of(app).edit().putBoolean(ThemePrefs.KEY_LIKELY, false).commit()
        val buttons = registerLetterButtons()
        val before = buttons.map { it.background }

        host.pm!!.onCharacter("h")
        controller.update()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "ohne Likely-Modus darf kein Hintergrund getauscht werden",
            buttons.indices.none { buttons[it].background !== before[it] }
        )
    }

    @Test
    fun `clearLikelyHighlights crasht nicht`() {
        controller.clearLikelyHighlights()
    }

    @Test
    fun emojiChipsSug4Sug5HabenClickListenerG9() {
        // G9: sug_4/sug_5 sind nach setup im Layout vorhanden und über ein
        // onClickListener (bindChip in SuggestionController) verbunden.
        val sug4 = root?.findViewById<TextView>(R.id.sug_4)
        val sug5 = root?.findViewById<TextView>(R.id.sug_5)
        assertNotNull("sug_4 fehlt im Layout", sug4)
        assertNotNull("sug_5 fehlt im Layout", sug5)
        // Ein einfacher Klick wird verarbeitet → Listener ist aktiv.
        assertTrue("sug_4 ClickListener nicht aktiv (tap)", sug4?.performClick() ?: false)
        assertTrue("sug_5 ClickListener nicht aktiv (tap)", sug5?.performClick() ?: false)
    }
}
