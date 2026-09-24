package com.scanrift.android.ui.collection

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardCondition
import com.scanrift.android.domain.model.CollectionEntry
import org.junit.Test

class SelectionExporterTest {

    private fun card(id: String, name: String, number: Int) = Card(
        id = id, name = name, riftboundId = "ogn-$number-298", publicCode = "OGN-$number/298",
        collectorNumber = number, type = "Unit", rarity = "Common", setId = "OGN", setLabel = "Origins",
        sourceSetId = "OGN", cleanName = name,
    )

    private val ahri = card("b-ahri", "Ahri", 21)
    private val jinx = card("a-jinx", "Jinx", 7)

    private fun entry(card: Card, quantity: Int, isFoil: Boolean) = CollectionEntry(
        id = if (isFoil) 2 else 1, cardId = card.id, card = card, quantity = quantity, isFoil = isFoil,
        dateAdded = 1_700_000_000_000, condition = CardCondition.NEAR_MINT,
    )

    private val rows = listOf(
        DisplayCard(ahri, entry(ahri, 2, isFoil = false)),
        DisplayCard(ahri, entry(ahri, 1, isFoil = true)),
        DisplayCard(jinx, null),
    )

    @Test
    fun `riftbound gg groups by card id, sums normal and foil, and keeps unowned rows at zero`() {
        assertThat(SelectionExporter.riftboundGg(rows)).isEqualTo(
            "CardId, Normal, Foil, Name, Set\n" +
                "OGN-007, 0, 0, \"Jinx\", \"Origins\"\n" +
                "OGN-021, 2, 1, \"Ahri\", \"Origins\"\n",
        )
    }

    @Test
    fun `csv writes unowned rows with zero quantity and empty fields`() {
        val csv = SelectionExporter.csv(rows).lines()
        assertThat(csv[0]).isEqualTo("Name,Set,Rarity,Quantity,Condition,Date Added")
        assertThat(csv[1]).isEqualTo("\"Ahri\",\"Origins\",\"Common\",2,\"Near Mint\",\"2023-11-14T22:13:20Z\"")
        assertThat(csv[3]).isEqualTo("\"Jinx\",\"Origins\",\"Common\",0,\"\",\"\"")
    }

    @Test
    fun `json omits condition and date for unowned rows`() {
        val json = SelectionExporter.json(listOf(DisplayCard(jinx, null)))
        assertThat(json).contains("\"quantity\": 0")
        assertThat(json).doesNotContain("condition")
        assertThat(json).doesNotContain("date_added")
    }
}
