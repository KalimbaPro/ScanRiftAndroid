package com.scanrift.android.ui.scanner

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardLanguage
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

    // ── One row per scan ─────────────────────────────────────────────────────

    @Test fun `a first scan appends a row`() {
        val results = emptyList<ScanResult>().addingScan(common)
        assertThat(results.map { it.card.id to it.quantity }).containsExactly("001" to 1)
    }

    @Test fun `two scans of the same card are two rows`() {
        val results = emptyList<ScanResult>().addingScan(common).addingScan(common)
        assertThat(results.map { it.card.id to it.quantity }).containsExactly("001" to 1, "001" to 1)
        assertThat(results.map { it.id }.toSet()).hasSize(2)
    }

    @Test fun `the newest scan is first`() {
        val (once, firstId) = emptyList<ScanResult>().addingScanReturningId(common, CardLanguage.EN)
        val (twice, secondId) = once.addingScanReturningId(other, CardLanguage.EN)
        assertThat(twice.map { it.id }).containsExactly(secondId, firstId).inOrder()
        assertThat(twice.first().card.id).isEqualTo("002")
    }

    @Test fun `an always foil card scans as foil`() {
        val results = emptyList<ScanResult>().addingScan(rare)
        assertThat(results.single().isFoil).isTrue()
    }

    @Test fun `binder page adds are new rows too`() {
        val results = emptyList<ScanResult>().addingScan(common).adding(common, isFoil = false, language = CardLanguage.EN)
        assertThat(results).hasSize(2)
    }

    @Test fun `setting a quantity to zero removes the row`() {
        val results = emptyList<ScanResult>().addingScan(common)
        assertThat(results.withQuantity(results.single().id, 0)).isEmpty()
    }

    @Test fun `deleting one of two identical rows keeps the other`() {
        val results = emptyList<ScanResult>().addingScan(common).addingScan(common)
        val remaining = results.withQuantity(results.first().id, 0)
        assertThat(remaining.map { it.id }).containsExactly(results.last().id)
    }

    @Test fun `setting a quantity changes only that row`() {
        val results = emptyList<ScanResult>().addingScan(common).addingScan(other)
        val updated = results.withQuantity(results.first().id, 5)
        assertThat(updated.map { it.quantity }).containsExactly(5, 1).inOrder()
    }

    // ── Foil ─────────────────────────────────────────────────────────────────

    @Test fun `toggling a single copy flips it`() {
        val results = emptyList<ScanResult>().addingScan(common)
        assertThat(results.togglingFoil(results.single().id).single().isFoil).isTrue()
    }

    @Test fun `toggling leaves an identical row alone`() {
        val results = emptyList<ScanResult>().addingScan(common).addingScan(common)
        val toggled = results.togglingFoil(results.first().id)
        assertThat(toggled.map { it.isFoil }).containsExactly(true, false).inOrder()
    }

    @Test fun `toggling a stack splits one copy into a new row right after it`() {
        val results = listOf(ScanResult(common, quantity = 3), ScanResult(other))
        val toggled = results.togglingFoil(results.first().id)
        assertThat(toggled.map { Triple(it.card.id, it.isFoil, it.quantity) })
            .containsExactly(Triple("001", false, 2), Triple("001", true, 1), Triple("002", false, 1))
            .inOrder()
        assertThat(toggled.first().id).isEqualTo(results.first().id)
    }

    @Test fun `always foil cards cannot be toggled`() {
        val results = emptyList<ScanResult>().addingScan(rare)
        assertThat(results.togglingFoil(results.single().id)).isEqualTo(results)
    }

    @Test fun `the foil toggle keeps the row's language`() {
        val start = emptyList<ScanResult>().addingScan(common, CardLanguage.FR)
        assertThat(start.togglingFoil(start.single().id).single().language).isEqualTo(CardLanguage.FR)
    }

    // ── Correction ───────────────────────────────────────────────────────────

    @Test fun `a correction replaces the card and keeps the row identity and quantity`() {
        val results = listOf(ScanResult(common, quantity = 2))
        val corrected = results.correcting(results.single().id, other).single()
        assertThat(corrected.card.id).isEqualTo("002")
        assertThat(corrected.quantity).isEqualTo(2)
        assertThat(corrected.id).isEqualTo(results.single().id)
    }

    @Test fun `a correction does not merge into an existing row for the new card`() {
        val results = listOf(ScanResult(common, quantity = 2), ScanResult(other))
        val corrected = results.correcting(results.first().id, other)
        assertThat(corrected.map { it.card.id to it.quantity }).containsExactly("002" to 2, "002" to 1).inOrder()
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

    // ── Language ─────────────────────────────────────────────────────────────

    @Test fun `the same card in two languages is two rows`() {
        val results = emptyList<ScanResult>()
            .addingScan(common, CardLanguage.EN)
            .addingScan(common, CardLanguage.FR)
        assertThat(results.map { it.language }).containsExactly(CardLanguage.FR, CardLanguage.EN).inOrder()
    }

    @Test fun `changing one row's language leaves its twin alone`() {
        val start = emptyList<ScanResult>().addingScan(common).addingScan(common)
        val relabelled = start.withLanguage(start.first().id, CardLanguage.FR)
        assertThat(relabelled.map { it.id to it.language })
            .containsExactly(start[0].id to CardLanguage.FR, start[1].id to CardLanguage.EN).inOrder()
    }

    @Test fun `changing to a language another row has does not merge`() {
        val start = emptyList<ScanResult>().addingScan(common, CardLanguage.KO).addingScan(common, CardLanguage.EN)
        val relabelled = start.withLanguage(start.first().id, CardLanguage.KO)
        assertThat(relabelled.map { it.language to it.quantity })
            .containsExactly(CardLanguage.KO to 1, CardLanguage.KO to 1)
    }

    // ── Write-out ────────────────────────────────────────────────────────────

    @Test fun `writing out sums identical rows on the entry key`() {
        val start = emptyList<ScanResult>()
            .addingScan(common).addingScan(common).addingScan(common, CardLanguage.FR).addingScan(rare)
        val withPlusOne = start.withQuantity(start.last().id, 3) // a "+1"'d stack of the first scan
        val stacks = withPlusOne.collectionStacks()
        assertThat(stacks.map { Triple(it.card.id, it.language, it.quantity) }).containsExactly(
            Triple("003", CardLanguage.EN, 1),
            Triple("001", CardLanguage.FR, 1),
            Triple("001", CardLanguage.EN, 4),
        )
        assertThat(stacks.sumOf { it.quantity }).isEqualTo(withPlusOne.sumOf { it.quantity })
    }

    @Test fun `writing out keeps foil and non-foil apart`() {
        val start = emptyList<ScanResult>().addingScan(common).addingScan(common)
        val stacks = start.togglingFoil(start.first().id).collectionStacks()
        assertThat(stacks.map { it.isFoil to it.quantity }).containsExactly(true to 1, false to 1)
    }
}
