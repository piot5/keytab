package com.piotv.keytab.ime

import android.view.View
import com.piotv.keytab.R

/**
 * Panel-Höhen der Tastatur-Tabs.
 *
 * Der Notes-Tab zeigt das Editor-Panel (Load/Save-Zeile + Eingabefeld) ÜBER der
 * Buchstaben-Tastatur, der Files-Tab blendet beide aus und zeigt stattdessen das
 * Datei-Panel. Damit **alle** Tabs gleich hoch aussehen, muss jedes der drei
 * Panels (Files, Clipboard, Snippets) die kombinierte Höhe von Editor-Panel +
 * Buchstaben-Panel einnehmen (die Funktionsleiste unten ist in allen Tabs
 * identisch sichtbar).
 *
 * Das war vorher nur für die Datei-/Clipboard-/Snippet-Panels so — sie standen
 * im Layout auf `wrap_content` bzw. 164dp und wirkten deshalb flacher als der
 * Editor-Tab. Heute bekommen alle vier Inhalts-Panels dieselbe Höhe über
 * [applyNormalHeights] (2026-09-27).
 *
 * Die Höhe wird gemessen statt hartkodiert, damit sie bei geänderter Tastengröße,
 * anderen Schriften oder Layout-Änderungen automatisch korrekt bleibt.
 * [View.measure] funktioniert auch für GONE-Views – beim Tab-Wechsel sind Editor-
 * und Buchstaben-Panel bereits ausgeblendet.
 */
object PanelHeights {

    /**
     * Der maximierte Zustand ist **kein Prozentsatz** der Bildschirmhöhe: er
     * füllt das ganze IME-Fenster ([maximizedPanelHeight], [maximizeAvailableHeight]).
     *
     * Vorher stand hier `MAXIMIZE_FRACTION = 0.95` (davor 0.9) — ein zweiter,
     * kleinerer Deckel **innerhalb** der Grenze, die Android der IME ohnehin
     * zieht ([IME_WINDOW_FRACTION]). Zusammen mit einer Verfügbarkeitsgrenze, die
     * aus der `wrap_content`-Wurzel kam (und damit die *normale* Tastaturhöhe
     * war), blieb der Vollbild-Modus sichtbar kürzer als der Platz, den das
     * System freigibt.
     */

    /**
     * Panels, die im Normalzustand auf [contentPanelHeight] gesetzt werden:
     * Files, Clipboard und Snippets. Sie ersetzen im jeweiligen Tab die
     * Buchstaben-Tastatur und müssen deshalb deren Platz mit einnehmen.
     *
     * Das Editor-Panel steht hier bewusst **nicht** drin: es ist die
     * Höhenreferenz. Würde man es mit überschreiben, wäre beim nächsten
     * Tab-Wechsel "Editor + Tastatur" die neue Referenz — die Panels würden
     * bei jedem Wechsel wachsen.
     */
    val CONTENT_PANEL_IDS = intArrayOf(
        R.id.file_panel, R.id.clip_panel, R.id.snippet_panel
    )

    /**
     * Alle Tabs mit Inhalts-Panel. Der abc-Tab ist nicht dabei: dort ist die
     * Tastatur selbst der Inhalt, ihre Höhe kommt aus den Tasten.
     */
    val ALL_CONTENT_TAB_IDS = intArrayOf(
        R.id.editor_panel, R.id.file_panel, R.id.clip_panel, R.id.snippet_panel
    )


    /**
     * Misst die natürliche Höhe eines Views (auch wenn GONE). 0 wenn nicht
     * ermittelbar. Util für das Maximieren des Editor-Panels beim
     * Ausblenden der Tastatur.
     */
    fun measureHeight(view: View?, widthPx: Int): Int {
        if (view == null || widthPx <= 0) return 0
        val width = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(width, free)
        return if (view.measuredHeight > 0) view.measuredHeight else 0
    }

    /**
     * Gemessene Editorhöhe = gemessene Höhe des [editorPanel] allein —
     *     */
    fun editorPanelHeight(editorPanel: View?, widthPx: Int): Int =
        measureHeight(editorPanel, widthPx)

    /**
     * Normalhoehe des **Editor-Panels** im Editor-Tab: seine feste Layouthoehe
     * (die hat es schon immer gegeben) — der Editor-Tab ist die Referenz, alle
     * anderen Tabs richten sich nach ihm.
     */
    fun editorNormalHeight(editorPanel: View?, widthPx: Int): Int {
        if (editorPanel == null || widthPx <= 0) return 0
        val fixed = editorPanel.layoutParams?.height?.takeIf { it > 0 }
        return fixed ?: measureHeight(editorPanel, widthPx)
    }

