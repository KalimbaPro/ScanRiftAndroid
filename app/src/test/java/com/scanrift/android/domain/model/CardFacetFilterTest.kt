package com.scanrift.android.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CardFacetFilterTest {

    private fun card(
        name: String,
        energy: Int? = null,
        power: Int? = null,
        might: Int? = null,
        setId: String = "OGN",
        setLabel: String = "Origins",
    ) = Card(
        id = name, name = name, riftboundId = "ogn-001-298", publicCode = "OGN-001/298", collectorNumber = 1,
        energy = energy, power = power, might = might, type = CardType.UNIT, rarity = Rarity.COMMON,
        domains = listOf(Domain.FURY), setId = setId, setLabel = setLabel, sourceSetId = setId, cleanName = name,
    )

    private val cheap = card("Cheap", energy = 2, might = 1)
    private val big = card("Big", energy = 9, power = 2, might = 8)
    private val legend = card("Legend")
    private val cards = listOf(cheap, big, legend)

    private fun names(filter: CardFacetFilter) = cards.filter(filter::matches).map { it.name }

    @Test
    fun `the top bucket holds every value from the cap up`() {
        assertThat(names(CardFacetFilter(stats = mapOf(CardStat.ENERGY to setOf(CardStat.CAP))))).containsExactly("Big")
        assertThat(CardStat.label(CardStat.CAP)).isEqualTo("${CardStat.CAP}+")
        assertThat(CardStat.label(3)).isEqualTo("3")
    }

    @Test
    fun `a costed card with no power reads as power zero, an uncosted one has none`() {
        assertThat(names(CardFacetFilter(stats = mapOf(CardStat.POWER to setOf(0))))).containsExactly("Cheap")
        assertThat(CardStat.POWER.value(legend)).isNull()
    }

    @Test
    fun `buckets match any within a stat and every stat at once`() {
        assertThat(names(CardFacetFilter(stats = mapOf(CardStat.MIGHT to setOf(1, CardStat.CAP)))))
            .containsExactly("Cheap", "Big")
        assertThat(names(CardFacetFilter(stats = mapOf(CardStat.ENERGY to setOf(2), CardStat.MIGHT to setOf(8)))))
            .isEmpty()
    }

    @Test
    fun `active count counts every selected value and emptying a stat drops it`() {
        val filter = CardFacetFilter(domains = setOf(Domain.FURY)).withStat(CardStat.ENERGY, setOf(1, 2))
        assertThat(filter.activeCount).isEqualTo(3)
        assertThat(filter.withStat(CardStat.ENERGY, emptySet()).stats).isEmpty()
        assertThat(CardFacetFilter().isEmpty).isTrue()
    }

    @Test
    fun `facets offer only stats with two buckets and sets in release order`() {
        val radiance = card("Radiant", energy = 2, setId = "RAD", setLabel = "Radiance")
        val vendetta = card("Vengeful", energy = 2, setId = "VEN", setLabel = "Vendetta")
        val facets = CardFacets.of(cards + radiance + vendetta)
        assertThat(facets.stats[CardStat.ENERGY]).containsExactly(2, CardStat.CAP).inOrder()
        assertThat(facets.stats[CardStat.POWER]).containsExactly(0, 2).inOrder()
        assertThat(facets.sets.map { it.value }).containsExactly("Origins", "Vendetta", "Radiance").inOrder()
        assertThat(CardFacets.of(listOf(cheap)).stats).isEmpty()
    }

    @Test
    fun `statuses are always offered with their counts`() {
        val facets = CardFacets.of(listOf(cheap, big.copy(banned = true)))
        assertThat(facets.statuses).containsExactly(CardStatusFilter.BANNED to 1, CardStatusFilter.ERRATA to 0).inOrder()
    }
}
