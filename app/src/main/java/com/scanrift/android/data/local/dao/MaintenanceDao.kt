package com.scanrift.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Repointing and cleanup used by card de-duplication.
 *
 * When two catalogue rows turn out to be the same physical card, everything pointing at
 * the loser has to move to the keeper before the loser is deleted. The unique indices
 * on `collection_entries` and `deck_entries` make a naive `UPDATE ... SET cardId`
 * unsafe — it can collide with a row the keeper already owns — so each repoint merges
 * the colliding rows first, then moves the survivors.
 *
 * That merge is a small, deliberate improvement over iOS, which would leave two
 * separate rows with the same identity triple. Under a unique index it is also the only
 * correct behaviour.
 */
@Dao
interface MaintenanceDao {

    // ── Collection entries ───────────────────────────────────────────────────

    @Query(
        """
        UPDATE collection_entries
        SET quantity = quantity + COALESCE((
            SELECT SUM(d.quantity) FROM collection_entries d
            WHERE d.cardId = :duplicateId
              AND d.isFoil = collection_entries.isFoil
              AND d.condition = collection_entries.condition
        ), 0)
        WHERE cardId = :keeperId
          AND EXISTS (
            SELECT 1 FROM collection_entries d
            WHERE d.cardId = :duplicateId
              AND d.isFoil = collection_entries.isFoil
              AND d.condition = collection_entries.condition
          )
        """,
    )
    suspend fun mergeCollectionQuantities(keeperId: String, duplicateId: String)

    @Query(
        """
        DELETE FROM collection_entries
        WHERE cardId = :duplicateId
          AND EXISTS (
            SELECT 1 FROM collection_entries k
            WHERE k.cardId = :keeperId
              AND k.isFoil = collection_entries.isFoil
              AND k.condition = collection_entries.condition
          )
        """,
    )
    suspend fun deleteCollidingCollectionEntries(keeperId: String, duplicateId: String)

    @Query("UPDATE collection_entries SET cardId = :keeperId WHERE cardId = :duplicateId")
    suspend fun repointCollectionEntries(keeperId: String, duplicateId: String)

    // ── Deck entries ─────────────────────────────────────────────────────────

    @Query(
        """
        UPDATE deck_entries
        SET quantity = quantity + COALESCE((
            SELECT SUM(d.quantity) FROM deck_entries d
            WHERE d.cardId = :duplicateId
              AND d.deckId = deck_entries.deckId
              AND d.section = deck_entries.section
        ), 0)
        WHERE cardId = :keeperId
          AND EXISTS (
            SELECT 1 FROM deck_entries d
            WHERE d.cardId = :duplicateId
              AND d.deckId = deck_entries.deckId
              AND d.section = deck_entries.section
          )
        """,
    )
    suspend fun mergeDeckQuantities(keeperId: String, duplicateId: String)

    @Query(
        """
        DELETE FROM deck_entries
        WHERE cardId = :duplicateId
          AND EXISTS (
            SELECT 1 FROM deck_entries k
            WHERE k.cardId = :keeperId
              AND k.deckId = deck_entries.deckId
              AND k.section = deck_entries.section
          )
        """,
    )
    suspend fun deleteCollidingDeckEntries(keeperId: String, duplicateId: String)

    @Query("UPDATE deck_entries SET cardId = :keeperId WHERE cardId = :duplicateId")
    suspend fun repointDeckEntries(keeperId: String, duplicateId: String)

    // ── Deck slots and list membership ───────────────────────────────────────

    @Query("UPDATE decks SET legendCardId = :keeperId WHERE legendCardId = :duplicateId")
    suspend fun repointLegends(keeperId: String, duplicateId: String)

    @Query("UPDATE decks SET championCardId = :keeperId WHERE championCardId = :duplicateId")
    suspend fun repointChampions(keeperId: String, duplicateId: String)

    @Query(
        """
        INSERT OR IGNORE INTO card_list_cross_ref (listId, cardId)
        SELECT listId, :keeperId FROM card_list_cross_ref WHERE cardId = :duplicateId
        """,
    )
    suspend fun unionListMembership(keeperId: String, duplicateId: String)

    @Query("DELETE FROM card_list_cross_ref WHERE cardId = :duplicateId")
    suspend fun clearListMembership(duplicateId: String)

    // ── Orphan cleanup ───────────────────────────────────────────────────────

    @Query("DELETE FROM collection_entries WHERE cardId IS NULL")
    suspend fun deleteOrphanedCollectionEntries(): Int

    @Query("DELETE FROM deck_entries WHERE cardId IS NULL")
    suspend fun deleteOrphanedDeckEntries(): Int

    /**
     * Moves every reference from [duplicateId] onto [keeperId].
     *
     * Order matters: merge quantities into the keeper's matching row, delete the rows
     * that would now violate the unique index, then move whatever survives.
     */
    @Transaction
    suspend fun repointReferences(keeperId: String, duplicateId: String) {
        if (keeperId == duplicateId) return

        mergeCollectionQuantities(keeperId, duplicateId)
        deleteCollidingCollectionEntries(keeperId, duplicateId)
        repointCollectionEntries(keeperId, duplicateId)

        mergeDeckQuantities(keeperId, duplicateId)
        deleteCollidingDeckEntries(keeperId, duplicateId)
        repointDeckEntries(keeperId, duplicateId)

        repointLegends(keeperId, duplicateId)
        repointChampions(keeperId, duplicateId)

        unionListMembership(keeperId, duplicateId)
        clearListMembership(duplicateId)
    }

    @Transaction
    suspend fun deleteOrphans(): Int =
        deleteOrphanedCollectionEntries() + deleteOrphanedDeckEntries()
}
