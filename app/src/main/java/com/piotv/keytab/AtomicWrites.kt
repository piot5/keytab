package com.piotv.keytab

import java.io.File

/**
 * Atomarer Text-Schreibzugriff (N20): Inhalt geht erst in eine `.tmp`-Datei
 * im selben Verzeichnis, dann ersetzt ein `renameTo` das Ziel. Auf Android
 * (Linux) ist `rename` atomar — ein Abbruch dazwischen hinterlässt also
 * höchstens die alte oder die neue Datei, nie eine halb geschriebene.
 * Android-frei (nur `java.io.File`), damit unit-testbar.
 */
object AtomicWrites {

    fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        try {
            if (!tmp.renameTo(file)) {
                // Fallback: nicht atomar, aber korrekt. Für ein Verzeichnis
                // wirft writeText wie gewohnt (IOException → Fehler wird
                // gemeldet), statt das Verzeichnis still zu ersetzen.
                file.writeText(text)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
