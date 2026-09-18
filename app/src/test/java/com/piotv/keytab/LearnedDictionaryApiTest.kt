package com.piotv.keytab

import com.piotv.keytab.ime.SuggestionEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests für die externe Aufräum-API (LearnedDictionaryApi).
 *
 * Fokus:
 * - Lesen, vorbereiten, kontrolliert Ändern des gelernten Bereichs.
 * - Preview vor Commit, Revisionssicherheit, Validierung.
 * - Artefakt-Heuristik als Diagnosehilfe, nicht als automatisches Sweeping.
 */
class LearnedDictionaryApiTest {

    private fun engine(): SuggestionEngine = SuggestionEngine(
        listOf(
            "ich" to 5890279,
            "das" to 3122198,
            "und" to 2900000,
            "wort" to 150000,
            "haus" to 200000
        )
    )

    @Test
    fun stats_zeigt_learnarea() {
        val e = engine()
        e.learn(null, "termux")
        e.learn("das", "haus")
        val s = LearnedDictionaryApi.stats(e)
        assertEquals(1, s.userWordCount)
        assertEquals(1, s.bigramCount)
        assertTrue(s.revision >= 0)
    }

    @Test
    fun listAll_enthält_lernwörter_und_bigramme() {
        val e = engine()
        e.learn(null, "termux")
        e.learn("das", "haus")
        val list = LearnedDictionaryApi.listAll(e)
        assertTrue(list.any { it.word == "termux" && !it.isBigramPair })
        assertTrue(list.any { it.word == "das haus" && it.isBigramPair })
    }


    @Test
    fun batchPreview_lehnt_fehlerhafte_einträge_ab() {
        val e = engine()
        e.learn(null, "termux")

        val preview = LearnedDictionaryApi.batchPreview(
            e,
            listOf(
                LearnedDictionaryApi.CleanupOperation.Delete("nichtda", e.revision),
                LearnedDictionaryApi.CleanupOperation.Delete("termux", e.revision)
            ),
            listOf(
                LearnedDictionaryApi.CleanupOperation.Add("ok", e.revision),
                LearnedDictionaryApi.CleanupOperation.Add("clebwerte", e.revision, weight = 0.01)
            )
        )

        assertTrue(preview.rejections.any { it.startsWith("DELETE unknown") })
        assertFalse(preview.wouldDelete.contains("nichtda"))
        assertTrue(preview.wouldDelete.contains("termux"))
        assertTrue(preview.wouldAdd.any { it.first == "ok" })
        assertTrue(preview.rejections.any { it.startsWith("ADD invalid token") })
        assertFalse(preview.wouldAdd.any { it.first == "clebwerte" })
    }

    @Test
    fun batchPreview_blockt_revisionskonflikt() {
        val e = engine()
        val preview = LearnedDictionaryApi.batchPreview(
            e,
            listOf(LearnedDictionaryApi.CleanupOperation.Delete("termux", e.revision + 1)),
            emptyList()
        )
        assertTrue(preview.rejections.any { it.startsWith("DELETE revision conflict") })
        assertFalse(preview.wouldChange)
    }

    @Test
    fun applyPreview_erfordert_bestätigung() {
        val e = engine()
        e.learn(null, "termuxx")
        val preview = LearnedDictionaryApi.batchPreview(
            e,
            listOf(LearnedDictionaryApi.CleanupOperation.Delete("termuxx", e.revision)),
            emptyList()
        )
        val rejected = LearnedDictionaryApi.applyPreview(e, preview, confirm = false)
        assertTrue(rejected is LearnedDictionaryApi.CleanupResult.Rejected)
        assertTrue(e.userFreq.containsKey("termuxx"))
    }


    @Test
    fun looksLikeArtifact_klassifiziert_bereits_bekannte_rückstände() {
        assertTrue(LearnedDictionaryApi.looksLikeArtifact("clebwerte"))
        assertTrue(LearnedDictionaryApi.looksLikeArtifact("clearbewerte"))
        assertTrue(LearnedDictionaryApi.looksLikeArtifact("shinstallire"))
        assertTrue(LearnedDictionaryApi.looksLikeArtifact("actionok"))
        assertTrue(LearnedDictionaryApi.looksLikeArtifact("klickba"))
        assertTrue(LearnedDictionaryApi.looksLikeArtifact("shweiterpero"))
        assertTrue(LearnedDictionaryApi.looksLikeArtifact("eingesuten"))
        assertTrue(LearnedDictionaryApi.looksLikeArtifact("habenkann"))
        assertTrue(LearnedDictionaryApi.looksLikeArtifact("machdich"))
        assertTrue(LearnedDictionaryApi.looksLikeArtifact("unteruche"))
    }

    @Test
    fun looksLikeArtifact_ist_konservativ() {
        assertFalse(LearnedDictionaryApi.looksLikeArtifact("termux"))
        assertFalse(LearnedDictionaryApi.looksLikeArtifact("wörterbuch"))
        assertFalse(LearnedDictionaryApi.looksLikeArtifact("api"))
        assertFalse(LearnedDictionaryApi.looksLikeArtifact("projekt"))
        assertFalse(LearnedDictionaryApi.looksLikeArtifact("edit"))
        assertFalse(LearnedDictionaryApi.looksLikeArtifact("script"))
        assertFalse(LearnedDictionaryApi.looksLikeArtifact("code"))
        assertFalse(LearnedDictionaryApi.looksLikeArtifact("editor"))
        assertFalse(LearnedDictionaryApi.looksLikeArtifact("terminal"))
    }

    @Test
    fun artifact_arbeit_als_vollständiger_durchlauf() {
        val e = engine()
        e.learn(null, "clebwerte")
        e.learn(null, "shinstallire")
        e.learn(null, "termux")

        val list = LearnedDictionaryApi.listAll(e)
        val artifacts = list.filter { LearnedDictionaryApi.looksLikeArtifact(it.word) }
        val keeps = list.filter { !LearnedDictionaryApi.looksLikeArtifact(it.word) }

        assertEquals(2, artifacts.size)
        assertTrue(keeps.any { it.word == "termux" })

        val preview = LearnedDictionaryApi.batchPreview(
            e,
            artifacts.map { LearnedDictionaryApi.CleanupOperation.Delete(it.word, e.revision) },
            emptyList()
        )
        assertTrue(preview.wouldChange)
        assertEquals(2, preview.wouldDelete.size)
        assertTrue(preview.rejections.isEmpty())

        val result = LearnedDictionaryApi.applyPreview(e, preview, confirm = true)
        assertTrue(result is LearnedDictionaryApi.CleanupResult.Ok)
        assertEquals(2, (result as LearnedDictionaryApi.CleanupResult.Ok).deletedCount)
        assertFalse(e.userFreq.containsKey("clebwerte"))
        assertFalse(e.userFreq.containsKey("shinstallire"))
        assertTrue(e.userFreq.containsKey("termux"))
    }
}
