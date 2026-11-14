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

    /** Lookup by the identity `(cardId, isFoil, condition, language)` every upsert path keys on. */
    @Query(
        """
        SELECT * FROM collection_entries
        WHERE cardId = :cardId AND isFoil = :isFoil AND condition = :condition AND language = :language
        LIMIT 1
        """,
    )
    suspend fun findEntry(cardId: String, isFoil: Boolean, condition: String, language: String): CollectionEntryEntity?

    @Transaction
    suspend fun adjustQuantity(
        cardId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        delta: Int,
        now: Long,
    ) {
        val existing = findEntry(cardId, isFoil, condition, language)
        val quantity = (existing?.quantity ?: 0) + delta
        when {
            existing == null -> if (quantity > 0) {
                insert(
                    CollectionEntryEntity(
                        cardId = cardId, quantity = quantity, isFoil = isFoil, dateAdded = now,
                        condition = condition, language = language,
                    ),
                )
            }
            quantity <= 0 -> delete(existing)
            else -> update(existing.copy(quantity = quantity))
        }
    }

    /**
     * Moves up to [quantity] copies from one stack to another, merging into the target
     * stack when it exists. Used when the scanner's version picker re-keys copies that
     * auto-add already wrote: only the scanned copies move, so copies the user owned
     * before the scan stay where they were. Atomic, so a crash cannot drop the copies
     * between the two stacks.
     *
     * @return how many copies actually moved (fewer than asked when the source stack
     *   was edited in the meantime; zero when it is gone).
     */
    @Transaction
    suspend fun moveCopies(
        fromCardId: String,
        fromFoil: Boolean,
        toCardId: String,
        toFoil: Boolean,
        condition: String,
        language: String,
        quantity: Int,
        now: Long,
    ): Int {
        if (quantity <= 0 || (fromCardId == toCardId && fromFoil == toFoil)) return 0
        val source = findEntry(fromCardId, fromFoil, condition, language) ?: return 0
        val moved = minOf(quantity, source.quantity)
        if (moved == source.quantity) delete(source) else update(source.copy(quantity = source.quantity - moved))
        val target = findEntry(toCardId, toFoil, condition, language)
        if (target == null) {
            insert(
                CollectionEntryEntity(
                    cardId = toCardId, quantity = moved, isFoil = toFoil, dateAdded = now,
                    condition = condition, language = language,
                ),
            )
        } else {
            update(target.copy(quantity = target.quantity + moved))
        }
        return moved
    }

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
