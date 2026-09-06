package com.scanrift.android.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Derived-property parity with iOS `Card.swift`.
 *
 * These four properties drive collection ordering and sync de-duplication, so a
 * divergence here shows up as cards in the wrong place or the wrong duplicate winning.
 */
class CardTest {

    private fun card(
        riftboundId: String = "ogn-021-298",
        type: String = CardType.UNIT,
        supertype: String? = null,
        rarity: String = Rarity.COMMON,
        alternateArt: Boolean = false,
        overnumbered: Boolean = false,
        collectorNumber: Int = 21,
        setId: String = "OGN",
        publicCode: String = "OGN-021/298",
    ) = Card(
        id = "id-$riftboundId",
        name = "Test Card",
        riftboundId = riftboundId,
        publicCode = publicCode,
        collectorNumber = collectorNumber,
        type = type,
        supertype = supertype,
        rarity = rarity,
        setId = setId,
        setLabel = "Origins",
        sourceSetId = setId,
        cleanName = "Test Card",
        alternateArt = alternateArt,
        overnumbered = overnumbered,
    )

    // ── isAlwaysFoil ─────────────────────────────────────────────────────────

    @Test
    fun `rare epic and showcase are always foil`() {
        assertThat(card(rarity = Rarity.RARE).isAlwaysFoil).isTrue()
        assertThat(card(rarity = Rarity.EPIC).isAlwaysFoil).isTrue()
        assertThat(card(rarity = Rarity.SHOWCASE).isAlwaysFoil).isTrue()
    }

    @Test
    fun `common uncommon and promo are not always foil`() {
        assertThat(card(rarity = Rarity.COMMON).isAlwaysFoil).isFalse()
        assertThat(card(rarity = Rarity.UNCOMMON).isAlwaysFoil).isFalse()
        assertThat(card(rarity = Rarity.PROMO).isAlwaysFoil).isFalse()
    }

    // ── isSpecialPrint ───────────────────────────────────────────────────────

    @Test
    fun `sp prefix in the number segment marks a special print`() {
        assertThat(card(riftboundId = "ven-sp2-006").isSpecialPrint).isTrue()
    }

    @Test
    fun `special print detection is case insensitive`() {
        assertThat(card(riftboundId = "VEN-SP2-006").isSpecialPrint).isTrue()
    }

    @Test
    fun `an ordinary number segment is not a special print`() {
        assertThat(card(riftboundId = "ogn-021-298").isSpecialPrint).isFalse()
    }

    @Test
    fun `a malformed riftbound id is not a special print`() {
        assertThat(card(riftboundId = "ogn").isSpecialPrint).isFalse()
        assertThat(card(riftboundId = "").isSpecialPrint).isFalse()
    }

    // ── isAlternateArt ───────────────────────────────────────────────────────

    @Test
    fun `a trailing letter marks alternate art`() {
        assertThat(card(riftboundId = "ven-042a-166").isAlternateArt).isTrue()
    }

    @Test
    fun `a rune alternate art is detected without a third segment`() {
        assertThat(card(riftboundId = "ven-r01a").isAlternateArt).isTrue()
    }

    @Test
    fun `the metadata flag is the fallback when the id has no suffix`() {
        assertThat(card(riftboundId = "ogn-021-298", alternateArt = true).isAlternateArt).isTrue()
        assertThat(card(riftboundId = "ogn-021-298", alternateArt = false).isAlternateArt).isFalse()
    }

    @Test
    fun `a special print is not alternate art`() {
        // "sp2" ends in a digit, but the guard also excludes special prints explicitly.
        assertThat(card(riftboundId = "ven-spa-006").isAlternateArt).isFalse()
    }

    @Test
    fun `a signature star suffix is not alternate art`() {
        // The live API emits e.g. "unl-229*-219" for signature prints. '*' is not a
        // letter, so these must not be treated as alternate art.
        assertThat(card(riftboundId = "unl-229*-219").isAlternateArt).isFalse()
    }

    // ── setOrderRank ─────────────────────────────────────────────────────────

    @Test
    fun `set order rank buckets base overnumbered special rune and token`() {
        assertThat(card().setOrderRank).isEqualTo(0)
        assertThat(card(overnumbered = true).setOrderRank).isEqualTo(1)
        assertThat(card(riftboundId = "ven-sp2-006").setOrderRank).isEqualTo(2)
        assertThat(card(type = CardType.RUNE).setOrderRank).isEqualTo(3)
        assertThat(card(supertype = CardSupertype.TOKEN).setOrderRank).isEqualTo(4)
    }

    @Test
    fun `tokens are keyed on supertype not type`() {
        // A token's `type` is usually "Unit" — keying off type would bucket it as base.
        val token = card(type = CardType.UNIT, supertype = CardSupertype.TOKEN)
        assertThat(token.setOrderRank).isEqualTo(4)
    }

    // ── Number sort ──────────────────────────────────────────────────────────

    @Test
    fun `number sort orders by set then bucket then number then base before alt art`() {
        val base = card(riftboundId = "ogn-021-298", collectorNumber = 21)
        val alt = card(riftboundId = "ogn-021a-298", collectorNumber = 21)
        val later = card(riftboundId = "ogn-022-298", collectorNumber = 22)
        val rune = card(riftboundId = "ogn-r01-298", collectorNumber = 1, type = CardType.RUNE)
        val otherSet = card(riftboundId = "sfd-001-288", collectorNumber = 1, setId = "SFD")

        val sorted = listOf(rune, otherSet, alt, later, base).sortedWith(CardNumberComparator)

        assertThat(sorted.map { it.riftboundId }).containsExactly(
            "ogn-021-298", "ogn-021a-298", "ogn-022-298", "ogn-r01-298", "sfd-001-288",
        ).inOrder()
    }

    // ── Derived codes ────────────────────────────────────────────────────────

    @Test
    fun `dotGg id zero pads the collector number`() {
        assertThat(card(collectorNumber = 21).dotGgId).isEqualTo("OGN-021")
        assertThat(card(collectorNumber = 7).dotGgId).isEqualTo("OGN-007")
        assertThat(card(collectorNumber = 298).dotGgId).isEqualTo("OGN-298")
    }

    @Test
    fun `public code prefix drops the print run`() {
        assertThat(card(publicCode = "OGN-021a/298").publicCodePrefix).isEqualTo("OGN-021a")
        assertThat(card(publicCode = "OGN-021").publicCodePrefix).isEqualTo("OGN-021")
    }
}
