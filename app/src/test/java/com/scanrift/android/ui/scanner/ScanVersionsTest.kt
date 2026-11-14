package com.scanrift.android.ui.scanner

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardLanguage
import com.scanrift.android.domain.model.CardPrintings
import com.scanrift.android.service.scanning.NormalizedBox
import org.junit.Test

class ScanVersionsTest {

    private fun card(id: String, name: String, rarity: String = "Common", riftboundId: String = "ogn-$id-298") = Card(
        id = id,
        name = name,
        riftboundId = riftboundId,
        publicCode = "OGN-$id/298",
        collectorNumber = 1,
        type = "Unit",
        rarity = rarity,
        setId = "OGN",
        setLabel = "Origins",
        sourceSetId = "OGN",
        cleanName = name,
    )

    private val regular = card("001", "Jinx - Rebel")
    private val altArt = card("001a", "Jinx - Rebel (Alternate Art)", riftboundId = "ogn-001a-298")
    private val showcase = card("301", "Jinx - Rebel (Overnumbered)", rarity = "Showcase")
    private val index = CardPrintings.index(listOf(regular, altArt, showcase))

    @Test fun `picking a version replaces the row and keeps quantity and language`() {
        val row = ScanResult(regular, quantity = 2, language = CardLanguage.FR)
        val state = ScannerUiState(results = listOf(row), lastScanned = regular, lastResultId = row.id, printingIndex = index)

        val (next, move) = state.withVersion(VersionTarget.Row(row.id), altArt)

        assertThat(move).isNull()
        val replaced = next.results.single()
        assertThat(replaced.id).isEqualTo(row.id)
        assertThat(replaced.card).isEqualTo(altArt)
        assertThat(replaced.quantity).isEqualTo(2)
        assertThat(replaced.language).isEqualTo(CardLanguage.FR)
        assertThat(next.lastScanned).isEqualTo(altArt)
        assertThat(next.lastResultId).isEqualTo(row.id)
    }

    @Test fun `picking a version merges into an identical row instead of duplicating`() {
        val existing = ScanResult(altArt, quantity = 3)
        val scanned = ScanResult(regular)
        val state = ScannerUiState(results = listOf(existing, scanned), lastScanned = regular, lastResultId = scanned.id)

        val (next, _) = state.withVersion(VersionTarget.Row(scanned.id), altArt)

        assertThat(next.results.map { it.card.id to it.quantity }).containsExactly(altArt.id to 4)
        // The banner follows the copies into the row they merged into.
        assertThat(next.lastResultId).isEqualTo(existing.id)
        assertThat(next.lastScanned).isEqualTo(altArt)
    }

    @Test fun `different language rows do not merge`() {
        val existing = ScanResult(altArt, language = CardLanguage.ZH)
        val scanned = ScanResult(regular)
        val (next, _) = ScannerUiState(results = listOf(existing, scanned)).withVersion(VersionTarget.Row(scanned.id), altArt)
        assertThat(next.results).hasSize(2)
    }

    @Test fun `changing an older row leaves the last-scanned banner alone`() {
        val older = ScanResult(regular)
        val last = ScanResult(card("009", "Other"))
        val state = ScannerUiState(results = listOf(older, last), lastScanned = last.card, lastResultId = last.id)
        val (next, _) = state.withVersion(VersionTarget.Row(older.id), altArt)
        assertThat(next.lastScanned).isEqualTo(last.card)
        assertThat(next.lastResultId).isEqualTo(last.id)
    }

    @Test fun `foil follows the new card's default unless the user toggled it`() {
        // Default non-foil → always-foil showcase becomes foil.
        val plain = ScanResult(regular)
        assertThat(listOf(plain).replacingCard(plain.id, showcase).first.single().isFoil).isTrue()
        // Always-foil showcase → regular drops back to non-foil: nobody chose that foil.
        val shiny = ScanResult(showcase)
        assertThat(shiny.isFoil).isTrue()
        assertThat(listOf(shiny).replacingCard(shiny.id, regular).first.single().isFoil).isFalse()
        // A foil the user toggled on survives the change.
        val toggled = ScanResult(regular, isFoil = true)
        assertThat(listOf(toggled).replacingCard(toggled.id, altArt).first.single().isFoil).isTrue()
    }

    @Test fun `picking a version for a binder page item replaces its card`() {
        val item = PageItem("p1", NormalizedBox(0f, 0f, 0.3f, 0.3f), "OGN-001", showcase, CardLanguage.EN)
        val items = listOf(item).withItemCard("p1", regular)
        assertThat(items.single().card).isEqualTo(regular)
        assertThat(items.single().isFoil).isFalse()
    }

    @Test fun `auto-added scan re-keys exactly the copies it wrote`() {
        val added = AutoAddedScan(regular, isFoil = false, language = CardLanguage.FR, quantity = 2)
        val state = ScannerUiState(lastScanned = regular, lastAutoAdded = added, printingIndex = index)
        assertThat(state.lastScanVersionTarget()).isEqualTo(VersionTarget.AutoAdded)
        assertThat(state.lastScanHasVersions).isTrue()

        val (next, move) = state.withVersion(VersionTarget.AutoAdded, showcase)

        assertThat(move).isEqualTo(CopyMove(added, AutoAddedScan(showcase, isFoil = true, language = CardLanguage.FR, quantity = 2)))
        assertThat(next.lastScanned).isEqualTo(showcase)
        assertThat(next.lastAutoAdded!!.card).isEqualTo(showcase)
        assertThat(next.results).isEmpty()
    }

    @Test fun `re-picking the same version in auto-add mode moves nothing`() {
        val state = ScannerUiState(lastScanned = regular, lastAutoAdded = AutoAddedScan(regular, false, CardLanguage.EN))
        assertThat(state.withVersion(VersionTarget.AutoAdded, regular).second).isNull()
    }
}
