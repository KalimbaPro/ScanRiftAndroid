package com.scanrift.android.service.deck

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckEntry
import com.scanrift.android.domain.model.DeckSection
import org.junit.Test

class DeckStatsTest {

    private var nextId = 0

    private fun card(
        type: String = CardType.UNIT,
        energy: Int? = null,
        power: Int? = null,
        domains: List<String> = listOf("Fury"),
    ) = Card(
        id = "id-${nextId++}", name = "Card $nextId", riftboundId = "ven-001-166", publicCode = "VEN-001/166",
        collectorNumber = 1, energy = energy, power = power, type = type, rarity = "Common", domains = domains,
        setId = "VEN", setLabel = "Vendetta", sourceSetId = "VEN", cleanName = "Card $nextId",
    )

    private fun deck(vararg entries: Pair<Card, Pair<Int, DeckSection>>) = Deck(
        id = "d", createdDate = 0, lastModifiedDate = 0,
        entries = entries.map { (card, placement) ->
            DeckEntry(deckId = "d", cardId = card.id, card = card, quantity = placement.first, section = placement.second)
        },
    )

    @Test
    fun `only main deck and sideboard are analysed`() {
        val stats = DeckStats.of(
            deck(
                card(energy = 2) to (3 to DeckSection.MAIN_DECK),
                card(energy = 4) to (1 to DeckSection.SIDEBOARD),
                card(type = CardType.RUNE, energy = 0) to (12 to DeckSection.RUNE),
                card(type = CardType.BATTLEFIELD) to (1 to DeckSection.BATTLEFIELD),
            ),
        )
        assertThat(stats.cardCount).isEqualTo(4)
        assertThat(stats.averageEnergy).isWithin(1e-9).of(2.5)
    }

    @Test
    fun `averages are quantity weighted over cards that have the stat`() {
        val stats = DeckStats.of(
            deck(
                card(power = 2) to (2 to DeckSection.MAIN_DECK),
                card(power = 5) to (1 to DeckSection.MAIN_DECK),
                card(power = null) to (3 to DeckSection.MAIN_DECK),
            ),
        )
        assertThat(stats.averagePower).isWithin(1e-9).of(3.0)
        assertThat(stats.averageEnergy).isEqualTo(0.0)
    }

    @Test
    fun `the energy curve always has buckets 0 to 6 and 7+`() {
        val stats = DeckStats.of(
            deck(
                card(energy = 9, domains = listOf("Calm", "Fury")) to (2 to DeckSection.MAIN_DECK),
                card(energy = 1, domains = emptyList()) to (1 to DeckSection.MAIN_DECK),
            ),
        )
        assertThat(stats.energyCurve.map { it.bucket }.distinct())
            .containsExactly("0", "1", "2", "3", "4", "5", "6", "7+").inOrder()
        assertThat(stats.energyCurve).contains(CurveSegment("7+", "Calm", 2))
        assertThat(stats.energyCurve).contains(CurveSegment("1", DeckStats.NEUTRAL, 1))
        assertThat(stats.energyCurve).contains(CurveSegment("0", DeckStats.NEUTRAL, 0))
    }

    @Test
    fun `the power curve has only the values present, numerically sorted`() {
        val stats = DeckStats.of(
            deck(
                card(power = 10) to (1 to DeckSection.MAIN_DECK),
                card(power = 2) to (1 to DeckSection.MAIN_DECK),
            ),
        )
        assertThat(stats.powerCurve.map { it.bucket }).containsExactly("2", "10").inOrder()
    }

    @Test
    fun `a multi-domain card counts once per domain and distributions sort by count`() {
        val stats = DeckStats.of(
            deck(
                card(domains = listOf("Calm", "Fury")) to (2 to DeckSection.MAIN_DECK),
                card(type = CardType.SPELL, domains = listOf("Fury")) to (1 to DeckSection.MAIN_DECK),
            ),
        )
        assertThat(stats.domains).containsExactly(StatEntry("Fury", 3), StatEntry("Calm", 2)).inOrder()
        assertThat(stats.types).containsExactly(StatEntry(CardType.UNIT, 2), StatEntry(CardType.SPELL, 1)).inOrder()
    }
}
