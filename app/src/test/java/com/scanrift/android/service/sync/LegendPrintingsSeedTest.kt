package com.scanrift.android.service.sync

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.data.local.mapper.toDomain
import com.scanrift.android.data.remote.dto.PaginatedCardsResponse
import com.scanrift.android.di.SerializationModule
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.LegendGroup
import com.scanrift.android.domain.model.LegendPrintings
import com.scanrift.android.domain.model.Rarity
import java.io.File
import org.junit.Test

/**
 * Legend grouping against the bundled catalogue, run through the same de-duplication
 * the sync applies, so these are the legends a fresh install actually shows.
 */
class LegendPrintingsSeedTest {

    private val legends = run {
        // Unit tests run from the module directory.
        val seed = SerializationModule.provideApiJson()
            .decodeFromString<PaginatedCardsResponse>(File("src/main/res/raw/cards.json").readText())
        CardDedup.dedupedByRiftboundId(seed.items)
            .map { it.toEntity().toDomain() }
            .filter { it.type == CardType.LEGEND }
    }

    private val groups = LegendPrintings.groups(legends)

    private fun group(name: String): LegendGroup = groups.single { it.name == name }

    @Test
    fun `every legend card lands in exactly one group`() {
        assertThat(groups.sumOf { it.printings.size }).isEqualTo(legends.map { it.id }.toSet().size)
        assertThat(groups.map { it.identity }.toSet()).hasSize(groups.size)
    }

    @Test
    fun `irelia's lunar revel promo is a printing of irelia`() {
        val irelia = group("Irelia - Blade Dancer")
        assertThat(irelia.defaultPrinting.riftboundId).isEqualTo("sfd-195-221")
        assertThat(irelia.printings.map { it.riftboundId }).contains("pr-lunar26-001")
        val lunar = irelia.printings.single { it.riftboundId == "pr-lunar26-001" }
        assertThat(LegendPrintings.variantLabel(lunar)).isEqualTo("Lunar Revel 2026")
    }

    @Test
    fun `lillia's secret garden promo is a printing of lillia`() {
        val lillia = group("Lillia - Bashful Bloom")
        assertThat(lillia.defaultPrinting.riftboundId).isEqualTo("unl-189-219")
        val secretGarden = lillia.printings.single { it.riftboundId == "sgn-001-003" }
        assertThat(LegendPrintings.printingLabel(secretGarden)).isEqualTo("Secret Garden · Promo")
    }

    @Test
    fun `starter-only legends default to the starter printing`() {
        listOf("Annie - Dark Child", "Garen - Might of Demacia", "Lux - Lady of Luminosity", "Master Yi - Wuju Bladesman")
            .forEach { name ->
                assertThat(group(name).defaultPrinting.setId).isEqualTo("OGS")
            }
    }

    @Test
    fun `master yi's organized-play printing groups with wuju bladesman, not wuju master`() {
        // The Metal and the plain promo share `opp-019-024`, so de-duplication keeps one;
        // whichever survives belongs to Wuju Bladesman.
        assertThat(group("Master Yi - Wuju Bladesman").printings.map { it.riftboundId })
            .containsExactly("ogs-019-024", "opp-019-024")
        assertThat(group("Master Yi - Wuju Master").printings.map { it.riftboundId })
            .containsNoneOf("ogs-019-024", "opp-019-024")
    }

    @Test
    fun `no main-set legend defaults to a promo or metal printing`() {
        groups.filter { group -> group.printings.any { it.rarity != Rarity.PROMO && !it.name.endsWith("(Metal)") } }
            .forEach { group ->
                assertThat(group.defaultPrinting.name).doesNotContain("(Metal)")
                assertThat(group.defaultPrinting.rarity).isNotEqualTo(Rarity.PROMO)
            }
    }
}
