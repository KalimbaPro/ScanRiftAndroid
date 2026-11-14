package com.scanrift.android.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.data.repository.CardListRepository
import com.scanrift.android.data.repository.CollectionRepository
import com.scanrift.android.di.DefaultDispatcher
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardList
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CollectionHubState(
    val lists: List<CardList> = emptyList(),
    val totalCards: Int = 0,
    val uniqueCards: Int = 0,
)

/** A global-search hit and how many copies the user owns (0 for unowned). */
data class CatalogueHit(val card: Card, val owned: Int)

@HiltViewModel
class CollectionHubViewModel @Inject constructor(
    private val listRepository: CardListRepository,
    collectionRepository: CollectionRepository,
    @param:DefaultDispatcher private val default: CoroutineDispatcher,
) : ViewModel() {

    val state: StateFlow<CollectionHubState> = combine(
        listRepository.observeLists(),
        collectionRepository.observeTotalCardCount(),
        collectionRepository.observeUniqueCardCount(),
    ) { lists, total, unique ->
        CollectionHubState(lists = lists, totalCards = total, uniqueCards = unique)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionHubState())

    private val query = MutableStateFlow("")

    /** The home search bar searches the whole catalogue, owned or not. */
    val searchResults: StateFlow<List<CatalogueHit>> = combine(
        collectionRepository.observeAllCards(),
        collectionRepository.observeOwnedQuantities(),
        query,
    ) { cards, owned, q ->
        CollectionQuery.searchCatalogue(cards, q).map { CatalogueHit(it, owned[it.id] ?: 0) }
    }.flowOn(default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        query.value = value
    }

    init {
        // Guarantees the single system Wishlist exists before the hub renders it.
        viewModelScope.launch { listRepository.reconcileWishlist() }
    }

    suspend fun saveList(editing: CardList?, name: String, colorHex: String): Boolean =
        listRepository.saveList(editing, name, colorHex)

    fun deleteList(listId: String) {
        viewModelScope.launch { listRepository.delete(listId) }
    }
}
