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

    fun addCard(card: Card, section: DeckSection = DeckValidator.inferSection(card)) =
        withDeck { deckRepository.addCard(it, card, section) }

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
     * The browser's filter chain, in iOS's order.
     *
     * The interesting parts: once a legend is chosen, other legends disappear unless
     * you filter for them explicitly; signature cards only show if they share a tag
     * with the legend; and domain identity hides off-colour cards unless "show all" is
     * on. Battlefields and colourless cards are always allowed through the domain
     * filter, which is the same carve-out the validator makes.
     */
    private fun filterBrowser(
        allCards: List<Card>,
        deck: Deck?,
        query: String,
        type: String?,
        showAll: Boolean,
    ): List<Card> {
        val legend = deck?.legend
        val legendTags = legend?.tags?.toSet().orEmpty()
        val legendDomains = legend?.domains?.toSet().orEmpty()
        val normalizedQuery = query.trim().lowercase()

        return allCards.asSequence()
            .filter { card ->
                when {
                    type == CHAMPION_FILTER ->
                        card.type == CardType.UNIT && card.supertype == CardSupertype.CHAMPION &&
                            legendTags.intersect(card.tags.toSet()).isNotEmpty()
                    type != null -> card.type == type
                    // With a legend set, hide the other legends from the general browser.
                    legend != null -> card.type != CardType.LEGEND
                    else -> true
                }
            }
            .filter { card ->
                if (!card.signature) return@filter true
                legendTags.isNotEmpty() && legendTags.intersect(card.tags.toSet()).isNotEmpty()
            }
            .filter { card ->
                if (showAll || legend == null) return@filter true
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
            // Alternate-art printings would otherwise double every entry in the browser.
            .filterNot { it.isAlternateArt }
            .sortedBy { it.name }
            .toList()
    }

    private inline fun withDeck(crossinline block: suspend (Deck) -> Unit) {
        val currentDeck = state.value.deck ?: return
        viewModelScope.launch { block(currentDeck) }
    }

    companion object {
        /** Pseudo-filter: champions eligible for the current legend. */
        const val CHAMPION_FILTER = "Champion"

        val browserFilters = listOf(
            CardType.UNIT, CardType.SPELL, CardType.GEAR,
            CardType.RUNE, CardType.BATTLEFIELD, CardType.LEGEND, CHAMPION_FILTER,
        )
    }
}
