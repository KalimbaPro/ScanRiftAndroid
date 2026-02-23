package com.scanrift.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.scanrift.android.data.local.entity.CardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: CardEntity)

    @Update
    suspend fun update(card: CardEntity)

    @Query("SELECT * FROM cards ORDER BY name ASC")
    fun getAllCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards ORDER BY name ASC")
    suspend fun getAllCardsList(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getCardById(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE id = :id")
    fun getCardByIdFlow(id: String): Flow<CardEntity?>

    @Query("SELECT * FROM cards WHERE name LIKE '%' || :query || '%' OR publicCode LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchCards(query: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE setId = :setId AND collectorNumber = :collectorNumber LIMIT 1")
    suspend fun findBySetAndNumber(setId: String, collectorNumber: Int): CardEntity?

    @Query("SELECT * FROM cards WHERE publicCode = :publicCode LIMIT 1")
    suspend fun findByPublicCode(publicCode: String): CardEntity?

    @Query("SELECT COUNT(*) FROM cards")
    fun getCardCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getCardCountValue(): Int

    @Query("SELECT DISTINCT setId FROM cards ORDER BY setId ASC")
    suspend fun getAllSetIds(): List<String>

    @Query("SELECT DISTINCT setLabel FROM cards ORDER BY setLabel ASC")
    suspend fun getAllSetLabels(): List<String>

    @Query("SELECT DISTINCT type FROM cards ORDER BY type ASC")
    suspend fun getAllTypes(): List<String>

    @Query("SELECT DISTINCT rarity FROM cards ORDER BY rarity ASC")
    suspend fun getAllRarities(): List<String>

    @Query("DELETE FROM cards")
    suspend fun deleteAll()
}
