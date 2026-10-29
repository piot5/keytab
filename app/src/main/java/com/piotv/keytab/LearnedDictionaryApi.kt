package com.piotv.keytab

import com.piotv.keytab.ime.SuggestionEngine
import java.util.UUID

/**
 * Aufräum-API für das gelernte Benutzer-Wörterbuch (`user_dict`) — **in-process,
 * bewusst nicht nach außen exponiert**.
 *
 * Zuständigkeit (kein Basis-Korpus):
 * - Lesen, vorbereiten und kontrolliert Ändern des gelernten Bereichs.
 * - Kein Ersatz des Basiswortschatzes, kein unkontrolliertes Massenlöschen,
 *   kein automatisches Hochzählen von Gewichten durch externes Programm.
 *
 * **Entscheidung 2026-09-25:** Die API bleibt `internal`, wird aber durch den
 * In-App-Wartungsweg in den Einstellungen verwendet
 * (`MainActivity.cleanupLearnedDictionary`). Ein externer Transportweg
 * (`ContentProvider`/exportierter Service) bleibt bewusst abgelehnt: KeyTab ist
 * eine Offline-Tastatur ohne Netzwerk-Permission; ein exportiertes Tor wäre ein
 * neuer Angriffs-/Abflusspfad.
 */
internal object LearnedDictionaryApi {

    private const val MAX_WORD_LEN = 32
    private const val MAX_ADD_PER_COMMIT = 200
    /** Gesamtgröße eines Batches (Deletes + Adds) — bewusst getrennt vom Delete-Limit. */
    private const val MAX_BATCH_PER_COMMIT = 500

    // ---------- Model ----------

    data class LearnedEntry(
        val word: String,
        val weight: Double,
        val isBigramPair: Boolean = false
    )

    data class Stats(
        val userWordCount: Int,
        val bigramCount: Int,
        val revision: Long
    )

    sealed class CleanupOperation {
        data class Delete(val word: String, val expectedRevision: Long) : CleanupOperation()
        data class Add(
            val word: String,
            val expectedRevision: Long,
            val weight: Double = 0.05,
            val allowMerge: Boolean = true
        ) : CleanupOperation()
    }

    data class BatchPreview(
        val previewId: String,
        val expectedRevision: Long,
        val wouldDelete: List<String>,
        val wouldAdd: List<Pair<String, Double>>,
        val rejections: List<String>
    ) {
        val wouldChange: Boolean get() = wouldDelete.isNotEmpty() || wouldAdd.isNotEmpty()
    }

    sealed class CleanupResult {
        data class Ok(val revision: Long, val deletedCount: Int, val addedCount: Int) : CleanupResult()
        data class Rejected(val reason: String, val currentRevision: Long) : CleanupResult()
    }

    // ---------- Lesen ----------

    /** Zähler + Revision des gelernten Bereichs. */
    fun stats(store: SuggestionEngine): Stats {
        val userWords = store.userFreq.keys.count { store.baseScore(it) <= 0.0 }
        return Stats(userWordCount = userWords, bigramCount = store.bigrams.size,
            revision = store.revision)
    }

    /** Alle gelernten Einträge (Einzelwörter + Bigramme). */
    fun listAll(store: SuggestionEngine): List<LearnedEntry> =
        store.userFreq.map { (w, weight) -> LearnedEntry(w, weight, isBigramPair = false) } +
            store.bigrams.map { (k, weight) -> LearnedEntry(k, weight, isBigramPair = true) }

    // ---------- Schreibende Ausführung ----------

    fun applyPreview(store: SuggestionEngine, preview: BatchPreview, confirm: Boolean): CleanupResult {
        if (!confirm) return CleanupResult.Rejected("preview not confirmed", preview.expectedRevision)
        if (store.revision != preview.expectedRevision)
            return CleanupResult.Rejected("revision conflict at apply time", store.revision)

        var deleted = 0
        var added = 0
        for (w in preview.wouldDelete) {
            store.userFreq.remove(w)
            store.bigrams.remove(w)
            deleted++
        }
        for ((w, weight) in preview.wouldAdd) {
            store.userFreq[w] = weight
            added++
        }
        store.incrementRevision()
        return CleanupResult.Ok(store.revision, deleted, added)
    }

