package com.scanrift.android.service.export

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardCondition
import com.scanrift.android.domain.model.CollectionEntry
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckEntry
import com.scanrift.android.domain.model.DeckSection
import com.scanrift.android.domain.model.GameRecord
import com.scanrift.android.domain.model.GameResult
import org.junit.Test

/**
 * Export formats are an interop contract, not an implementation detail: riftbound.gg
 * parses the CSV it emitted, so the odd-looking spacing has to survive refactors.
 */
class CollectionExporterTest {

    private fun card(
        id: String = "c1",
        name: String = "Akali, Deadly Weapon",
        setId: String = "VEN",
        setLabel: String = "Vendetta",
        collectorNumber: Int = 21,
        rarity: String = "Epic",
        publicCode: String = "VEN-021/166",
    ) = Card(
        id = id, name = name, riftboundId = "ven-021-166", publicCode = publicCode,
        collectorNumber = collectorNumber, type = "Unit", rarity = rarity,
        setId = setId, setLabel = setLabel, sourceSetId = setId, cleanName = name,
    )

    private fun entry(
        card: Card,
        quantity: Int = 1,
        isFoil: Boolean = false,
        condition: CardCondition = CardCondition.NEAR_MINT,
        dateAdded: Long = 1_780_000_000_000L,
    ) = CollectionEntry(
        cardId = card.id, card = card, quantity = quantity,
        isFoil = isFoil, dateAdded = dateAdded, condition = condition,
    )

    // ── riftbound.gg CSV ─────────────────────────────────────────────────────

    @Test
    fun `riftbound gg csv keeps the spaces after commas`() {
        val csv = CollectionExporter.exportAsRiftboundGg(listOf(entry(card(), quantity = 3)))

        assertThat(csv.lineSequence().first()).isEqualTo("CardId, Normal, Foil, Name, Set")
        assertThat(csv).contains("VEN-021, 3, 0, \"Akali, Deadly Weapon\", \"Vendetta\"")
    }

    @Test
    fun `riftbound gg csv sums normal and foil into one row per card`() {
        val c = card()
        val csv = CollectionExporter.exportAsRiftboundGg(
            listOf(
                entry(c, quantity = 2, isFoil = false),
                entry(c, quantity = 1, isFoil = false, condition = CardCondition.MINT),
                entry(c, quantity = 4, isFoil = true),
            ),
        )

        assertThat(csv.trim().lines()).hasSize(2)
        assertThat(csv).contains("VEN-021, 3, 4,")
    }

    @Test
    fun `riftbound gg card ids are zero padded`() {
        val csv = CollectionExporter.exportAsRiftboundGg(
            listOf(entry(card(collectorNumber = 7)), entry(card(id = "c2", collectorNumber = 298))),
        )
        assertThat(csv).contains("VEN-007, ")
        assertThat(csv).contains("VEN-298, ")
    }

    @Test
    fun `riftbound gg output is ordered by internal card id so exports are reproducible`() {
        // iOS sorts the grouped keys, which are the internal card ids, not the printed
        // CardId column. Two exports of the same collection must be byte-identical.
        val cards = listOf(card(id = "zzz", collectorNumber = 1), card(id = "aaa", collectorNumber = 2))
        val csv = CollectionExporter.exportAsRiftboundGg(cards.map { entry(it) })
        val reversed = CollectionExporter.exportAsRiftboundGg(cards.reversed().map { entry(it) })

        assertThat(csv).isEqualTo(reversed)
        // "aaa" sorts first, and it is the card numbered 002.
        assertThat(csv.trim().lines()[1]).startsWith("VEN-002")
    }

    @Test
    fun `an entry with no card is skipped rather than exported blank`() {
        val orphan = CollectionEntry(cardId = null, card = null, quantity = 1, dateAdded = 1L)
        assertThat(CollectionExporter.exportAsRiftboundGg(listOf(orphan)).trim().lines()).hasSize(1)
        assertThat(CollectionExporter.exportAsCsv(listOf(orphan)).trim().lines()).hasSize(1)
    }

    // ── Standard CSV ─────────────────────────────────────────────────────────

    @Test
    fun `standard csv header and row shape`() {
        val csv = CollectionExporter.exportAsCsv(listOf(entry(card(), quantity = 2)))

        assertThat(csv.lineSequence().first()).isEqualTo("Name,Set,Rarity,Quantity,Condition,Date Added")
        // Quantity is unquoted; every other field is quoted.
        assertThat(csv).contains("\"Akali, Deadly Weapon\",\"Vendetta\",\"Epic\",2,\"Near Mint\",\"2026-05-28T20:26:40Z\"")
    }

