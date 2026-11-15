package com.scanrift.android.demo

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.scanrift.android.core.Constants
import com.scanrift.android.domain.model.CardLanguage
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckEntry
import com.scanrift.android.domain.model.DeckSection
import com.scanrift.android.domain.model.ScoreCategory
import com.scanrift.android.service.deck.DeckValidationError
import com.scanrift.android.service.deck.DeckValidator
import com.scanrift.android.service.scanning.SeedCatalogue
import org.junit.Test

/**
 * The screenshot demo data against the bundled catalogue, so a catalogue refresh that
 * drops or renames a card fails here rather than halfway through a screenshot session.
 */
class DemoDataTest {

    private fun deck(demo: DemoData.DemoDeck): Deck = Deck(
        id = demo.name,
        name = demo.name,
        createdDate = 0,
        lastModifiedDate = 0,
        legend = SeedCatalogue.card(demo.legend),
        champion = SeedCatalogue.card(demo.champion),
        entries = demo.main.map { (rid, qty) -> entry(demo, rid, qty, DeckSection.MAIN_DECK) } +
            demo.runes.map { (rid, qty) -> entry(demo, rid, qty, DeckSection.RUNE) } +
            demo.battlefields.map { entry(demo, it, 1, DeckSection.BATTLEFIELD) },
    )

    private fun entry(demo: DemoData.DemoDeck, rid: String, quantity: Int, section: DeckSection): DeckEntry {
        val card = SeedCatalogue.card(rid)
        return DeckEntry(deckId = demo.name, cardId = card.id, card = card, quantity = quantity, section = section)
    }

    @Test
    fun `every card the demo names is in the bundled catalogue`() {
        val ids = DemoData.collection.map { it.riftboundId } +
            DemoData.decks.flatMap { d -> listOf(d.legend, d.champion) + d.main.map { it.first } + d.runes.map { it.first } + d.battlefields } +
            DemoData.games.flatMap { listOf(it.legend, it.opponentLegend) } +
            (DemoData.seats + DemoData.fourPlayerSeats).map { it.legend } +
            DemoData.wishlist
        ids.distinct().forEach { SeedCatalogue.card(it) } // single() throws on a miss
    }

    @Test
    fun `collection stacks are unique by the collection's own key`() {
        val keys = DemoData.collection.map { Triple(it.riftboundId, it.foil, it.language) }
        assertThat(keys).containsNoDuplicates()
    }

    @Test
    fun `the first two decks are legal and the third is illegal only for its bans`() {
        val (lillia, irelia, leona) = DemoData.decks.map(::deck)
        assertThat(DeckValidator.validate(lillia)).isEmpty()
        assertThat(DeckValidator.validate(irelia)).isEmpty()
        val leonaErrors = DeckValidator.validate(leona)
        assertThat(leonaErrors).hasSize(1)
        assertThat(leonaErrors.single()).isInstanceOf(DeckValidationError.BannedCards::class.java)
    }

    @Test
    fun `the collection owns enough copies for every deck, so none shows missing cards`() {
        val owned = DemoData.collection.groupBy { it.riftboundId }.mapValues { (_, stacks) -> stacks.sumOf { it.quantity } }
        DemoData.decks.forEach { d ->
            val needed = (d.main + d.runes + d.battlefields.map { it to 1 } + (d.legend to 1))
                .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            needed.forEach { (rid, qty) ->
                assertWithMessage("${d.name}: $rid").that(owned[rid] ?: 0).isAtLeast(qty)
            }
        }
    }

    @Test
    fun `the collection shows a banned errata card and all four languages`() {
        val zhonya = SeedCatalogue.card("ogn-077-298")
        assertThat(zhonya.banned).isTrue()
        assertThat(zhonya.errata).isNotNull()
        assertThat(DemoData.collection.map { it.riftboundId }).contains("ogn-077-298")
        assertThat(DemoData.collection.map { it.language }.toSet()).containsExactlyElementsIn(CardLanguage.entries)
    }

    @Test
    fun `the four-player game has four distinct legends, real decks and a game in progress`() {
        val seats = DemoData.fourPlayerSeats
        assertThat(seats).hasSize(Constants.PointTracker.MAX_PLAYERS)
        assertThat(seats.map { it.name }).containsNoDuplicates()

        val legends = seats.map { SeedCatalogue.card(it.legend) }
        legends.forEach { assertWithMessage(it.name).that(it.type).isEqualTo(CardType.LEGEND) }
        assertThat(legends.map { it.cleanName }).containsNoDuplicates()
        assertThat(seats.map { it.legend }).contains("sgn-001-003") // Secret Garden Lillia

        val deckNames = DemoData.decks.map { it.name }
        seats.mapNotNull { it.deck }.forEach { assertThat(deckNames).contains(it) }

        val scores = seats.map { it.scoreLog.size }
        assertThat(scores).containsNoDuplicates()
        scores.forEach { assertThat(it).isIn(1 until Constants.PointTracker.VICTORY_POINTS) }
        seats.forEach { seat ->
            assertWithMessage("${seat.name} mixes categories").that(seat.scoreLog.toSet().size).isAtLeast(2)
            assertThat(ScoreCategory.entries.sumOf(seat::count)).isEqualTo(seat.scoreLog.size)
            assertThat(seat.xp).isIn(1..Constants.PointTracker.XP_MAX)
        }
    }
}
