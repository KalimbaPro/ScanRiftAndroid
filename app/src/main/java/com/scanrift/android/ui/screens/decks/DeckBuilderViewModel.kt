package com.scanrift.android.ui.screens.decks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.DeckEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity
import com.scanrift.android.data.local.entity.DeckEntryWithCard
import com.scanrift.android.data.local.entity.DeckSection
import com.scanrift.android.data.local.entity.DeckWithEntries
import com.scanrift.android.service.DeckValidationError
import com.scanrift.android.service.DeckValidator
import com.scanrift.android.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

class DeckBuilderViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ScanRiftDatabase.getInstance(application)
    private val deckDao = database.deckDao()
    private val cardDao = database.cardDao()

    // Deck list
    val allDecks: StateFlow<List<DeckWithEntries>> = deckDao.getAllDecksWithEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current deck editing
    private val _currentDeckId = MutableStateFlow<String?>(null)
    val currentDeckId: StateFlow<String?> = _currentDeckId.asStateFlow()

    private val _deckEntries = MutableStateFlow<List<DeckEntryWithCard>>(emptyList())
    val deckEntries: StateFlow<List<DeckEntryWithCard>> = _deckEntries.asStateFlow()

    private val _currentDeck = MutableStateFlow<DeckEntity?>(null)
    val currentDeck: StateFlow<DeckEntity?> = _currentDeck.asStateFlow()

    private val _legendCard = MutableStateFlow<CardEntity?>(null)
    val legendCard: StateFlow<CardEntity?> = _legendCard.asStateFlow()

    private val _championCard = MutableStateFlow<CardEntity?>(null)
    val championCard: StateFlow<CardEntity?> = _championCard.asStateFlow()

    private val _validationErrors = MutableStateFlow<List<DeckValidationError>>(emptyList())
    val validationErrors: StateFlow<List<DeckValidationError>> = _validationErrors.asStateFlow()

    // Card browser for deck building
    private val _browserSearchQuery = MutableStateFlow("")
    val browserSearchQuery: StateFlow<String> = _browserSearchQuery.asStateFlow()

    private val _browserTypeFilter = MutableStateFlow<String?>(null)
    val browserTypeFilter: StateFlow<String?> = _browserTypeFilter.asStateFlow()

    private val _browserCards = MutableStateFlow<List<CardEntity>>(emptyList())
    val browserCards: StateFlow<List<CardEntity>> = _browserCards.asStateFlow()

    fun createDeck(name: String = "New Deck") {
        viewModelScope.launch {
            val deck = DeckEntity(id = UUID.randomUUID().toString(), name = name)
            deckDao.insertDeck(deck)
            Timber.d("Created deck: %s", name)
        }
    }

    fun deleteDeck(deck: DeckEntity) {
        viewModelScope.launch {
            deckDao.deleteDeck(deck)
            Timber.d("Deleted deck: %s", deck.name)
        }
    }

    fun loadDeck(deckId: String) {
        _currentDeckId.value = deckId
        viewModelScope.launch {
            val deck = deckDao.getDeckById(deckId) ?: return@launch
            _currentDeck.value = deck

            deck.legendCardId?.let { _legendCard.value = cardDao.getCardById(it) }
            deck.championCardId?.let { _championCard.value = cardDao.getCardById(it) }

            deckDao.getEntriesWithCardsForDeck(deckId).collect { entries ->
                _deckEntries.value = entries
                validateDeck()
            }
        }
        loadBrowserCards()
    }

    fun setLegend(card: CardEntity) {
        viewModelScope.launch {
            _legendCard.value = card
            _currentDeck.value?.let { deck ->
                val updated = deck.copy(
                    legendCardId = card.id,
                    lastModifiedDate = System.currentTimeMillis()
                )
                deckDao.updateDeck(updated)
                _currentDeck.value = updated
            }
            validateDeck()
        }
    }

    fun setChampion(card: CardEntity) {
        viewModelScope.launch {
            _championCard.value = card
            _currentDeck.value?.let { deck ->
                val updated = deck.copy(
                    championCardId = card.id,
                    lastModifiedDate = System.currentTimeMillis()
                )
                deckDao.updateDeck(updated)
                _currentDeck.value = updated
            }
            validateDeck()
        }
    }

    fun addCardToDeck(card: CardEntity, section: DeckSection) {
        val deckId = _currentDeckId.value ?: return
        viewModelScope.launch {
            val existing = deckDao.findEntry(deckId, card.id, section.value)
            if (existing != null) {
                deckDao.updateEntry(existing.copy(quantity = existing.quantity + 1))
            } else {
                deckDao.insertEntry(
                    DeckEntryEntity(
                        deckId = deckId,
                        cardId = card.id,
                        section = section.value
                    )
                )
            }
            updateLastModified()
        }
    }

    fun removeCardFromDeck(entry: DeckEntryEntity) {
        viewModelScope.launch {
            if (entry.quantity > 1) {
                deckDao.updateEntry(entry.copy(quantity = entry.quantity - 1))
            } else {
                deckDao.deleteEntry(entry)
            }
            updateLastModified()
        }
    }

    fun updateDeckName(name: String) {
        viewModelScope.launch {
            _currentDeck.value?.let { deck ->
                val updated = deck.copy(name = name, lastModifiedDate = System.currentTimeMillis())
                deckDao.updateDeck(updated)
                _currentDeck.value = updated
            }
        }
    }

    /**
     * Check if a card can be added to a specific section.
     */
    fun canAddCard(card: CardEntity, section: DeckSection): Boolean {
        val entries = _deckEntries.value

        // Check copy limit
        val currentCopies = entries
            .filter { it.card.cleanName == card.cleanName }
            .sumOf { it.entry.quantity }
        if (currentCopies >= Constants.Deck.MAX_COPIES_PER_NAME) return false

        // Check domain identity
        val legend = _legendCard.value
        if (legend != null && card.domains.isNotEmpty()) {
            val deckDomains = legend.domains.toSet()
            if (!card.domains.all { it in deckDomains }) return false
        }

        return true
    }

    // Browser
    fun setBrowserSearchQuery(query: String) {
        _browserSearchQuery.value = query
        loadBrowserCards()
    }

    fun setBrowserTypeFilter(type: String?) {
        _browserTypeFilter.value = type
        loadBrowserCards()
    }

    private fun loadBrowserCards() {
        viewModelScope.launch {
            val allCards = cardDao.getAllCardsList()
            val query = _browserSearchQuery.value.lowercase()
            val typeFilter = _browserTypeFilter.value

            val filtered = allCards.filter { card ->
                val matchesQuery = query.isBlank() ||
                        card.name.lowercase().contains(query) ||
                        card.publicCode.lowercase().contains(query)

                val matchesType = typeFilter == null || card.type == typeFilter

                // Domain identity filter
                val legend = _legendCard.value
                val matchesDomain = legend == null ||
                        card.domains.isEmpty() ||
                        card.domains.all { it in legend.domains }

                matchesQuery && matchesType && matchesDomain
            }

            _browserCards.value = filtered
        }
    }

    private fun validateDeck() {
        val deck = _currentDeck.value ?: return
        val legend = _legendCard.value
        val champion = _championCard.value
        val entries = _deckEntries.value.map { it.entry to it.card }

        _validationErrors.value = DeckValidator.validate(
            DeckValidator.DeckData(legend, champion, entries)
        )
    }

    private suspend fun updateLastModified() {
        _currentDeck.value?.let { deck ->
            val updated = deck.copy(lastModifiedDate = System.currentTimeMillis())
            deckDao.updateDeck(updated)
            _currentDeck.value = updated
        }
    }

    /**
     * Export deck as TTS format.
     */
    fun exportAsTTS(): String {
        val entries = _deckEntries.value
        val lines = mutableListOf<String>()

        val legend = _legendCard.value
        val champion = _championCard.value

        if (legend != null) lines.add("1 ${legend.name} (${legend.setId}) ${legend.collectorNumber}")
        if (champion != null) lines.add("1 ${champion.name} (${champion.setId}) ${champion.collectorNumber}")

        for (section in DeckSection.entries) {
            val sectionEntries = entries.filter { it.entry.section == section.value }
            if (sectionEntries.isNotEmpty()) {
                lines.add("")
                lines.add("// ${section.name}")
                for ((entry, card) in sectionEntries) {
                    lines.add("${entry.quantity} ${card.name} (${card.setId}) ${card.collectorNumber}")
                }
            }
        }

        return lines.joinToString("\n")
    }

    /**
     * Export deck as plain text.
     */
    fun exportAsText(): String {
        val entries = _deckEntries.value
        val deck = _currentDeck.value
        val lines = mutableListOf<String>()

        lines.add("Deck: ${deck?.name ?: "Untitled"}")
        lines.add("")

        val legend = _legendCard.value
        val champion = _championCard.value
        if (legend != null) lines.add("Legend: ${legend.name}")
        if (champion != null) lines.add("Champion: ${champion.name}")
        lines.add("")

        for (section in DeckSection.entries) {
            val sectionEntries = entries.filter { it.entry.section == section.value }
            if (sectionEntries.isNotEmpty()) {
                lines.add("${section.name}:")
                for ((entry, card) in sectionEntries) {
                    lines.add("  ${entry.quantity}x ${card.name}")
                }
                lines.add("")
            }
        }

        return lines.joinToString("\n")
    }
}
