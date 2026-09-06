package com.scanrift.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.CardIdentity
import kotlinx.coroutines.flow.Flow

/**
 * Catalogue access.
 *
 * Two deliberate absences:
 *
 * 1. **No `deleteAll()`.** The old build called it at the start of every sync, and
 *    because the child tables cascaded, a failed or crashed sync erased the user's
 *    collection, decks and lists. Sync is upsert-only now; there is no bulk delete to
 *    misuse.
 * 2. **No `OnConflictStrategy.REPLACE`.** REPLACE is implemented as DELETE + INSERT,
 *    so it fires the very cascades we just designed out. Always use [upsertAll], which
 *    is INSERT-then-UPDATE and leaves child rows alone.
 */
@Dao
interface CardDao {

    @Upsert
    suspend fun upsertAll(cards: List<CardEntity>)

    @Upsert
    suspend fun upsert(card: CardEntity)

    @Query("SELECT * FROM cards ORDER BY name ASC")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards ORDER BY name ASC")
    suspend fun getAll(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getById(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE id = :id")
    fun observeById(id: String): Flow<CardEntity?>

    @Query("SELECT * FROM cards WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<CardEntity>

    @Query("SELECT * FROM cards WHERE riftboundId = :riftboundId")
    suspend fun getByRiftboundId(riftboundId: String): List<CardEntity>

    /**
     * riftbound.gg imports identify cards as `SET-NNN`, which is ambiguous now that the
     * promo sets share `setId = "PROMO"` and alternate arts reuse collector numbers.
     * iOS takes `.first` non-deterministically; ordering base art before alternate art
     * makes the choice stable and sensible.
     */
    @Query(
        """
        SELECT * FROM cards
        WHERE setId = :setId AND collectorNumber = :collectorNumber
        ORDER BY alternateArt ASC, id ASC
        LIMIT 1
        """,
    )
    suspend fun findBySetAndNumber(setId: String, collectorNumber: Int): CardEntity?

    @Query("SELECT * FROM cards WHERE publicCode = :publicCode ORDER BY alternateArt ASC, id ASC LIMIT 1")
    suspend fun findByPublicCode(publicCode: String): CardEntity?

    @Query(
        """
        SELECT * FROM cards
        WHERE LOWER(name) = LOWER(:name) OR LOWER(cleanName) = LOWER(:name)
        ORDER BY alternateArt ASC, id ASC
        """,
    )
    suspend fun findByName(name: String): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun count(): Int

    /** Lightweight projection for delta sync — avoids loading 1500 rows of JSON columns. */
    @Query("SELECT id, riftboundId, sourceSetId, updatedOn, cleanName FROM cards")
    suspend fun getAllIdentities(): List<CardIdentity>

    @Query("SELECT sourceSetId, COUNT(*) AS count FROM cards GROUP BY sourceSetId")
    suspend fun countBySourceSet(): List<SourceSetCount>

    @Query("SELECT DISTINCT setId FROM cards ORDER BY setId ASC")
    fun observeSetIds(): Flow<List<String>>

    @Query("SELECT DISTINCT setLabel FROM cards ORDER BY setLabel ASC")
    fun observeSetLabels(): Flow<List<String>>

    @Query("SELECT DISTINCT type FROM cards ORDER BY type ASC")
    fun observeTypes(): Flow<List<String>>

    @Query("SELECT DISTINCT rarity FROM cards ORDER BY rarity ASC")
    fun observeRarities(): Flow<List<String>>

    @Query("SELECT DISTINCT supertype FROM cards WHERE supertype IS NOT NULL ORDER BY supertype ASC")
    fun observeSupertypes(): Flow<List<String>>

    /**
     * Every card the user's data points at.
     *
     * Sync de-duplication uses this to make sure a referenced row always wins over an
     * unreferenced duplicate. List membership is deliberately excluded, matching iOS's
     * `isReferenced`.
     */
    @Query(
        """
        SELECT DISTINCT cardId FROM collection_entries WHERE cardId IS NOT NULL
        UNION SELECT DISTINCT cardId FROM deck_entries WHERE cardId IS NOT NULL
        UNION SELECT DISTINCT legendCardId FROM decks WHERE legendCardId IS NOT NULL
        UNION SELECT DISTINCT championCardId FROM decks WHERE championCardId IS NOT NULL
        """,
    )
    suspend fun referencedCardIds(): List<String>

    @Query("DELETE FROM cards WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** Every riftboundId that has more than one row — the de-duplication work list. */
    @Query("SELECT riftboundId FROM cards GROUP BY riftboundId HAVING COUNT(*) > 1")
    suspend fun duplicatedRiftboundIds(): List<String>

    @Transaction
    suspend fun upsertAllChunked(cards: List<CardEntity>, chunkSize: Int = 500) {
        cards.chunked(chunkSize).forEach { upsertAll(it) }
    }
}

data class SourceSetCount(val sourceSetId: String, val count: Int)
