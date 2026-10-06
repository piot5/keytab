package com.piotv.keytab

import androidx.test.platform.app.InstrumentationRegistry

/**
 * Gemeinsame Flags für Instrumented Tests (v0.16 Performance/Flow).
 *
 * Die langsamen Screenshot-/GIF-Aufnahmen ([KeyTabDeviceScreensTest]) sind ein
 * Dokumentations-Werkzeug, kein Contract-Test — sie laufen nur, wenn sie
 * explizit angefordert werden. Der Standard-`connectedDebugAndroidTest`
 * bleibt damit unter der 5-Minuten-Grenze und in einem einzigen Lauf.
 *
 * Aktivieren:
 *   `am instrument -e screens true ...`
 *   bzw. Gradle `-Pandroid.testInstrumentationRunnerArguments.screens=true`.
 *
 * Showcase-Videos ([KeyTabShowcaseTest]) sind das gleiche Prinzip eine Stufe
 * langsamer: ein MP4 pro Feature via `screenrecord`. Sie laufen nur mit
 * `-e showcase true` (Host-Skript: `scripts/showcase_videos.sh` — ein
 * `am instrument`-Aufruf pro Methode, damit jedes Video genau ein Feature
 * zeigt).
 */
object InstrumentedTestFlags {
    /** Sind die langsamen Screenshot-/GIF-Aufnahmen angefordert? */
    fun screensEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("screens") == "true"

    /** Sind die Showcase-Video-Aufnahmen angefordert? */
    fun showcaseEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("showcase") == "true"

    /**
     * Laufen die langsamen/geraeteabhaengigen Klassen mit?
     *
     * Betroffen sind `KeyTabImeHeightTest` (Hoehenmessung mit Tab-Wechseln und
     * Wartezeiten), `KeyTabImeSuggestionsTest` (Vorschlags-Engine muss den
     * Korpus laden) und `KeyTabTrailVisualTest` (Screenshot-Pixelvergleich).
     * Zusammen kosten sie im CI-Emulator mehrere Minuten und liefern dort
     * Flakes, die auf echter Hardware nicht auftreten.
     *
     * Standard: **aus** — das CI-Budget ist 3 Minuten. Auf dem Geraet laufen sie
     * mit: `scripts/test_on_device.sh` setzt `-e slow true`.
     */
    fun slowEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("slow") == "true"

    /**
     * Ist der Test-Runner (Anzeige von Name/Erwartung, Pause/Weiter, Notizfeld
     * im Debug-Host) angefordert? Standard: **aus**.
     *
     * Der Runner ist ein Beobachtungs-Werkzeug für den Menschen. Im
     * Standard-`connectedDebugAndroidTest` (CI) ist er aus, damit seine
     * zusätzlichen Views die Messungen der Tests nicht beeinflussen.
     * Aktivieren: `-e runner true`.
     */
    fun runnerEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("runner") == "true"
}

