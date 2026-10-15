package com.piotv.keytab.ime

import android.app.AlertDialog
import android.os.Binder
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regression zum Absturz vom 2026-10-12 (Files-Tab, Long-Press →
 * `WindowManager$BadTokenException: token null is not valid`): Dialoge aus dem
 * IME brauchen den Fenstertyp `TYPE_APPLICATION_ATTACHED_DIALOG` **und** das
 * Token des Input-Views — ohne Token darf gar nicht erst gezeigt werden.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImeDialogTest {

    private fun dialog(): AlertDialog = AlertDialog.Builder(RuntimeEnvironment.getApplication())
        .setTitle("KeyTab")
        .setMessage("Test")
        .create()

    @Test
    fun `mit Token wird der Dialog am IME-Fenster gehaengt`() {
        val token = Binder()
        val dialog = dialog()

        assertTrue("show() muss true melden", ImeDialog.show(dialog, token))
        assertTrue("Dialog muss sichtbar sein", dialog.isShowing)

        val attributes = dialog.window!!.attributes
        assertEquals(
            "Fenstertyp muss an das IME-Fenster angehaengt sein",
            WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG, attributes.type
        )
        assertEquals("Token des Input-Views muss gesetzt sein", token, attributes.token)
    }

    @Test
    fun `ohne Token wird nicht gezeigt statt abzustuerzen`() {
        val dialog = dialog()

        assertFalse("ohne Token kein show()", ImeDialog.show(dialog, null))
        assertFalse("Dialog darf nicht sichtbar sein", dialog.isShowing)
    }
}
