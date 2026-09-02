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
    /** Sprachspezifische Long-Press-Akzente + Umlaute pro Basistaste. */
    val extras: Map<Char, List<String>>
) {
    /** Kombiniert die Akzente mit dem commonen Interpunktions-Set. */
    fun letterExtras(basePunct: Map<Char, List<String>>): Map<Char, List<String>> {
        val out = HashMap(basePunct)
        for ((k, v) in extras) {
            val merged = (out[k] ?: emptyList()) + v.filterNot { out[k]?.contains(it) == true }
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
            acc('A', "Ä", "À", "Á", "Â", "Ã", "Å", "Æ"),
            acc('c', "ç"), acc('C', "Ç"),
            acc('e', "é", "è", "ê", "ë", "ē"),
            acc('E', "É", "È", "Ê", "Ë", "Ē"),
            acc('i', "í", "ì", "ï", "î", "ī"),
            acc('I', "Í", "Ì", "Ï", "Î", "Ī"),
            acc('n', "ñ", "ń"), acc('N', "Ñ", "Ń"),
            acc('o', "ö", "ó", "ò", "ô", "õ", "ø", "œ", "ō"),
            acc('O', "Ö", "Ó", "Ò", "Ô", "Õ", "Ø", "Œ", "Ō"),
            acc('s', "ß", "š", "ś"), acc('S', "ẞ", "Š", "Ś"),
            acc('u', "ü", "ú", "ù", "û", "ū"),
            acc('U', "Ü", "Ú", "Ù", "Û", "Ū")
        )
    )
val en = KeyboardLanguage(
        code = "en", displayName = "English", assetName = "en_freq_top6000.txt",
        extras = mapOf(
            acc('a', "à", "á", "â", "ä", "æ", "ã", "å"),
            acc('A', "À", "Á", "Â", "Ä", "Æ", "Ã", "Å"),
            acc('c', "ç"), acc('C', "Ç"),
            acc('e', "é", "è", "ê", "ë", "ē"),
            acc('E', "É", "È", "Ê", "Ë", "Ē"),
            acc('i', "í", "ì", "ï", "î", "ī"),
            acc('I', "Í", "Ì", "Ï", "Î", "Ī"),
            acc('n', "ñ", "ń"), acc('N', "Ñ", "Ń"),
            acc('o', "ó", "ò", "ô", "õ", "ö", "ø", "œ", "ō"),
            acc('O', "Ó", "Ò", "Ô", "Õ", "Ö", "Ø", "Œ", "Ō"),
            acc('s', "š", "ś"), acc('S', "Š", "Ś"),
            acc('u', "ú", "ù", "û", "ü", "ū"),
            acc('U', "Ú", "Ù", "Û", "Ü", "Ū")
        )
    )
val es = KeyboardLanguage(
        code = "es", displayName = "Español", assetName = "es_freq_top6000.txt",
        extras = mapOf(
            acc('a', "á", "à", "ä", "â"), acc('A', "Á", "À", "Ä", "Â"),
            acc('c', "ç"), acc('C', "Ç"),
            acc('e', "é", "è", "ë", "ê"), acc('E', "É", "È", "Ë", "Ê"),
            acc('i', "í", "ì", "ï", "î"), acc('I', "Í", "Ì", "Ï", "Î"),
            acc('n', "ñ", "ń"), acc('N', "Ñ", "Ń"),
            acc('o', "ó", "ò", "ô", "ö"), acc('O', "Ó", "Ò", "Ô", "Ö"),
            acc('u', "ú", "ù", "ü", "û"), acc('U', "Ú", "Ù", "Ü", "Û")
        )
    )

    val fr = KeyboardLanguage(
        code = "fr", displayName = "Français", assetName = "fr_freq_top6000.txt",
        extras = mapOf(
            acc('a', "à", "â", "ä", "æ"), acc('A', "À", "Â", "Ä", "Æ"),
            acc('c', "ç"), acc('C', "Ç"),
            acc('e', "é", "è", "ê", "ë", "ē"), acc('E', "É", "È", "Ê", "Ë", "Ē"),
            acc('i', "î", "ï", "í", "ì"), acc('I', "Î", "Ï", "Í", "Ì"),
            acc('o', "ô", "ö", "œ", "ó", "ò"), acc('O', "Ô", "Ö", "Œ", "Ó", "Ò"),
            acc('u', "ù", "û", "ü", "ú"), acc('U', "Ù", "Û", "Ü", "Ú")
        )
    )
val it = KeyboardLanguage(
        code = "it", displayName = "Italiano", assetName = "it_freq_top6000.txt",
        extras = mapOf(
            acc('a', "à", "á", "ä", "â"), acc('A', "À", "Á", "Ä", "Â"),
            acc('e', "é", "è", "ê", "ë"), acc('E', "É", "È", "Ê", "Ë"),
            acc('i', "ì", "í", "î", "ï"), acc('I', "Ì", "Í", "Î", "Ï"),
            acc('o', "ó", "ò", "ô", "ö"), acc('O', "Ó", "Ò", "Ô", "Ö"),
            acc('u', "ù", "ú", "û", "ü"), acc('U', "Ù", "Ú", "Û", "Ü")
        )
    )

    val pt = KeyboardLanguage(
        code = "pt", displayName = "Português", assetName = "pt_freq_top6000.txt",
        extras = mapOf(
            acc('a', "á", "à", "ã", "â", "ä"), acc('A', "Á", "À", "Ã", "Â", "Ä"),
            acc('c', "ç"), acc('C', "Ç"),
            acc('e', "é", "ê", "è", "ë", "ē"), acc('E', "É", "Ê", "È", "Ë", "Ē"),
            acc('i', "í", "ì", "î", "ï"), acc('I', "Í", "Ì", "Î", "Ï"),
            acc('o', "ó", "õ", "ô", "ò", "ö", "ø"), acc('O', "Ó", "Õ", "Ô", "Ò", "Ö", "Ø"),
            acc('u', "ú", "ù", "û", "ü"), acc('U', "Ú", "Ù", "Û", "Ü")
        )
    )

    val nl = KeyboardLanguage(
        code = "nl", displayName = "Nederlands", assetName = "nl_freq_top6000.txt",
        extras = mapOf(
            acc('a', "à", "á", "â", "ä", "ã"), acc('A', "À", "Á", "Â", "Ä", "Ã"),
            acc('c', "ç"), acc('C', "Ç"),
            acc('e', "é", "è", "ê", "ë", "ē"), acc('E', "É", "È", "Ê", "Ë", "Ē"),
            acc('i', "í", "ì", "ï", "î"), acc('I', "Í", "Ì", "Ï", "Î"),
            acc('o', "ó", "ò", "ô", "ö", "õ"), acc('O', "Ó", "Ò", "Ô", "Ö", "Õ"),
            acc('u', "ú", "ù", "û", "ü"), acc('U', "Ú", "Ù", "Û", "Ü")
        )
    )
fun byCode(code: String?): KeyboardLanguage = all.firstOrNull { it.code == code } ?: de

    /** Reines Interpunktions-Set (sprachunabhängig), Basis für alle Sprachen. */
    val basePunctuation: Map<Char, List<String>> = mapOf(
        ',' to listOf("&", "%", "+", "\"", "-", ":", "'", "@", ";", "/", "(", ")", "#", "!", "?"),
        'r' to listOf(".", ","), 't' to listOf("?", "!"), 'z' to listOf("!"),
        'p' to listOf("/", "\\", "|"), 'f' to listOf("@", "#", "&"),
        'g' to listOf("(", "[", "{"), 'h' to listOf(")", "]", "}"),
        'j' to listOf(":", ";"), 'k' to listOf(";", ":"),
        'l' to listOf(",", "\"", "'"), 'm' to listOf("&", "%", "*"),
        'v' to listOf("\""), 'b' to listOf("'"), 'x' to listOf("+", "-", "="),
        'w' to listOf("-", "_"), 'y' to listOf("#", "$", "€"), 'd' to listOf("_")
    )
}