package com.scanrift.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.local.entity.CollectionEntryWithCard
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CollectionEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CollectionEntryEntity>)

    @Update
    suspend fun update(entry: CollectionEntryEntity)

    @Delete
    suspend fun delete(entry: CollectionEntryEntity)

    @Transaction
    @Query("SELECT * FROM collection_entries ORDER BY dateAdded DESC")
    fun getAllWithCards(): Flow<List<CollectionEntryWithCard>>

    @Transaction
    @Query("SELECT * FROM collection_entries ORDER BY dateAdded DESC")
    suspend fun getAllWithCardsList(): List<CollectionEntryWithCard>

    @Query("SELECT * FROM collection_entries WHERE cardId = :cardId")
    suspend fun getEntriesForCard(cardId: String): List<CollectionEntryEntity>

    @Query("SELECT * FROM collection_entries WHERE cardId = :cardId AND isFoil = :isFoil LIMIT 1")
    suspend fun findEntry(cardId: String, isFoil: Boolean): CollectionEntryEntity?

    @Query("SELECT COUNT(*) FROM collection_entries")
    fun getEntryCount(): Flow<Int>

    @Query("SELECT SUM(quantity) FROM collection_entries")
    fun getTotalCardCount(): Flow<Int?>

    @Query("SELECT COUNT(DISTINCT cardId) FROM collection_entries")
    fun getUniqueCardCount(): Flow<Int>

    @Query("DELETE FROM collection_entries")
    suspend fun deleteAll()
}
