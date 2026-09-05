package com.scanrift.android.service.sync

import androidx.room.withTransaction
import com.scanrift.android.core.log.Log
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.dao.CardDao
import com.scanrift.android.data.local.dao.MaintenanceDao
import com.scanrift.android.data.remote.datasource.RiftboundDataSource
import com.scanrift.android.data.remote.dto.CardDto
import com.scanrift.android.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Catalogue sync.
 *
 * Ported from iOS `CardDatabaseService.swift`, with one correctness fix and one
 * structural one:
 *
 * - iOS builds its local per-set counts from the *normalised* set id but compares them
 *   against the *raw* id from `/sets`. For OPP, JDG and PR the count is therefore
 *   always zero, `cardCount > local` is always true, and all three promo sets refetch
 *   on every single sync forever. Counting by `sourceSetId` fixes it.
 * - Every write is upsert-only inside a transaction. The old Android build cleared the
 *   whole table first, which cascaded into the user's collection.
 */
class CardDatabaseService @Inject constructor(
    private val db: ScanRiftDatabase,
    private val cardDao: CardDao,
    private val maintenanceDao: MaintenanceDao,
    private val source: RiftboundDataSource,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    /** Full seed. Used for a cold start from the bundled catalogue. */
    suspend fun fetchAndStoreCards(onProgress: ((Int, Int) -> Unit)? = null): Int =
        withContext(io) {
            val dtos = CardDedup.dedupedByRiftboundId(source.fetchAllCards(onProgress))
            db.withTransaction { cardDao.upsertAllChunked(dtos.map { it.toEntity() }) }
            deduplicateLocalCards()
            maintenanceDao.deleteOrphans()
            Log.database.i("Seeded %d cards", dtos.size)
            dtos.size
        }

    /**
     * Incremental sync.
     *
     * `/sets` is the freshness signal: a set is refetched only when the API reports
     * more cards than we hold. Within a refetched set, a card whose `updated_on`
     * matches the local value is skipped without a write.
     */
    suspend fun syncDelta(onProgress: ((Int, Int) -> Unit)? = null): SyncReport =
        withContext(io) {
            val apiSets = source.fetchSets()

            // One projection rather than 1500 full rows with their JSON columns.
            val identities = cardDao.getAllIdentities()
            val updatedOnById = identities.associate { it.id to it.updatedOn }
            val localCountBySourceSet = cardDao.countBySourceSet().associate { it.sourceSetId to it.count }

            var report = SyncReport()
            onProgress?.invoke(0, apiSets.size)

            apiSets.forEachIndexed { index, set ->
                coroutineContext.ensureActive()
                report = report.copy(setsChecked = report.setsChecked + 1)

                val localCount = localCountBySourceSet[set.setId] ?: 0
                if (set.cardCount > localCount) {
                    val dtos = CardDedup.dedupedByRiftboundId(source.fetchCardsInSet(set.setId))
                    // One transaction per set, so a mid-sync failure leaves whole sets
                    // consistent rather than half-written.
                    val setReport = db.withTransaction { applyDtos(dtos, updatedOnById) }
                    report = report + setReport.copy(setsRefetched = 1)
                }

                onProgress?.invoke(index + 1, apiSets.size)
            }

            // Runs unconditionally: a set whose local count is inflated by duplicates
            // never trips the refetch check above, so this is the only place those
            // stale rows are ever cleaned up.
            val removed = deduplicateLocalCards()
            val orphans = maintenanceDao.deleteOrphans()

            report.copy(duplicatesRemoved = removed, orphansRemoved = orphans).also {
                Log.database.i(
                    "Delta sync: +%d ~%d =%d across %d/%d sets (dedup %d, orphans %d)",
                    it.added, it.updated, it.unchanged, it.setsRefetched, it.setsChecked,
                    it.duplicatesRemoved, it.orphansRemoved,
                )
            }
        }

    /**
     * Upserts a batch, skipping rows the API says are unchanged.
     *
     * The skip needs both timestamps present and equal — a null on either side means
     * "unknown", which must be treated as changed rather than silently skipped.
     */
    private suspend fun applyDtos(
        dtos: List<CardDto>,
        updatedOnById: Map<String, Long?>,
    ): SyncReport {
        var added = 0
        var updated = 0
        var unchanged = 0
        val toWrite = ArrayList<CardDto>(dtos.size)

        for (dto in dtos) {
            if (!updatedOnById.containsKey(dto.id)) {
                added++
                toWrite += dto
                continue
            }
            val localUpdatedOn = updatedOnById[dto.id]
            val apiUpdatedOn = dto.updatedOnMillis
            if (apiUpdatedOn != null && localUpdatedOn != null && apiUpdatedOn == localUpdatedOn) {
                unchanged++
                continue
            }
            updated++
            toWrite += dto
        }

        if (toWrite.isNotEmpty()) {
            cardDao.upsertAllChunked(toWrite.map { it.toEntity() })
        }
        return SyncReport(added = added, updated = updated, unchanged = unchanged)
    }

    /**
     * De-duplication layer 2: collapse local rows sharing a `riftboundId`.
     *
     * Everything pointing at a loser is repointed onto the keeper first, inside a
     * transaction, so the user never loses an owned card to a catalogue cleanup.
     *
     * @return how many rows were deleted.
     */
    suspend fun deduplicateLocalCards(): Int = withContext(io) {
        val duplicatedIds = cardDao.duplicatedRiftboundIds()
        if (duplicatedIds.isEmpty()) return@withContext 0

        val referenced = cardDao.referencedCardIds().toSet()
        val identities = cardDao.getAllIdentities()
            .filter { it.riftboundId in duplicatedIds.toSet() }
            .groupBy { it.riftboundId }

        var removed = 0
        db.withTransaction {
            for ((_, rows) in identities) {
                if (rows.size < 2) continue
                val keeper = CardDedup.pickKeeper(rows, referenced)
                for (row in rows) {
                    if (row.id == keeper.id) continue
                    maintenanceDao.repointReferences(keeperId = keeper.id, duplicateId = row.id)
                }
                val losers = rows.filter { it.id != keeper.id }.map { it.id }
                cardDao.deleteByIds(losers)
                removed += losers.size
            }
        }

        if (removed > 0) Log.database.i("Removed %d duplicate card row(s)", removed)
        removed
    }

    suspend fun cardCount(): Int = withContext(io) { cardDao.count() }
}
