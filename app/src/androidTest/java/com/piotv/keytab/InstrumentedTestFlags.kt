package com.piotv.keytab

import androidx.test.platform.app.InstrumentationRegistry

/**
 * Flags für die instrumentierten Tests.
 *
 * Der **Test-Runner** (Anzeige von Name/Erwartung, Pause/Weiter, Notizfeld im
 * Debug-Host `ImeTargetActivity`) ist ein Beobachtungs-Werkzeug für den
 * Menschen: Standard **aus**, damit seine zusätzlichen Views die Messungen
 * nicht beeinflussen. Aktivieren: `-e runner true`.
 */
object InstrumentedTestFlags {
    fun runnerEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("runner") == "true"
}

