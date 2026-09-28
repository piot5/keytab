package com.piotv.keytab.ime

import android.view.View
import com.piotv.keytab.R

/**
 * Vollbild-Modus ("Maximieren") der Inhalts-Tabs.
 *
 * Je Tab gibt es zwei definierte Zustaende:
 *  * **normal** — der Tab so hoch wie der Editor-Tab. Im Editor steht das
 *    Editor-Panel ueber der Tastatur, in Files/Clip/Snippets das jeweilige
 *    Panel allein (Tastatur dort nie sichtbar).
 *  * **maximiert** — das Inhalts-Panel fuellt das IME-Fenster, soweit das System
 *    es hergibt ([PanelHeights.maximizedPanelHeight]). Die obere Tab-Leiste
 *    bleibt sichtbar: ohne sie waere nicht erkennbar, in welchem Tab man ist.
 *    Die Maximieren-Zeile bleibt **nur dort**, wo sie auch im Normalzustand
 *    steht (Files/Clip/Snippets, `maximizeRowVisible`); im Editor-Tab sitzt der
 *    Ausweg im Symbol der Prediction-Zeile — eine zweite Zeile waere doppelt und
 *    kostete Hoehe, die der Editor braucht.
 *
 * Der abc-Tab kennt keinen maximierten Zustand — dort gibt es nichts zu
 * vergroessern, die Tastatur ist selbst das groesste Element.
 *
 * Tastatur-Sichtbarkeit ist **tababhaengig, nicht zustandsabhaengig**:
 *  * Editor: Tastatur immer sichtbar, auch maximiert (sonst koennte man im
 *    Text nichts mehr tippen),
 *  * Files/Clip/Snippets: Tastatur nie sichtbar, auch nicht im Normalzustand,
 *  * abc: Tastatur immer sichtbar, nie maximiert.
 *
 * Die Höhen-/Sichtbarkeitslogik steckt hier, damit sie ohne Service-Mock
 * testbar bleibt; der Service ruft nur [collapse] bzw. [expand] auf.
 */
internal object KeyboardCollapse {

    /** Die Panels, die ihre Sichtbarkeit selbst steuern und nie umgeschaltet werden. */
    private val PANEL_IDS = setOf(
        R.id.editor_panel,
        R.id.file_panel,
        R.id.clip_panel,
        R.id.snippet_panel
    )

    /**
     * Das Panel, das im Vollbild-Modus wächst: das sichtbare Inhalts-Panel,
     * im abc-Tab die Buchstaben-Tastatur.
     */
    fun targetPanel(root: View): View? {
        for (id in PANEL_IDS) {
            val panel = root.findViewById<View>(id) ?: continue
            if (panel.visibility == View.VISIBLE) return panel
        }
        return root.findViewById<View>(R.id.kb_panel)
    }

    /**
     * In den Vollbild-Modus wechseln: das aktive Panel (oder die Tastatur im
     * abc-Tab) wächst auf die Höhe, die das IME-Fenster hergibt
     * ([PanelHeights.maximizedPanelHeight] — das ist die Systemgrenze, nicht ein
     * Prozentsatz davon), Chrome-Zeilen weg, Maximieren-Zeile je Tab.
     *
     * Die Normalhöhe wird vor dem Umschalten gemerkt, damit [expand] sie
     * verlustfrei zurücksetzen kann.
     *
     * @param keyboardVisible Tastatur bleibt sichtbar (Editor- und abc-Tab)
     * @param maximizeRowVisible die Max-Zeile am unteren Rand zeigen
     *   (Files/Clip/Snippets). Im Editor-Tab **nicht**: dort ist das
     *   ⇲-Symbol der Prediction-Zeile der Weg zurück, und die Zeile nähme dem
     *   Editor Höhe weg, die er im Vollbild gerade bekommen soll.
     * @param heightPx Höhe aus der Config-Datei in px, 0 = Fenster füllen
     *   (siehe [TabHeights])
     */
    fun collapse(
        root: View,
        keyboardVisible: Boolean,
        maximizeRowVisible: Boolean,
        heightPx: Int = 0
    ) {
        val target = targetPanel(root) ?: return
        // Normalhoehe merken, damit expand() sie verlustfrei zuruecksetzen kann.
        root.setTag(R.id.maximize_normal_height, target.layoutParams?.height ?: 0)

        // Sichtbarkeiten **zuerst**, die Hoehe danach: die Rechnung zieht die
        // sichtbaren Zeilen ab, und vorher stand sie vor dem Umschalten — dadurch
        // wurde die untere Key-Leiste versteckt, ihr Platz aber weiter
        // abgezogen (rund 46dp Leerraum in jedem Vollbild).
        root.findViewById<View>(R.id.sym_panel)?.visibility = View.GONE
        // Oberkante bleibt als Orientierung; die Ausweg-Zeile nur, wo sie hingehoert.
        root.findViewById<View>(R.id.ime_tabs)?.visibility = View.VISIBLE
        root.findViewById<View>(R.id.maximize_row)?.visibility =
            if (maximizeRowVisible) View.VISIBLE else View.GONE
        // Die untere Key-Leiste gehoert zur Tastatur: wo die Tastatur bleibt
        // (Editor-Tab), bleibt auch sie — sonst fehlten im Vollbild Leerzeichen,
        // Tab und Enter (die Buchstaben-Ebene hat sie nicht).
        root.findViewById<View>(R.id.bottom_row)?.visibility =
            if (keyboardVisible) View.VISIBLE else View.GONE
        // Nur die nicht sichtbaren Panels verschwinden; alle anderen behalten
        // ihren Zustand (im Editor bleibt die Tastatur stehen).
        for (id in PANEL_IDS) {
            val panel = root.findViewById<View>(id) ?: continue
            if (panel !== target && panel.visibility == View.VISIBLE) {
                panel.visibility = View.GONE
            }
        }
        root.findViewById<View>(R.id.kb_panel)?.visibility =
            if (keyboardVisible) View.VISIBLE else View.GONE

        val width = root.resources.displayMetrics.widthPixels
        val maximized = PanelHeights.maximizedPanelHeight(root, width, keyboardVisible, heightPx)
        if (maximized <= 0) return

        target.layoutParams = target.layoutParams.apply { height = maximized }
    }

    /**
     * Aus dem Vollbild-Modus zurück: Normalhöhe und Sichtbarkeit wiederherstellen.
     * Die Tabs entscheiden danach selbst, welches Panel sichtbar ist.
     */
    fun expand(root: View) {
        val target = targetPanel(root)
            ?: root.findViewById<View>(R.id.kb_panel)
            ?: return
        val normal = root.getTag(R.id.maximize_normal_height) as? Int ?: 0
        if (normal > 0) {
            target.layoutParams = target.layoutParams.apply { height = normal }
        } else {
            // Kein gemerkter Wert (z. B. nach Activity-Rebuild): neu berechnen.
            PanelHeights.applyNormalHeights(root, root.resources.displayMetrics.widthPixels)
        }
        // Sichtbarkeit gibt danach der Tab-Wechsler vor — der kennt die
        // Tastatur-Regeln (Editor immer, Files/Clip/Snip nie).
        for (id in intArrayOf(R.id.ime_tabs)) {
            root.findViewById<View>(id)?.visibility = View.VISIBLE
        }
    }
}
