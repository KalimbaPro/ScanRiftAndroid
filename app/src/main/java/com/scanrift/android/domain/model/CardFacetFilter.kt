package com.scanrift.android.domain.model

import com.scanrift.android.core.Constants

/**
 * Rules statuses a card can be filtered on. [BANNED] reads [Card.banned], which is
 * already resolved for the user's competitive region.
 */
enum class CardStatusFilter(val label: String) {
    BANNED("Banned"),
    ERRATA("Errata"),
    ;

    fun matches(card: Card): Boolean = when (this) {
        BANNED -> card.banned
        ERRATA -> !card.errata.isNullOrBlank()
    }
}

enum class CardStat(val label: String) {
    ENERGY("Energy"),
    POWER("Power"),
    MIGHT("Might"),
    ;

    fun value(card: Card): Int? = when (this) {
        ENERGY -> card.energy
        POWER -> card.power ?: card.energy?.let { 0 }
        MIGHT -> card.might
    }

    fun bucket(card: Card): Int? = value(card)?.coerceAtMost(CAP)

    companion object {
        const val CAP = Constants.Deck.ENERGY_BUCKET_MAX

        fun label(bucket: Int): String = if (bucket >= CAP) "$CAP+" else "$bucket"
    }
}

data class CardFacetFilter(
    val domains: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val supertypes: Set<String> = emptySet(),
    val rarities: Set<String> = emptySet(),
    val sets: Set<String> = emptySet(),
    val artists: Set<String> = emptySet(),
    val statuses: Set<CardStatusFilter> = emptySet(),
    val stats: Map<CardStat, Set<Int>> = emptyMap(),
) {
    val activeCount: Int
        get() = domains.size + types.size + supertypes.size + rarities.size + sets.size + artists.size +
            statuses.size + stats.values.sumOf { it.size }

    val isEmpty: Boolean get() = activeCount == 0

    fun withStat(stat: CardStat, buckets: Set<Int>): CardFacetFilter =
        copy(stats = if (buckets.isEmpty()) stats - stat else stats + (stat to buckets))

    fun matches(card: Card): Boolean {
        if (domains.isNotEmpty() && card.domains.none { it in domains }) return false
        if (types.isNotEmpty() && card.type !in types) return false
        if (supertypes.isNotEmpty() && card.supertype !in supertypes) return false
        if (rarities.isNotEmpty() && card.rarity !in rarities) return false
        if (sets.isNotEmpty() && card.setLabel !in sets) return false
        if (artists.isNotEmpty() && card.artist !in artists) return false
        if (statuses.isNotEmpty() && statuses.none { it.matches(card) }) return false
        return stats.all { (stat, buckets) -> buckets.isEmpty() || stat.bucket(card) in buckets }
    }
}

data class Facet(val value: String, val count: Int)

data class CardFacets(
    val types: List<Facet>,
    val supertypes: List<Facet>,
    val sets: List<Facet>,
    val rarities: List<Facet>,
    val artists: List<Facet>,
    val statuses: List<Pair<CardStatusFilter, Int>>,
    val stats: Map<CardStat, List<Int>>,
) {
    companion object {
        private val typeOrder = listOf(
            CardType.LEGEND, CardType.UNIT, CardType.SPELL, CardType.GEAR, CardType.RUNE, CardType.BATTLEFIELD,
        )
        val supertypeOrder = listOf(CardSupertype.CHAMPION, CardSupertype.SIGNATURE, CardSupertype.TOKEN)

        fun of(cards: List<Card>): CardFacets {
            fun counted(values: List<String>, order: (Set<String>) -> List<String>): List<Facet> {
                val counts = values.groupingBy { it }.eachCount()
                return order(counts.keys).map { Facet(it, counts.getValue(it)) }
            }
            val setLabels = cards.associate { it.setId to it.setLabel }
            return CardFacets(
                types = counted(cards.map { it.type }) { present ->
                    typeOrder.filter { it in present } + (present - typeOrder.toSet()).sorted()
                },
                supertypes = counted(cards.mapNotNull { it.supertype }) { present -> supertypeOrder.filter { it in present } },
                sets = counted(cards.map { it.setLabel }) {
                    setLabels.keys.sortedWith(SetIdComparator).map(setLabels::getValue).distinct()
                },
                rarities = counted(cards.map { it.rarity }) { present -> Rarity.displayOrder.filter { it in present } },
                artists = counted(cards.mapNotNull { it.artist }) { it.sorted() },
                statuses = CardStatusFilter.entries.map { status -> status to cards.count(status::matches) },
                stats = CardStat.entries.associateWith { stat -> cards.mapNotNull(stat::bucket).distinct().sorted() }
                    .filterValues { it.size > 1 },
            )
        }
    }
}
