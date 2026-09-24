package com.scanrift.android.ui.collection

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardCondition
import com.scanrift.android.domain.model.CardList
import com.scanrift.android.domain.model.CollectionEntry
import org.junit.Test

class CollectionQueryTest {

    private fun card(
        id: String,
        name: String = id,
        number: Int = 1,
        energy: Int? = null,
        power: Int? = null,
        artist: String? = null,
        supertype: String? = null,
        domains: List<String> = emptyList(),
        rarity: String = "Common",
    ) = Card(
        id = id, name = name, riftboundId = "ogn-$number-298", publicCode = "OGN-$number/298",
        collectorNumber = number, energy = energy, power = power, type = "Unit", supertype = supertype,
        rarity = rarity, domains = domains, setId = "OGN", setLabel = "Origins", sourceSetId = "OGN",
        artist = artist, cleanName = name,
    )

    private fun entry(card: Card, quantity: Int = 1, isFoil: Boolean = false, id: Long = 1, dateAdded: Long = 0) =
        CollectionEntry(
            id = id, cardId = card.id, card = card, quantity = quantity, isFoil = isFoil,
            dateAdded = dateAdded, condition = CardCondition.NEAR_MINT,
        )

    private val ahri = card("ahri", "Ahri", number = 2, energy = 3, power = 2, artist = "Lucho", domains = listOf("Calm"))
    private val jinx = card("jinx", "Jinx", number = 1, energy = 5, domains = listOf("Fury", "Chaos"))
    private val teemo = card("teemo", "Teemo", number = 3, supertype = "Champion")

    @Test
    fun `all collection shows one row per entry and one unowned row per unowned card`() {
        val entries = mapOf(ahri.id to listOf(entry(ahri, id = 1), entry(ahri, isFoil = true, id = 2)))
        val rows = CollectionQuery.displayRows(listOf(ahri, jinx), entries, isListContext = false, OwnershipFilter.ALL)
        assertThat(rows.map { it.card.id to it.isOwned })
            .containsExactly("ahri" to true, "ahri" to true, "jinx" to false).inOrder()
    }

    @Test
    fun `owned filter drops unowned cards before building rows`() {
        val entries = mapOf(ahri.id to listOf(entry(ahri)))
        val rows = CollectionQuery.displayRows(listOf(ahri, jinx), entries, isListContext = false, OwnershipFilter.OWNED)
        assertThat(rows.map { it.card.id }).containsExactly("ahri")
    }

    @Test
    fun `list context shows one row per card preferring the non-foil entry`() {
        val entries = mapOf(ahri.id to listOf(entry(ahri, isFoil = true, id = 2), entry(ahri, id = 1)))
        val rows = CollectionQuery.displayRows(listOf(ahri), entries, isListContext = true, OwnershipFilter.ALL)
        assertThat(rows).hasSize(1)
        assertThat(rows.single().entry?.isFoil).isFalse()
    }

    @Test
    fun `artist is searched only with the lucho parameter`() {
        val rows = listOf(DisplayCard(ahri, null), DisplayCard(jinx, null))
        assertThat(CollectionQuery.filter(rows, "lucho", OwnershipFilter.ALL, CollectionFilters(), false)).isEmpty()
        assertThat(CollectionQuery.filter(rows, "lucho", OwnershipFilter.ALL, CollectionFilters(), true).map { it.card.id })
            .containsExactly("ahri")
    }

    @Test
    fun `energy range excludes cards without energy and honours open bounds`() {
        val rows = listOf(DisplayCard(ahri, null), DisplayCard(jinx, null), DisplayCard(teemo, null))
        val atLeastFour = CollectionFilters(energyMin = 4)
        assertThat(CollectionQuery.filter(rows, "", OwnershipFilter.ALL, atLeastFour, false).map { it.card.id })
            .containsExactly("jinx")
        val upToFour = CollectionFilters(energyMax = 4)
        assertThat(CollectionQuery.filter(rows, "", OwnershipFilter.ALL, upToFour, false).map { it.card.id })
            .containsExactly("ahri")
    }

    @Test
    fun `supertype filter excludes cards with no supertype`() {
        val rows = listOf(DisplayCard(ahri, null), DisplayCard(teemo, null))
        val filters = CollectionFilters(supertypes = setOf("Champion"))
        assertThat(CollectionQuery.filter(rows, "", OwnershipFilter.ALL, filters, false).map { it.card.id })
            .containsExactly("teemo")
    }

    @Test
    fun `foil only keeps foil entries and drops unowned always-foil cards`() {
        val rare = card("rare", rarity = "Rare")
        val rows = listOf(DisplayCard(rare, null), DisplayCard(ahri, entry(ahri, isFoil = true)), DisplayCard(jinx, entry(jinx)))
        val result = CollectionQuery.filter(rows, "", OwnershipFilter.ALL, CollectionFilters(foilOnly = true), false)
        assertThat(result.map { it.card.id }).containsExactly("ahri")
    }

    @Test
    fun `domain filter matches any selected domain`() {
        val rows = listOf(DisplayCard(ahri, null), DisplayCard(jinx, null), DisplayCard(teemo, null))
        val filters = CollectionFilters(domains = setOf("Chaos", "Calm"))
        assertThat(CollectionQuery.filter(rows, "", OwnershipFilter.ALL, filters, false).map { it.card.id })
            .containsExactly("ahri", "jinx")
    }

    @Test
    fun `quantity sort ranks unowned below every owned card and descending inverts it`() {
        val rows = listOf(DisplayCard(jinx, null), DisplayCard(ahri, entry(ahri, quantity = 2)), DisplayCard(teemo, entry(teemo)))
        assertThat(CollectionQuery.sort(rows, CollectionSortOption.QUANTITY, true).map { it.card.id })
            .containsExactly("jinx", "teemo", "ahri").inOrder()
        assertThat(CollectionQuery.sort(rows, CollectionSortOption.QUANTITY, false).map { it.card.id })
            .containsExactly("ahri", "teemo", "jinx").inOrder()
    }

    @Test
    fun `facets count cards and only offer ranges with a spread`() {
        val facets = CollectionQuery.facets(listOf(ahri, jinx, teemo))
        assertThat(facets.domains.map { it.value }).containsExactly("Fury", "Calm", "Chaos").inOrder()
        assertThat(facets.types).containsExactly(Facet("Unit", 3))
        assertThat(facets.supertypes).containsExactly(Facet("Champion", 1))
        assertThat(facets.energy).isEqualTo(3..5)
        assertThat(facets.power).isNull()
        assertThat(facets.artists).containsExactly(Facet("Lucho", 1))
    }

    @Test
    fun `list membership removes all when every card is in and otherwise adds only the missing`() {
        fun list(vararg cards: Card) = CardList(id = "l", name = "L", createdDate = 0, cards = cards.toList())
        assertThat(list(ahri, jinx).membershipChange(listOf("ahri", "jinx")))
            .isEqualTo(emptyList<String>() to listOf("ahri", "jinx"))
        assertThat(list(ahri).membershipChange(listOf("ahri", "jinx")))
            .isEqualTo(listOf("jinx") to emptyList<String>())
    }

    @Test
    fun `preferred entry is the non-foil stack, falling back to the foil one`() {
        val foil = entry(ahri, isFoil = true, id = 2)
        val normal = entry(ahri, id = 1)
        assertThat(CollectionQuery.preferredEntry(listOf(foil, normal))).isEqualTo(normal)
        assertThat(CollectionQuery.preferredEntry(listOf(foil))).isEqualTo(foil)
        assertThat(CollectionQuery.preferredEntry(null)).isNull()
    }
}
