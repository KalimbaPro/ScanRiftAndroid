package com.scanrift.android.service.backup

import androidx.room.withTransaction
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.entity.CardListCrossRef
import com.scanrift.android.data.local.entity.CardListEntity
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.local.entity.DeckEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity
import com.scanrift.android.data.local.entity.GameRecordEntity
import com.scanrift.android.domain.model.EntryKey
import javax.inject.Inject

/**
 * Restores a snapshot **without destroying local data**.
 *
 * Restore is a merge, not a replace — the same semantics iOS uses, so the two
 * platforms agree about what a shared file means:
 *
 * - **Entries** are keyed on `(cardId, isFoil, condition)`. A newer `dateAdded` wins;
 *   an older one is a silent no-op (and is *not* counted as skipped, matching iOS).
 *   An unknown `cardId` is skipped and counted.
 * - **Decks** are keyed on `id`. A newer `lastModifiedDate` wins, and when it does the
 *   deck's entries are **fully replaced** rather than merged — a deck is a designed
 *   whole, so a half-merged one would be worse than either version.
 * - **Lists** are **union only**. Restoring never removes a card from a list and never
 *   renames or recolours one.
 * - **Game records** are insert-if-absent. A record has no mutation timestamp — its
 *   `date` is when the match happened, not when the row changed — so last-write-wins
 *   is not expressible and records are treated as immutable.
 */
