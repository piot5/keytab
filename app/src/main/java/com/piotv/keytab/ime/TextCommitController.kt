package com.piotv.keytab.ime

import android.view.inputmethod.InputConnection

/**
 * Text-Eingabe-Orchestrierung (Phase 6, docs/REFACTORING_PLAN.md):
 * kapselt die Logik, die zuvor inline in [KeyTabImeService] lag:
 * - [commit]       – Text an aktives Ziel (Auto-Korrektur, Vorhersage-Buchführung)
 * - [commitToApp]  – direktes Einfügen ins App-Feld (Clipboard/Editor-Load)
 * - [deleteLastWord] – Wortlöschung + Vorhersage-Reset
 * Reine Delegation, keine View-Abhängigkeit → Verhalten unverändert.
 */
class TextCommitController(private val host: Host) {

    /** Vom Service bereitgestellte Abhängigkeiten (schmale Schnittstelle). */
    interface Host {
        fun haptic()
        val inputRouter: InputRouter?
        val predictionManager: WordPredictionManager?
        fun consumeSingleShift()
        fun updateSuggestions()
    }

    fun commit(text: String) {
        host.haptic()
        // Aktive Autokorrektur (v0.9.1): Space nach unbekanntem Wort → Wort
        // ersetzen, wenn ein klarer Wörterbuch-Kandidat existiert (nur App-Felder;
        // Editor/Terminal buchen ihren Text selbst).
        if (text == " " && host.inputRouter?.isApp == true &&
            host.predictionManager?.autoCorrectBeforeSpace() == true
        ) {
            host.consumeSingleShift()
            host.updateSuggestions()
            return
        }
        host.inputRouter?.insert(text)
        // Wortvorhersage-Buchführung: Buchstaben sammeln, Abschluss lernen
        val pm = host.predictionManager
        if (pm != null) {
            if (text.length == 1 && text[0].isLetter()) {
                pm.onCharacter(text)
            } else if (text == " " || text == "." || text == "\n") {
                pm.onWordCompleted()
            } else if (pm.currentTypedWord.isNotEmpty() && !text[0].isLetter()) {
                pm.onWordCompleted()
            }
        }
        host.consumeSingleShift()
        host.updateSuggestions()
    }

    /**
     * Clipboard-Einfügen IMMER direkt ins Zielfeld (InputConnection) – der Editor
     * (Notes-Tab) fängt die Eingabe nicht ab. Nur der Editor-Load befüllt den Editor.
     */
    fun commitToApp(text: String, connection: InputConnection?) {
        host.haptic()
        runCatching { connection?.commitText(text, 1) }
        host.consumeSingleShift()
    }

    fun deleteLastWord() {
        host.haptic()
        host.inputRouter?.deleteWord()
        host.predictionManager?.reset()
        host.updateSuggestions()
    }
}
