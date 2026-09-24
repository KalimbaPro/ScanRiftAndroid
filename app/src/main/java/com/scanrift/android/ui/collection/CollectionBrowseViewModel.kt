package com.scanrift.android.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.data.prefs.UserPreferences
import com.scanrift.android.data.repository.CardListRepository
import com.scanrift.android.data.repository.CollectionRepository
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardCondition
import com.scanrift.android.domain.model.CardList
import com.scanrift.android.domain.model.CollectionEntry
import com.scanrift.android.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CollectionControls(
    val searchQuery: String = "",
    val viewMode: CollectionViewMode = CollectionViewMode.GRID,
    val sortOption: CollectionSortOption = CollectionSortOption.NUMBER,
    val sortAscending: Boolean = true,
    val ownership: OwnershipFilter = OwnershipFilter.ALL,
    val filters: CollectionFilters = CollectionFilters(),
    val isSelecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
)

data class CollectionPrefs(
    val showUnownedInColor: Boolean = false,
    val luchoParameter: Boolean = false,
)

data class CollectionBrowseState(
    val controls: CollectionControls = CollectionControls(),
    val prefs: CollectionPrefs = CollectionPrefs(),
    val cards: List<DisplayCard> = emptyList(),
    val facets: FilterFacets = FilterFacets(),
    val entriesByCard: Map<String, List<CollectionEntry>> = emptyMap(),
    val lists: List<CardList> = emptyList(),
    val listContext: CardList? = null,
    val hasEntries: Boolean = false,
    val isLoading: Boolean = true,
) {
    val ownedCount: Int get() = cards.count { it.isOwned }
    val customLists: List<CardList> get() = lists.filterNot { it.isSystem }
    val wishlist: CardList? get() = lists.firstOrNull { it.isWishlist }
    val selectedRows: List<DisplayCard> get() = cards.filter { it.id in controls.selectedIds }
    val allSelected: Boolean get() = cards.isNotEmpty() && cards.all { it.id in controls.selectedIds }
    val isListScopeEmpty: Boolean
        get() = listContext != null && listContext.cards.isEmpty() &&
            controls.searchQuery.isEmpty() && controls.filters.isEmpty
    val isCollectionEmpty: Boolean
        get() = listContext == null && controls.ownership == OwnershipFilter.OWNED && !hasEntries && !isLoading
}

@HiltViewModel
class CollectionBrowseViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val listRepository: CardListRepository,
    private val userPreferences: UserPreferences,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val listId = MutableStateFlow<String?>(null)
    private val controls = MutableStateFlow(CollectionControls())

    private val prefs = combine(
        userPreferences.showUnownedInColor,
        userPreferences.luchoParameter,
        ::CollectionPrefs,
    )

    val state: StateFlow<CollectionBrowseState> = combine(
        collectionRepository.observeAllCards(),
        collectionRepository.observeEntries(),
        combine(listRepository.observeLists(), listId, ::Pair),
        controls,
        prefs,
    ) { catalogue, entries, (lists, id), ui, userPrefs ->
        val entriesByCard = entries.filter { it.cardId != null }.groupBy { it.cardId!! }
        val listContext = id?.let { lid -> lists.firstOrNull { it.id == lid } }
        val scoped = if (id == null) catalogue else listContext?.cards.orEmpty()
        val facetSource = if (ui.ownership == OwnershipFilter.OWNED) scoped.filter { it.id in entriesByCard } else scoped
        val rows = CollectionQuery.displayRows(scoped, entriesByCard, id != null, ui.ownership)
        val filtered = CollectionQuery.filter(rows, ui.searchQuery, ui.ownership, ui.filters, userPrefs.luchoParameter)
        CollectionBrowseState(
            controls = ui,
            prefs = userPrefs,
            cards = CollectionQuery.sort(filtered, ui.sortOption, ui.sortAscending),
            facets = CollectionQuery.facets(facetSource),
            entriesByCard = entriesByCard,
            lists = lists,
            listContext = listContext,
            hasEntries = entries.isNotEmpty(),
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionBrowseState())

    fun setListContext(id: String?) { listId.value = id }
    fun setSearchQuery(value: String) = controls.update { it.copy(searchQuery = value) }
    fun setViewMode(mode: CollectionViewMode) = controls.update { it.copy(viewMode = mode) }
    fun setOwnership(filter: OwnershipFilter) = controls.update { it.copy(ownership = filter) }
    fun setSortOption(option: CollectionSortOption) = controls.update { it.copy(sortOption = option) }
    fun toggleSortDirection() = controls.update { it.copy(sortAscending = !it.sortAscending) }
    fun updateFilters(value: CollectionFilters) = controls.update { it.copy(filters = value) }
    fun clearFilters() = updateFilters(CollectionFilters())

    fun setShowUnownedInColor(value: Boolean) {
        viewModelScope.launch { userPreferences.setShowUnownedInColor(value) }
    }

    fun startSelection(rowId: String) =
        controls.update { it.copy(isSelecting = true, selectedIds = it.selectedIds + rowId) }

    fun toggleSelection(rowId: String) = controls.update {
        it.copy(selectedIds = if (rowId in it.selectedIds) it.selectedIds - rowId else it.selectedIds + rowId)
    }

    fun toggleSelectAll() {
        val current = state.value
        controls.update {
            it.copy(selectedIds = if (current.allSelected) emptySet() else current.cards.map { row -> row.id }.toSet())
        }
    }

    fun exitSelection() = controls.update { it.copy(isSelecting = false, selectedIds = emptySet()) }

    suspend fun writeSelectionExport(directory: File, format: SelectionExportFormat): File {
        val content = format.render(state.value.selectedRows)
        exitSelection()
        return withContext(io) {
            directory.resolve(SHARED_DIRECTORY).apply { mkdirs() }.resolve(format.fileName).apply { writeText(content) }
        }
    }

    fun addSelectionToCollection() {
        val unowned = state.value.selectedRows.filterNot { it.isOwned }.map { it.card }
        viewModelScope.launch { unowned.forEach { collectionRepository.addCopies(it) } }
        exitSelection()
    }

    fun adjustQuantity(card: Card, entry: CollectionEntry?, delta: Int) {
        viewModelScope.launch { collectionRepository.adjustQuantity(card, entry, delta) }
    }

    fun setQuantity(card: Card, entry: CollectionEntry?, quantity: Int) {
        viewModelScope.launch {
            if (entry != null) {
                collectionRepository.setQuantity(entry, quantity)
            } else {
                collectionRepository.adjustQuantity(card, null, quantity)
            }
        }
    }

    fun setCondition(entry: CollectionEntry, condition: CardCondition) {
        viewModelScope.launch { collectionRepository.setCondition(entry, condition) }
    }

    fun toggleWishlist(card: Card): Boolean {
        val wishlist = state.value.wishlist ?: return false
        viewModelScope.launch {
            if (wishlist.cards.any { it.id == card.id }) {
                listRepository.removeCard(wishlist.id, card.id)
            } else {
                listRepository.addCard(wishlist.id, card.id)
            }
        }
        return true
    }

    fun toggleListMembership(list: CardList, cards: List<Card>) {
        viewModelScope.launch { listRepository.toggleCards(list, cards.map { it.id }) }
    }

    suspend fun saveList(editing: CardList?, name: String, colorHex: String): Boolean =
        listRepository.saveList(editing, name, colorHex)

    private companion object {
        const val SHARED_DIRECTORY = "shared"
    }
}
