package com.scanrift.android.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Legend grouping and default-printing choice, on the names and ids the catalogue
 * really carries. Parity with iOS `LegendPrintings`.
 */
class LegendPrintingsTest {

    private fun legend(
        name: String,
        riftboundId: String,
        setId: String,
        setLabel: String,
        rarity: String = Rarity.RARE,
        collectorNumber: Int = riftboundId.split('-')[1].takeWhile { it.isDigit() }.toIntOrNull() ?: 0,
        alternateArt: Boolean = false,
        overnumbered: Boolean = false,
        signature: Boolean = false,
    ) = Card(
        id = "id-$riftboundId-${name.hashCode()}",
        name = name,
        riftboundId = riftboundId,
        publicCode = riftboundId.uppercase(),
        collectorNumber = collectorNumber,
        type = CardType.LEGEND,
        rarity = rarity,
        setId = setId,
        setLabel = setLabel,
        sourceSetId = setId,
        cleanName = name,
        alternateArt = alternateArt,
        overnumbered = overnumbered,
        signature = signature,
    )

    private val promo = SetNormalizer.PROMO_SET_ID

    // Irelia: Spiritforged regular, overnumbered, Metal, and the Lunar Revel promo.
    private val irelia = legend("Irelia - Blade Dancer", "sfd-195-221", "SFD", "Spiritforged")
    private val ireliaOvernumbered = legend(
        "Irelia - Blade Dancer (Overnumbered)", "sfd-246-221", "SFD", "Spiritforged",
        rarity = Rarity.SHOWCASE, overnumbered = true,
    )
    private val ireliaMetal = legend(
        "Irelia - Blade Dancer (Metal)", "opp-195-221", promo, "Promos", rarity = Rarity.PROMO,
    )
    private val ireliaLunar = legend(
        "Irelia - Blade Dancer (Lunar Revel 2026)", "pr-lunar26-001", promo, "Promos",
        rarity = Rarity.PROMO, collectorNumber = 1, alternateArt = true,
    )

    // Lillia: Unleashed regular, overnumbered, signature, and the Secret Garden promo.
    private val lillia = legend("Lillia - Bashful Bloom", "unl-189-219", "UNL", "Unleashed")
    private val lilliaSecretGarden = legend(
        "Lillia - Bashful Bloom", "sgn-001-003", "SGN", "Secret Garden", rarity = Rarity.PROMO,
    )
    private val lilliaSignature = legend(
        "Lillia - Bashful Bloom (Signature)", "unl-230*-219", "UNL", "Unleashed", signature = true,
    )

    // Garen only exists as a Proving Grounds starter, a promo and a Metal.
    private val garenPromo = legend("Garen - Might of Demacia", "opp-023-024", promo, "Promos", rarity = Rarity.PROMO)
    private val garenMetal = legend("Garen - Might of Demacia (Metal)", "opp-023-024", promo, "Promos", rarity = Rarity.PROMO)
    private val garenStarter = legend("Garen - Might of Demacia (Starter)", "ogs-023-024", "OGS", "Proving Grounds")

    // Master Yi is two different legends, one of them only Starter + Metal.
    private val yiBladesmanMetal = legend(
        "Master Yi - Wuju Bladesman (Metal)", "opp-019-024", promo, "Promos", rarity = Rarity.PROMO,
    )
    private val yiBladesmanStarter = legend("Master Yi - Wuju Bladesman (Starter)", "ogs-019-024", "OGS", "Proving Grounds")
    private val yiMaster = legend("Master Yi - Wuju Master", "unl-191-219", "UNL", "Unleashed")

    private val teemoAlt = legend(
        "Teemo - Swift Scout (Alternate Art)", "pr-263a-298", promo, "Promos", rarity = Rarity.PROMO, alternateArt = true,
    )

    // ── Identity ─────────────────────────────────────────────────────────────