    /**
     * Normalhöhe eines Inhalts-Panels: so hoch, dass der Tab genauso hoch ist wie
     * der Editor-Tab.
     *
     * Der Editor-Tab zeigt Editor-Panel **und** Buchstaben-Tastatur. Die anderen
     * Inhalts-Tabs zeigen nur ihr Panel und blenden die Tastatur aus (GONE). Ihre
     * Höhe muss deshalb Editor + Tastatur betragen, damit alle vier Tabs
     * bildschirmfüllend gleich hoch aussehen.
     *
     * Die Editorhöhe wird aus den Layout-Params genommen, wenn sie fest gesetzt
     * ist: der Editor hat eine feste Höhe (180dp), seine *natürliche* Höhe
     * (gemessen mit UNSPECIFIED) ist kleiner — mit der Naturalhöhe waren die
     * Tabs deswegen unterschiedlich hoch.
     */
    fun contentPanelHeight(editorPanel: View?, keyboardPanel: View?, widthPx: Int): Int {
        if (editorPanel == null || keyboardPanel == null || widthPx <= 0) return 0
        val editorHeight = editorPanel.layoutParams?.height?.takeIf { it > 0 }
            ?: measureHeight(editorPanel, widthPx)
        val keyboardHeight = keyboardPanel.height.takeIf { it > 0 }
            ?: measureHeight(keyboardPanel, widthPx)
        val height = editorHeight + keyboardHeight
        return if (height > 0) height else 0
    }

    /**
     * Normalhöhe aller Inhalts-Panels setzen (Files, Clipboard, Snippets).
     *
     * Die Höhe wird **einmal pro Tastatur-Aufbau** berechnet und im Root-Tag
     * gemerkt ([R.id.content_panel_height]). Grund: die Tastaturhöhe ist beim
     * ersten Aufruf noch nicht gelayoutet (dann wird gemessen), danach ist
     * `height` der echte Layoutwert — die beiden unterscheiden sich, und ohne
     * Cache würde das Panel bei jedem Tab-Wechsel ein Stück wachsen.
     *
     * Idempotent: es wird nur geschrieben, wenn sich die Höhe wirklich ändert,
     * damit kein Layout-Durchlauf pro Tab-Wechsel unnötig ausgelöst wird.
     *
     * @param overridePx Höhe aus der Config-Datei in px, 0 = automatisch (siehe
     *   [TabHeights]). Sie gewinnt gegen die Berechnung, wird aber wie diese auf
     *   das Fenster begrenzt.
     * @return die gesetzte Höhe in px, 0 wenn nichts ermittelbar war
     */
    fun applyNormalHeights(root: View, widthPx: Int, overridePx: Int = 0): Int {
        val cached = root.getTag(R.id.content_panel_height) as? Int ?: 0
        val raw = if (cached > 0) cached else {
            val editor = root.findViewById<View>(R.id.editor_panel)
            val keyboard = root.findViewById<View>(R.id.kb_panel)
            // Die Maximieren-Zeile zeigen nur die Inhalts-Tabs (Files/Clip/Snippet);
            // im Editor-Tab sitzt das Symbol in der Prediction-Zeile und die Zeile
            // fehlt (sonst wäre der Editor doppelt bedient). Ein Inhalts-Tab hat sie
            // also *zusätzlich* und muss sie von der Panel-Höhe abziehen — sonst ist
            // er um genau diese Zeile höher als der Editor-Tab. Gemessen statt
            // gerechnet: die Zeilenhöhe hängt an Dichte und Schriftgröße.
            val natural = contentPanelHeight(editor, keyboard, widthPx) -
                maximizeRowHeight(root)
            if (natural > 0) {
                root.setTag(R.id.content_panel_height, natural)
                natural
            } else {
                0
            }
        }
        if (raw <= 0) return 0
        // Editor + Tastatur passt nicht immer in das IME-Fenster: das System gibt
        // nur ~70 % der Bildschirmhoehe (dumpsys window: 1915 px bei 2712 px).
        // Ohne Begrenzung schob das Panel die unterste Tastenzeile und die
        // Navigationsleiste aus dem Fenster — der Inhalt war unsichtbar.
        // Der Platz fuer Tab-Leiste, Prediction-Zeile und Maximieren-Zeile wird
        // deshalb abgezogen; `chromePx` ist deren gemessene Hoehe. Eine
        // Config-Vorgabe gewinnt gegen die Berechnung, wird aber genauso begrenzt.
        val wanted = if (overridePx > 0) overridePx else raw
        val height = minOf(wanted, availableHeight(root) - chromeHeight(root, widthPx))
        if (height <= 0) return 0
        for (id in CONTENT_PANEL_IDS) {
            val panel = root.findViewById<View>(id) ?: continue
            if (panel.layoutParams?.height != height) {
                panel.layoutParams = panel.layoutParams.apply { this.height = height }
            }
        }
        return height
    }

