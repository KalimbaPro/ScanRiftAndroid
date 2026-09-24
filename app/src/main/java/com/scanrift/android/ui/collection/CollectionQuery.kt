package com.scanrift.android.ui.collection

import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardNumberComparator
import com.scanrift.android.domain.model.CardSupertype
import com.scanrift.android.domain.model.CollectionEntry
import com.scanrift.android.domain.model.Domain
import com.scanrift.android.domain.model.Rarity

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
    val id: String
        get() = entry?.let { "${card.id}-${if (it.isFoil) "foil" else "normal"}-${it.condition.value}-${it.id}" }
            ?: card.id
}

data class CollectionFilters(
    val domains: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val supertypes: Set<String> = emptySet(),
    val rarities: Set<String> = emptySet(),
    val sets: Set<String> = emptySet(),
    val artists: Set<String> = emptySet(),
    val foilOnly: Boolean = false,
    val energyMin: Int? = null,
    val energyMax: Int? = null,
    val powerMin: Int? = null,
    val powerMax: Int? = null,
) {
    val hasEnergyRange: Boolean get() = energyMin != null || energyMax != null
    val hasPowerRange: Boolean get() = powerMin != null || powerMax != null

    val isEmpty: Boolean
        get() = domains.isEmpty() && types.isEmpty() && supertypes.isEmpty() && rarities.isEmpty() &&
            sets.isEmpty() && artists.isEmpty() && !foilOnly && !hasEnergyRange && !hasPowerRange
}

data class Facet(val value: String, val count: Int)

data class FilterFacets(
    val domains: List<Facet> = emptyList(),
    val types: List<Facet> = emptyList(),
    val supertypes: List<Facet> = emptyList(),
    val rarities: List<Facet> = emptyList(),
    val sets: List<Facet> = emptyList(),
    val artists: List<Facet> = emptyList(),
    val energy: IntRange? = null,
    val power: IntRange? = null,
)

object CollectionQuery {

    val filterSupertypes = listOf(CardSupertype.CHAMPION, CardSupertype.SIGNATURE)

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

    fun facets(cards: List<Card>): FilterFacets {
        fun count(values: List<String>) = values.groupingBy { it }.eachCount()
        val domainCounts = count(cards.flatMap { it.domains.distinct() })
        val typeCounts = count(cards.map { it.type })
        val supertypeCounts = count(cards.mapNotNull { it.supertype })
        val rarityCounts = count(cards.map { it.rarity })
        val setCounts = count(cards.map { it.setLabel })
        val artistCounts = count(cards.mapNotNull { it.artist })
        return FilterFacets(
            // Colourless is deliberately absent from the domain filter, matching iOS —
            // it is the absence of a domain rather than a choice. It is still in the
            // data, so those cards remain reachable with the filter off.
            domains = Domain.filterOrder.mapNotNull { d -> domainCounts[d]?.let { Facet(d, it) } },
            types = typeCounts.toSortedMap().map { (value, n) -> Facet(value, n) },
            supertypes = filterSupertypes.mapNotNull { s -> supertypeCounts[s]?.let { Facet(s, it) } },
            rarities = Rarity.displayOrder.mapNotNull { r -> rarityCounts[r]?.let { Facet(r, it) } },
            sets = setCounts.toSortedMap().map { (value, n) -> Facet(value, n) },
            artists = artistCounts.toSortedMap().map { (value, n) -> Facet(value, n) },
            energy = spread(cards.mapNotNull { it.energy }),
            power = spread(cards.mapNotNull { it.power }),
        )
    }

    private fun spread(values: List<Int>): IntRange? {
        val min = values.minOrNull() ?: return null
        val max = values.max()
        return if (min == max) null else min..max
    }

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
        val card = row.card
        if (filters.rarities.isNotEmpty() && card.rarity !in filters.rarities) return false
        if (filters.sets.isNotEmpty() && card.setLabel !in filters.sets) return false
        if (filters.types.isNotEmpty() && card.type !in filters.types) return false
        if (filters.supertypes.isNotEmpty() && card.supertype !in filters.supertypes) return false
        if (filters.foilOnly && row.entry?.isFoil != true) return false
        if (filters.artists.isNotEmpty() && card.artist !in filters.artists) return false
        if (filters.domains.isNotEmpty() && card.domains.none { it in filters.domains }) return false
        if (filters.hasEnergyRange && !card.energy.within(filters.energyMin, filters.energyMax)) return false
        if (filters.hasPowerRange && !card.power.within(filters.powerMin, filters.powerMax)) return false
        return true
    }

    private fun Int?.within(min: Int?, max: Int?): Boolean =
        this != null && (min == null || this >= min) && (max == null || this <= max)
}