    @Test
    fun `base name strips every trailing variant suffix`() {
        assertThat(LegendPrintings.baseName("Irelia - Blade Dancer (Lunar Revel 2026)")).isEqualTo("Irelia - Blade Dancer")
        assertThat(LegendPrintings.baseName("Ahri - Nine-Tailed Fox (Korea Exclusive)")).isEqualTo("Ahri - Nine-Tailed Fox")
        assertThat(LegendPrintings.baseName("Master Yi - Wuju Bladesman (Metal)")).isEqualTo("Master Yi - Wuju Bladesman")
        assertThat(LegendPrintings.baseName("A - B (Alternate Art) (Signature)")).isEqualTo("A - B")
        assertThat(LegendPrintings.baseName("Irelia - Blade Dancer")).isEqualTo("Irelia - Blade Dancer")
    }

    @Test
    fun `a name that is only a parenthetical is left alone`() {
        assertThat(LegendPrintings.baseName("(Metal)")).isEqualTo("(Metal)")
    }

    @Test
    fun `variant suffixes keep reading order`() {
        assertThat(LegendPrintings.variantSuffixes("A - B (Alternate Art) (Signature)"))
            .containsExactly("Alternate Art", "Signature").inOrder()
        assertThat(LegendPrintings.variantSuffixes("Irelia - Blade Dancer")).isEmpty()
    }

    @Test
    fun `identity folds case and diacritics`() {
        assertThat(LegendPrintings.identity("Kai'Sa - Daughter of the Void (Metal)"))
            .isEqualTo(LegendPrintings.identity("KAI'SA - DAUGHTER OF THE VOID"))
        assertThat(LegendPrintings.identity("Pokémon - Test")).isEqualTo(LegendPrintings.identity("Pokemon - test"))
    }

    @Test
    fun `every printing of a legend is the same legend`() {
        assertThat(LegendPrintings.isSameLegend(irelia, ireliaLunar)).isTrue()
        assertThat(LegendPrintings.isSameLegend(irelia, ireliaMetal)).isTrue()
        assertThat(LegendPrintings.isSameLegend(lillia, lilliaSecretGarden)).isTrue()
        assertThat(LegendPrintings.isSameLegend(yiMaster, yiBladesmanStarter)).isFalse()
        assertThat(LegendPrintings.isSameLegend(irelia, null)).isFalse()
    }

    // ── Grouping ─────────────────────────────────────────────────────────────

    @Test
    fun `one group per legend with every printing in it`() {
        val groups = LegendPrintings.groups(
            listOf(ireliaLunar, lillia, ireliaMetal, lilliaSecretGarden, irelia, ireliaOvernumbered, lilliaSignature),
        )

        assertThat(groups.map { it.name }).containsExactly("Irelia - Blade Dancer", "Lillia - Bashful Bloom").inOrder()
        assertThat(groups[0].printings).hasSize(4)
        assertThat(groups[1].printings).hasSize(3)
    }

    @Test
    fun `promo and alternate art printings are offered rather than hidden`() {
        val irelias = LegendPrintings.groups(listOf(irelia, ireliaLunar, ireliaMetal)).single()
        assertThat(irelias.printings).contains(ireliaLunar)
        assertThat(irelias.printings).contains(ireliaMetal)

        val lillias = LegendPrintings.groups(listOf(lillia, lilliaSecretGarden)).single()
        assertThat(lillias.printings).contains(lilliaSecretGarden)
        assertThat(lillias.hasChoice).isTrue()
    }

    @Test
    fun `non-legend cards and repeated ids are ignored`() {
        val unit = irelia.copy(id = "unit", type = CardType.UNIT, name = "Irelia - Fervent")
        val group = LegendPrintings.groups(listOf(irelia, irelia, unit)).single()
        assertThat(group.printings).containsExactly(irelia)
        assertThat(group.hasChoice).isFalse()
    }

    @Test
    fun `different legends of one champion stay apart`() {
        val groups = LegendPrintings.groups(listOf(yiBladesmanMetal, yiMaster, yiBladesmanStarter))
        assertThat(groups.map { it.name })
            .containsExactly("Master Yi - Wuju Bladesman", "Master Yi - Wuju Master").inOrder()
    }

