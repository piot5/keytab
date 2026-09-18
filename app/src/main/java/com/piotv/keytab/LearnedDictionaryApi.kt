package com.piotv.keytab

import com.piotv.keytab.ime.SuggestionEngine
import java.util.UUID

/**
 * Externe Aufräum-API für das gelernte Benutzer-Wörterbuch (user_dict).
 *
 * Zuständigkeit (kein Basis-Korpus):
 * - Lesen, vorbereiten und kontrolliert Ändern des gelernten Bereichs.
 * - Kein Ersatz des Basiswortschatzes, kein unkontrolliertes Massenlöschen,
 *   kein automatisches Hochzählen von Gewichten durch externes Programm.
 *
 * Stand: 2026-09-18. V1-Entwurf.
 */
object LearnedDictionaryApi {

    private const val MAX_WORD_LEN = 32
    private const val MAX_ADD_PER_COMMIT = 200
    private const val MAX_DELETE_PER_COMMIT = 500

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

    /** Einzelnachschau: erst gelernte Einzelwörter, dann Bigramme; null wenn unbekannt. */
    fun lookup(store: SuggestionEngine, word: String): LearnedEntry? {
        store.userFreq[word]?.let { return LearnedEntry(word, it, isBigramPair = false) }
        store.bigrams[word]?.let { return LearnedEntry(word, it, isBigramPair = true) }
        return null
    }

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

    fun addWord(store: SuggestionEngine, op: CleanupOperation.Add): CleanupResult {
        val preview = batchPreview(store, emptyList(), listOf(op))
        return applyPreview(store, preview, confirm = true)
    }

    fun removeWord(store: SuggestionEngine, op: CleanupOperation.Delete): CleanupResult {
        val preview = batchPreview(store, listOf(op), emptyList())
        return applyPreview(store, preview, confirm = true)
    }

    // ---------- Validierungshilfen ----------

    private fun String.isNotLearnable(): Boolean {
        if (isBlank()) return true
        if (length < 2 || length > MAX_WORD_LEN) return true
        if (!all { it.isLetter() || it == ' ' }) return true
        return false
    }

    /** Artefakt-Heuristik für externes Aufräumen. Keine Grundlage für automatisches Sweeping. */
    fun looksLikeArtifact(word: String): Boolean {
        if (word.length < 3) return false
        val lower = word.lowercase()
        if (word.contains("und") && !word.contains(" ")) return true
        val fragments = listOf(
            "clebwerte", "clearbewerte", "shinstallire", "actionok", "klickba",
            "shweiterpero", "eingesuten", "shon übertroffenund", "clbewerte",
            "habenkann", "dichbrauche", "machdich", "unteruche",
            "precupearsepero", "drchscjeinender"
        )
        return fragments.any { lower.startsWith(it) || lower == it }
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
            if (op.expectedRevision != current) {
                rejected.add("DELETE revision conflict: ${op.word}")
                continue
            }
            if (!store.userFreq.containsKey(op.word) && !store.bigrams.containsKey(op.word)) {
                rejected.add("DELETE unknown: ${op.word}")
                continue
            }
            wouldDelete.add(op.word)
        }

        for (op in adds) {
            if (op.expectedRevision != current) {
                rejected.add("ADD revision conflict: ${op.word}")
                continue
            }
            if (op.word.length > MAX_WORD_LEN) {
                rejected.add("ADD too long: ${op.word}")
                continue
            }
            if (op.word.isNotLearnable()) {
                rejected.add("ADD invalid token: ${op.word}")
                continue
            }
            if (looksLikeArtifact(op.word)) {
                rejected.add("ADD invalid token (artifact): ${op.word}")
                continue
            }
            if (op.weight <= 0.0 || op.weight.isNaN() || op.weight.isInfinite()) {
                rejected.add("ADD invalid weight: ${op.word}")
                continue
            }
            if (!op.allowMerge && (store.userFreq.containsKey(op.word) || store.bigrams.containsKey(op.word))) {
                rejected.add("ADD already exists: ${op.word}")
                continue
            }
            if (wouldAdd.size >= MAX_ADD_PER_COMMIT) {
                rejected.add("ADD limit exceeded: ${op.word}")
                continue
            }
            if (wouldDelete.size + wouldAdd.size > MAX_DELETE_PER_COMMIT) {
                rejected.add("BATCH too large: ${op.word}")
                continue
            }
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


