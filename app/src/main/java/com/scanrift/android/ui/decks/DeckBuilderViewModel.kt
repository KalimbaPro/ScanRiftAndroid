package com.scanrift.android.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.data.repository.CardListRepository
import com.scanrift.android.data.repository.CollectionRepository
import com.scanrift.android.data.repository.DeckRepository
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardList
import com.scanrift.android.domain.model.CardSupertype
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckEntry
import com.scanrift.android.domain.model.DeckSection
import com.scanrift.android.domain.model.MissingCard
import com.scanrift.android.service.deck.DeckValidationError
import com.scanrift.android.service.deck.DeckValidator
import com.scanrift.android.service.export.CollectionExporter
import com.scanrift.android.service.importer.DeckImportResult
import com.scanrift.android.service.importer.DeckImportService
import com.scanrift.android.service.importer.DeckListParser
import com.scanrift.android.service.importer.ParsedDeckList
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BrowserFilters(
    val searchQuery: String = "",
    val typeFilter: String? = null,
    val showAllCards: Boolean = false,
)

data class DeckBuilderState(
    val deck: Deck? = null,
    val browserCards: List<Card> = emptyList(),
    val validationErrors: List<DeckValidationError> = emptyList(),
    val filters: BrowserFilters = BrowserFilters(),
    val missingCards: List<MissingCard> = emptyList(),
) {
    val missingCount: Int get() = missingCards.sumOf { it.deficit }

    fun addBlockReason(card: Card): String? =
        deck?.let { DeckValidator.addBlockReason(it, card, filters.showAllCards) } ?: "Cannot add"

    fun entryFor(card: Card): DeckEntry? = deck?.entries?.firstOrNull { it.cardId == card.id }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DeckBuilderViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val deckImportService: DeckImportService,
    private val cardListRepository: CardListRepository,
    collectionRepository: CollectionRepository,
) : ViewModel() {

    private val deckId = MutableStateFlow<String?>(null)
    private val filters = MutableStateFlow(BrowserFilters())

    private val deck = deckId.flatMapLatest { id ->
        if (id == null) flowOf(null) else deckRepository.observeDeck(id)
    }

    private val allCards = collectionRepository.observeAllCards()

    val state: StateFlow<DeckBuilderState> = combine(
        deck,
        allCards,
        filters,
        collectionRepository.observeOwnedQuantities(),
    ) { currentDeck, cards, currentFilters, owned ->
        DeckBuilderState(
            deck = currentDeck,
            browserCards = filterBrowser(cards, currentDeck, currentFilters),
            validationErrors = currentDeck?.let { DeckValidator.validate(it) }.orEmpty(),
            filters = currentFilters,
            missingCards = currentDeck?.missingCards(owned).orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckBuilderState())

    val customLists: StateFlow<List<CardList>> = cardListRepository.observeLists()
        .map { lists -> lists.filterNot { it.isSystem }.sortedBy { it.createdDate } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(id: String) {
        if (deckId.value == id) return
        deckId.value = id
        viewModelScope.launch {
            if (deckRepository.observeDeck(id).first()?.legend == null) setTypeFilter(LEGEND_FILTER)
        }
    }

    fun setSearchQuery(value: String) { filters.value = filters.value.copy(searchQuery = value) }
    fun setTypeFilter(value: String?) { filters.value = filters.value.copy(typeFilter = value) }
    fun setShowAllCards(value: Boolean) { filters.value = filters.value.copy(showAllCards = value) }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) withDeck { deckRepository.rename(it.id, trimmed) }
    }

    fun handleBrowserTap(card: Card) {
        if (card.type == CardType.LEGEND) setLegend(card) else addCard(card)
    }

    fun setLegend(card: Card) = withDeck { deck ->
        setTypeFilter(if (deck.champion == null) CHAMPION_FILTER else null)
        deckRepository.setLegend(deck.id, card.id)
    }

    fun clearLegend() = withDeck { deckRepository.setLegend(it.id, null) }

    fun setChampion(card: Card) = withDeck { deck ->
        setTypeFilter(null)
        deckRepository.setChampion(deck.id, card)
    }

    fun clearChampion() = withDeck { deckRepository.setChampion(it.id, null) }

    fun addCard(card: Card, section: DeckSection? = null) = withDeck { deck ->
        deckRepository.addCard(deck, card, section ?: DeckValidator.inferSection(card))
    }

    fun setQuantity(entry: DeckEntry, quantity: Int) = withDeck { deck ->
        deckRepository.setEntryQuantity(deck, entry, quantity.coerceAtMost(DeckValidator.maxQuantity(deck, entry)))
    }

    fun remove(entry: DeckEntry) = withDeck { deckRepository.setEntryQuantity(it, entry, 0) }

    fun copyToSection(entry: DeckEntry, section: DeckSection) = withDeck { deck ->
        val card = entry.card ?: return@withDeck
        if (DeckValidator.canCopy(deck, card, section)) deckRepository.addCard(deck, card, section)
    }

    fun moveToSection(entry: DeckEntry, section: DeckSection) = withDeck { deck ->
        if (DeckValidator.canMove(deck, entry, section)) deckRepository.moveToSection(deck, entry, section)
    }

    fun addMissingCardsToWishlist() {
        val cardIds = state.value.missingCards.map { it.card.id }
        viewModelScope.launch {
            cardListRepository.wishlistId()?.let { cardListRepository.addCards(it, cardIds) }
        }
    }

    fun toggleList(list: CardList, cards: List<Card>) {
        val inList = list.cards.map { it.id }.toSet()
        viewModelScope.launch {
            if (cards.all { it.id in inList }) {
                cards.forEach { cardListRepository.removeCard(list.id, it.id) }
            } else {
                cardListRepository.addCards(list.id, cards.map { it.id }.filterNot { it in inList })
            }
        }
    }

    fun createList(name: String, colorHex: String) {
        viewModelScope.launch { cardListRepository.createList(name, colorHex) }
    }

    /** Set when an import finishes, so the screen can report what happened once. */
    private val _importResult = MutableStateFlow<DeckImportResult?>(null)
    val importResult: StateFlow<DeckImportResult?> = _importResult

    fun clearImportResult() { _importResult.value = null }

    /** Null until the deck has loaded — there is nothing to share before then. */
    fun exportAsText(): String? = state.value.deck?.let { CollectionExporter.exportAsText(it) }

    fun exportAsTts(): String? = state.value.deck?.let { CollectionExporter.exportAsTts(it) }

    fun importText(raw: String) = runImport { DeckListParser.parseText(raw) }

    fun importTts(raw: String) = runImport { DeckListParser.parseTts(raw) }

    private fun runImport(parse: () -> ParsedDeckList) {
        val id = deckId.value ?: return
        viewModelScope.launch {
            _importResult.value = deckImportService.import(id, parse())
        }
    }

    /**
     * The browser's filter chain.
     *
     * A legend and its champions are the same character — and the data makes that
     * easy: every legend carries exactly one tag, the character's name, and every
     * champion carries that same tag plus its regions. So "Ahri's champions" is just a
     * tag intersection, no name parsing required.
     *
     * The two slots constrain each other in both directions, which is what makes the
     * builder usable from either end:
     *
     * - **Legend chosen, champion empty** -> the Champion tab shows only that
     *   character's champions.
     * - **Champion chosen, legend empty** -> the Legend tab shows only that character's
     *   legends.
     * - **Neither chosen** -> both tabs show everything, so you can start from
     *   whichever you have in mind. (Showing nothing here was the bug that made the
     *   Champion tab look empty.)
     */
    private fun filterBrowser(allCards: List<Card>, deck: Deck?, filters: BrowserFilters): List<Card> {
        val legend = deck?.legend
        val champion = deck?.champion
        val legendTags = legend?.tags?.toSet().orEmpty()
        val championTags = champion?.tags?.toSet().orEmpty()
        val type = filters.typeFilter
        val normalizedQuery = filters.searchQuery.trim().lowercase()

        return allCards.asSequence()
            .filter { card -> matchesTab(card, type, legendTags, championTags) }
            .filter { card -> legend == null || type == LEGEND_FILTER || card.type != CardType.LEGEND }
            .filter { card ->
                // A signature card belongs to one character. With no legend chosen we
                // cannot know which, so they all stay visible rather than all vanish.
                if (!card.signature || legendTags.isEmpty()) return@filter true
                legendTags.intersect(card.tags.toSet()).isNotEmpty()
            }
            .filter { card ->
                // Same carve-out the validator makes: battlefields and colourless cards
                // are legal in any deck.
                filters.showAllCards || card.type == CardType.BATTLEFIELD ||
                    DeckValidator.isCardLegalForDeck(card, legend)
            }
            .filter { card ->
                normalizedQuery.isEmpty() ||
                    card.name.lowercase().contains(normalizedQuery) ||
                    card.type.lowercase().contains(normalizedQuery) ||
                    card.plainText?.lowercase()?.contains(normalizedQuery) == true
            }
            // Alternate-art printings would otherwise double every entry.
            .filterNot { it.isAlternateArt }
            .sortedBy { it.name }
            .toList()
    }

    private fun matchesTab(
        card: Card,
        type: String?,
        legendTags: Set<String>,
        championTags: Set<String>,
    ): Boolean = when (type) {
        LEGEND_FILTER -> {
            if (card.type != CardType.LEGEND) {
                false
            } else if (championTags.isEmpty()) {
                true
            } else {
                // A champion is already chosen, so only that character's legends apply.
                card.tags.toSet().intersect(championTags).isNotEmpty()
            }
        }
        CHAMPION_FILTER -> {
            if (card.type != CardType.UNIT || card.supertype != CardSupertype.CHAMPION) {
                false
            } else if (legendTags.isEmpty()) {
                true
            } else {
                card.tags.toSet().intersect(legendTags).isNotEmpty()
            }
        }
        null -> true
        else -> card.type == type
    }

    private inline fun withDeck(crossinline block: suspend (Deck) -> Unit) {
        val currentDeck = state.value.deck ?: return
        viewModelScope.launch { block(currentDeck) }
    }

    companion object {
        /** Champions eligible for the current legend. Not a card type of its own. */
        const val CHAMPION_FILTER = "Champion"

        /** Legends eligible for the current champion. */
        const val LEGEND_FILTER = "Legend"

        /**
         * Tab order. Legend comes first because it is the choice everything else hangs
         * off — domain identity, which champions are legal, which signature cards are.
         */
        val browserFilters = listOf(
            LEGEND_FILTER, CHAMPION_FILTER,
            CardType.UNIT, CardType.SPELL, CardType.GEAR,
            CardType.RUNE, CardType.BATTLEFIELD,
        )
    }
}
