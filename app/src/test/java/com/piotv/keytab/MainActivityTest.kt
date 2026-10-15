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
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper
import com.piotv.keytab.ime.StorageAccess

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

    /** Testhaken für „Alle Dateien“ zurücksetzen (Robolectric hat keinen Shadow dafür). */
    @After
    fun resetStorageAccessOverride() {
        StorageAccess.resetForTests()
    }

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

    // ---------- All-files access (API 30+), ab v0.19 ----------
    //
    // F-Droid-Konvention: kein Prompt beim Start, kein Hintergrundzugriff,
    // Berechtigung nur im Kontext und mit Begründung anfragen, vollwertiger
    // Fallback ohne sie. MANAGE_EXTERNAL_STORAGE lässt sich nur über den
    // System-Screen erteilen — deshalb führt der Weg über einen Dialog dorthin.

    private fun allFilesButton(activity: MainActivity): Button =
        activity.findViewById(R.id.btn_grant_all_files)

    @Test
    fun `All-files access zeigt Vollzugriff und deaktiviert beide Buttons`() {
        StorageAccess.allFilesOverride = true
        val activity = start()
        assertEquals(activity.getString(R.string.settings_storage_all_files), status(activity))
        assertFalse("Vollzugriff braucht keinen Medien-Prompt", grantButton(activity).isEnabled)
        assertFalse("Vollzugriff braucht keinen zweiten All-files-Prompt",
            allFilesButton(activity).isEnabled)
    }

    @Test
    fun `Ohne All-files bleibt der Button anfragbar und der Medienstatus stehen`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        val activity = start()
        assertEquals(activity.getString(R.string.settings_storage_granted_media), status(activity))
        assertTrue("All-files ist optional und muss anfragbar bleiben",
            allFilesButton(activity).isEnabled)
    }

    @Test
    fun `Klick auf All-files begruendet zuerst und oeffnet dann die System-Einstellungen`() {
        val activity = start()
        assertNull("Kein System-Screen ohne Klick", shadowOf(activity).nextStartedActivity)
        allFilesButton(activity).performClick()
        assertNull("Kein Runtime-Prompt für All-files", shadowOf(activity).lastRequestedPermission)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("Begründungs-Dialog vor dem System-Screen", dialog)
        dialog!!.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        // AlertController verschickt den Button-Klick über einen Handler des
        // Main-Loopers — unter Robolectric (PAUSED) muss er erst laufen.
        ShadowLooper.runUiThreadTasks()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(
            "Der paketbezogene System-Screen für „Alle Dateien“",
            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            started?.action
        )
        assertEquals("package:${activity.packageName}", started?.data?.toString())
    }

}
