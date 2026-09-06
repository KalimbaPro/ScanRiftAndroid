package com.scanrift.android.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanrift.android.data.repository.CollectionRepository
import com.scanrift.android.data.repository.DeckRepository
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardSupertype
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckEntry
import com.scanrift.android.domain.model.DeckSection
import com.scanrift.android.service.deck.DeckValidationError
import com.scanrift.android.service.deck.DeckValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeckBuilderState(
    val deck: Deck? = null,
    val browserCards: List<Card> = emptyList(),
    val validationErrors: List<DeckValidationError> = emptyList(),
    val searchQuery: String = "",
    val typeFilter: String? = null,
    val showAllCards: Boolean = false,
) {
    val isValid: Boolean get() = deck != null && validationErrors.isEmpty()

    fun count(section: DeckSection): Int =
        deck?.entries?.filter { it.section == section }?.sumOf { it.quantity } ?: 0

    val totalCards: Int
        get() {
            val entries = deck?.entries?.sumOf { it.quantity } ?: 0
            return entries + (if (deck?.legend != null) 1 else 0)
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DeckBuilderViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    collectionRepository: CollectionRepository,
) : ViewModel() {

    private val deckId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val typeFilter = MutableStateFlow<String?>(null)
    private val showAllCards = MutableStateFlow(false)

    private val deck = deckId.flatMapLatest { id ->
        if (id == null) flowOf(null) else deckRepository.observeDeck(id)
    }

    val state: StateFlow<DeckBuilderState> = combine(
        deck,
        collectionRepository.observeAllCards(),
        searchQuery,
        typeFilter,
        showAllCards,
    ) { currentDeck, allCards, query, type, showAll ->
        DeckBuilderState(
            deck = currentDeck,
            browserCards = filterBrowser(allCards, currentDeck, query, type, showAll),
            validationErrors = currentDeck?.let { DeckValidator.validate(it) }.orEmpty(),
            searchQuery = query,
            typeFilter = type,
            showAllCards = showAll,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckBuilderState())

    fun load(id: String) { deckId.value = id }
    fun setSearchQuery(value: String) { searchQuery.value = value }
    fun setTypeFilter(value: String?) { typeFilter.value = value }
    fun setShowAllCards(value: Boolean) { showAllCards.value = value }

    fun rename(name: String) = withDeck { deckRepository.rename(it.id, name) }
    fun setLegend(card: Card?) = withDeck { deckRepository.setLegend(it.id, card?.id) }
    fun setChampion(card: Card?) = withDeck { deckRepository.setChampion(it.id, card) }

    /**
     * What tapping a card in the browser does.
     *
     * Legends and champions are **slots**, not deck entries: a deck has exactly one of
     * each, so tapping one fills or replaces the slot rather than adding a copy to the
     * main deck. Previously a legend tapped in the browser was routed to the main deck
     * by `inferSection`, which produced a deck with a legend among its 40 cards and no
     * legend set.
     */
    fun addCard(card: Card, section: DeckSection? = null) = withDeck { deck ->
        when {
            card.type == CardType.LEGEND -> deckRepository.setLegend(deck.id, card.id)
            section == null && card.isChampionUnit -> deckRepository.setChampion(deck.id, card)
            else -> deckRepository.addCard(deck, card, section ?: DeckValidator.inferSection(card))
        }
    }

    /** Explicitly promote a card already in the deck to the champion slot. */
    fun setChampionFromBrowser(card: Card) = withDeck { deckRepository.setChampion(it.id, card) }

    fun setQuantity(entry: DeckEntry, quantity: Int) =
        withDeck { deckRepository.setEntryQuantity(it, entry, quantity) }

    fun moveToSection(entry: DeckEntry, section: DeckSection) =
        withDeck { deckRepository.moveToSection(it, entry, section) }

    /** How many more copies of [card] the deck may legally take. */
    fun remainingCopies(card: Card): Int {
        val currentDeck = state.value.deck ?: return DeckValidator.maxCopies(card)
        return (DeckValidator.maxCopies(card) - DeckValidator.copiesInDeck(currentDeck, card)).coerceAtLeast(0)
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
    private fun filterBrowser(
        allCards: List<Card>,
        deck: Deck?,
        query: String,
        type: String?,
        showAll: Boolean,
    ): List<Card> {
        val legend = deck?.legend
        val champion = deck?.champion
        val legendTags = legend?.tags?.toSet().orEmpty()
        val championTags = champion?.tags?.toSet().orEmpty()
        val legendDomains = legend?.domains?.toSet().orEmpty()
        val normalizedQuery = query.trim().lowercase()

        return allCards.asSequence()
            .filter { card -> matchesTab(card, type, legendTags, championTags) }
            .filter { card ->
                // A signature card belongs to one character. With no legend chosen we
                // cannot know which, so they all stay visible rather than all vanish.
                if (!card.signature || legendTags.isEmpty()) return@filter true
                legendTags.intersect(card.tags.toSet()).isNotEmpty()
            }
            .filter { card ->
                if (showAll || legend == null) return@filter true
                // Same carve-out the validator makes: battlefields and colourless cards
                // are legal in any deck.
                card.type == CardType.BATTLEFIELD ||
                    card.domains.isEmpty() ||
                    legendDomains.containsAll(card.domains)
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
