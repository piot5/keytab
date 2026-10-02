package com.piotv.keytab

import androidx.test.platform.app.InstrumentationRegistry

/**
 * Gemeinsame Flags für Instrumented Tests (v0.16 Performance/Flow).
 *
 * Die langsamen Screenshot-/GIF-Tests ([KeyTabDeviceScreensTest]) sind ein
 * Dokumentations-Werkzeug, kein Contract-Test — sie laufen nur, wenn sie
 * explizit angefordert werden. Der Standard-`connectedDebugAndroidTest`
 * bleibt damit unter der 5-Minuten-Grenze und in einem einzigen Lauf.
 *
 * Aktivieren:
 *   `am instrument -e screens true ...`
 *   bzw. Gradle `-Pandroid.testInstrumentationRunnerArguments.screens=true`.
 */
object InstrumentedTestFlags {
    /** Sind die langsamen Screenshot-/GIF-Aufnahmen angefordert? */
    fun screensEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("screens") == "true"
}
