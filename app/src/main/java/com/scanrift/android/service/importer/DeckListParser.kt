package com.scanrift.android.service.importer

/**
 * Turns a pasted decklist into [ParsedDeckList]. Pure text work — no card lookup, no
 * database, no Android imports — so it runs on plain JUnit like `DeckValidator`.
 *
 * Two formats, both produced by [com.scanrift.android.service.export.CollectionExporter]:
 *
 *  - **Text**: `Legend:` / `Champion:` / `MainDeck:` / `Battlefields:` / `Runes:` /
 *    `Sideboard:` headers, each followed by `<quantity> <card name>` lines.
 *  - **TTS**: whitespace-separated `<public-code-prefix>-<copies>` tokens, one per
 *    physical copy, e.g. `OGN-021-1 OGN-021-1`.
 */
object DeckListParser {

    /** `3 Scuttle Crab`, `3x Scuttle Crab`, or a bare name (which counts as one copy). */
    private val CARD_LINE = Regex("""^(\d+)\s*[xX]?\s+(.+)$""")

    /** `OGN-021-1` splits into the code `OGN-021` and a copy count of 1. */
    private val TTS_TOKEN = Regex("""^(.*\S)-(\d+)$""")

    fun parseText(content: String): ParsedDeckList {
        var section: DeckListSection? = null
        val lines = mutableListOf<ParsedLine>()

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach

            // A header owns every line after it until the next header. An unrecognised
            // header clears the section rather than inheriting the previous one, so its
            // cards get placed by card type instead of being filed somewhere wrong.
            if (line.endsWith(":")) {
                section = DeckListSection.fromHeader(line.dropLast(1))
                return@forEach
            }

            val match = CARD_LINE.matchEntire(line)
            val quantity = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val name = match?.groupValues?.get(2)?.trim() ?: line
            if (name.isEmpty()) return@forEach

            // A deck has one legend and one champion however many the list claims.
            val effective = if (section == DeckListSection.LEGEND || section == DeckListSection.CHAMPION) 1 else quantity
            lines += ParsedLine(quantity = effective, token = name, section = section)
        }

        return ParsedDeckList(lines = merge(lines), source = DeckListSource.TEXT)
    }

    fun parseTts(content: String): ParsedDeckList {
        val lines = content.split(' ', '\t', '\n', '\r')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { token ->
                val match = TTS_TOKEN.matchEntire(token)
                if (match != null) {
                    ParsedLine(
                        quantity = match.groupValues[2].toIntOrNull() ?: 1,
                        token = match.groupValues[1],
                        section = null,
                    )
                } else {
                    ParsedLine(quantity = 1, token = token, section = null)
                }
            }
            .toList()

        return ParsedDeckList(lines = merge(lines), source = DeckListSource.TTS)
    }

    /**
     * Spellings to try, in order, when resolving a pasted name against the catalogue.
     *
     * Legend and champion names carry a hyphen in the card data — `Ornn - Fire Below
     * the Mountain` — but every decklist in the wild writes them with a comma. Both
     * substitutions are offered because `Allay, Eager Admirer` is a real card name with
     * a real comma in it, so the raw spelling has to be tried first and win.
     */
    fun nameVariants(raw: String): List<String> {
        val trimmed = raw.trim()
        return listOf(
            trimmed,
            trimmed.replace(", ", " - "),
            trimmed.replace(" - ", ", "),
        ).distinct()
    }

    /**
     * Folds repeats of the same token in the same section into one line, summing the
     * copies. TTS depends on this — it emits one token per physical copy — and a hand
     * -written list that mentions a card twice collapses the same way.
     */
    private fun merge(lines: List<ParsedLine>): List<ParsedLine> {
        val byKey = LinkedHashMap<Pair<String, DeckListSection?>, ParsedLine>()
        lines.forEach { line ->
            val key = line.token.lowercase() to line.section
            val existing = byKey[key]
            byKey[key] = existing?.copy(quantity = existing.quantity + line.quantity) ?: line
        }
        return byKey.values.toList()
    }
}
