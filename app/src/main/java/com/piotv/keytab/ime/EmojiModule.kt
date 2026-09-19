package com.piotv.keytab.ime

/**
 * Reine Emoji-Vorschlags-Logik (Android-frei, JUnit-testbar) — v0.11 optional.
 *
 * **Feature:** Optional (Einstellungen, Standard **aus**) ergänzt die Vorschlags-Leiste
 * thematisch passende Emojis zu den Wortvorschlägen. Das Mapping ist ein kleiner,
 * eingebetteter Keyword→Emoji-Katalog (de/en) — kein Netz, kein Modell, keine
 * neue Permission. Der passende Keyword-Index wird aus dem getippten Teilwort
 * (bzw. beim Leerraum-Fall aus dem vorherigen Wort) per Substring-Match bestimmt.
 *
 * Platzierung: Emojis nehmen maximal [MAX_EMOJI] der 3 Vorschlags-Slots ein und
 * werden **hinten** angehängt — Wortschläge behalten immer mindestens einen Slot
 * (siehe SuggestionEngine.suggest).
 */
object EmojiModule {

    /** Maximaler Anteil der Vorschlags-Slots, den Emojis einnehmen dürfen. */
    const val MAX_EMOJI = 2

    /**
     * Keyword→Emoji-Katalog. Substring-Match (lowercase) — Reihenfolge im
     * [LinkedHashMap] determiniert die Emoji-Reihenfolge innerhalb eines Themas.
     */
    private val KEYWORDS: Map<String, List<String>> = linkedMapOf(
        "lach" to listOf("😂", "😆"),
        "laugh" to listOf("😂", "😆"),
        "lol" to listOf("😂"),
        "witz" to listOf("😂"),
        "liebe" to listOf("❤️", "💕"),
        "love" to listOf("❤️", "💕"),
        "herz" to listOf("❤️", "💕"),
        "heart" to listOf("❤️", "💕"),
        "traur" to listOf("😢"),
        "sad" to listOf("😢"),
        "wein" to listOf("😢"),
        "freu" to listOf("😄"),
        "stern" to listOf("⭐"),
        "star" to listOf("⭐"),
        "feuer" to listOf("🔥"),
        "fire" to listOf("🔥"),
        "bug" to listOf("🐛"),
        "fehler" to listOf("🐛"),
        "error" to listOf("🐛"),
        "test" to listOf("✅"),
        "build" to listOf("🚀"),
        "rakete" to listOf("🚀"),
        "rocket" to listOf("🚀"),
        "code" to listOf("💻"),
        "terminal" to listOf("🖥️"),
        "shell" to listOf("🖥️"),
        "kaffee" to listOf("☕"),
        "coffee" to listOf("☕"),
        "zeit" to listOf("⏰"),
        "uhr" to listOf("⏰"),
        "time" to listOf("⏰"),
        "wichtig" to listOf("❗"),
        "achtung" to listOf("⚠️"),
        "warn" to listOf("⚠️"),
        "frage" to listOf("❓"),
        "question" to listOf("❓"),
        "daumen" to listOf("👍"),
        "thumb" to listOf("👍"),
        "ok" to listOf("👍", "✅"),
        "gut" to listOf("👍"),
        "check" to listOf("✅"),
        "klatsch" to listOf("👏"),
        "musik" to listOf("🎵"),
        "sonne" to listOf("☀️"),
        "sun" to listOf("☀️"),
        "regen" to listOf("🌧️"),
        "rain" to listOf("🌧️"),
        "notiz" to listOf("📝"),
        "note" to listOf("📝"),
        "datei" to listOf("📁"),
        "file" to listOf("📁")
    )

    /**
     * Emojis für [word] (Substring-Match, lowercase) — höchstens [max],
     * dedupliziert in Katalog-Reihenfolge, deterministisch (kein Zufall).
     */
    fun emojisFor(word: String, max: Int = MAX_EMOJI): List<String> {
        if (word.isBlank() || max <= 0) return emptyList()
        val w = word.lowercase()
        val out = LinkedHashSet<String>()
        for ((kw, emojis) in KEYWORDS) {
            if (kw !in w) continue
            for (e in emojis) if (out.add(e) && out.size >= max) return out.toList()
        }
        return out.toList()
    }
}