class SnapshotMerger @Inject constructor(
    private val db: ScanRiftDatabase,
) {

    suspend fun merge(snapshot: CollectionSnapshot): RestoreResult = db.withTransaction {
        val knownCardIds = db.cardDao().getAllIdentities().mapTo(HashSet()) { it.id }

        var result = RestoreResult()
        result = mergeEntries(snapshot, knownCardIds, result)
        result = mergeDecks(snapshot, knownCardIds, result)
        result = mergeLists(snapshot, knownCardIds, result)
        result = mergeGameRecords(snapshot, result)
        result
    }

    private suspend fun mergeEntries(
        snapshot: CollectionSnapshot,
        knownCardIds: Set<String>,
        initial: RestoreResult,
    ): RestoreResult {
        var result = initial
        val dao = db.collectionEntryDao()

        // Mutated as we insert, so two rows in one file that share a key merge rather
        // than colliding on the unique index. iOS fixed the same bug the same way.
        val index = HashMap<EntryKey, CollectionEntryEntity>()
        dao.getAll().forEach { entry ->
            val cardId = entry.cardId ?: return@forEach
            index[EntryKey(cardId, entry.isFoil, entry.condition)] = entry
        }

        for (dto in snapshot.entries) {
            if (dto.cardId !in knownCardIds) {
                result = result.copy(entriesSkipped = result.entriesSkipped + 1)
                continue
            }

            val key = EntryKey(dto.cardId, dto.isFoil, dto.condition)
            val existing = index[key]
            if (existing != null) {
                // Older data loses silently; iOS does not count that as skipped.
                if (dto.dateAdded <= existing.dateAdded) continue
                val updated = existing.copy(
                    quantity = dto.quantity,
                    dateAdded = dto.dateAdded,
                    notes = dto.notes,
                    folder = dto.folder,
                )
                dao.update(updated)
                index[key] = updated
                result = result.copy(entriesUpdated = result.entriesUpdated + 1)
            } else {
                val inserted = CollectionEntryEntity(
                    cardId = dto.cardId,
                    quantity = dto.quantity,
                    isFoil = dto.isFoil,
                    dateAdded = dto.dateAdded,
                    condition = dto.condition,
                    notes = dto.notes,
                    folder = dto.folder,
                )
                val id = dao.insert(inserted)
                index[key] = inserted.copy(id = id)
                result = result.copy(entriesAdded = result.entriesAdded + 1)
            }
        }
        return result
    }

    private suspend fun mergeDecks(
        snapshot: CollectionSnapshot,
        knownCardIds: Set<String>,
        initial: RestoreResult,
    ): RestoreResult {
        var result = initial
        val dao = db.deckDao()
        val existingById = dao.getAll().associateBy { it.id }

        for (dto in snapshot.decks) {
            // A legend or champion the catalogue no longer knows becomes an empty slot
            // rather than failing the whole restore.
            val legendId = dto.legendCardId?.takeIf { it in knownCardIds }
            val championId = dto.championCardId?.takeIf { it in knownCardIds }
            val existing = existingById[dto.id]

            if (existing != null) {
                if (dto.lastModifiedDate <= existing.lastModifiedDate) continue
                dao.upsertDeck(
                    existing.copy(
                        name = dto.name,
                        lastModifiedDate = dto.lastModifiedDate,
                        legendCardId = legendId,
                        championCardId = championId,
                    ),
                )
                dao.deleteAllEntriesForDeck(dto.id)
                dao.upsertEntries(dto.entries.toEntities(dto.id, knownCardIds))
                result = result.copy(decksUpdated = result.decksUpdated + 1)
            } else {
                dao.upsertDeck(
                    DeckEntity(
                        id = dto.id,
                        name = dto.name,
                        createdDate = dto.createdDate,
                        lastModifiedDate = dto.lastModifiedDate,
                        legendCardId = legendId,
                        championCardId = championId,
                    ),
                )
                dao.upsertEntries(dto.entries.toEntities(dto.id, knownCardIds))
                result = result.copy(decksAdded = result.decksAdded + 1)
            }
        }
        return result
    }

    /**
     * Collapses duplicate `(cardId, section)` pairs before insert, since the unique
     * index would otherwise reject a snapshot that contained both.
     */
    private fun List<CollectionSnapshot.DeckEntryExport>.toEntities(
        deckId: String,
        knownCardIds: Set<String>,
    ): List<DeckEntryEntity> =
        filter { it.cardId in knownCardIds }
            .groupBy { it.cardId to it.section }
            .map { (key, group) ->
                DeckEntryEntity(
                    deckId = deckId,
                    cardId = key.first,
                    quantity = group.sumOf { it.quantity },
                    section = key.second,
                )
            }

    private suspend fun mergeLists(
        snapshot: CollectionSnapshot,
        knownCardIds: Set<String>,
        initial: RestoreResult,
    ): RestoreResult {
        var result = initial
        val dao = db.cardListDao()
        val existingById = dao.getAll().associateBy { it.id }

        for (dto in snapshot.lists) {
            val cardIds = dto.cardIds.filter { it in knownCardIds }
            val existing = existingById[dto.id]

            if (existing != null) {
                val alreadyPresent = dao.cardIdsForList(dto.id).toSet()
                val missing = cardIds.filterNot { it in alreadyPresent }
                if (missing.isEmpty()) continue
                dao.addCardsToList(missing.map { CardListCrossRef(listId = dto.id, cardId = it) })
                result = result.copy(listsUpdated = result.listsUpdated + 1)
            } else {
                dao.upsert(
                    CardListEntity(
                        id = dto.id,
                        name = dto.name,
                        colorHex = dto.colorHex,
                        isSystem = dto.isSystem,
                        systemType = dto.systemType,
                        createdDate = dto.createdDate,
                    ),
                )
                dao.addCardsToList(cardIds.map { CardListCrossRef(listId = dto.id, cardId = it) })
                result = result.copy(listsAdded = result.listsAdded + 1)
            }
        }
        return result
    }

    private suspend fun mergeGameRecords(
        snapshot: CollectionSnapshot,
        initial: RestoreResult,
    ): RestoreResult {
        if (snapshot.gameRecords.isEmpty()) return initial
        val dao = db.gameRecordDao()
        val knownDeckIds = db.deckDao().getAll().mapTo(HashSet()) { it.id }
        val existingIds = dao.allIds().toSet()

        val toInsert = snapshot.gameRecords
            .filterNot { it.id in existingIds }
            .distinctBy { it.id }
            .map { dto ->
                GameRecordEntity(
                    id = dto.id,
                    date = dto.date,
                    name = dto.name,
                    result = dto.result,
                    playerName = dto.playerName,
                    opponentName = dto.opponentName,
                    pointsScored = dto.pointsScored,
                    pointsAllowed = dto.pointsAllowed,
                    ties = dto.ties,
                    conquerCount = dto.conquerCount,
                    holdCount = dto.holdCount,
                    abilityCount = dto.abilityCount,
                    notes = dto.notes,
                    // A record whose deck is gone still belongs in global history.
                    deckId = dto.deckId?.takeIf { it in knownDeckIds },
                    legendId = dto.legendId,
                    opponentLegendId = dto.opponentLegendId,
                )
            }

        if (toInsert.isEmpty()) return initial
        dao.insertIfAbsent(toInsert)
        return initial.copy(gameRecordsAdded = initial.gameRecordsAdded + toInsert.size)
    }
}
