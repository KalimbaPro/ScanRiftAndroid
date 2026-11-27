package com.scanrift.android.ui.collection

import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardFacetFilter
import com.scanrift.android.domain.model.CardLanguage
import com.scanrift.android.domain.model.CardNumberComparator
import com.scanrift.android.domain.model.CollectionEntry
import com.scanrift.android.domain.model.Facet

enum class CollectionViewMode(val label: String) {
    GRID("Grid"),
    LIST("List"),
}

enum class CollectionSortOption(val label: String) {
    NUMBER("Number"),
    NAME("Name"),
    QUANTITY("Quantity"),
    DATE_ADDED("Date Added"),
}

enum class OwnershipFilter(val label: String) {
    ALL("All"),
    OWNED("Owned"),
    UNOWNED("Not Owned"),
}

/**
 * One row in the browser: a card, plus the owned stack it represents if any.
 *
 * The same card appears once per owned variant (foil, condition), which is why the id
 * has to include them — keying on the card id alone would collapse a foil and a normal
 * copy into one tile.
 */
data class DisplayCard(val card: Card, val entry: CollectionEntry?) {
    val isOwned: Boolean get() = entry != null
    val quantity: Int get() = entry?.quantity ?: 0

    /** The owned copy's language; an unowned row sits with English, the default print. */
    val language: CardLanguage get() = entry?.language ?: CardLanguage.EN

    val id: String
        get() = entry?.let {
            "${card.id}-${if (it.isFoil) "foil" else "normal"}-${it.condition.value}-${it.language.value}-${it.id}"
        } ?: card.id
}

data class CollectionFilters(
    val card: CardFacetFilter = CardFacetFilter(),
    /** Raw [CardLanguage.value]s of owned copies to keep. Unowned rows never match. */
    val languages: Set<String> = emptySet(),
    val foilOnly: Boolean = false,
) {
    val isEmpty: Boolean get() = card.isEmpty && languages.isEmpty() && !foilOnly
}

object CollectionQuery {

    fun preferredEntry(entries: List<CollectionEntry>?): CollectionEntry? =
        entries?.firstOrNull { !it.isFoil } ?: entries?.firstOrNull()

    fun displayRows(
        cards: List<Card>,
        entriesByCard: Map<String, List<CollectionEntry>>,
        isListContext: Boolean,
        ownership: OwnershipFilter,
    ): List<DisplayCard> = when {
        isListContext -> cards.map { DisplayCard(it, preferredEntry(entriesByCard[it.id])) }
        ownership == OwnershipFilter.OWNED -> cards.flatMap { card ->
            entriesByCard[card.id].orEmpty().map { DisplayCard(card, it) }
        }
        else -> cards.flatMap { card ->
            val owned = entriesByCard[card.id].orEmpty()
            if (owned.isEmpty()) listOf(DisplayCard(card, null)) else owned.map { DisplayCard(card, it) }
        }
    }

    fun filter(
        rows: List<DisplayCard>,
        query: String,
        ownership: OwnershipFilter,
        filters: CollectionFilters,
        searchArtist: Boolean,
    ): List<DisplayCard> {
        val needle = query.trim()
        return rows.filter { row ->
            matchesSearch(row.card, needle, searchArtist) &&
                matchesOwnership(row, ownership) &&
                matchesFilters(row, filters)
        }
    }

    fun sort(rows: List<DisplayCard>, option: CollectionSortOption, ascending: Boolean): List<DisplayCard> {
        val comparator = when (option) {
            CollectionSortOption.NUMBER -> compareBy(CardNumberComparator) { it.card }
            CollectionSortOption.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.card.name }
            CollectionSortOption.QUANTITY -> compareBy<DisplayCard> { it.entry?.quantity ?: -1 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.card.name }
            CollectionSortOption.DATE_ADDED -> compareBy<DisplayCard> { it.entry?.dateAdded ?: Long.MIN_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.card.name }
        }
        return rows.sortedWith(if (ascending) comparator else comparator.reversed())
    }

    /**
     * Groups rows by language (en, fr, zh, ko), keeping the sort order within each
     * group. Returns the rows unchanged when they hold one language or none, which is
     * the common case and needs no section headers.
     */
    fun groupByLanguage(rows: List<DisplayCard>): List<DisplayCard> =
        if (spansLanguages(rows)) rows.sortedBy { it.language.ordinal } else rows

    fun spansLanguages(rows: List<DisplayCard>): Boolean = rows.map { it.language }.distinct().size > 1

    fun languageFacets(entries: List<CollectionEntry>): List<Facet> {
        val counts = entries.groupingBy { it.language }.eachCount()
        return CardLanguage.entries.map { Facet(it.value, counts[it] ?: 0) }
    }

    /**
     * The home page's global search: the **whole catalogue**, owned or not, in set order
     * then collector number, like the iOS app. Needs two characters, so one keystroke does
     * not list 1500 cards.
     */
    fun searchCatalogue(cards: List<Card>, query: String, limit: Int = GLOBAL_SEARCH_LIMIT): List<Card> {
        val needle = query.trim()
        if (needle.length < GLOBAL_SEARCH_MIN_LENGTH) return emptyList()
        return cards
            .filter { matchesSearch(it, needle, searchArtist = false) }
            .sortedWith(CardNumberComparator)
            .take(limit)
    }

    const val GLOBAL_SEARCH_MIN_LENGTH = 2
    const val GLOBAL_SEARCH_LIMIT = 60

    private fun matchesSearch(card: Card, needle: String, searchArtist: Boolean): Boolean {
        if (needle.isEmpty()) return true
        fun String?.hit() = this?.contains(needle, ignoreCase = true) == true
        return card.name.hit() || card.setLabel.hit() || card.publicCode.hit() || card.plainText.hit() ||
            card.supertype.hit() || card.tags.any { it.hit() } || (searchArtist && card.artist.hit())
    }

    private fun matchesOwnership(row: DisplayCard, ownership: OwnershipFilter): Boolean = when (ownership) {
        OwnershipFilter.ALL -> true
        OwnershipFilter.OWNED -> row.isOwned
        OwnershipFilter.UNOWNED -> !row.isOwned
    }

    private fun matchesFilters(row: DisplayCard, filters: CollectionFilters): Boolean {
        if (filters.foilOnly && row.entry?.isFoil != true) return false
        if (filters.languages.isNotEmpty() && row.entry?.language?.value !in filters.languages) return false
        return filters.card.matches(row.card)
    }
}
