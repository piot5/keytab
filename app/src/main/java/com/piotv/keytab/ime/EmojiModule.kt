package com.piotv.keytab.ime

/**
 * Reiner Emoji-Katalog des ☺-Buttons in der Vorschlagsleiste.
 *
 * **Feature:** Der Katalog ist über den ☺-Button erreichbar: die Leiste blendet
 * seitenweise Emoji-Chips an ([PAGE_SIZE]) und über ▦ den ganzen Katalog als
 * Raster. Das Mapping ist ein kleiner, eingebetteter Keyword→Emoji-Katalog
 * (de/en) — kein Netz, kein Modell, keine neue Permission.
 *
 * Entfernt (2026-09-27): die *automatische* Emoji-Vorschlagsfunktion, die
 * thematisch passende Emojis an die Wortvorschläge anhängte. Grund: sie war
 * Default **aus**, brauchte also einen Settings-Schalter, und der Katalog
 * selbst ist über den Button ohnehin erreichbar — zwei Wege zu denselben
 * Emojis. `emojisFor` und `MAX_EMOJI` sind damit überflüssig; die
 * Keyword-Liste bleibt als Quelle des Katalogs bestehen.
 *
 * Android-frei, JUnit-testbar.
 */
object EmojiModule {

    /** Katalog-Browser: Emojis pro Seite in der Vorschlags-Leiste. */
    const val PAGE_SIZE = 5

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
        "file" to listOf("📁"),
        "ordner" to listOf("📂", "🗂️"),
        "folder" to listOf("📂", "🗂️"),
        "loeschen" to listOf("🗑️"),
        "delete" to listOf("🗑️"),
        // --- Erweitert 2026-09-27 (größerer Katalog) ---
        "gefeiert" to listOf("🎉", "🥳"),
        "feier" to listOf("🎉"),
        "party" to listOf("🎉", "🥳"),
        "geburtstag" to listOf("🎂"),
        "birthday" to listOf("🎂"),
        "essen" to listOf("🍎", "🍕"),
        "food" to listOf("🍎", "🍕"),
        "pizza" to listOf("🍕"),
        "fisch" to listOf("🐟"),
        "fish" to listOf("🐟"),
        "tier" to listOf("🐶", "🐱"),
        "dog" to listOf("🐶"),
        "katze" to listOf("🐱"),
        "cat" to listOf("🐱"),
        "blume" to listOf("🌸"),
        "flower" to listOf("🌸"),
        "baum" to listOf("🌳"),
        "tree" to listOf("🌳"),
        "mond" to listOf("🌙"),
        "moon" to listOf("🌙"),
        "nacht" to listOf("🌙"),
        "night" to listOf("🌙"),
        "schule" to listOf("📚", "🎓"),
        "school" to listOf("📚", "🎓"),
        "studium" to listOf("🎓"),
        "arbeit" to listOf("💼"),
        "work" to listOf("💼"),
        "buero" to listOf("💼"),
        "terminal" to listOf("🖥️", "⌨️"),
        "kommando" to listOf("⌨️"),
        "flagge" to listOf("🚩"),
        "flag" to listOf("🚩"),
        "ziel" to listOf("🎯"),
        "target" to listOf("🎯"),
        "gedanke" to listOf("💡"),
        "idea" to listOf("💡"),
        "warnung" to listOf("🚧"),
        "baustelle" to listOf("🚧"),
        "schlaf" to listOf("😴"),
        "sleep" to listOf("😴"),
        "limonade" to listOf("🥤"),
        "getraenk" to listOf("🥤"),
        "drink" to listOf("🥤"),
        "stadt" to listOf("🌃"),
        "city" to listOf("🌃"),
        "sport" to listOf("⚽"),
        "ball" to listOf("⚽"),
        "fahrrad" to listOf("🚲"),
        "bike" to listOf("🚲"),
        "flug" to listOf("✈️"),
        "reise" to listOf("✈️", "🧳"),
        "travel" to listOf("✈️", "🧳"),
        "medizin" to listOf("💊"),
        "pille" to listOf("💊"),
        "geld" to listOf("💰"),
        "money" to listOf("💰"),
        "geschenk" to listOf("🎁"),
        "gift" to listOf("🎁"),
        "schnell" to listOf("⚡"),
        "fast" to listOf("⚡"),
        "langsam" to listOf("🐢"),
        "slow" to listOf("🐢")
    )

    /**
     * Katalog-Browser: flache, deduplizierte Emoji-Liste (Katalog-Reihenfolge,
     * alphabetisch nach Keyword sortiert) — Grundlage für die Paging-Logik
     * ([page]) und den 😀-Katalog-Zugriff in der Vorschlags-Leiste.
     */
    val catalog: List<String> by lazy {
        KEYWORDS.keys.sorted()
            .flatMap { kw -> KEYWORDS[kw].orEmpty() }
            .distinct()
    }

    /** Emojis einer Katalog-Seite ([page], 0-basiert, [perPage] Einträge). */
    fun page(page: Int, perPage: Int = PAGE_SIZE): List<String> {
        if (perPage <= 0 || page < 0) return emptyList()
        val from = page * perPage
        if (from >= catalog.size) return emptyList()
        return catalog.subList(from, minOf(from + perPage, catalog.size))
    }

    /** Erste Seite, die [emoji] enthält (−1, wenn nicht im Katalog). */
    fun pageOf(emoji: String, perPage: Int = PAGE_SIZE): Int {
        if (perPage <= 0) return -1
        val idx = catalog.indexOf(emoji)
        return if (idx < 0) -1 else idx / perPage
    }

    /** Anzahl Katalog-Seiten bei [perPage] Emojis pro Seite. */
    fun pageCount(perPage: Int = PAGE_SIZE): Int {
        if (perPage <= 0) return 0
        return (catalog.size + perPage - 1) / perPage
    }
}
