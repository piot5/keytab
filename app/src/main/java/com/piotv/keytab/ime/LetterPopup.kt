package com.piotv.keytab.ime

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.piotv.keytab.R

/**
 * Long-Press-Popup: zeigt NUR die Zusatz-Sonderzeichen eines Buchstaben-Keys
 * in einem kompakten Grid (ohne Hauptbuchstaben-Header). Unterstützt Drag-Auswahl
 * (Gboard-Stil): Der Finger kann über die Zellen ziehen, beim Loslassen wird die
 * hervorgehobene Zelle committet. Hält alle Popup-Zustände (Fenster, Zellen,
 * Highlight) selbst – so bleibt der Keyboard-Core ([KeyTabImeService]) schlank.
 */
class LetterPopup(
    private val service: KeyTabImeService
) {
    private var activePopup: PopupWindow? = null
    private var cells: List<TextView> = emptyList()
    private var highlighted: TextView? = null
    private var highlightBg: Drawable? = null
    // Erstes Zusatz-Zeichen → Fallback: wenn beim Loslassen keine Zelle angesteuert
    // wurde, wird dieses (erste der Popup-Reihe) ausgelöst.
    private var firstChar: Char? = null

    /** Blendet ein aktives Popup aus, falls vorhanden. */
    fun dismiss() {
        activePopup?.dismiss()
        activePopup = null
    }

    /** Zeichnet das Popup über [anchor]; zeigt NUR die Zusatz-Sonderzeichen
     *  (ohne Hauptbuchstaben-Header). Unterstützt Drag-Auswahl (Gboard-Stil). */
    @SuppressLint("InflateParams")
    fun show(anchor: View, showExtras: List<String>, onCommit: (Char) -> Unit) {
        dismiss()
        val ctx = anchor.context
        // FlorisBoard-Farben: Popup-Fläche + hervorgehobener Fokus
        val popupBg = ContextCompat.getColor(service, R.color.popup_bg)
        val popupText = ContextCompat.getColor(service, R.color.popup_text)
        val focusBg = ContextCompat.getColor(service, R.color.popup_focus_bg)
        val focusBgDrawable = GradientDrawable().apply {
            setColor(focusBg)
            cornerRadius = 8f * service.resources.displayMetrics.density
        }
        // Nur die Zusatz-Sonderzeichen als kompaktes Grid (3 pro Zeile)
        val grid = GridLayout(ctx).apply {
            columnCount = 3
            orientation = GridLayout.HORIZONTAL
            setBackgroundColor(popupBg)
            setPadding(6, 6, 6, 6)
        }
        firstChar = showExtras.firstOrNull()?.first()
        for (ch in showExtras) {
            val tv = TextView(ctx).apply {
                text = ch
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(popupText)
                // großzügige Zellen: gut drückbar + genug Platz für Drag-Auswahl
                setPadding(18, 14, 18, 14)
                setOnClickListener {
                    onCommit(ch.first())
                    dismiss()
                }
            }
            grid.addView(tv)
        }
        val popup = PopupWindow(
            grid,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            // nicht fokusierbar: klaut dem Eingabefeld keinen Fokus;
            // transparenter Background aktiviert Dismiss bei Tap außerhalb
            isOutsideTouchable = true
            isFocusable = false
            // darf über den Rand des IME-Fensters hinausragen (sonst Abschneiden oben)
            isClippingEnabled = false
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        // Popup exakt über der angetippten Taste zentrieren.
        // showAtLocation erwartet FENSTER-Relative Koordinaten → getLocationInWindow
        // (Screen-Koordinaten würden das Popup unterhalb des IME-Fensters platzieren).
        grid.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupW = grid.measuredWidth
        val popupH = grid.measuredHeight
        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val rootW = anchor.rootView.width
        val x = (location[0] + anchor.width / 2 - popupW / 2)
            .coerceIn(0, (rootW - popupW).coerceAtLeast(0))
        val y = (location[1] - popupH - 8).coerceAtLeast(0)
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        activePopup = popup
        // Drag-Auswahl: alle wählbaren Zellen registrieren (Grid), Highlight zurücksetzen
        highlightBg = focusBgDrawable
        highlighted = null
        cells = buildList {
            for (i in 0 until grid.childCount) (grid.getChildAt(i) as? TextView)?.let { add(it) }
        }
    }

    /** Hebt die Zelle unter dem Finger hervor (Drag-Auswahl, Gboard-Stil). */
    fun highlightCellUnder(event: MotionEvent, onHaptic: () -> Unit) {
        if (activePopup == null) return
        var found: TextView? = null
        for (cell in cells) {
            // Popup-Inhalt liegt in einem EIGENEN Fenster → Screen-Koordinaten verwenden
            // (getLocationInWindow wäre popup-relativ und würde nie matchen)
            val loc = IntArray(2)
            cell.getLocationOnScreen(loc)
            if (event.rawX >= loc[0] && event.rawX < loc[0] + cell.width &&
                event.rawY >= loc[1] && event.rawY < loc[1] + cell.height) {
                found = cell
                break
            }
        }
        if (found != highlighted) {
            highlighted?.background = null
            found?.background = highlightBg
            highlighted = found
            if (found != null) onHaptic()
        }
    }

    /** Liefert den auszulösenden Buchstaben: die beim Drag hervorgehobene Zelle,
     *  oder (falls nichts angesteuert wurde) das erste Zeichen der Popup-Reihe. */
    fun pickedChar(): Char? {
        if (activePopup == null) return null
        return highlighted?.text?.firstOrNull() ?: firstChar
    }
}

