package com.scanrift.android.ui.screens.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.CollectionEntryWithCard
import com.scanrift.android.data.remote.api.RetrofitInstance
import com.scanrift.android.data.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ViewMode { GRID, LIST }

enum class SortOption(val label: String) {
    NAME("Name"),
    SET("Set"),
    RARITY("Rarity"),
    TYPE("Type"),
    ENERGY("Energy"),
    POWER("Power"),
    DATE_ADDED("Date Added")
}

enum class OwnershipFilter(val label: String) {
    ALL("All Cards"),
    OWNED("Owned"),
    UNOWNED("Not Owned")
}

data class FilterState(
    val domains: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val rarities: Set<String> = emptySet(),
    val sets: Set<String> = emptySet(),
    val energyRange: IntRange? = null,
    val powerRange: IntRange? = null,
    val foilOnly: Boolean = false
) {
    val isActive: Boolean
        get() = domains.isNotEmpty() || types.isNotEmpty() || rarities.isNotEmpty() ||
                sets.isNotEmpty() || energyRange != null || powerRange != null || foilOnly

    val activeFilterCount: Int
        get() = domains.size + types.size + rarities.size + sets.size +
                (if (energyRange != null) 1 else 0) +
                (if (powerRange != null) 1 else 0) +
                (if (foilOnly) 1 else 0)
}

class CollectionViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ScanRiftDatabase.getInstance(application)
    private val cardDao = database.cardDao()
    private val collectionEntryDao = database.collectionEntryDao()
    private val cardListDao = database.cardListDao()
    private val cardRepository = CardRepository(
        cardDao = cardDao,
        api = RetrofitInstance.api,
        context = application
    )

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // View mode
    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    // Sort
    private val _sortOption = MutableStateFlow(SortOption.SET)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _sortAscending = MutableStateFlow(true)
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    // Filters
    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    // Ownership filter
    private val _ownershipFilter = MutableStateFlow(OwnershipFilter.ALL)
    val ownershipFilter: StateFlow<OwnershipFilter> = _ownershipFilter.asStateFlow()

    // Multi-select
    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    private val _selectedCardIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedCardIds: StateFlow<Set<String>> = _selectedCardIds.asStateFlow()

    // List context (for filtering by card list)
    private val _listContextId = MutableStateFlow<String?>(null)

    // Source data
    private val allCards = cardDao.getAllCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val collectionEntries = collectionEntryDao.getAllWithCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Available filter options
    private val _availableSets = MutableStateFlow<List<String>>(emptyList())
    val availableSets: StateFlow<List<String>> = _availableSets.asStateFlow()

    private val _availableTypes = MutableStateFlow<List<String>>(emptyList())
    val availableTypes: StateFlow<List<String>> = _availableTypes.asStateFlow()

    private val _availableRarities = MutableStateFlow<List<String>>(emptyList())
    val availableRarities: StateFlow<List<String>> = _availableRarities.asStateFlow()

    // List context card IDs (for filtering by card list)
    private val _listCardIds = MutableStateFlow<Set<String>?>(null)

    // Filtered + sorted cards
    @Suppress("UNCHECKED_CAST")
    val displayedCards: StateFlow<List<CardEntity>> = combine(
        allCards,
        collectionEntries,
        _searchQuery,
        _filterState,
        _ownershipFilter,
        _sortOption,
        _sortAscending,
        _listCardIds
    ) { args ->
        val cards = args[0] as List<CardEntity>
        val entries = args[1] as List<CollectionEntryWithCard>
        val query = args[2] as String
        val filters = args[3] as FilterState
        val ownership = args[4] as OwnershipFilter
        val sort = args[5] as SortOption
        val ascending = args[6] as Boolean
        val listCardIds = args[7] as Set<String>?

        val ownedCardIds = entries.map { it.entry.cardId }.toSet()

        var filtered = cards.asSequence()

        // Filter by list context
        if (listCardIds != null) {
            filtered = filtered.filter { it.id in listCardIds }
        }

        // Search
        if (query.isNotBlank()) {
            val lowerQuery = query.lowercase()
            filtered = filtered.filter {
                it.name.lowercase().contains(lowerQuery) ||
                        it.publicCode.lowercase().contains(lowerQuery) ||
                        it.type.lowercase().contains(lowerQuery)
            }
        }

        // Ownership
        when (ownership) {
            OwnershipFilter.OWNED -> filtered = filtered.filter { it.id in ownedCardIds }
            OwnershipFilter.UNOWNED -> filtered = filtered.filter { it.id !in ownedCardIds }
            OwnershipFilter.ALL -> { /* no filter */ }
        }

        // Domain filter
        if (filters.domains.isNotEmpty()) {
            filtered = filtered.filter { card ->
                card.domains.any { it in filters.domains }
            }
        }

        // Type filter
        if (filters.types.isNotEmpty()) {
            filtered = filtered.filter { it.type in filters.types }
        }

        // Rarity filter
        if (filters.rarities.isNotEmpty()) {
            filtered = filtered.filter { it.rarity in filters.rarities }
        }

        // Set filter
        if (filters.sets.isNotEmpty()) {
            filtered = filtered.filter { it.setLabel in filters.sets }
        }

        // Energy range
        filters.energyRange?.let { range ->
            filtered = filtered.filter { card ->
                card.energy?.let { it in range } ?: false
            }
        }

        // Power range
        filters.powerRange?.let { range ->
            filtered = filtered.filter { card ->
                card.power?.let { it in range } ?: false
            }
        }

        // Sort
        val sorted = when (sort) {
            SortOption.NAME -> if (ascending) filtered.sortedBy { it.name }
            else filtered.sortedByDescending { it.name }
            SortOption.SET -> if (ascending) filtered.sortedWith(compareBy({ it.setLabel }, { it.collectorNumber }))
            else filtered.sortedWith(compareByDescending<CardEntity> { it.setLabel }.thenByDescending { it.collectorNumber })
            SortOption.RARITY -> {
                val rarityOrder = mapOf("Common" to 0, "Uncommon" to 1, "Rare" to 2, "Epic" to 3, "Showcase" to 4)
                if (ascending) filtered.sortedBy { rarityOrder[it.rarity] ?: 0 }
                else filtered.sortedByDescending { rarityOrder[it.rarity] ?: 0 }
            }
            SortOption.TYPE -> if (ascending) filtered.sortedBy { it.type }
            else filtered.sortedByDescending { it.type }
            SortOption.ENERGY -> if (ascending) filtered.sortedBy { it.energy ?: Int.MAX_VALUE }
            else filtered.sortedByDescending { it.energy ?: Int.MIN_VALUE }
            SortOption.POWER -> if (ascending) filtered.sortedBy { it.power ?: Int.MAX_VALUE }
            else filtered.sortedByDescending { it.power ?: Int.MIN_VALUE }
            SortOption.DATE_ADDED -> filtered.sortedBy { it.name } // Fallback for non-collection view
        }

        sorted.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Owned card quantities (cardId -> total quantity)
    val ownedQuantities: StateFlow<Map<String, Int>> = collectionEntries
        .combine(MutableStateFlow(Unit)) { entries, _ ->
            entries.groupBy { it.entry.cardId }
                .mapValues { (_, group) -> group.sumOf { it.entry.quantity } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        loadFilterOptions()
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            _availableSets.value = cardRepository.getAllSetLabels()
            _availableTypes.value = cardRepository.getAllTypes()
            _availableRarities.value = cardRepository.getAllRarities()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    fun setSortOption(option: SortOption) {
        if (_sortOption.value == option) {
            _sortAscending.value = !_sortAscending.value
        } else {
            _sortOption.value = option
            _sortAscending.value = true
        }
    }

    fun setOwnershipFilter(filter: OwnershipFilter) {
        _ownershipFilter.value = filter
    }

    fun updateFilters(filters: FilterState) {
        _filterState.value = filters
    }

    fun clearFilters() {
        _filterState.value = FilterState()
    }

    fun toggleDomainFilter(domain: String) {
        val current = _filterState.value
        val newDomains = if (domain in current.domains) {
            current.domains - domain
        } else {
            current.domains + domain
        }
        _filterState.value = current.copy(domains = newDomains)
    }

    fun toggleTypeFilter(type: String) {
        val current = _filterState.value
        val newTypes = if (type in current.types) {
            current.types - type
        } else {
            current.types + type
        }
        _filterState.value = current.copy(types = newTypes)
    }

    fun toggleRarityFilter(rarity: String) {
        val current = _filterState.value
        val newRarities = if (rarity in current.rarities) {
            current.rarities - rarity
        } else {
            current.rarities + rarity
        }
        _filterState.value = current.copy(rarities = newRarities)
    }

    fun toggleSetFilter(set: String) {
        val current = _filterState.value
        val newSets = if (set in current.sets) {
            current.sets - set
        } else {
            current.sets + set
        }
        _filterState.value = current.copy(sets = newSets)
    }

    // Multi-select
    fun toggleMultiSelect() {
        _isMultiSelectMode.value = !_isMultiSelectMode.value
        if (!_isMultiSelectMode.value) {
            _selectedCardIds.value = emptySet()
        }
    }

    fun toggleCardSelection(cardId: String) {
        val current = _selectedCardIds.value
        _selectedCardIds.value = if (cardId in current) current - cardId else current + cardId
    }

    fun selectAll(cardIds: List<String>) {
        _selectedCardIds.value = cardIds.toSet()
    }

    fun clearSelection() {
        _selectedCardIds.value = emptySet()
    }

    fun setListContext(listId: String?) {
        _listContextId.value = listId
        if (listId != null) {
            viewModelScope.launch {
                cardListDao.getListWithCards(listId).collect { listWithCards ->
                    _listCardIds.value = listWithCards?.cards?.map { it.id }?.toSet() ?: emptySet()
                }
            }
        } else {
            _listCardIds.value = null
        }
    }
}
