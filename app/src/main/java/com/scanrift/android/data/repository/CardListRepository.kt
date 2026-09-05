package com.scanrift.android.data.repository

import com.scanrift.android.core.Constants
import com.scanrift.android.data.local.dao.CardListDao
import com.scanrift.android.data.local.entity.CardListCrossRef
import com.scanrift.android.data.local.entity.CardListEntity
import com.scanrift.android.data.local.mapper.toDomain
import com.scanrift.android.di.IoDispatcher
import com.scanrift.android.domain.model.CardList
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class CardListRepository @Inject constructor(
    private val dao: CardListDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    fun observeLists(): Flow<List<CardList>> =
        dao.observeAllWithCards().map { rows -> rows.map { it.toDomain() } }

    fun observeList(id: String): Flow<CardList?> =
        dao.observeWithCards(id).map { it?.toDomain() }

    suspend fun listIdsForCard(cardId: String): List<String> = withContext(io) { dao.listIdsForCard(cardId) }

    suspend fun createList(name: String, colorHex: String, now: Long = System.currentTimeMillis()): String =
        withContext(io) {
            val id = UUID.randomUUID().toString()
            dao.upsert(CardListEntity(id = id, name = name, colorHex = colorHex, createdDate = now))
            id
        }

    suspend fun rename(list: CardList, name: String, colorHex: String) = withContext(io) {
        dao.upsert(
            CardListEntity(
                id = list.id, name = name, colorHex = colorHex,
                isSystem = list.isSystem, systemType = list.systemType, createdDate = list.createdDate,
            ),
        )
    }

    suspend fun delete(listId: String) = withContext(io) { dao.deleteByIds(listOf(listId)) }

    suspend fun addCard(listId: String, cardId: String) = withContext(io) {
        dao.addCardToList(CardListCrossRef(listId, cardId))
    }

    suspend fun addCards(listId: String, cardIds: List<String>) = withContext(io) {
        dao.addCardsToList(cardIds.map { CardListCrossRef(listId, it) })
    }

    suspend fun removeCard(listId: String, cardId: String) = withContext(io) {
        dao.removeCardFromList(CardListCrossRef(listId, cardId))
    }

    suspend fun wishlistId(): String? = withContext(io) {
        dao.getSystemLists(Constants.Wishlist.SYSTEM_TYPE).firstOrNull()?.id
    }

    /**
     * Guarantees exactly one system Wishlist.
     *
     * Ported from iOS's launch reconciliation, minus the CloudKit wait — with no cloud
     * there is no race that could create a second one, but duplicates could still
     * arrive through a restore, so the merge path stays.
     */
    suspend fun reconcileWishlist(now: Long = System.currentTimeMillis()) = withContext(io) {
        val existing = dao.getSystemLists(Constants.Wishlist.SYSTEM_TYPE)
        when {
            existing.isEmpty() -> dao.upsert(
                CardListEntity(
                    id = UUID.randomUUID().toString(),
                    name = Constants.Wishlist.NAME,
                    colorHex = Constants.Wishlist.COLOR_HEX,
                    isSystem = true,
                    systemType = Constants.Wishlist.SYSTEM_TYPE,
                    createdDate = now,
                ),
            )
            existing.size > 1 -> {
                // Merge into the oldest so the id the user's other data points at wins.
                val keeper = existing.first()
                val losers = existing.drop(1)
                val union = losers.flatMap { dao.cardIdsForList(it.id) }.distinct()
                dao.addCardsToList(union.map { CardListCrossRef(keeper.id, it) })
                dao.deleteByIds(losers.map { it.id })
            }
        }
    }
}
