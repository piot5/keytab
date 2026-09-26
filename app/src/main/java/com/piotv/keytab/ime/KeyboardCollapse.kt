package com.piotv.keytab.ime

import android.view.View
import android.view.ViewGroup
import com.piotv.keytab.R

/**
 * Einklappen/Ausklappen der Tastatur im Editor-Tab (☰-Schaltfläche).
 *
 * Refactoring: aus [KeyTabImeService.hideKeyboard] extrahiert, damit der
 * Service schlank bleibt und die reine Höhen-/Sichtbarkeitslogik testbar ist.
 * Verhalten unverändert: der Suggestion-Trigger blendet beim Einklappen alle
 * Geschwister-Kinder aus, beim Ausklappen wieder ein – die vier Panel-IDs
 * bleiben in beiden Richtungen sichtbar, da sie ihre Sichtbarkeit selbst steuern.
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
     * Sichtbarkeit der Geschwister-Kinder der Vorschlagsleiste umschalten.
     * @param collapse true = einklappen (Geschwister aus), false = ausklappen (ein)
     */
    /**
     * Sichtbarkeit der Geschwister-Kinder der Vorschlagsleiste umschalten.
     *
     * Die beiden Richtungen sind bewusst **asymmetrisch** (Originalverhalten):
     * beim Einklappen wandern alle Geschwister raus, beim Ausklappen kommen nur
     * die Nicht-Panels zurück – die Panels steuern ihre Sichtbarkeit selbst.
     *
     * @param skipPanels true = Panel-IDs stehen lassen (Ausklappen), false = alle
     */
    private fun applySiblingVisibility(root: View, visible: Boolean, skipPanels: Boolean) {
        val sugBar = root.findViewById<View>(R.id.suggestion_bar)
        val lettersRoot = sugBar?.parent as? ViewGroup ?: return
        for (i in 0 until lettersRoot.childCount) {
            val child = lettersRoot.getChildAt(i)
            if (isToggleable(child, skipPanels)) {
                child.visibility = if (visible) View.VISIBLE else View.GONE
            }
        }
    }

    /**
     * Darf die Sichtbarkeit dieses Kindes umgeschaltet werden? Die
     * Vorschlagsleiste selbst und – im Ausklappen-Zweig – die Panels nicht:
     * beide steuern ihre Sichtbarkeit selbst.
     */
    private fun isToggleable(child: View, skipPanels: Boolean): Boolean =
        child.id != R.id.suggestion_bar && !(skipPanels && child.id in PANEL_IDS)

    /**
     * Einklappen-Zweig: Geschwister der Vorschlagsleiste ausblenden, untere
     * Zeile verstecken, im Editor-Tab die volle Höhe auf die Tastatur legen.
     */
    fun collapse(root: View, kind: TabController.TabKind) {
        val ed = root.findViewById<View>(R.id.editor_panel)
        val kb = root.findViewById<View>(R.id.kb_panel)
        val bottomRow = root.findViewById<View>(R.id.bottom_row)
        val width = root.resources.displayMetrics.widthPixels
        val maxH = PanelHeights.measureHeight(ed, width) +
            PanelHeights.measureHeight(kb, width) +
            PanelHeights.measureHeight(bottomRow, width)
        applySiblingVisibility(root, visible = false, skipPanels = false)
        bottomRow?.visibility = View.GONE
        if (maxH > 0 && kind == TabController.TabKind.EDITOR && ed != null) {
            root.setTag(R.id.editor_normal_height, ed.layoutParams.height)
            ed.layoutParams = ed.layoutParams.apply { height = maxH }
        }
    }

    /**
     * Ausklappen-Zweig: Geschwister wieder einblenden, untere Zeile zeigen
     * und die gemerkte Normalhöhe des Editors wiederherstellen.
     */
    fun expand(root: View, kind: TabController.TabKind) {
        val ed = root.findViewById<View>(R.id.editor_panel)
        val bottomRow = root.findViewById<View>(R.id.bottom_row)
        val width = root.resources.displayMetrics.widthPixels
        applySiblingVisibility(root, visible = true, skipPanels = true)
        bottomRow?.visibility = View.VISIBLE
        val h = PanelHeights.measureHeight(ed, width)
        if (h > 0 && kind == TabController.TabKind.EDITOR && ed != null) {
            val normal = root.getTag(R.id.editor_normal_height) as? Int ?: h
            ed.layoutParams = ed.layoutParams.apply { height = normal }
        }
    }
}
