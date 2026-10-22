package com.piotv.keytab.ime

/**
 * Modularer Sprachsupport für KeyTab.
 *
 * Jede Sprache ist ein [KeyboardLanguage] mit einem lateinischen Corporate-Asset
 * für die Wortvorhersage und sprachspezifischen Long-Press-Akzenten.
 * Bewusst auf **lateinische Schriften** beschränkt (siehe [Languages]).
 */
data class KeyboardLanguage(
    /** Kurzcode (z. B. "de") – Präferenz-Wert und Asset-Namensbestandteil. */
    val code: String,
    /** Anzeigename (z. B. "Deutsch"). */
    val displayName: String,
    /** Asset-Datei im assets/-Ordner (Format "wort zahl" pro Zeile). */
    val assetName: String,
    /**
     * Sprachspezifische Long-Press-Akzente + Umlaute pro Basistaste.
     *
     * Nur Basis-Tasten (Kleinbuchstaben): die Reihe gilt in beiden Schreibweisen
     * unverändert (siehe [LetterCaseRenderer.extrasFor]) — Großbuchstaben-Einträge
     * wären tote Daten.
     */
    val extras: Map<Char, List<String>>
) {
    /** Kombiniert die Akzente mit dem commonen Interpunktions-Set. */
    fun letterExtras(basePunct: Map<Char, List<String>>): Map<Char, List<String>> {
        val out = HashMap(basePunct)
        for ((k, v) in extras) {
            val merged = (out[k]).orEmpty() + v.filterNot { out[k]?.contains(it) == true }
            out[k] = merged
        }
        return out
    }
}

object Languages {

    /** Nur lateinische Schriften – keine nichtlateinischen Alphabete. */
    val all: List<KeyboardLanguage> by lazy {
        listOf(
            de, en, es, fr, it, pt, nl
        )
    }

    private fun acc(c: Char, vararg s: String) = c to s.toList()

    val de = KeyboardLanguage(
        code = "de", displayName = "Deutsch", assetName = "de_freq_top6000.txt",
        extras = mapOf(
            acc('a', "ä", "à", "á", "â", "ã", "å", "æ"),
            acc('c', "ç"),
            acc('e', "é", "è", "ê", "ë", "ē"),
            acc('i', "í", "ì", "ï", "î", "ī"),
            acc('n', "ñ", "ń"),
            acc('o', "ö", "ó", "ò", "ô", "õ", "ø", "œ", "ō"),
            acc('s', "ß", "š", "ś"),
            acc('u', "ü", "ú", "ù", "û", "ū")
        )
    )
val en = KeyboardLanguage(
        code = "en", displayName = "English", assetName = "en_freq_top6000.txt",
        extras = mapOf(
            acc('a', "à", "á", "â", "ä", "æ", "ã", "å"),
            acc('c', "ç"),
            acc('e', "é", "è", "ê", "ë", "ē"),
            acc('i', "í", "ì", "ï", "î", "ī"),
            acc('n', "ñ", "ń"),
            acc('o', "ó", "ò", "ô", "õ", "ö", "ø", "œ", "ō"),
            acc('s', "š", "ś"),
            acc('u', "ú", "ù", "û", "ü", "ū")
        )
    )
val es = KeyboardLanguage(
        code = "es", displayName = "Español", assetName = "es_freq_top6000.txt",
        extras = mapOf(
            acc('a', "á", "à", "ä", "â"),
            acc('c', "ç"),
            acc('e', "é", "è", "ë", "ê"),
            acc('i', "í", "ì", "ï", "î"),
            acc('n', "ñ", "ń"),
            acc('o', "ó", "ò", "ô", "ö"),
            acc('u', "ú", "ù", "ü", "û")
        )
    )

    val fr = KeyboardLanguage(
        code = "fr", displayName = "Français", assetName = "fr_freq_top6000.txt",
        extras = mapOf(
            acc('a', "à", "â", "ä", "æ"),
            acc('c', "ç"),
            acc('e', "é", "è", "ê", "ë", "ē"),
            acc('i', "î", "ï", "í", "ì"),
            acc('o', "ô", "ö", "œ", "ó", "ò"),
            acc('u', "ù", "û", "ü", "ú")
        )
    )
val it = KeyboardLanguage(
        code = "it", displayName = "Italiano", assetName = "it_freq_top6000.txt",
        extras = mapOf(
            acc('a', "à", "á", "ä", "â"),
            acc('e', "é", "è", "ê", "ë"),
            acc('i', "ì", "í", "î", "ï"),
            acc('o', "ó", "ò", "ô", "ö"),
            acc('u', "ù", "ú", "û", "ü")
        )
    )

    val pt = KeyboardLanguage(
        code = "pt", displayName = "Português", assetName = "pt_freq_top6000.txt",
        extras = mapOf(
            acc('a', "á", "à", "ã", "â", "ä"),
            acc('c', "ç"),
            acc('e', "é", "ê", "è", "ë", "ē"),
            acc('i', "í", "ì", "î", "ï"),
            acc('o', "ó", "õ", "ô", "ò", "ö", "ø"),
            acc('u', "ú", "ù", "û", "ü")
        )
    )

    val nl = KeyboardLanguage(
        code = "nl", displayName = "Nederlands", assetName = "nl_freq_top6000.txt",
        extras = mapOf(
            acc('a', "à", "á", "â", "ä", "ã"),
            acc('c', "ç"),
            acc('e', "é", "è", "ê", "ë", "ē"),
            acc('i', "í", "ì", "ï", "î"),
            acc('o', "ó", "ò", "ô", "ö", "õ"),
            acc('u', "ú", "ù", "û", "ü")
        )
    )
fun byCode(code: String?): KeyboardLanguage = all.firstOrNull { it.code == code } ?: de

    /**
     * Sprachunabhängiges Zeichen-Set, Basis für alle Sprachen.
     *
     * Enthält Interpunktion für Tasten ohne sprachspezifische Akzente — und die
     * beiden Zeichen, die auf der deutschen Standardtastatur per AltGr erreichbar
     * sind (`@` auf `q`, `µ` auf `m`). Damit hat jede Buchstabentaste außer den
     * Umlaut-Tasten (`ä`/`ö`/`ü` tragen ihren Akzent selbst) eine Long-Press-Reihe.
     */
    val basePunctuation: Map<Char, List<String>> = mapOf(
        ',' to listOf("&", "%", "+", "\"", "-", ":", "'", "@", ";", "/", "(", ")", "#", "!", "?"),
        'r' to listOf(".", ","), 't' to listOf("?", "!"), 'z' to listOf("!"),
        'p' to listOf("/", "\\", "|"), 'f' to listOf("@", "#", "&"),
        'g' to listOf("(", "[", "{"), 'h' to listOf(")", "]", "}"),
        'j' to listOf(":", ";"), 'k' to listOf(";", ":"),
        'l' to listOf(",", "\"", "'"), 'm' to listOf("µ", "&", "%", "*"),
        'q' to listOf("@", "#", "1"),
        'v' to listOf("\""), 'b' to listOf("'"), 'x' to listOf("+", "-", "="),
        'w' to listOf("-", "_"), 'y' to listOf("#", "$", "€"), 'd' to listOf("_")
    )
}
