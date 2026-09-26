package com.piotv.keytab.ime

import android.content.Context
import android.content.res.AssetManager
import com.piotv.keytab.Prefs

/**
 * Laden der Wortlisten-Corpora aus den Assets (ex-`WordPredictionManager.loadEngine`).
 *
 * Kapselt die reine Lese-/I/O-Logik rund um das Wörterbuch:
 *  - Frequenz-Asset zeilenweise parsen (`"wort frequenz"`, via [KeyboardLanguage.assetName]),
 *  - mit dem persistierten User-Wörterbuch ([Prefs.KEY_USER_DICT]) zusammenführen
 *    (serialize/restore der [SuggestionEngine]),
 *  - das Speichern des gelernten Wörterbuchs.
 *
 * Bewusst android-frei gehalten: der [AssetManager] wird nur als Parameter
 * durchgereicht, der [Context] dient ausschließlich dem [Prefs]-Zugriff. Es
 * passiert hier **kein** Async- und kein State-Handling — Executor, Reentrancy-
 * Guards und Engine-Zustand bleiben beim [WordPredictionManager].
 */
internal object WordCorpusLoader {

    /**
     * Baut die Engine für [language] aus Frequenz-Asset + gespeichertem
     * User-Wörterbuch.
     *
     * Fehlt das Asset (z. B. beim Test oder einem unvollständigen Build), wird
     * nicht geworfen: die Engine läuft dann nur mit den gelernten Wörtern.
     */
    fun load(context: Context, assets: AssetManager, language: KeyboardLanguage): SuggestionEngine {
        val loaded = SuggestionEngine(readFrequencyAsset(assets, language.assetName))
        val saved = Prefs.of(context).getString(Prefs.KEY_USER_DICT, null)
        if (saved != null) loaded.restoreUserDict(saved)
        return loaded
    }

    /** Liest `"wort frequenz"`-Zeilen; unparsbare Zeilen werden übersprungen. */
    private fun readFrequencyAsset(
        assets: AssetManager,
        assetName: String
    ): List<Pair<String, Int>> {
        val words = mutableListOf<Pair<String, Int>>()
        try {
            assets.open(assetName).bufferedReader().useLines { lines ->
                for (line in lines) {
                    val sp = line.trim().split(' ')
                    if (sp.size == 2) {
                        val f = sp[1].toIntOrNull() ?: continue
                        words.add(sp[0] to f)
                    }
                }
            }
        } catch (_: Exception) { /* Asset fehlt: nur gelernte Wörter */ }
        return words
    }

    /** Schreibt das serialisierte User-Wörterbuch (asynchron vom Aufrufer aufgerufen). */
    fun saveUserDict(context: Context, raw: String) {
        Prefs.of(context).edit().putString(Prefs.KEY_USER_DICT, raw).apply()
    }
}
