package com.scanrift.android.ui.decks

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckEntry
import com.scanrift.android.domain.model.DeckSection
import org.junit.Test

/**
 * The browser dims a card when [DeckBuilderState.addBlockReason] is non-null. It used to
 * be `deck?.let { validator } ?: "Cannot add"`, whose elvis also caught the validator's
 * "nothing blocks this" null — so every legal, addable card was dimmed.
 */
class DeckBuilderStateTest {

    private fun card(name: String, type: String = CardType.UNIT, domains: List<String> = listOf("Calm")) = Card(
        id = "id-$name", name = name, riftboundId = "unl-001-219", publicCode = "UNL-001/219",
        collectorNumber = 1, type = type, rarity = "Common", domains = domains,
        setId = "UNL", setLabel = "Unleashed", sourceSetId = "UNL", cleanName = name,
    )

    private val legend = card("Lillia - Bashful Bloom", CardType.LEGEND, listOf("Calm", "Mind"))

    private fun deck(vararg entries: Pair<Card, Int>) = Deck(
        id = "deck-1", createdDate = 0, lastModifiedDate = 0,
        legendCardId = legend.id, legend = legend,
        entries = entries.map { (card, quantity) ->
            DeckEntry(deckId = "deck-1", cardId = card.id, card = card, quantity = quantity, section = DeckSection.MAIN_DECK)
        },
    )

    @Test
    fun `a legal card with copies left is not blocked`() {
        val state = DeckBuilderState(deck = deck())
        assertThat(state.addBlockReason(card("Last Stand"))).isNull()
    }

    @Test
    fun `a card already at three copies is blocked`() {
        val charm = card("Charm")
        val state = DeckBuilderState(deck = deck(charm to 3))
        assertThat(state.addBlockReason(charm)).isEqualTo("Max 3 copies")
        assertThat(state.addBlockReason(card("Last Stand"))).isNull()
    }

    @Test
    fun `a card outside the legend's domains is blocked`() {
        val state = DeckBuilderState(deck = deck())
        assertThat(state.addBlockReason(card("Kinkou Monk", domains = listOf("Body")))).isEqualTo("Outside deck domains")
    }

    @Test
    fun `nothing can be added before the deck has loaded`() {
        assertThat(DeckBuilderState(deck = null).addBlockReason(card("Last Stand"))).isNotNull()
    }
}
