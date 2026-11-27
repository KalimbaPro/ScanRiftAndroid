package com.scanrift.android.ui.decks

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardType
import org.junit.Test

class DeckBrowserSortTest {

    private fun card(name: String, energy: Int?, setId: String = "OGN", number: Int = 1) = Card(
        id = name, name = name, riftboundId = "${setId.lowercase()}-00$number-200", publicCode = "$setId-00$number/200",
        collectorNumber = number, energy = energy, type = CardType.UNIT, rarity = "Common",
        setId = setId, setLabel = setId, sourceSetId = setId, cleanName = name,
    )

    private val zed = card("Zed", energy = 2, setId = "RAD")
    private val annie = card("Annie", energy = 2, setId = "VEN")
    private val legend = card("Legend", energy = null, number = 2)
    private val cheap = card("Cheap", energy = 1, number = 1)
    private val cards = listOf(zed, annie, legend, cheap)

    private fun sorted(sort: DeckBrowserSort) = cards.sortedWith(sort.comparator).map { it.name }

    @Test
    fun `stat sorts put cards without the stat last and break ties by name`() {
        assertThat(sorted(DeckBrowserSort.ENERGY)).containsExactly("Cheap", "Annie", "Zed", "Legend").inOrder()
    }

    @Test
    fun `number sort follows the set order`() {
        assertThat(sorted(DeckBrowserSort.NUMBER)).containsExactly("Cheap", "Legend", "Annie", "Zed").inOrder()
    }

    @Test
    fun `the browser defaults to number order`() {
        assertThat(BrowserFilters().sort).isEqualTo(DeckBrowserSort.NUMBER)
    }
}
