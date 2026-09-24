package com.scanrift.android.ui.scanner

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.Card
import org.junit.Test

class ScanResultsTest {

    private fun card(id: String, rarity: String = "Common") = Card(
        id = id,
        name = "Card $id",
        riftboundId = "ogn-$id-298",
        publicCode = "OGN-$id/298",
        collectorNumber = 1,
        type = "Unit",
        rarity = rarity,
        setId = "OGN",
        setLabel = "Origins",
        sourceSetId = "OGN",
        cleanName = "Card $id",
    )

    private val common = card("001")
    private val other = card("002")
    private val rare = card("003", rarity = "Rare")

    @Test fun `a first scan appends a row`() {
        val results = emptyList<ScanResult>().addingScan(common)
        assertThat(results.map { it.card.id to it.quantity }).containsExactly("001" to 1)
    }

    @Test fun `a duplicate scan increments the existing row`() {
        val results = emptyList<ScanResult>().addingScan(common).addingScan(common)
        assertThat(results.single().quantity).isEqualTo(2)
    }

    @Test fun `an always foil card scans as foil and merges into its foil row`() {
        val results = emptyList<ScanResult>().addingScan(rare).addingScan(rare)
        assertThat(results.single().isFoil).isTrue()
        assertThat(results.single().quantity).isEqualTo(2)
    }

    @Test fun `a duplicate scan does not merge into a row the user made foil`() {
        val first = emptyList<ScanResult>().addingScan(common)
        val results = first.togglingFoil(first.single().id).addingScan(common)
        assertThat(results.map { it.isFoil to it.quantity }).containsExactly(true to 1, false to 1).inOrder()
    }

    @Test fun `setting a quantity to zero removes the row`() {
        val results = emptyList<ScanResult>().addingScan(common)
        assertThat(results.withQuantity(results.single().id, 0)).isEmpty()
    }

    @Test fun `setting a quantity changes only that row`() {
        val results = emptyList<ScanResult>().addingScan(common).addingScan(other)
        val updated = results.withQuantity(results.first().id, 5)
        assertThat(updated.map { it.quantity }).containsExactly(5, 1).inOrder()
    }

    @Test fun `toggling a single copy flips it`() {
        val results = emptyList<ScanResult>().addingScan(common)
        assertThat(results.togglingFoil(results.single().id).single().isFoil).isTrue()
    }

    @Test fun `toggling a stack splits one copy into a new row right after it`() {
        val results = listOf(ScanResult(common, quantity = 3), ScanResult(other))
        val toggled = results.togglingFoil(results.first().id)
        assertThat(toggled.map { Triple(it.card.id, it.isFoil, it.quantity) })
            .containsExactly(Triple("001", false, 2), Triple("001", true, 1), Triple("002", false, 1))
            .inOrder()
        assertThat(toggled.first().id).isEqualTo(results.first().id)
    }

    @Test fun `toggling a stack moves one copy into an existing opposite row`() {
        val results = listOf(ScanResult(common, quantity = 3), ScanResult(common, isFoil = true))
        val toggled = results.togglingFoil(results.first().id)
        assertThat(toggled.map { it.isFoil to it.quantity }).containsExactly(false to 2, true to 2).inOrder()
    }

    @Test fun `toggling a single copy merges into an existing opposite row`() {
        val results = listOf(ScanResult(common), ScanResult(common, quantity = 2, isFoil = true))
        val toggled = results.togglingFoil(results.first().id)
        assertThat(toggled.map { it.isFoil to it.quantity }).containsExactly(true to 3)
    }

    @Test fun `always foil cards cannot be toggled`() {
        val results = emptyList<ScanResult>().addingScan(rare)
        assertThat(results.togglingFoil(results.single().id)).isEqualTo(results)
    }

    @Test fun `a correction replaces the card and keeps the row identity and quantity`() {
        val results = listOf(ScanResult(common, quantity = 2))
        val corrected = results.correcting(results.single().id, other).single()
        assertThat(corrected.card.id).isEqualTo("002")
        assertThat(corrected.quantity).isEqualTo(2)
        assertThat(corrected.id).isEqualTo(results.single().id)
    }

    @Test fun `a correction merges into an existing row for the new card`() {
        val results = listOf(ScanResult(common, quantity = 2), ScanResult(other))
        val corrected = results.correcting(results.first().id, other)
        assertThat(corrected.map { it.card.id to it.quantity }).containsExactly("002" to 3)
    }

    @Test fun `a correction to an always foil card makes the row foil`() {
        val results = listOf(ScanResult(common))
        assertThat(results.correcting(results.single().id, rare).single().isFoil).isTrue()
    }

    @Test fun `a correction away from an always foil card drops the automatic foil`() {
        val results = listOf(ScanResult(rare))
        assertThat(results.correcting(results.single().id, common).single().isFoil).isFalse()
    }

    @Test fun `a correction keeps a foil the user chose`() {
        val results = listOf(ScanResult(common, isFoil = true))
        assertThat(results.correcting(results.single().id, other).single().isFoil).isTrue()
    }

    @Test fun `a correction to the same card leaves the row alone`() {
        val results = listOf(ScanResult(common, quantity = 2))
        assertThat(results.correcting(results.single().id, common)).isEqualTo(results)
    }
}
