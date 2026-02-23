package com.scanrift.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.scanrift.android.data.local.entity.CardListCrossRef
import com.scanrift.android.data.local.entity.CardListEntity
import com.scanrift.android.data.local.entity.CardListWithCards
import kotlinx.coroutines.flow.Flow

@Dao
interface CardListDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: CardListEntity)

    @Update
    suspend fun update(list: CardListEntity)

    @Delete
    suspend fun delete(list: CardListEntity)

    @Query("SELECT * FROM card_lists ORDER BY isSystem DESC, createdDate ASC")
    fun getAllLists(): Flow<List<CardListEntity>>

    @Query("SELECT * FROM card_lists WHERE id = :id")
    suspend fun getListById(id: String): CardListEntity?

    @Query("SELECT * FROM card_lists WHERE systemType = :systemType LIMIT 1")
    suspend fun getSystemList(systemType: String): CardListEntity?

    @Transaction
    @Query("SELECT * FROM card_lists WHERE id = :id")
    fun getListWithCards(id: String): Flow<CardListWithCards?>

    @Transaction
    @Query("SELECT * FROM card_lists ORDER BY isSystem DESC, createdDate ASC")
    fun getAllListsWithCards(): Flow<List<CardListWithCards>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCardToList(crossRef: CardListCrossRef)

    @Delete
    suspend fun removeCardFromList(crossRef: CardListCrossRef)

    @Query("SELECT listId FROM card_list_cross_ref WHERE cardId = :cardId")
    suspend fun getListIdsForCard(cardId: String): List<String>

    @Query("SELECT COUNT(*) FROM card_list_cross_ref WHERE listId = :listId")
    fun getCardCountForList(listId: String): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM card_lists WHERE name = :name AND id != :excludeId)")
    suspend fun existsWithName(name: String, excludeId: String = ""): Boolean
}
