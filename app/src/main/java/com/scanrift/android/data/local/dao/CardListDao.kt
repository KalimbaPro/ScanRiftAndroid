package com.scanrift.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.scanrift.android.data.local.entity.CardListCrossRef
import com.scanrift.android.data.local.entity.CardListEntity
import com.scanrift.android.data.local.entity.CardListWithCards
import kotlinx.coroutines.flow.Flow

@Dao
interface CardListDao {

    @Upsert
    suspend fun upsert(list: CardListEntity)

    @Update
    suspend fun update(list: CardListEntity)

    @Delete
    suspend fun delete(list: CardListEntity)

    @Query("SELECT * FROM card_lists ORDER BY isSystem DESC, createdDate ASC")
    fun observeAll(): Flow<List<CardListEntity>>

    @Query("SELECT * FROM card_lists ORDER BY isSystem DESC, createdDate ASC")
    suspend fun getAll(): List<CardListEntity>

    @Transaction
    @Query("SELECT * FROM card_lists ORDER BY isSystem DESC, createdDate ASC")
    fun observeAllWithCards(): Flow<List<CardListWithCards>>

    @Transaction
    @Query("SELECT * FROM card_lists WHERE id = :id")
    fun observeWithCards(id: String): Flow<CardListWithCards?>

    @Query("SELECT * FROM card_lists WHERE id = :id")
    suspend fun getById(id: String): CardListEntity?

    /** Ordered oldest-first so wishlist reconciliation can merge into the original. */
    @Query("SELECT * FROM card_lists WHERE systemType = :systemType ORDER BY createdDate ASC")
    suspend fun getSystemLists(systemType: String): List<CardListEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM card_lists WHERE LOWER(name) = LOWER(:name) AND id != :excludeId)")
    suspend fun existsWithName(name: String, excludeId: String = ""): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCardToList(crossRef: CardListCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCardsToList(crossRefs: List<CardListCrossRef>)

    @Delete
    suspend fun removeCardFromList(crossRef: CardListCrossRef)

    @Query("SELECT listId FROM card_list_cross_ref WHERE cardId = :cardId")
    suspend fun listIdsForCard(cardId: String): List<String>

    @Query("SELECT cardId FROM card_list_cross_ref WHERE listId = :listId")
    suspend fun cardIdsForList(listId: String): List<String>

    @Query("SELECT * FROM card_list_cross_ref")
    suspend fun getAllCrossRefs(): List<CardListCrossRef>

    @Query("DELETE FROM card_list_cross_ref WHERE listId = :listId")
    suspend fun clearList(listId: String)

    @Query("DELETE FROM card_lists WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
