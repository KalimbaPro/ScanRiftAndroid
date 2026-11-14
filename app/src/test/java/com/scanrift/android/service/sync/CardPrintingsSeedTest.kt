package com.scanrift.android.service.sync

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.data.local.mapper.toDomain
import com.scanrift.android.data.remote.dto.PaginatedCardsResponse
import com.scanrift.android.di.SerializationModule
import com.scanrift.android.domain.model.CardPrintings
import com.scanrift.android.domain.model.PrintingGroup
import com.scanrift.android.ui.scanner.ScanResult
import com.scanrift.android.ui.scanner.ScannerUiState
import java.io.File
import org.junit.Test

/**
 * Printing grouping for every card type against the bundled catalogue, run through the
 * same de-duplication the sync applies: these are the versions the scanner's picker
 * offers on a fresh install.
 */
class CardPrintingsSeedTest {

    private val cards = run {
        // Unit tests run from the module directory.
        val seed = SerializationModule.provideApiJson()
            .decodeFromString<PaginatedCardsResponse>(File("src/main/res/raw/cards.json").readText())
        CardDedup.dedupedByRiftboundId(seed.items).map { it.toEntity().toDomain() }
    }

    private val index = CardPrintings.index(cards)

    private fun group(name: String): PrintingGroup = index.groups.single { it.name == name }

    private fun card(riftboundId: String) = cards.single { it.riftboundId == riftboundId }

    @Test
    fun `every card lands in exactly one group and no group mixes types`() {
        assertThat(index.groups.sumOf { it.printings.size }).isEqualTo(cards.map { it.id }.toSet().size)
        assertThat(index.groups.map { it.identity }.toSet()).hasSize(index.groups.size)
        index.groups.forEach { group ->
            assertThat(group.printings.map { it.type }.toSet()).hasSize(1)
        }
    }

    @Test
    fun `an alternate art pairs with its regular printing`() {
        val zed = group("Zed, From the Shadows")
        assertThat(zed.printings.map { it.riftboundId }).containsAtLeast("ven-023-166", "ven-023a-166")
        assertThat(zed.defaultPrinting.riftboundId).isEqualTo("ven-023-166")
        assertThat(CardPrintings.variantLabel(card("ven-023a-166"))).isEqualTo("Alternate Art")
        assertThat(index.printingsOf(card("ven-023a-166"))).isEqualTo(zed.printings)
    }

    @Test
    fun `overnumbered and signature copies are versions of the base card`() {
        val kaisa = group("Kai'Sa - Daughter of the Void")
        assertThat(kaisa.defaultPrinting.riftboundId).isEqualTo("ogn-247-298")
        assertThat(kaisa.printings.map { it.riftboundId }).containsAtLeast("ogn-247-298", "ogn-299-298", "ogn-299*-298")
        assertThat(CardPrintings.variantLabel(card("ogn-299-298"))).isEqualTo("Overnumbered")
        assertThat(CardPrintings.variantLabel(card("ogn-299*-298"))).isEqualTo("Signature")
        assertThat(index.hasChoice(card("ogn-299*-298"))).isTrue()
    }

    @Test
    fun `a promo reprint is a version of the main-set card`() {
        val scorcher = group("Blazing Scorcher")
        assertThat(scorcher.printings.map { it.riftboundId }).containsExactly("ogn-001-298", "opp-001-298").inOrder()
        assertThat(CardPrintings.variantLabel(card("opp-001-298"))).isEqualTo("Promo")
    }

    @Test
    fun `filter sets matches set label, set id, public code and variant label`() {
        val kaisa = group("Kai'Sa - Daughter of the Void").printings
        assertThat(CardPrintings.filterPrintings(kaisa, "")).isEqualTo(kaisa)
        assertThat(CardPrintings.filterPrintings(kaisa, " signature ").map { it.riftboundId }).containsExactly("ogn-299*-298")
        assertThat(CardPrintings.filterPrintings(kaisa, "origins").map { it.setLabel }.toSet()).containsExactly("Origins")
        val code = card("ogn-299-298").publicCode
        assertThat(CardPrintings.filterPrintings(kaisa, code).map { it.riftboundId }).contains("ogn-299-298")
        assertThat(CardPrintings.filterPrintings(kaisa, "zzz-nothing")).isEmpty()
    }

    @Test
    fun `a single-printing card leaves the set chip inactive`() {
        val single = cards.single { it.name == "Brazen Buccaneer" }
        assertThat(index.hasChoice(single)).isFalse()
        assertThat(index.printingsOf(single)).containsExactly(single)
        val row = ScanResult(single)
        val state = ScannerUiState(results = listOf(row), lastScanned = single, lastResultId = row.id, printingIndex = index)
        assertThat(state.lastScanHasVersions).isFalse()

        val kaisa = card("ogn-247-298")
        val kaisaRow = ScanResult(kaisa)
        val active = ScannerUiState(results = listOf(kaisaRow), lastScanned = kaisa, lastResultId = kaisaRow.id, printingIndex = index)
        assertThat(active.lastScanHasVersions).isTrue()
    }
}