    // ---------- Validierungshilfen ----------

    private fun String.isNotLearnable(): Boolean {
        if (isBlank()) return true
        if (length < 2 || length > MAX_WORD_LEN) return true
        if (!all { it.isLetter() || it == ' ' }) return true
        return false
    }

    /**
     * Artefakt-Heuristik für externes Aufräumen. Keine Grundlage für automatisches
     * Sweeping.
     *
     * Bewusst **konservativ**: nur explizit bekannte Rückstände (Fragmentliste)
     * werden erkannt. Die frühere Regel „enthält ‚und'“ wurde entfernt (v0.20) —
     * sie traf fast jedes echte deutsche Wort (Stunde, gesund, Grund, wunderbar,
     * Kunde, hundert, Bundesland …) und hätte den Bestand nach einer Bestätigung
     * irreversibel gelöscht.
     */
    fun looksLikeArtifact(word: String): Boolean {
        if (word.length < 3) return false
        val lower = word.lowercase()
        val fragments = listOf(
            "clebwerte", "clearbewerte", "shinstallire", "actionok", "klickba",
            "shweiterpero", "eingesuten", "shon übertroffenund", "clbewerte",
            "habenkann", "dichbrauche", "machdich", "unteruche",
            "precupearsepero", "drchscjeinender"
        )
        return fragments.any { lower.startsWith(it) || lower == it }
    }

    private fun deleteRejection(
        op: CleanupOperation.Delete,
        current: Long,
        store: SuggestionEngine
    ): String? {
        if (op.expectedRevision != current) return "DELETE revision conflict: ${op.word}"
        if (!store.userFreq.containsKey(op.word) && !store.bigrams.containsKey(op.word)) {
            return "DELETE unknown: ${op.word}"
        }
        return null
    }

    private fun addRejection(
        op: CleanupOperation.Add,
        current: Long,
        store: SuggestionEngine,
        wouldDelete: List<String>,
        wouldAdd: List<Pair<String, Double>>
    ): String? {
        if (op.expectedRevision != current) return "ADD revision conflict: ${op.word}"
        if (op.word.length > MAX_WORD_LEN) return "ADD too long: ${op.word}"
        if (op.word.isNotLearnable()) return "ADD invalid token: ${op.word}"
        if (looksLikeArtifact(op.word)) return "ADD invalid token (artifact): ${op.word}"
        if (op.weight <= 0.0 || op.weight.isNaN() || op.weight.isInfinite()) {
            return "ADD invalid weight: ${op.word}"
        }
        if (!op.allowMerge && (store.userFreq.containsKey(op.word) || store.bigrams.containsKey(op.word))) {
            return "ADD already exists: ${op.word}"
        }
        if (wouldAdd.size >= MAX_ADD_PER_COMMIT) return "ADD limit exceeded: ${op.word}"
        if (wouldDelete.size + wouldAdd.size > MAX_BATCH_PER_COMMIT) return "BATCH too large: ${op.word}"
        return null
    }

    fun batchPreview(
        store: SuggestionEngine,
        deletes: List<CleanupOperation.Delete>,
        adds: List<CleanupOperation.Add>
    ): BatchPreview {
        val current = store.revision
        val rejected = mutableListOf<String>()
        val wouldDelete = mutableListOf<String>()
        val wouldAdd = mutableListOf<Pair<String, Double>>()

        for (op in deletes) {
            val reason = deleteRejection(op, current, store)
            if (reason != null) { rejected.add(reason); continue }
            wouldDelete.add(op.word)
        }

        for (op in adds) {
            val reason = addRejection(op, current, store, wouldDelete, wouldAdd)
            if (reason != null) { rejected.add(reason); continue }
            wouldAdd.add(op.word to op.weight)
        }

        return BatchPreview(
            previewId = UUID.randomUUID().toString(),
            expectedRevision = current,
            wouldDelete = wouldDelete,
            wouldAdd = wouldAdd,
            rejections = rejected
        )
    }
}


