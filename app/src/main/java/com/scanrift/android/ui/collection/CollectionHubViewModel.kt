package com.scanrift.android.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.data.repository.CardListRepository
import com.scanrift.android.data.repository.CollectionRepository
import com.scanrift.android.domain.model.CardList
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CollectionHubState(
    val lists: List<CardList> = emptyList(),
    val totalCards: Int = 0,
    val uniqueCards: Int = 0,
)

@HiltViewModel
class CollectionHubViewModel @Inject constructor(
    private val listRepository: CardListRepository,
    collectionRepository: CollectionRepository,
) : ViewModel() {

    val state: StateFlow<CollectionHubState> = combine(
        listRepository.observeLists(),
        collectionRepository.observeTotalCardCount(),
        collectionRepository.observeUniqueCardCount(),
    ) { lists, total, unique ->
        CollectionHubState(lists = lists, totalCards = total, uniqueCards = unique)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionHubState())

    init {
        // Guarantees the single system Wishlist exists before the hub renders it.
        viewModelScope.launch { listRepository.reconcileWishlist() }
    }

    fun createList(name: String, colorHex: String) {
        viewModelScope.launch { listRepository.createList(name, colorHex) }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch { listRepository.delete(listId) }
    }
}
