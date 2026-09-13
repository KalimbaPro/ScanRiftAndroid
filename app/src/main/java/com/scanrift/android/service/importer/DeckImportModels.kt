package com.scanrift.android.service.importer

import com.scanrift.android.domain.model.DeckSection

/** Where the pasted list came from. Decides whether [ParsedLine.section] can be trusted. */
enum class DeckListSource { TEXT, TTS }

/**
 * A section header in a pasted decklist.
 *
 * [LEGEND] and [CHAMPION] are slots on the deck, not sections of it, so their
 * [deckSection] is null — the import service routes them to the legend/champion
 * fields instead of to an entry row.
 */
enum class DeckListSection(val header: String, val deckSection: DeckSection?) {
    LEGEND("Legend", null),
    CHAMPION("Champion", null),
    MAIN_DECK("MainDeck", DeckSection.MAIN_DECK),
    BATTLEFIELDS("Battlefields", DeckSection.BATTLEFIELD),
    RUNES("Runes", DeckSection.RUNE),
    SIDEBOARD("Sideboard", DeckSection.SIDEBOARD),
    ;

    companion object {
        /**
         * Headers are matched on letters only, so `MainDeck:`, `Main Deck:` and
         * `MAIN DECK :` all land on the same section.
         */
        fun fromHeader(raw: String): DeckListSection? {
            val key = raw.filter { it.isLetter() }.lowercase()
            return entries.firstOrNull { it.header.filter { c -> c.isLetter() }.lowercase() == key }
        }
    }
}

/**
 * One resolved-to-be line of a pasted list.
 *
 * [token] is a card name for [DeckListSource.TEXT] and a public-code prefix such as
 * `VEN-021` for [DeckListSource.TTS]. [section] is null when the source carries no
 * section information, which is always the case for TTS.
 */
data class ParsedLine(
    val quantity: Int,
    val token: String,
    val section: DeckListSection?,
)

data class ParsedDeckList(
    val lines: List<ParsedLine>,
    val source: DeckListSource,
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

/**
 * Outcome of an import.
 *
 * [notes] carries anything the user should know that is not a per-line failure — most
 * importantly that a TTS string cannot express a sideboard.
 */
data class DeckImportResult(
    val cardsAdded: Int = 0,
    val linesImported: Int = 0,
    val skippedLines: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
) {
    val summary: String
        get() = buildString {
            append("Imported $cardsAdded cards")
            if (skippedLines.isNotEmpty()) {
                append(", skipped ${skippedLines.size}: ")
                append(skippedLines.take(3).joinToString(", "))
                if (skippedLines.size > 3) append(" and ${skippedLines.size - 3} more")
            }
            append(".")
            notes.forEach { append(" $it") }
        }
}
