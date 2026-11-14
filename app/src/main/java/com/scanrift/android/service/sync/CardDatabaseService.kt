package com.scanrift.android.service.sync

import androidx.room.withTransaction
import com.scanrift.android.core.log.Log
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.dao.CardDao
import com.scanrift.android.data.local.dao.MaintenanceDao
import com.scanrift.android.data.remote.datasource.RiftboundDataSource
import com.scanrift.android.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Catalogue sync.
 *
 * Every write is upsert-only. The old Android build cleared the whole table first,
 * which cascaded into the user's collection; nothing here ever deletes a catalogue row
 * except the de-duplication pass, which repoints references onto the survivor first.
 *
 * *Whether* to sync is not decided here: [CatalogueTagSync] compares the API's
 * `/version` tag with the stored one and calls [refreshCatalogue] only on a change.
 */
class CardDatabaseService @Inject constructor(
    private val db: ScanRiftDatabase,
    private val cardDao: CardDao,
    private val maintenanceDao: MaintenanceDao,
    private val source: RiftboundDataSource,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    /**
     * Fetches every card the source serves and upserts all of them, then runs the
     * de-duplication and orphan passes. Used for the bundled seed and, through
     * [refreshCatalogue], for every network refresh.
     *
     * Nothing is skipped on an unchanged `updated_on`: a tag change can come from a
     * legality or localisation edit that leaves `updated_on` alone, and ~1700 upserts
     * in one transaction cost well under a second.
     */
    suspend fun fetchAndStoreCards(onProgress: ((Int, Int) -> Unit)? = null): Int =
        withContext(io) {
            val dtos = CardDedup.dedupedByRiftboundId(source.fetchAllCards(onProgress))
            ensureActive()
            db.withTransaction { cardDao.upsertAllChunked(dtos.map { it.toEntity() }) }
            val removed = deduplicateLocalCards()
            val orphans = maintenanceDao.deleteOrphans()
            Log.database.i("Stored %d cards (dedup %d, orphans %d)", dtos.size, removed, orphans)
            dtos.size
        }

    /**
     * Full catalogue refresh: `/sets`, every `/cards` page, then `/legality` for
     * [region]. Throws if any step fails, so the caller keeps the old tag and retries
     * on the next launch.
     *
     * Legality runs last, never before: an upserted card carries the API's
     * default-region `banned`, which the region-resolved value must overwrite.
     */
    suspend fun refreshCatalogue(region: String, onProgress: ((Int, Int) -> Unit)? = null): Int =
        withContext(io) {
            // Nothing is stored from /sets; it is fetched as part of the snapshot and
            // used to catch a short /cards pagination in the log.
            val expected = source.fetchSets().sumOf { it.cardCount }
            val count = fetchAndStoreCards(onProgress)
            if (expected > 0 && count < expected) {
                Log.database.w("/sets lists %d cards but %d survived de-duplication", expected, count)
            }
            refreshLegality(region)
            count
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

    /**
     * Pulls `/legality` for [region] and rewrites the legality columns of the whole
     * catalogue in one transaction. Never deletes a card.
     *
     * Must run **after** a card sync: an upserted card carries the API's default-region
     * `banned`, which this then overwrites with the region-resolved value.
     *
     * @return how many cards are banned or have errata, or null when the source has no
     *   legality endpoint (the bundled seed).
     */
    suspend fun refreshLegality(region: String): Int? = withContext(io) {
        val response = source.fetchLegality(region) ?: return@withContext null
        val legality = LegalityResolver.resolve(response.items, cardDao.getAllIdentities())
        cardDao.applyLegality(legality)
        Log.database.i("Legality (%s): %d listed, %d applied", region, response.items.size, legality.size)
        legality.size
    }

    suspend fun cardCount(): Int = withContext(io) { cardDao.count() }
}