    @Test
    fun `search matches the legend name`() {
        val groups = LegendPrintings.groups(listOf(irelia, ireliaLunar, lillia, yiMaster))
        assertThat(LegendPrintings.filter(groups, " blade ").map { it.name }).containsExactly("Irelia - Blade Dancer")
        assertThat(LegendPrintings.filter(groups, "")).isEqualTo(groups)
        // The suffix is not part of the legend's name, so it does not match.
        assertThat(LegendPrintings.filter(groups, "Lunar")).isEmpty()
    }

    // ── Default printing ─────────────────────────────────────────────────────

    @Test
    fun `the standard main-set printing is the default`() {
        val group = LegendPrintings.groups(listOf(ireliaLunar, ireliaMetal, ireliaOvernumbered, irelia)).single()
        assertThat(group.defaultPrinting).isEqualTo(irelia)
        // Overnumbered beats the promos, the alternate-art promo beats only the Metal.
        assertThat(group.printings).containsExactly(irelia, ireliaOvernumbered, ireliaLunar, ireliaMetal).inOrder()
    }

    @Test
    fun `a main set beats a supplemental promo of the same name`() {
        val group = LegendPrintings.groups(listOf(lilliaSecretGarden, lilliaSignature, lillia)).single()
        assertThat(group.defaultPrinting).isEqualTo(lillia)
        assertThat(group.printings).containsExactly(lillia, lilliaSecretGarden, lilliaSignature).inOrder()
    }

    @Test
    fun `a starter-only legend defaults to its starter printing`() {
        val garen = LegendPrintings.groups(listOf(garenMetal, garenPromo, garenStarter)).single()
        assertThat(garen.defaultPrinting).isEqualTo(garenStarter)
        assertThat(garen.name).isEqualTo("Garen - Might of Demacia")

        val yi = LegendPrintings.groups(listOf(yiBladesmanMetal, yiBladesmanStarter)).single()
        assertThat(yi.defaultPrinting).isEqualTo(yiBladesmanStarter)
        assertThat(yi.printings.last()).isEqualTo(yiBladesmanMetal)
    }

    @Test
    fun `a legend that only exists as one special printing is still pickable`() {
        val teemo = LegendPrintings.groups(listOf(teemoAlt)).single()
        assertThat(teemo.defaultPrinting).isEqualTo(teemoAlt)
        assertThat(teemo.name).isEqualTo("Teemo - Swift Scout")
    }

    @Test
    fun `default printing is independent of input order`() {
        val cards = listOf(ireliaLunar, ireliaMetal, ireliaOvernumbered, irelia)
        val reversed = LegendPrintings.sortedPrintings(cards.reversed())
        assertThat(LegendPrintings.sortedPrintings(cards)).isEqualTo(reversed)
        assertThat(LegendPrintings.defaultPrinting(emptyList())).isNull()
    }

    // ── Labels ───────────────────────────────────────────────────────────────

    @Test
    fun `variant labels name the treatment or the event`() {
        assertThat(LegendPrintings.variantLabel(irelia)).isEqualTo("Regular")
        assertThat(LegendPrintings.variantLabel(ireliaOvernumbered)).isEqualTo("Overnumbered")
        assertThat(LegendPrintings.variantLabel(ireliaMetal)).isEqualTo("Metal")
        assertThat(LegendPrintings.variantLabel(ireliaLunar)).isEqualTo("Lunar Revel 2026")
        assertThat(LegendPrintings.variantLabel(lilliaSignature)).isEqualTo("Signature")
        assertThat(LegendPrintings.variantLabel(lilliaSecretGarden)).isEqualTo("Promo")
        assertThat(LegendPrintings.variantLabel(garenStarter)).isEqualTo("Starter")
        assertThat(LegendPrintings.variantLabel(teemoAlt)).isEqualTo("Alternate Art")
    }

    @Test
    fun `printing label leads with the set`() {
        assertThat(LegendPrintings.printingLabel(lilliaSecretGarden)).isEqualTo("Secret Garden · Promo")
        assertThat(LegendPrintings.printingLabel(irelia)).isEqualTo("Spiritforged · Regular")
    }
}
