package com.scanrift.android.service.deck

import com.scanrift.android.core.Constants
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckSection

data class CurveSegment(val bucket: String, val domain: String, val count: Int)

data class StatEntry(val label: String, val count: Int)

data class DeckStats(
    val cardCount: Int,
    val averageEnergy: Double,
    val averagePower: Double,
    val energyCurve: List<CurveSegment>,
    val powerCurve: List<CurveSegment>,
    val types: List<StatEntry>,
    val domains: List<StatEntry>,
) {
    companion object {
        const val NEUTRAL = "Neutral"

        fun of(deck: Deck): DeckStats {
            val cards = (deck.sortedEntries(DeckSection.MAIN_DECK) + deck.sortedEntries(DeckSection.SIDEBOARD))
                .mapNotNull { entry -> entry.card?.let { it to entry.quantity } }
            return DeckStats(
                cardCount = cards.sumOf { it.second },
                averageEnergy = weightedAverage(cards) { it.energy },
                averagePower = weightedAverage(cards) { it.power },
                energyCurve = energyCurve(cards),
                powerCurve = powerCurve(cards),
                types = distribution(cards) { listOf(it.type) },
                domains = distribution(cards) { it.domains },
            )
        }

        private fun weightedAverage(cards: List<Pair<Card, Int>>, stat: (Card) -> Int?): Double {
            val counted = cards.mapNotNull { (card, quantity) -> stat(card)?.let { it to quantity } }
            val total = counted.sumOf { it.second }
            return if (total == 0) 0.0 else counted.sumOf { it.first * it.second }.toDouble() / total
        }

        private fun energyCurve(cards: List<Pair<Card, Int>>): List<CurveSegment> {
            val max = Constants.Deck.ENERGY_BUCKET_MAX
            val labels = (0 until max).map { "$it" } + "$max+"
            val buckets = segmentsByBucket(cards) { card ->
                card.energy?.let { if (it >= max) "$max+" else "$it" }
            }
            return labels.flatMap { label ->
                buckets[label]?.toSegments(label) ?: listOf(CurveSegment(label, NEUTRAL, 0))
            }
        }

        private fun powerCurve(cards: List<Pair<Card, Int>>): List<CurveSegment> {
            val buckets = segmentsByBucket(cards) { it.power?.toString() }
            return buckets.keys.sortedBy { it.toInt() }.flatMap { buckets.getValue(it).toSegments(it) }
        }

        private fun segmentsByBucket(
            cards: List<Pair<Card, Int>>,
            bucket: (Card) -> String?,
        ): Map<String, Map<String, Int>> {
            val result = mutableMapOf<String, MutableMap<String, Int>>()
            cards.forEach { (card, quantity) ->
                val key = bucket(card) ?: return@forEach
                val domain = card.domains.firstOrNull() ?: NEUTRAL
                val counts = result.getOrPut(key) { mutableMapOf() }
                counts[domain] = (counts[domain] ?: 0) + quantity
            }
            return result
        }

        private fun Map<String, Int>.toSegments(bucket: String): List<CurveSegment> =
            entries.sortedBy { it.key }.map { CurveSegment(bucket, it.key, it.value) }

        private fun distribution(cards: List<Pair<Card, Int>>, labels: (Card) -> List<String>): List<StatEntry> {
            val counts = mutableMapOf<String, Int>()
            cards.forEach { (card, quantity) ->
                labels(card).forEach { counts[it] = (counts[it] ?: 0) + quantity }
            }
            return counts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { StatEntry(it.key, it.value) }
        }
    }
}
