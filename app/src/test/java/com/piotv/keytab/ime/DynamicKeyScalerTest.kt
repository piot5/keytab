package com.piotv.keytab.ime

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * DynamicKeyScaler: Layout-Anbindung der KeyScaleLogic.
 * Zwei Reihen (abc/def): Nachbarn von 'a' = b, d, e — 'f' ist fern.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DynamicKeyScalerTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    private fun board(letters: String = "abcdef"): Pair<LinearLayout, Map<Button, Char>> {
        val root = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL }
        val map = linkedMapOf<Button, Char>()
        for (row in listOf(letters.substring(0, 3), letters.substring(3, 6))) {
            val line = LinearLayout(app).apply { orientation = LinearLayout.HORIZONTAL }
            root.addView(line)
            for (c in row) {
                val b = Button(app)
                b.text = c.toString()
                b.layoutParams = LinearLayout.LayoutParams(0, 100, 1f)
                line.addView(b)
                map[b] = c
            }
        }
        return root to map
    }

    private fun button(map: Map<Button, Char>, c: Char): Button =
        map.entries.first { it.value == c }.key

    @Test
    fun `deaktiviert bleibt alles neutral`() {
        val (_, map) = board()
        val scaler = DynamicKeyScaler(map)
        scaler.rebuildNeighbors()
        scaler.apply(listOf(SuggestionEngine.Suggestion("abc", 9.0)), 0, enabled = false)
        for ((btn, _) in map) {
            assertEquals(1f, btn.scaleX, 0.001f)
            assertEquals(1f, btn.scaleY, 0.001f)
            assertEquals(1f, (btn.layoutParams as LinearLayout.LayoutParams).weight, 0.001f)
        }
    }

    @Test
    fun `Top-Buchstabe waechst, ferner Buchstabe bleibt neutral`() {
        val (_, map) = board()
        val scaler = DynamicKeyScaler(map)
        scaler.rebuildNeighbors()
        scaler.apply(listOf(SuggestionEngine.Suggestion("abc", 9.0)), 0, enabled = true)
        val top = button(map, 'a')
        assertEquals("Top-Skala", KeyScaleLogic.MAX_SCALE, top.scaleX, 0.001f)
        assertEquals(KeyScaleLogic.MAX_SCALE, top.scaleY, 0.001f)
        assertEquals(KeyScaleLogic.MAX_SCALE,
            (top.layoutParams as LinearLayout.LayoutParams).weight, 0.001f)
        val far = button(map, 'f')
        assertEquals("ferne Taste neutral", 1f, far.scaleX, 0.001f)
    }

    @Test
    fun `direkter Nachbar weicht aus`() {
        val (_, map) = board()
        val scaler = DynamicKeyScaler(map)
        scaler.rebuildNeighbors()
        scaler.apply(listOf(SuggestionEngine.Suggestion("abc", 9.0)), 0, enabled = true)
        // 'b','d','e' sind direkte Nachbarn von 'a' → Shrink < 1
        for (c in listOf('b', 'd', 'e')) {
            val n = button(map, c)
            assertTrue("Nachbar $c schrumpft (war ${n.scaleX})", n.scaleX < 1f)
        }
    }

    @Test
    fun `Grossbuchstaben-Taste wird ueber Kleinbuchstabe skaliert`() {
        val (root, _) = board()
        val line = root.getChildAt(0) as LinearLayout
        val b = line.getChildAt(0) as Button
        val map = mapOf(b to 'H')
        val scaler = DynamicKeyScaler(map)
        scaler.rebuildNeighbors()
        scaler.apply(listOf(SuggestionEngine.Suggestion("haus", 9.0)), 0, enabled = true)
        assertEquals(KeyScaleLogic.MAX_SCALE, b.scaleX, 0.001f)
    }

    @Test
    fun `leere Vorschlaege lassen alles neutral`() {
        val (_, map) = board()
        val scaler = DynamicKeyScaler(map)
        scaler.rebuildNeighbors()
        scaler.apply(emptyList(), 0, enabled = true)
        for ((btn, _) in map) assertEquals(1f, btn.scaleX, 0.001f)
    }
}
