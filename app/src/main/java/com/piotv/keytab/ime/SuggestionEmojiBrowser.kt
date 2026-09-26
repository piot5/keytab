package com.piotv.keytab.ime

import android.view.View
import android.widget.TextView
import com.piotv.keytab.R

/**
 * Emoji-Katalog-Browser der Vorschlagsleiste – Seitennavigation über den
 * [EmojiModule]-Katalog, Katalog-Chrome der Leiste und das Raster-Popup mit
 * dem ganzen Katalog.
 *
 * Refactoring (docs/REFACTORING_PLAN.md Phase 4): aus [SuggestionController]
 * extrahiert („Umziehen statt Umschreiben“, verhaltensneutral). Die Leiste
 * blendet im Katalog-Modus anstelle der Wortvorschläge je 5 Emoji-Chips
 * (sug_1..sug_5); der ☺-Button links öffnet Seite 0, jeder weitere Tap blättert
 * weiter und springt nach der letzten Seite zurück zu den Wortvorschlägen
 * ([browsePage] == -1).
 *
 * Slots (sug_1..sug_3) und der Rückruf [onUpdate] werden von
 * [SuggestionController.setup] gebunden; alle Render-Aufrufe laufen von dort
 * über [SuggestionController.update].
 */
internal class SuggestionEmojiBrowser(
    private val host: SuggestionHost,
    /** Auslöser für [SuggestionController.update] nach Katalog-Aktionen. */
    private val onUpdate: () -> Unit,
) {

    // --- Layout-Konstanten des Emoji-Raster-Popups (alle Werte in dp) ---
    // Sie liegen im companion object am Dateiende.

    /**
     * Emoji-Katalog-Browser: Aktuelle Seite (−1 = Wortvorschläge). Der 😀-Button
     * links in der Leiste öffnet Seite 0; jeder weitere Tap blättert weiter und
     * springt nach der letzten Seite zurück zu den Wortvorschlägen.
     */
    var browsePage: Int = -1
        private set

    /** Aktuelles Katalog-Raster-Popup (null = keines offen). */
    private var popup: android.widget.PopupWindow? = null

    /** Die 3 Vorschlags-Slots (gleiche Array-Referenz wie in [WordPredictionManager]). */
    private var slots: Array<TextView?> = arrayOfNulls(3)

    /**
     * Slot-Array der Leiste binden (gleiche Referenz wie an
     * [WordPredictionManager] übergeben) und die Katalog-Buttons der Leiste
     * verdrahten.
     *
     * @param root Inflated Tastatur-Root
     * @param suggestionViews Array (gleiche Referenz wie an [WordPredictionManager] übergeben)
     */
    fun bind(root: View, suggestionViews: Array<TextView?>) {
        slots = suggestionViews
        // ☺-Katalog-Button links in der Leiste: öffnet/blättert den Emoji-Katalog
        root.findViewById<TextView>(R.id.sug_emoji)?.setOnClickListener {
            host.haptic()
            val pages = EmojiModule.pageCount(CATALOG_PER_PAGE)
            browsePage = if (pages == 0) -1
            else if (browsePage + 1 >= pages) -1 else browsePage + 1
            onUpdate()
        }
        // ◀ (nur im Katalog-Modus): zurück zu den Wortvorschlägen
        root.findViewById<TextView>(R.id.sug_back)?.setOnClickListener {
            host.haptic()
            browsePage = -1
            onUpdate()
        }
        // ▦ (nur im Katalog-Modus): ganzen Katalog als Tabelle auswählen
        root.findViewById<TextView>(R.id.sug_grid)?.setOnClickListener {
            host.haptic()
            showEmojiGrid(it)
        }
    }

    /**
     * Katalog-Seitennavigation in [SuggestionController.update] einhängen: ist
     * der Katalog-Browser aktiv, wird die aktuelle Katalog-Seite gerendert und
     * `true` zurückgegeben (die Wortvorschläge werden dann nicht berechnet). Bei
     * deaktivierter Leiste oder Passwort-Feld wird der Browser geschlossen und
     * `false` zurückgegeben (die Leiste blendet sich ggf. aus).
     */
    fun renderIfActive(bar: View, suggestionEnabled: Boolean, fieldAllowed: Boolean): Boolean {
        if (browsePage < 0) return false
        // Katalog-Browser aktiv: zeigt anstelle der Wortvorschläge die aktuelle
        // Emoji-Katalog-Seite (jeder ☺-Tap blättert weiter / zurück zu Wörtern).
        if (suggestionEnabled && fieldAllowed) renderEmojiPage(bar)
        else {
            browsePage = -1
            if (!fieldAllowed) bar.visibility = View.GONE
        }
        return true
    }

    /**
     * Rendert die aktuelle [EmojiModule]-Katalog-Seite in die Leiste (5 Chips:
     * sug_1..sug_5). Chips tragen [SuggestionEngine.EMOJI_TAG] (Klick →
     * [commitEmoji]), freie Slots bleiben INVISIBLE/GONE und ohne Tag.
     * Zusätzlich erscheinen links ◀ (zurück zu den Wortvorschlägen) und ▦
     * (ganzen Katalog als Tabelle auswählen).
     */
    private fun renderEmojiPage(bar: View) {
        setPageChrome(bar, catalogMode = true)
        val page = EmojiModule.page(browsePage, CATALOG_PER_PAGE)
        val extra = arrayOf(
            bar.findViewById<TextView>(R.id.sug_4),
            bar.findViewById<TextView>(R.id.sug_5))
        for (i in 0..2) {
            val tv = slots[i] ?: continue
            applyEmojiChip(tv, page.getOrNull(i))
        }
        for (i in 0..1) {
            val tv = extra[i] ?: continue
            applyEmojiChip(tv, page.getOrNull(3 + i))
        }
        bar.visibility = View.VISIBLE
    }

    /** Ein Emoji-Chip befüllen (oder leer/unten halten). */
    private fun applyEmojiChip(tv: TextView, emoji: String?) {
        if (emoji == null) {
            tv.visibility = View.INVISIBLE
            tv.tag = null
            tv.setTag(SuggestionEngine.SNIPPET_TAG, null)
            tv.setTag(SuggestionEngine.EMOJI_TAG, null)
        } else {
            tv.visibility = View.VISIBLE
            tv.text = emoji
            tv.tag = null
            tv.setTag(SuggestionEngine.SNIPPET_TAG, null)
            tv.setTag(SuggestionEngine.EMOJI_TAG, emoji)
        }
    }

    /**
     * Katalog-Chrome (◀/▦/sug_4/sug_5) an/aus: im Katalog-Modus sichtbar,
     * sonst ausgeblendet. sug_1..sug_3 sind immer da (Wortvorschläge-Chips).
     */
    fun setPageChrome(bar: View, catalogMode: Boolean) {
        val vis = if (catalogMode) View.VISIBLE else View.GONE
        for (id in intArrayOf(R.id.sug_back, R.id.sug_grid, R.id.sug_4, R.id.sug_5)) {
            bar.findViewById<View>(id)?.visibility = vis
        }
        if (!catalogMode) {
            for (id in intArrayOf(R.id.sug_4, R.id.sug_5)) {
                (bar.findViewById<View>(id) as? TextView)?.let { applyEmojiChip(it, null) }
            }
        }
    }

    /** Katalog-Browser schließen (zurück zu den Wortvorschlägen). */
    fun close() {
        browsePage = -1
    }

    /**
     * Ganzen Emoji-Katalog als Tabelle (Raster) anbieten: scrollbares Popup-
     * Fenster mit allen [EmojiModule.catalog]-Emojis, Tap fügt das Emoji ein
     * ([commitEmoji]) und schließt Tabelle und Katalog-Browser.
     */
    private fun showEmojiGrid(anchor: View) {
        val ctx = anchor.context
        val dip = ctx.resources.displayMetrics.density
        val cols = GRID_COLUMNS
        val cell = (CELL_DP * dip).toInt()
        val grid = android.widget.GridLayout(ctx).apply {
            columnCount = cols
            setPadding((GRID_PADDING_DP * dip).toInt(), (GRID_PADDING_DP * dip).toInt(),
                (GRID_PADDING_DP * dip).toInt(), (GRID_PADDING_DP * dip).toInt())
        }
        for (emoji in EmojiModule.catalog) {
            val cellView = TextView(ctx).apply {
                text = emoji
                textSize = EMOJI_TEXT_SIZE_SP
                gravity = android.view.Gravity.CENTER
                setOnClickListener {
                    commitEmoji(emoji)
                    popup?.dismiss()
                }
            }
            grid.addView(cellView, android.widget.GridLayout.LayoutParams().apply {
                width = cell
                height = cell
            })
        }
        // Theme-übersteuerte Farben (beide Modi: Dark/Light) für Raster-Popup
        val prefs = com.piotv.keytab.Prefs.of(host.context)
        val keyBg = ThemePrefs.getColor(prefs, host.isDarkMode(), ThemePrefs.KIND_KEY,
            androidx.core.content.ContextCompat.getColor(ctx, R.color.key_bg))
        val textColor = ThemePrefs.getColor(prefs, host.isDarkMode(), ThemePrefs.KIND_TEXT,
            androidx.core.content.ContextCompat.getColor(ctx, R.color.key_text))
        val scroll = android.widget.ScrollView(ctx).apply {
            addView(grid)
            // Opaker, abgerundeter Theme-Hintergrund (keyBg kann Alpha enthalten
            // → für die Tabelle auf decklich erzwingen)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = POPUP_CORNER_RADIUS_DP * dip
                setColor(keyBg or 0xFF000000.toInt())
            }
        }
        for (i in 0 until grid.childCount) {
            (grid.getChildAt(i) as TextView).setTextColor(textColor)
        }
        popup = android.widget.PopupWindow(scroll,
            (GRID_COLUMNS * CELL_DP * dip).toInt() + (POPUP_WIDTH_MARGIN_DP * dip).toInt(),
            (GRID_COLUMNS * CELL_DP * dip).toInt() + (POPUP_HEIGHT_MARGIN_DP * dip).toInt(), true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(keyBg))
            showAsDropDown(anchor, 0, (POPUP_Y_OFFSET_DP * dip).toInt())
        }
    }

    /**
     * Emoji einfügen (Katalog-Chip): direktes [InputOperations.insert] — ohne
     * Wort-Lern-Side-Effects und ohne Trailing-Space (bewusst anders als
     * [SuggestionController.applySuggestion]); Emojis sind keine Wörter und
     * sollen die n-gram-Vorhersage nicht verfälschen. Danach zurück zu den
     * Wortvorschlägen (Katalog-Browser schließt sich — Emoji-Session ist damit
     * beendet).
     */
    fun commitEmoji(emoji: String) {
        host.predictionManager?.commitEmoji(emoji)
        browsePage = -1
        onUpdate()
    }

    companion object {
        /** Katalog-Seitenlänge in der Leiste (5 Slots: sug_1..sug_5). */
        const val CATALOG_PER_PAGE = 5

        // --- Layout-Konstanten des Emoji-Raster-Popups (alle Werte in dp) ---

        /** Spaltenzahl des Emoji-Rasters. */
        const val GRID_COLUMNS = 5

        /** Kantenlänge einer Raster-Zelle in dp. */
        const val CELL_DP = 46

        /** Innenabstand des Rasters in dp. */
        const val GRID_PADDING_DP = 8

        /** Schriftgröße des Emoji-Zeichens in einer Zelle (sp). */
        const val EMOJI_TEXT_SIZE_SP = 22f

        /** Eckenradius des Popup-Hintergrunds in dp. */
        const val POPUP_CORNER_RADIUS_DP = 10f

        /** Zusätzlicher horizontaler Rand des Popups in dp. */
        const val POPUP_WIDTH_MARGIN_DP = 20

        /** Zusätzlicher vertikaler Rand des Popups in dp. */
        const val POPUP_HEIGHT_MARGIN_DP = 16

        /** Vertikaler Versatz des Popups gegenüber dem Anker-View in dp. */
        const val POPUP_Y_OFFSET_DP = 4
    }
}
