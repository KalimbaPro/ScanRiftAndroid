package com.scanrift.android.service.sync

import com.scanrift.android.data.local.entity.CardIdentity
import com.scanrift.android.data.remote.dto.CardDto

/**
 * De-duplication, layer 1: incoming DTOs.
 *
 * The API emits two records for the same physical card during a set's rollout — a
 * preliminary stub and a finalised record — sharing a `riftbound_id` but carrying
 * different `id`s. 16 such pairs sit in the bundled catalogue right now, so this runs
 * on the offline seed too, not just on network responses.
 *
 * Pure functions with no database or Android dependency, so the iOS de-duplication
 * tests port across directly.
 */
object CardDedup {

    /**
     * Collapses same-`riftboundId` DTOs to the most complete one.
     *
     * Output preserves the first-appearance order of each `riftboundId` while emitting
     * that group's winner, matching iOS exactly — stable output keeps upsert batches
     * deterministic.
     */
    fun dedupedByRiftboundId(dtos: List<CardDto>): List<CardDto> {
        if (dtos.size < 2) return dtos

        val best = LinkedHashMap<String, CardDto>(dtos.size)
        for (dto in dtos) {
            val current = best[dto.riftboundId]
            if (current == null || isMoreComplete(dto, current)) {
                best[dto.riftboundId] = dto
            }
        }

        val emitted = HashSet<String>(best.size)
        val result = ArrayList<CardDto>(best.size)
        for (dto in dtos) {
            if (emitted.add(dto.riftboundId)) {
                best[dto.riftboundId]?.let(result::add)
            }
        }
        return result
    }

    /**
     * A record is "more complete" when it carries a non-blank `clean_name` — the
     * finalised batch populates it, the preliminary stub leaves it null. Ties break on
     * the most recent `updated_on`.
     *
     * Strictly greater, never equal: on a genuine tie the incumbent stays, because
     * Swift only replaces when this returns true.
     */
    fun isMoreComplete(candidate: CardDto, incumbent: CardDto): Boolean {
        val candidateNamed = !candidate.metadata.cleanName.isNullOrBlank()
        val incumbentNamed = !incumbent.metadata.cleanName.isNullOrBlank()
        if (candidateNamed != incumbentNamed) return candidateNamed

        val candidateDate = candidate.updatedOnMillis ?: Long.MIN_VALUE
        val incumbentDate = incumbent.updatedOnMillis ?: Long.MIN_VALUE
        return candidateDate > incumbentDate
    }

    /**
     * De-duplication, layer 2: rows already in the database.
     *
     * Picks which of several same-`riftboundId` rows survives. A **referenced** row
     * always beats an unreferenced one, so a card the user owns or has put in a deck is
     * never the one deleted; otherwise the most recently updated row wins, which
     * favours the finalised record over the stub.
     *
     * Swift's `max(by:)` keeps the first element on a tie, so this reduces rather than
     * using `maxByOrNull`, which keeps the last.
     */
    fun pickKeeper(rows: List<CardIdentity>, referencedIds: Set<String>): CardIdentity {
        require(rows.isNotEmpty()) { "pickKeeper requires at least one row" }
        return rows.reduce { incumbent, candidate ->
            if (isBetterLocalRow(candidate, incumbent, referencedIds)) candidate else incumbent
        }
    }

    private fun isBetterLocalRow(
        candidate: CardIdentity,
        incumbent: CardIdentity,
        referencedIds: Set<String>,
    ): Boolean {
        val candidateReferenced = candidate.id in referencedIds
        val incumbentReferenced = incumbent.id in referencedIds
        if (candidateReferenced != incumbentReferenced) return candidateReferenced

        val candidateDate = candidate.updatedOn ?: Long.MIN_VALUE
        val incumbentDate = incumbent.updatedOn ?: Long.MIN_VALUE
        return candidateDate > incumbentDate
    }
}
