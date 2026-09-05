package com.scanrift.android.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.data.prefs.UserPreferences
import com.scanrift.android.data.repository.CardListRepository
import com.scanrift.android.data.repository.CollectionRepository
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardNumberComparator
import com.scanrift.android.domain.model.CollectionEntry
import com.scanrift.android.domain.model.Rarity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CollectionViewMode { GRID, LIST }

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
    val rarities: Set<String> = emptySet(),
    val sets: Set<String> = emptySet(),
    val foilOnly: Boolean = false,
) {
    val activeCount: Int
        get() = domains.size + types.size + rarities.size + sets.size + if (foilOnly) 1 else 0

    val isEmpty: Boolean get() = activeCount == 0
}

data class CollectionBrowseState(
    val cards: List<DisplayCard> = emptyList(),
    val availableTypes: List<String> = emptyList(),
    val availableRarities: List<String> = emptyList(),
    val availableSets: List<String> = emptyList(),
    val ownedCount: Int = 0,
    val totalCount: Int = 0,
    val searchQuery: String = "",
    val viewMode: CollectionViewMode = CollectionViewMode.GRID,
    val sortOption: CollectionSortOption = CollectionSortOption.NUMBER,
    val sortAscending: Boolean = true,
    val ownership: OwnershipFilter = OwnershipFilter.ALL,
    val filters: CollectionFilters = CollectionFilters(),
    val showUnownedInColor: Boolean = false,
    val selectedCardId: String? = null,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionBrowseViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val listRepository: CardListRepository,
    userPreferences: UserPreferences,
) : ViewModel() {

    private val listId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val viewMode = MutableStateFlow(CollectionViewMode.GRID)
    private val sortOption = MutableStateFlow(CollectionSortOption.NUMBER)
    private val sortAscending = MutableStateFlow(true)
    private val ownership = MutableStateFlow(OwnershipFilter.ALL)
    private val filters = MutableStateFlow(CollectionFilters())
    private val selectedCardId = MutableStateFlow<String?>(null)

    private val scopedCards = listId.flatMapLatest { id ->
        if (id == null) {
            collectionRepository.observeAllCards()
        } else {
            listRepository.observeList(id).map { it?.cards ?: emptyList() }
        }
    }

    /**
     * Facet values come from the cards actually in scope, not a hardcoded list — so a
     * new set appears in the filter the moment it syncs, and a list containing only
     * two types does not offer six.
     */
    private val facets = scopedCards.map { cards ->
        Triple(
            cards.map { it.type }.distinct().sorted(),
            cards.map { it.rarity }.distinct().sortedBy { Rarity.sortRank(it) },
            cards.map { it.setLabel }.distinct().sorted(),
        )
    }

    private val displayCards = combine(
        scopedCards,
        collectionRepository.observeEntries(),
        searchQuery,
        ownership,
        filters,
    ) { cards, entries, query, ownershipFilter, activeFilters ->
        buildDisplayCards(cards, entries, query, ownershipFilter, activeFilters)
    }

    val state: StateFlow<CollectionBrowseState> = combine(
        displayCards,
        combine(sortOption, sortAscending, ::Pair),
        combine(viewMode, ownership, filters, ::Triple),
        combine(searchQuery, selectedCardId, ::Pair),
        combine(userPreferences.showUnownedInColor, facets, ::Pair),
    ) { cards, sort, view, searchAndSelection, prefsAndFacets ->
        val (option, ascending) = sort
        val (mode, ownershipFilter, activeFilters) = view
        val (query, selected) = searchAndSelection
        val (unownedInColor, facetValues) = prefsAndFacets
        val (types, rarities, sets) = facetValues
        val sorted = sortCards(cards, option, ascending)
        CollectionBrowseState(
            cards = sorted,
            availableTypes = types,
            availableRarities = rarities,
            availableSets = sets,
            ownedCount = sorted.count { it.isOwned },
            totalCount = sorted.size,
            searchQuery = query,
            viewMode = mode,
            sortOption = option,
            sortAscending = ascending,
            ownership = ownershipFilter,
            filters = activeFilters,
            showUnownedInColor = unownedInColor,
            selectedCardId = selected,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionBrowseState())

    fun setListContext(id: String?) { listId.value = id }
    fun setSearchQuery(value: String) { searchQuery.value = value }
    fun setViewMode(mode: CollectionViewMode) { viewMode.value = mode }
    fun setOwnership(filter: OwnershipFilter) { ownership.value = filter }
    fun updateFilters(value: CollectionFilters) { filters.value = value }
    fun clearFilters() { filters.value = CollectionFilters() }
    fun selectCard(id: String?) { selectedCardId.value = id }

    /** Re-selecting the current sort flips direction, matching iOS. */
    fun setSortOption(option: CollectionSortOption) {
        if (sortOption.value == option) {
            sortAscending.update { !it }
        } else {
            sortOption.value = option
            sortAscending.value = true
        }
    }

    fun addCopy(card: Card) {
        viewModelScope.launch { collectionRepository.addCopies(card) }
    }

    fun setQuantity(entry: CollectionEntry, quantity: Int) {
        viewModelScope.launch { collectionRepository.setQuantity(entry, quantity) }
    }

    private fun buildDisplayCards(
        cards: List<Card>,
        entries: List<CollectionEntry>,
        query: String,
        ownershipFilter: OwnershipFilter,
        activeFilters: CollectionFilters,
    ): List<DisplayCard> {
        val entriesByCard = entries.filter { it.cardId != null }.groupBy { it.cardId!! }
        val normalizedQuery = query.trim().lowercase()

        val rows = cards.flatMap { card ->
            val owned = entriesByCard[card.id].orEmpty()
            if (owned.isEmpty()) listOf(DisplayCard(card, null))
            else owned.map { DisplayCard(card, it) }
        }

        return rows.filter { row ->
            matchesOwnership(row, ownershipFilter) &&
                matchesSearch(row.card, normalizedQuery) &&
                matchesFilters(row, activeFilters)
        }
    }

    private fun matchesOwnership(row: DisplayCard, filter: OwnershipFilter): Boolean = when (filter) {
        OwnershipFilter.ALL -> true
        OwnershipFilter.OWNED -> row.isOwned
        OwnershipFilter.UNOWNED -> !row.isOwned
    }

    private fun matchesSearch(card: Card, query: String): Boolean {
        if (query.isEmpty()) return true
        return card.name.lowercase().contains(query) ||
            card.setLabel.lowercase().contains(query) ||
            card.publicCode.lowercase().contains(query) ||
            card.plainText?.lowercase()?.contains(query) == true ||
            card.supertype?.lowercase()?.contains(query) == true ||
            card.tags.any { it.lowercase().contains(query) }
    }

    private fun matchesFilters(row: DisplayCard, filters: CollectionFilters): Boolean {
        val card = row.card
        if (filters.domains.isNotEmpty() && card.domains.none { it in filters.domains }) return false
        if (filters.types.isNotEmpty() && card.type !in filters.types) return false
        if (filters.rarities.isNotEmpty() && card.rarity !in filters.rarities) return false
        if (filters.sets.isNotEmpty() && card.setLabel !in filters.sets) return false
        if (filters.foilOnly && row.entry?.isFoil != true && !card.isAlwaysFoil) return false
        return true
    }

    private fun sortCards(
        rows: List<DisplayCard>,
        option: CollectionSortOption,
        ascending: Boolean,
    ): List<DisplayCard> {
        val comparator = when (option) {
            // The 4-key set-aware sort: set, ordering bucket, number, base before alt art.
            CollectionSortOption.NUMBER -> compareBy(CardNumberComparator) { it.card }
            CollectionSortOption.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.card.name }
            CollectionSortOption.QUANTITY -> compareBy<DisplayCard> { it.quantity }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.card.name }
            CollectionSortOption.DATE_ADDED -> compareBy<DisplayCard> { it.entry?.dateAdded ?: Long.MIN_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.card.name }
        }
        val sorted = rows.sortedWith(comparator)
        return if (ascending) sorted else sorted.reversed()
    }

}
