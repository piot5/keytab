package com.piotv.keytab

import android.Manifest
import android.app.Application
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric-Tests für den Einstellungs-Screen (`MainActivity`).
 *
 * Kernaussage (F-Droid-Review 2026-10-06, Punkt 1): Der Dateizugriff wird
 * **ausschließlich** per Klick auf „Grant file access“ angefragt — der App-Start
 * selbst zeigt keinen System-Dialog. So unterbricht ein Ablehnen das Tippen
 * nicht bei jedem Start, und der Files-Tab bleibt optional.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()

    private fun start(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun status(activity: MainActivity): String =
        activity.findViewById<TextView>(R.id.text_file_access).text.toString()

    private fun grantButton(activity: MainActivity): Button =
        activity.findViewById(R.id.btn_grant_file_access)

    @Test
    fun `App-Start fragt keine Speicher-Berechtigung an`() {
        val activity = start()
        assertNull("Kein Berechtigungs-Dialog beim Start",
            shadowOf(activity).lastRequestedPermission)
    }

    @Test
    fun `Status-Zeile nennt fehlenden Zugriff und der Button ist aktiv`() {
        val activity = start()
        assertEquals(activity.getString(R.string.settings_storage_missing), status(activity))
        assertTrue("Ohne Zugriff muss der Button klickbar sein", grantButton(activity).isEnabled)
    }

    @Test
    fun `Klick auf Grant fragt die Berechtigungen erst dann an`() {
        val activity = start()
        grantButton(activity).performClick()

        val request = shadowOf(activity).lastRequestedPermission
        assertNotNull("Der Klick muss die Anfrage auslösen", request)
        val requested = request.requestedPermissions.toList()
        assertTrue("READ_MEDIA_IMAGES angefragt: $requested",
            requested.contains(Manifest.permission.READ_MEDIA_IMAGES))
    }

    /**
     * Android 13+: `READ_MEDIA_IMAGES` gibt nur Bilder frei — der Status darf
     * keine vollständige Dateiliste versprechen (F-Droid-Testerbericht 2026-10-13).
     */
    @Test
    fun `Voller Medienzugriff nennt Ordner und Bilder statt aller Dateien`() {
        shadowOf(app).grantPermissions(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        val activity = start()
        assertEquals(activity.getString(R.string.settings_storage_granted_media), status(activity))
        assertFalse("Mit Zugriff braucht es keinen zweiten Prompt", grantButton(activity).isEnabled)
    }

    /**
     * Bis Android 12 deckt `READ_EXTERNAL_STORAGE` den ganzen Speicher ab — der
     * alte Text bleibt dort gültig. Geprüft über den `sdkInt`-Parameter statt
     * `@Config(sdk = [32])`: ein zweites Robolectric-SDK-Level lädt in CI ein
     * zusätzliches android-all-Jar nach (dort am TLS-Download gescheitert).
     */
    @Test
    fun `Bis Android 12 nennt der Status weiter die vollstaendige Dateiliste`() {
        val activity = start()
        assertEquals(activity.getString(R.string.settings_storage_granted),
            activity.getString(activity.grantedStatusText(32)))
    }

    /** Android 14+: „nur ausgewählte Bilder“ ist Teilzugriff — sichtbar als solcher. */
    @Test
    fun `Teilzugriff auf ausgewaehlte Bilder wird als Teilzugriff angezeigt`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        val activity = start()
        assertEquals(activity.getString(R.string.settings_storage_partial), status(activity))
        assertTrue("Teilzugriff lässt sich aufweiten", grantButton(activity).isEnabled)
    }
}
