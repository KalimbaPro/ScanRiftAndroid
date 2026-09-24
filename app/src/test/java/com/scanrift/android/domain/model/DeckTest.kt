package com.scanrift.android.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeckTest {

    private fun card(id: String, name: String = id, type: String = CardType.UNIT, energy: Int? = null) = Card(
        id = id, name = name, riftboundId = "ven-001-166", publicCode = "VEN-001/166", collectorNumber = 1,
        energy = energy, type = type, rarity = "Common", setId = "VEN", setLabel = "Vendetta", sourceSetId = "VEN",
        cleanName = name,
    )

    private fun entry(card: Card, quantity: Int = 1, section: DeckSection = DeckSection.MAIN_DECK) =
        DeckEntry(deckId = "d", cardId = card.id, card = card, quantity = quantity, section = section)

    private fun deck(legend: Card? = null, champion: Card? = null, entries: List<DeckEntry> = emptyList()) =
        Deck(id = "d", createdDate = 0, lastModifiedDate = 0, legend = legend, champion = champion, entries = entries)

    @Test
    fun `main deck sorts by energy, then type, then name`() {
        val spell = card("s", "Bolt", CardType.SPELL, energy = 1)
        val unitB = card("b", "Brute", energy = 1)
        val unitA = card("a", "Archer", energy = 1)
        val free = card("f", "Zed", energy = null)
        val sorted = deck(entries = listOf(entry(spell), entry(unitB), entry(unitA), entry(free)))
            .sortedEntries(DeckSection.MAIN_DECK)
        assertThat(sorted.map { it.card?.name }).containsExactly("Zed", "Bolt", "Archer", "Brute").inOrder()
    }

    @Test
    fun `other sections sort by name`() {
        val runes = deck(
            entries = listOf(
                entry(card("r2", "Mind Rune", CardType.RUNE), section = DeckSection.RUNE),
                entry(card("r1", "Fury Rune", CardType.RUNE), section = DeckSection.RUNE),
            ),
        ).sortedEntries(DeckSection.RUNE)
        assertThat(runes.map { it.card?.name }).containsExactly("Fury Rune", "Mind Rune").inOrder()
    }

    @Test
    fun `total counts both slots on top of the entries`() {
        val champion = card("c")
        val total = deck(card("l", type = CardType.LEGEND), champion, listOf(entry(champion, 2))).totalCardCount
        assertThat(total).isEqualTo(4)
    }

    @Test
    fun `a champion alone does not count as contents`() {
        assertThat(deck(champion = card("c")).hasContents).isFalse()
        assertThat(deck(legend = card("l", type = CardType.LEGEND)).hasContents).isTrue()
    }

    @Test
    fun `missing cards compare needed copies against owned copies by exact card id`() {
        val legend = card("l", "Legend", CardType.LEGEND)
        val unit = card("u", "Unit")
        val rune = card("r", "Rune", CardType.RUNE)
        val deck = deck(
            legend = legend,
            entries = listOf(
                entry(unit, 2),
                entry(unit, 1, DeckSection.SIDEBOARD),
                entry(rune, 6, DeckSection.RUNE),
            ),
        )
        val missing = deck.missingCards(mapOf("u" to 1, "r" to 6, "u-alt" to 5))
        assertThat(missing).containsExactly(MissingCard(legend, 1), MissingCard(unit, 2)).inOrder()
    }
}
