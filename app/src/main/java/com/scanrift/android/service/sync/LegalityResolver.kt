package com.scanrift.android.service.sync

import com.scanrift.android.data.local.entity.CardIdentity
import com.scanrift.android.data.local.entity.CardLegality
import com.scanrift.android.data.remote.dto.LegalityItemDto

/**
 * Maps a `/legality?region=` payload onto local card ids.
 *
 * Pure so it is testable without Room. An item matches by `id` first — ids are stable
 * forever in the own API — and falls back to `riftbound_id`, which also covers a local
 * row that is a preliminary duplicate of the API's card. Items for cards we do not hold
 * are dropped. Everything not in the returned map is cleared to "legal, no errata" by
 * the caller.
 */
object LegalityResolver {

    fun resolve(items: List<LegalityItemDto>, cards: List<CardIdentity>): Map<String, CardLegality> {
        val knownIds = cards.mapTo(HashSet()) { it.id }
        val idsByRiftboundId = cards.groupBy({ it.riftboundId }, { it.id })
        val result = LinkedHashMap<String, CardLegality>()
        for (item in items) {
            val legality = CardLegality(
                banned = item.banned,
                bannedFormats = item.bannedFormats.orEmpty(),
                errata = item.errata?.takeIf { it.isNotBlank() },
                errataDate = item.errataDate?.takeIf { it.isNotBlank() },
            )
            val targets = when {
                item.id != null && item.id in knownIds -> listOf(item.id)
                item.riftboundId != null -> idsByRiftboundId[item.riftboundId].orEmpty()
                else -> emptyList()
            }
            targets.forEach { result[it] = legality }
        }
        return result
    }
}
