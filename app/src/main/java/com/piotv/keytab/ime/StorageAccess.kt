package com.piotv.keytab.ime

import android.os.Build
import android.os.Environment

/**
 * Dateizugriff-Stufe „Alle Dateien“ (`MANAGE_EXTERNAL_STORAGE`, ab API 30) als
 * **einziger** Zugriffspunkt — App-Einstellungen (`MainActivity`) und Files-Tab
 * (`FileManagerPanel`) müssen dieselbe Antwort bekommen, sonst widersprechen sich
 * Statuszeile und Listen-Hinweis.
 *
 * [allFilesOverride] ist der dokumentierte Testhaken: Robolectric 4.12 hat keinen
 * Shadow für `Environment.isExternalStorageManager()`, die echte Abfrage wäre in
 * JVM-Tests also immer `false` und der gewährte Zustand nicht prüfbar. Produktion
 * lässt den Haken auf `null` und fragt die Plattform.
 *
 * Die Berechtigung ist bewusst **optional**: sie wird nie beim Start und nie im
 * Hintergrund erfragt, sondern nur über den Button „Grant all-files access“ in den
 * Einstellungen (Begründungs-Dialog → System-Screen). Ohne sie bleibt der
 * Medien-Weg (Ordner + Bilder) vollständig nutzbar.
 */
internal object StorageAccess {

    /** „Alle Dateien“ (`MANAGE_EXTERNAL_STORAGE`) gibt es ab API 30 (Android 11). */
    private const val ANDROID_11 = 30

    /** Testhaken: `null` = echte Plattform-Abfrage. */
    internal var allFilesOverride: Boolean? = null

    /** true, wenn dem Paket „Alle Dateien“ freigegeben ist (API 30+). */
    fun allFilesGranted(): Boolean {
        allFilesOverride?.let { return it }
        if (Build.VERSION.SDK_INT < ANDROID_11) return false
        // Defensiv: die Plattform-Abfrage geht über den PackageManager und kann
        // in Umgebungen ohne vollständigen Systemdienst werfen (JVM-Tests mit
        // Robolectric: ArrayIndexOutOfBoundsException; einzelne ROMs werfen
        // SecurityException). Ein nicht erteilter Zugriff ist die sichere Antwort —
        // die Einstellungen und der Files-Tab dürfen daran nicht scheitern.
        return try {
            Environment.isExternalStorageManager()
        } catch (_: Exception) {
            false
        }
    }

    /** Nur für Tests: Haken zurücksetzen. */
    internal fun resetForTests() {
        allFilesOverride = null
    }
}
