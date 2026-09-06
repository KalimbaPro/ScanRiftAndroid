package com.scanrift.android.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.data.repository.DeckRepository
import com.scanrift.android.domain.model.Deck
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DeckListViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
) : ViewModel() {

    val decks: StateFlow<List<Deck>> = deckRepository.observeDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createDeck(onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(deckRepository.createDeck()) }
    }

    fun deleteDeck(deck: Deck) {
        viewModelScope.launch { deckRepository.deleteDeck(deck) }
    }
}