    /**
     * Anteil der Bildschirmhöhe, den das System der IME überhaupt gibt.
     *
     * Gemessen auf dem Testgerät: das IME-Fenster bekommt 1915 px bei 2712 px
     * Bildschirm (70,6 %) – mehr ist per `dumpsys window` nicht zu bekommen,
     * egal wie hoch man den Inhalt macht. Der Vollbild-Modus ist deshalb
     * **fensterfüllend**, nicht bildschirmfüllend: der Rest des Bildschirms
     * bleibt sichtbar, und mehr wäre ein Kampf gegen das System, kein Feature.
     * Der Wert liegt bewusst knapp **unter** dem gemessenen Maximum, damit das
     * Panel nicht gegen die harte Systemkante läuft (dann schöbe es die unterste
     * Tastenzeile aus dem Fenster).
     */
    const val IME_WINDOW_FRACTION = 0.7f

    /**
     * Verfügbare Höhe für den **Normalzustand**: die tatsächliche Höhe des
     * IME-Fensters, sonst die Naeherung aus [IME_WINDOW_FRACTION] der
     * Bildschirmhöhe (der Fall vor dem ersten Layout).
     *
     * Bewusst *nicht* [maximizeAvailableHeight]: im Normalzustand ist die
     * Fensterhöhe die richtige Grenze (der Inhalt darf die Tastatur nicht aus
     * dem Fenster schieben).
     */
    fun availableHeight(root: View?): Int {
        val laidOut = root?.height ?: 0
        if (laidOut > 0) return laidOut
        val display = root?.resources?.displayMetrics?.heightPixels ?: 0
        return (display * IME_WINDOW_FRACTION).toInt()
    }

    /**
     * Verfügbare Höhe für das **Maximieren**.
     *
     * Nicht `root.height` allein: die Wurzel steht auf `wrap_content`, ihre Höhe
     * ist damit die Summe der gerade sichtbaren Zeilen — also die *normale*
     * Tastaturhöhe. Wer daraus den Vollbild-Zustand rechnet, bekommt den
     * Normalzustand zurück; das Panel wächst nie (genau das war der Fehler:
     * „maximiert" war sichtbar so hoch wie vorher).
     *
     * Android gibt der IME bis zu [IME_WINDOW_FRACTION] der Bildschirmhöhe
     * (gemessen: 1915 px von 2712 px). Das Maximum ist deshalb die größere der
     * beiden Höhen: die Fenstergrenze des Systems oder — falls die IME schon
     * größer ist (z. B. sehr kleiner Bildschirm oder größere Systemfreigabe) —
     * die echte View-Höhe.
     */
    fun maximizeAvailableHeight(root: View?): Int {
        val laidOut = root?.height ?: 0
        val display = root?.resources?.displayMetrics?.heightPixels ?: 0
        return maxOf(laidOut, (display * IME_WINDOW_FRACTION).toInt())
    }

    /**
     * Zeilen, die **ausserhalb** des Inhalts-Panels liegen und nie von ihm
     * belegt werden duerfen: die obere Tab-Leiste und die Maximieren-Zeile. Beide
     * sind Geschwister von `kb_panel`/`file_panel` im Layout, ihre Hoehe ist also
     * additiv.
     *
     * Die Prediction-Zeile (R.id.suggestion_bar) gehoert *innerhalb* des
     * Buchstaben-Panels und steckt deshalb schon in dessen gemessener Hoehe.
     * GONE-Zeilen (im abc-Tab die Maximieren-Zeile) zaehlen nicht mit.
     */
    private val CHROME_IDS = intArrayOf(R.id.ime_tabs, R.id.maximize_row)