    @Test
    fun `standard csv dates carry no fractional seconds`() {
        val csv = CollectionExporter.exportAsCsv(listOf(entry(card(), dateAdded = 1_780_000_000_123L)))
        assertThat(csv).contains("2026-05-28T20:26:40Z")
        assertThat(csv).doesNotContain(".123")
    }

    // ── JSON ─────────────────────────────────────────────────────────────────

    @Test
    fun `json export carries the collection and the decks`() {
        val c = card()
        val legend = card(id = "legend", name = "Akali Legend")
        val deck = Deck(
            id = "d1", name = "Akali", createdDate = 1_780_000_000_000L, lastModifiedDate = 1_780_000_000_000L,
            legendCardId = legend.id, legend = legend,
            entries = listOf(DeckEntry(deckId = "d1", cardId = c.id, card = c, quantity = 3, section = DeckSection.MAIN_DECK)),
        )

        val json = CollectionExporter.exportAsJson(
            entries = listOf(entry(c, quantity = 2)),
            decks = listOf(deck),
            gameRecordsByDeck = mapOf(
                "d1" to listOf(
                    GameRecord(id = "g1", date = 1_780_000_100_000L, result = GameResult.WIN, pointsScored = 2),
                ),
            ),
            now = 1_780_000_000_000L,
        )

        assertThat(json).contains("\"exported_at\"")
        assertThat(json).contains("\"riftbound_id\": \"ven-021-166\"")
        assertThat(json).contains("\"legend_card_id\": \"legend\"")
        assertThat(json).contains("\"section\": \"mainDeck\"")
        assertThat(json).contains("\"result\": \"win\"")
        assertThat(json).contains("\"points_scored\": 2")
    }

    @Test
    fun `json omits absent optionals rather than writing null`() {
        val deck = Deck(id = "d1", name = "No Legend", createdDate = 1L, lastModifiedDate = 1L)
        val json = CollectionExporter.exportAsJson(emptyList(), listOf(deck), now = 1L)

        assertThat(json).doesNotContain("legend_card_id")
        assertThat(json).doesNotContain("null")
    }

    @Test
    fun `json history is ordered oldest first`() {
        val deck = Deck(id = "d1", name = "D", createdDate = 1L, lastModifiedDate = 1L)
        val json = CollectionExporter.exportAsJson(
            emptyList(), listOf(deck),
            gameRecordsByDeck = mapOf(
                "d1" to listOf(
                    GameRecord(id = "late", date = 3_000_000L, result = GameResult.LOSS),
                    GameRecord(id = "early", date = 1_000_000L, result = GameResult.WIN),
                ),
            ),
            now = 1L,
        )

        assertThat(json.indexOf("\"win\"")).isLessThan(json.indexOf("\"loss\""))
    }

    // ── Deck exports ─────────────────────────────────────────────────────────

    @Test
    fun `tts export emits one token per physical copy in section order`() {
        val legend = card(id = "l", publicCode = "VEN-001/166")
        val champion = card(id = "ch", publicCode = "VEN-002/166")
        val unit = card(id = "u", publicCode = "VEN-021/166")
        val rune = card(id = "r", publicCode = "VEN-R01/166")
        val deck = Deck(
            id = "d1", createdDate = 1L, lastModifiedDate = 1L,
            legend = legend, champion = champion,
            entries = listOf(
                DeckEntry(deckId = "d1", cardId = unit.id, card = unit, quantity = 2, section = DeckSection.MAIN_DECK),
                DeckEntry(deckId = "d1", cardId = rune.id, card = rune, quantity = 3, section = DeckSection.RUNE),
            ),
        )

        assertThat(CollectionExporter.exportAsTts(deck))
            .isEqualTo("VEN-001-1 VEN-002-1 VEN-021-1 VEN-021-1 VEN-R01-1 VEN-R01-1 VEN-R01-1")
    }

    @Test
    fun `text export groups by section with the legend's tag`() {
        val legend = card(id = "l", name = "Akali Legend").copy(tags = listOf("Akali"))
        val unit = card(id = "u", name = "Shadow Dancer")
        val deck = Deck(
            id = "d1", createdDate = 1L, lastModifiedDate = 1L, legend = legend,
            entries = listOf(
                DeckEntry(deckId = "d1", cardId = unit.id, card = unit, quantity = 3, section = DeckSection.MAIN_DECK),
            ),
        )

        val text = CollectionExporter.exportAsText(deck)
        assertThat(text).contains("Legend:\n1 Akali, Akali Legend")
        assertThat(text).contains("MainDeck:\n3 Shadow Dancer")
        // Empty sections are omitted entirely.
        assertThat(text).doesNotContain("Runes:")
    }
}
