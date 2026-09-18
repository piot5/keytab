package com.piotv.keytab.ime

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Zentrale Thread-Infrastruktur der App (Phase: Coroutine-Migration ohne neue
 * Dependencies – siehe docs/REFACTORING_PLAN.md). Vorher hatte jeder Erzeuger
 * (Service, FileManagerFragment, BackgroundImage) seinen eigenen
 * Single-Thread-Executor und teils lokale Main-Handler; jetzt gibt es pro
 * Prozess genau einen I/O- und einen Bild-Executor plus einen Main-Handler.
 *
 * Beide Executoren sind Daemon-Threads: Sie halten den Prozess nicht am Leben
 * und dürfen NICHT per shutdownNow() beendet werden – der IME-Service kann
 * zerstört und neu erzeugt werden, während die Pools weiterlaufen. Leere,
 * wartende Daemon-Pools kosten praktisch nichts; der Android-Prozess läuft
 * für die nächste IME-Sitzung ohnehin weiter.
 */
object KeyTabExecutors {

    /** Datei-/Settings-I/O (Panels, Fragment) – seriell, damit Schreibreihenfolge stimmt. */
    val io: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "keytab-io").apply { isDaemon = true }
    }

    /** Gebundenes Herunter-/Dekodieren großer Hintergrundbilder – eigener Name fürs Profiling. */
    val image: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "keytab-image").apply { isDaemon = true }
    }

    /** Gemeinsamer Main-Handler für UI-Rückkehr aus Hintergrund-Threads. */
    val main: Handler = Handler(Looper.getMainLooper())
}