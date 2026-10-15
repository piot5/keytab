package com.piotv.keytab.ime

import android.app.AlertDialog
import android.os.IBinder
import android.view.WindowManager

/**
 * Dialoge aus dem IME heraus anzeigen.
 *
 * Ein IME hat **keine** Activity: ein `AlertDialog` an einem Activity-losen
 * Context hat kein Fenster-Token und wirft beim `show()` eine
 * `WindowManager$BadTokenException` („Unable to add window -- token null is not
 * valid"). Genau das ist am 2026-10-12 im Files-Tab passiert: Long-Press auf
 * einen Eintrag → Kontextmenü → Absturz (`FileManagerPanel.showFileMenu`).
 *
 * Deshalb: Dialog mit `AlertDialog.Builder.create()` bauen, Typ
 * `TYPE_APPLICATION_ATTACHED_DIALOG` setzen und das Token des Input-Views als
 * `attributes.token` übergeben. Ohne Token wird **nicht** gezeigt — der Aufrufer
 * bekommt `false` und kann einen Toast anzeigen; ein Absturz wäre die
 * schlechteste aller Optionen.
 */
internal object ImeDialog {

    /** Zeigt [dialog] am IME-Fenster; `false`, wenn kein Token verfügbar ist. */
    fun show(dialog: AlertDialog, windowToken: IBinder?): Boolean {
        val window = dialog.window ?: return false
        if (windowToken == null) return false
        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
        window.attributes.token = windowToken
        dialog.show()
        return true
    }
}
