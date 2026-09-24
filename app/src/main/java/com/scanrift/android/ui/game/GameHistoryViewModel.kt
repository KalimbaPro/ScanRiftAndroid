package com.scanrift.android.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.data.local.dao.GameRecordDao
import com.scanrift.android.data.local.mapper.toEntity
import com.scanrift.android.data.repository.CollectionRepository
import com.scanrift.android.data.repository.DeckRepository
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.GameRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GameHistoryViewModel @Inject constructor(
    private val gameRecordDao: GameRecordDao,
    deckRepository: DeckRepository,
    collectionRepository: CollectionRepository,
) : ViewModel() {

    private val deckId = MutableStateFlow<String?>(null)

    val records: StateFlow<List<GameRecord>> = deckId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else deckRepository.observeGameRecords(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val cards = collectionRepository.observeAllCards()

    val cardsById: StateFlow<Map<String, Card>> = cards
        .map { all -> all.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val legends: StateFlow<List<Card>> = cards
        .map { it.pickableLegends() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(id: String) { deckId.value = id }

    fun save(record: GameRecord) {
        viewModelScope.launch { gameRecordDao.upsert(record.toEntity()) }
    }

    fun delete(record: GameRecord) {
        viewModelScope.launch { gameRecordDao.delete(record.toEntity()) }
    }
}
