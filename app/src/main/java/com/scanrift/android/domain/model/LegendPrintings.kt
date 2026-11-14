package com.scanrift.android.domain.model

/** Every printing of one legend, default printing first. See [PrintingGroup]. */
typealias LegendGroup = PrintingGroup

/**
 * Legend-only view of [CardPrintings], so a picker can offer one row per legend and let
 * the player choose which art sits on their seat. Port of iOS `LegendPrintings`.
 *
 * Identity, ordering and labels are [CardPrintings]'; this object only restricts the
 * grouping to `Legend`-type cards. Anything that compares or aggregates legends must
 * use [identity], never the card id: the id names one printing, not the legend.
 */
object LegendPrintings {

    // ── Identity ─────────────────────────────────────────────────────────────

    fun baseName(name: String): String = CardPrintings.baseName(name)

    fun variantSuffixes(name: String): List<String> = CardPrintings.variantSuffixes(name)

    fun identity(name: String): String = CardPrintings.identity(name)

    fun identity(card: Card): String = CardPrintings.identity(card)

    /** True when both cards are printings of the same legend. */
    fun isSameLegend(lhs: Card?, rhs: Card?): Boolean = CardPrintings.isSameCard(lhs, rhs)

    // ── Grouping ─────────────────────────────────────────────────────────────

    /** One group per legend among the `Legend`-type cards in [cards], sorted by name. */
    fun groups(cards: List<Card>): List<LegendGroup> =
        CardPrintings.groups(cards.filter { it.type == CardType.LEGEND })

    /** Groups whose legend name contains [query]; all groups when it is blank. */
    fun filter(groups: List<LegendGroup>, query: String): List<LegendGroup> = CardPrintings.filter(groups, query)

    // ── Default printing ─────────────────────────────────────────────────────

    fun defaultPrinting(printings: List<Card>): Card? = CardPrintings.defaultPrinting(printings)

    fun sortedPrintings(printings: List<Card>): List<Card> = CardPrintings.sortedPrintings(printings)

    // ── Labels ───────────────────────────────────────────────────────────────

    fun variantLabel(card: Card): String = CardPrintings.variantLabel(card)

    fun printingLabel(card: Card): String = CardPrintings.printingLabel(card)
}
