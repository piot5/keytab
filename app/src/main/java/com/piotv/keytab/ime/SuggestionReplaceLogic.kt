package com.piotv.keytab.ime

import java.text.Normalizer

/**
 * Reine Entscheidungs-Logik für das Ersetzen des getippten Worts beim Übernehmen
 * eines Vorschlags / bei der Auto-Korrektur (Android-frei, JUnit-testbar).
 *
 * **Warum das nötig war:** Die Vorschlags-Übernahme entschied früher allein anhand
 * von `before.takeLast(n).equals(typed, ignoreCase = true)`, ob das getippte Wort
 * vor dem Cursor steht. Schlug der Vergleich nur *scheinbar* fehl (Unicode-
 * Normalisierung, kombinierende Umlaute, abweichende Repräsentation im Feld),
 * wurde der Vorschlag **angehängt statt ersetzt** → der Text verdoppelte sich
 * („hauss“ → „hauss haus “). Umgekehrt konnte eine zu lockere Lösch-Prüfung
 * zusätzliche Zeichen entfernen → Text wurde zerstört.
 *
 * Konsequenz hier:
 *  - [sameWord] vergleicht **normalisiert (NFC)** und case-insensitiv.
 *  - [action] erlaubt „anhängen ohne löschen“ nur noch, wenn der gelesene Kontext
 *    **exakt** `typed.length` Zeichen lang ist UND wirklich ein **anderes** Wort
 *    ist (Cursor steht verifiziert woanders). Sonst wird ersetzt.
 *  - [deleteWorked] erkennt einen wirkungslosen `deleteSurroundingText` daran,
 *    dass sich der Kontext **nicht verändert** hat (statt an einer Gleichheits-
 *    Heuristik, die zu viel löschen konnte).
 */
object SuggestionReplaceLogic {

    /**
     * Zusätzlicher Lese-Kontext beim Prüfen „steht das getippte Wort am Cursor?“.
     * Nötig, weil NFC/NFD dieselbe Schreibweise unterschiedlich lang machen
     * („ä“ = 1 Zeichen in NFC, 2 in NFD) — ohne Puffer griffe die Prüfung daneben.
     */
    const val CONTEXT_PAD = 4

    /** Wie ein Vorschlag eingesetzt wird. */
    enum class Action {
        /** Kein getipptes Wort → einfach einfügen. */
        INSERT_PLAIN,

        /** Cursor steht nachweislich woanders → mit Trenner anfügen, nichts löschen. */
        INSERT_WITH_SEPARATOR,

        /** Getipptes Wort vor dem Cursor ersetzen (löschen + einfügen). */
        REPLACE_TYPED
    }

    /** Normalisierter, case-insensitiver Wortvergleich (NFC gegen Umlaut-Spaltung). */
    fun sameWord(a: String, b: String): Boolean =
        normalize(a).equals(normalize(b), ignoreCase = true)

    /** Wahr, wenn [text] (normalisiert) mit [suffix] endet. */
    fun endsWith(text: String, suffix: String): Boolean =
        text.length >= suffix.length && sameWord(text.takeLast(suffix.length), suffix)

    /**
     * **Roh**-Länge des getippten Worts im Feld — der kleinste Suffix von
     * [context] (in echten Zeichen), dessen Normalisierung [typed] ergibt, oder
     * `null`, wenn kein Suffix passt.
     *
     * Das ist die Lösch-Menge, **nicht** `typed.length`: bei NFD-Repräsentation
     * (kombinierende Umlaute) ist das Wort im Feld länger als das getippte Wort.
     * Mit `typed.length` würde zu wenig gelöscht → Text bliebe stehen/verdoppelt
     * sich (genau der gemeldete Bug).
     */
    fun rawWordLength(context: String, typed: String): Int? {
        if (typed.isEmpty()) return null
        val target = normalize(typed)
        var len = typed.length
        while (len <= context.length) {
            if (normalize(context.takeLast(len)).equals(target, ignoreCase = true)) return len
            len++
        }
        return null
    }

    /**
     * Entscheidet die Ersetzungs-Art.
     *
     * @param typed das getippte Wort (kann leer sein)
     * @param context lesbarer Text **vor** dem Cursor (leer = Feld nicht lesbar).
     *   Empfohlen: `typed.length + [CONTEXT_PAD]` Zeichen.
     */
    fun action(typed: String, context: String): Action {
        if (typed.isEmpty()) return Action.INSERT_PLAIN
        // Nicht lesbar: das getippte Wort steht am Cursor (der Nutzer hat es
        // gerade getippt) → ersetzen. NICHT anhängen, sonst Verdopplung.
        if (context.isEmpty()) return Action.REPLACE_TYPED
        // Wort steht (normalisierungs-tolerant) am Cursor → ersetzen.
        if (rawWordLength(context, typed) != null) return Action.REPLACE_TYPED
        // Kein passender Suffix, aber genug Kontext vorhanden → das Feld enthält
        // ein anderes Wort, der Cursor steht woanders: mit Trenner anhängen.
        return if (context.length >= typed.length) {
            Action.INSERT_WITH_SEPARATOR
        } else {
            Action.REPLACE_TYPED
        }
    }

    /**
     * Hat das Löschen gewirkt? Der Kontext vor dem Cursor muss sich verändert
     * haben — bleibt er identisch, hat das Feld `deleteSurroundingText`
     * ignoriert (WebView/Termux) und es muss über Key-Events gelöscht werden.
     */
    fun deleteWorked(before: String, after: String): Boolean = before != after

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFC)
}
