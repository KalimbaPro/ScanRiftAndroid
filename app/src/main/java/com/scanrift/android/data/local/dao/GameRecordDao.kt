package com.scanrift.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.scanrift.android.data.local.entity.GameRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameRecordDao {

    @Upsert
    suspend fun upsert(record: GameRecordEntity)

    @Upsert
    suspend fun upsertAll(records: List<GameRecordEntity>)

    /**
     * Snapshot restore uses IGNORE, not REPLACE: a game record has no mutation
     * timestamp (its `date` is when the match happened, not when the row changed), so
     * last-write-wins is not expressible. Records are treated as immutable — a restore
     * adds the ones you don't have and never overwrites the ones you do.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(records: List<GameRecordEntity>): List<Long>

    @Delete
    suspend fun delete(record: GameRecordEntity)

    @Query("SELECT * FROM game_records ORDER BY date DESC")
    fun observeAll(): Flow<List<GameRecordEntity>>

    @Query("SELECT * FROM game_records ORDER BY date DESC")
    suspend fun getAll(): List<GameRecordEntity>

    @Query("SELECT * FROM game_records WHERE deckId = :deckId ORDER BY date DESC")
    fun observeForDeck(deckId: String): Flow<List<GameRecordEntity>>

    @Query("SELECT * FROM game_records WHERE deckId = :deckId ORDER BY date ASC")
    suspend fun getForDeck(deckId: String): List<GameRecordEntity>

    @Query("SELECT * FROM game_records WHERE id = :id")
    suspend fun getById(id: String): GameRecordEntity?

    @Query("SELECT id FROM game_records")
    suspend fun allIds(): List<String>

    @Query("SELECT COUNT(*) FROM game_records WHERE deckId = :deckId AND result = :result")
    suspend fun countForDeckByResult(deckId: String, result: String): Int
}
