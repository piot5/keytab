package com.piotv.keytab.ime

import android.content.Context
import android.graphics.RectF
import android.view.View
import com.piotv.keytab.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * BackgroundImage: destination-Matrix (fit/cover/stretch), decode-Fehler,
 * apply mit leerer URI. (Bisher nur angerissen in SettingsRegressionTest.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundImageTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `fit passt ein mit Seitenraendern`() {
        assertEquals(RectF(0f, 25f, 100f, 75f),
            BackgroundImage.destination(200, 100, 100, 100, Prefs.FILL_FIT))
    }

    @Test
    fun `cover beschneidet mit Ueberhang`() {
        assertEquals(RectF(-50f, 0f, 150f, 100f),
            BackgroundImage.destination(200, 100, 100, 100, Prefs.FILL_COVER))
    }

    @Test
    fun `stretch fuellt exakt`() {
        assertEquals(RectF(0f, 0f, 100f, 100f),
            BackgroundImage.destination(200, 100, 100, 100, Prefs.FILL_STRETCH))
    }

    @Test
    fun `unbekannter Modus faellt auf fit zurueck`() {
        assertEquals(BackgroundImage.destination(200, 100, 100, 100, Prefs.FILL_FIT),
            BackgroundImage.destination(200, 100, 100, 100, "quatsch"))
    }

    @Test
    fun `Hochkant-Bild zentriert horizontal`() {
        val r = BackgroundImage.destination(100, 200, 100, 100, Prefs.FILL_FIT)
        assertEquals(RectF(25f, 0f, 75f, 100f), r)
    }

    @Test
    fun `Null-Dimensionen liefern leeres Rect`() {
        assertEquals(RectF(), BackgroundImage.destination(0, 100, 100, 100, Prefs.FILL_FIT))
        assertEquals(RectF(), BackgroundImage.destination(100, 100, 0, 100, Prefs.FILL_COVER))
        assertEquals(RectF(), BackgroundImage.destination(-5, 100, 100, 100, Prefs.FILL_FIT))
    }

    @Test
    fun `decode lehnt fehlend-fremd-leer ab`() {
        assertNull(BackgroundImage.decode(app, "/nonexistent/keytab-image.png"))
        assertNull(BackgroundImage.decode(app, "https://example.com/image.png"))
        assertNull(BackgroundImage.decode(app, "ftp://example.com/image.png"))
        assertNull(BackgroundImage.decode(app, ""))
    }

    @Test
    fun `decode mit Downsampling-Pfad nutzt echte PNG-Datei`() {
        // Robolectric-Bitmaps verhalten sich anders als Geräte-Bitmaps
        // (Shadow decodiert auch Müll zu non-null). Daher: echte 1x1-PNG
        // (Base64) muss decodieren — der Fehlerpfad ist via
        // nonexistent/https/ftp/leer oben bereits abgedeckt.
        val png = android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
            android.util.Base64.DEFAULT)
        val f = java.io.File(app.filesDir, "tiny.png")
        try {
            f.writeBytes(png)
            val bmp = BackgroundImage.decode(app, f.absolutePath)
            assertTrue("echte PNG decodiert", bmp != null)
        } finally {
            f.delete()
        }
    }

    @Test
    fun `apply mit leerer URI setzt Basis-Hintergrund synchron`() {
        Prefs.of(app).edit().putString(Prefs.KEY_BG_IMAGE_URI, "").commit()
        val base = android.graphics.drawable.ColorDrawable(0xFF112233.toInt())
        val view = View(app)
        BackgroundImage.apply(view, app, base)
        assertTrue("Basis bleibt synchron gesetzt", view.background === base)
    }
}
