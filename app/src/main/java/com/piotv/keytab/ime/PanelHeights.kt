package com.piotv.keytab.ime

import android.view.View

/**
 * Panel-Höhen der Tastatur-Tabs.
 *
 * Der Notes-Tab zeigt das Editor-Panel (Load/Save-Zeile + Eingabefeld) ÜBER der
 * Buchstaben-Tastatur, der Files-Tab blendet beide aus und zeigt stattdessen das
 * Datei-Panel. Damit der Files-Tab **genauso hoch** ist wie der Notes-Tab, muss das
 * Datei-Panel die kombinierte Höhe von Editor-Panel + Buchstaben-Panel einnehmen
 * (die Funktionsleiste unten ist in beiden Tabs identisch sichtbar).
 *
 * Die Höhe wird gemessen statt hartkodiert, damit sie bei geänderter Tastengröße,
 * anderen Schriften oder Layout-Änderungen automatisch korrekt bleibt.
 * [View.measure] funktioniert auch für GONE-Views – beim Tab-Wechsel sind Editor-
 * und Buchstaben-Panel bereits ausgeblendet.
 */
object PanelHeights {

    /**
     * Zielhöhe des Datei-Panels = gemessene Höhe von [editorPanel] + [keyboardPanel].
     *
     * @param widthPx Breite für den MeasureSpec (Display-Breite übergeben: der Root
     *   ist beim ersten Aufruf ggf. noch nicht gemessen)
     * @return Höhe in px; 0 wenn nicht ermittelbar (dann bleibt das Layout-Maß)
     */
    fun filesPanelHeight(editorPanel: View?, keyboardPanel: View?, widthPx: Int): Int {
        if (editorPanel == null || keyboardPanel == null || widthPx <= 0) return 0
        val width = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        editorPanel.measure(width, free)
        keyboardPanel.measure(width, free)
        val height = editorPanel.measuredHeight + keyboardPanel.measuredHeight
        return if (height > 0) height else 0
    }
}
