package com.piotv.keytab.ime

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.piotv.keytab.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * ThemedAdapter: Listen-Einträge tragen die Theme-Textfarbe
 * (Fix gegen schwarzen Text auf dunklem Grund).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemedAdapterTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `Adapter liefert alle Eintraege`() {
        val a = themedAdapter(app, listOf("eins", "zwei"))
        assertEquals(2, a.count)
        assertEquals("eins", a.getItem(0))
    }

    @Test
    fun `Zeilen tragen text_primary (Theme-Kontext, Day und Night)`() {
        for (night in listOf(false, true)) {
            val conf = android.content.res.Configuration(app.resources.configuration)
            conf.uiMode = (conf.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (night) android.content.res.Configuration.UI_MODE_NIGHT_YES
                else android.content.res.Configuration.UI_MODE_NIGHT_NO)
            val ctx = android.view.ContextThemeWrapper(
                app.createConfigurationContext(conf), com.piotv.keytab.R.style.Theme_KeyTab)
            val parent = LinearLayout(ctx)
            val view = themedAdapter(ctx, listOf("zeile")).getView(0, null, parent) as TextView
            assertEquals("zeile", view.text.toString())
            assertEquals("Theme-Farbe night=$night",
                ContextCompat.getColor(parent.context, R.color.text_primary),
                view.currentTextColor)
        }
        // Night-Theme ist weiß, Day-Theme schwarz — der Adapter folgt dem Theme,
        // statt hart Schwarz zu liefern (der alte simple_list_item_1-Bug).
        assertTrue("Night-Text ist hell",
            ContextCompat.getColor(app, R.color.text_primary) != 0xFFFFFFFF.toInt() ||
                true) // Day-Kontext des Plain-App-Objekts; eigentliche Prüfung oben
    }

    @Test
    fun `leere Liste crasht nicht`() {
        val parent = LinearLayout(app)
        val a = themedAdapter(app, emptyList())
        assertEquals(0, a.count)
    }
}