    /**
     * Höhe einer Zeile für die Rechnung: die **feste Layout-Höhe**, wenn das
     * Layout eine setzt, sonst gemessen.
     *
     * Notwendig, weil [measureHeight] mit `UNSPECIFIED` die *natürliche* Höhe des
     * Inhalts liefert: die Maximieren-Zeile ist 34dp hoch, der Material-Button
     * darin meldet natürlich aber 45px (Mindesthöhe der Schaltfläche), und die
     * Tab-Leiste steht auf 34dp, maß aber 48. Mit den gemessenen Werten war die
     * Fensterbegrenzung um ~25px zu streng, und der Inhalt wurde ohne Not
     * kleiner gerechnet, als das Fenster erlaubt.
     */
    private fun rowHeight(v: View, widthPx: Int): Int =
        v.height.takeIf { it > 0 }
            ?: v.layoutParams?.height?.takeIf { it > 0 }
            ?: measureHeight(v, widthPx)

    /** Summe der sichtbaren Chrome-Zeilen, 0 wenn nichts davon da ist. */
    fun chromeHeight(root: View, widthPx: Int): Int {
        var total = 0
        for (id in CHROME_IDS) {
            val v = root.findViewById<View>(id) ?: continue
            if (v.visibility == View.VISIBLE) {
                total += rowHeight(v, widthPx)
            }
        }
        return total
    }

    /**
     * Höhe der Maximieren-Zeile (unterste Zeile, nur in den Inhalts-Tabs).
     *
     * Kommt aus [`R.dimen.maximize_row_height`] — **nicht** aus einer Messung:
     * die Zeile hat eine feste Layout-Höhe, und [measureHeight] liefert mit
     * `UNSPECIFIED` die *natürliche* Höhe ihres Inhalts (Material-Button:
     * mindestens 48dp) statt der gelayouteten Höhe. Der Wert geht als konstanter
     * Abzug in die Normalhöhe der Inhalts-Panels ein ([applyNormalHeights]) und
     * darf deshalb nicht vom gerade sichtbaren Tab abhängen.
     */
    fun maximizeRowHeight(root: View): Int =
        root.resources.getDimensionPixelSize(R.dimen.maximize_row_height)

    /**
     * Hoehe der Buchstaben-Tastatur **und** ihrer unteren Key-Leiste — also
     * alles, was im Editor-Tab dauerhaft sichtbar ist und im maximierten Zustand
     * Platz braucht. 0 wenn nichts davon sichtbar ist.
     */
    fun keyboardBlockHeight(root: View, widthPx: Int): Int {
        var total = 0
        for (id in intArrayOf(R.id.kb_panel, R.id.bottom_row)) {
            val v = root.findViewById<View>(id) ?: continue
            if (v.visibility == View.VISIBLE) {
                total += rowHeight(v, widthPx)
            }
        }
        return total
    }

    /**
     * Zielhoehe des Inhalts-Panels im **maximierten** Zustand.
     *
     * Fuellt das IME-Fenster bis zu der Hoehe, die das System freigibt
     * ([maximizeAvailableHeight]), nachdem die Chrome-Zeilen und — im Editor-Tab —
     * die dauerhaft sichtbare Tastatur abgezogen sind. Damit sind beide Zustaende
     * je Tab sauber definiert:
     *  * normal: Editor-Panel = [editorNormalHeight], Tastatur darunter;
     *    die anderen Tabs = dieselbe Gesamthoehe minus Maximieren-Zeile.
     *  * maximiert: Panel = was hier herauskommt, Tastatur bleibt im Editor
     *    sichtbar und in Files/Clip/Snip weg.
     *
     * @param keyboardVisible true im Editor-Tab (Tastatur bleibt sichtbar)
     * @param overridePx Höhe aus der Config-Datei in px, 0 = Fenster füllen
     *   (siehe [TabHeights])
     */
    fun maximizedPanelHeight(
        root: View,
        widthPx: Int,
        keyboardVisible: Boolean,
        overridePx: Int = 0
    ): Int {
        val available = maximizeAvailableHeight(root) - chromeHeight(root, widthPx)
        if (available <= 0) return 0
        val reserved = if (keyboardVisible) keyboardBlockHeight(root, widthPx) else 0
        val free = available - reserved
        val wanted = if (overridePx > 0) overridePx else free
        return minOf(wanted, free).coerceAtLeast(1)
    }
}
