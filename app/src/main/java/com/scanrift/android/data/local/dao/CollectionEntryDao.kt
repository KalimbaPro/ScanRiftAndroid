package com.scanrift.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.local.entity.CollectionEntryWithCard
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionEntryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: CollectionEntryEntity): Long

    @Upsert
    suspend fun upsertAll(entries: List<CollectionEntryEntity>)

    @Update
    suspend fun update(entry: CollectionEntryEntity)

    @Delete
    suspend fun delete(entry: CollectionEntryEntity)

    @Transaction
    @Query("SELECT * FROM collection_entries ORDER BY dateAdded DESC")
    fun observeAllWithCards(): Flow<List<CollectionEntryWithCard>>

    @Transaction
    @Query("SELECT * FROM collection_entries ORDER BY dateAdded DESC")
    suspend fun getAllWithCards(): List<CollectionEntryWithCard>

    @Query("SELECT * FROM collection_entries")
    suspend fun getAll(): List<CollectionEntryEntity>

    @Query("SELECT * FROM collection_entries WHERE cardId = :cardId")
    suspend fun getForCard(cardId: String): List<CollectionEntryEntity>

    /** Lookup by the identity triple that every upsert path keys on. */
    @Query(
        """
        SELECT * FROM collection_entries
        WHERE cardId = :cardId AND isFoil = :isFoil AND condition = :condition
        LIMIT 1
        """,
    )
    suspend fun findEntry(cardId: String, isFoil: Boolean, condition: String): CollectionEntryEntity?

    @Query("SELECT SUM(quantity) FROM collection_entries")
    fun observeTotalCardCount(): Flow<Int?>

    @Query("SELECT COUNT(DISTINCT cardId) FROM collection_entries")
    fun observeUniqueCardCount(): Flow<Int>

    /** Card id to total owned quantity, for the collection grid's ownership overlays. */
    @Query("SELECT cardId, SUM(quantity) AS quantity FROM collection_entries WHERE cardId IS NOT NULL GROUP BY cardId")
    fun observeOwnedQuantities(): Flow<List<OwnedQuantity>>

    @Query("DELETE FROM collection_entries")
    suspend fun deleteAll()

    /** Reclaims rows whose card genuinely left the catalogue (FK set them to NULL). */
    @Query("DELETE FROM collection_entries WHERE cardId IS NULL")
    suspend fun deleteOrphaned(): Int
}

data class OwnedQuantity(val cardId: String, val quantity: Int)
