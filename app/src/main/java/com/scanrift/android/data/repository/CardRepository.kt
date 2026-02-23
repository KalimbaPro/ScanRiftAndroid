package com.scanrift.android.data.repository

import android.content.Context
import com.google.gson.Gson
import com.scanrift.android.R
import com.scanrift.android.data.local.dao.CardDao
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.remote.api.RiftboundApi
import com.scanrift.android.data.remote.dto.CardDTO
import com.scanrift.android.data.remote.dto.PaginatedResponse
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.io.InputStreamReader

class CardRepository(
    private val cardDao: CardDao,
    private val api: RiftboundApi,
    private val context: Context
) {

    fun getAllCards(): Flow<List<CardEntity>> = cardDao.getAllCards()

    fun getCardCount(): Flow<Int> = cardDao.getCardCount()

    suspend fun getCardById(id: String): CardEntity? = cardDao.getCardById(id)

    fun searchCards(query: String): Flow<List<CardEntity>> = cardDao.searchCards(query)

    suspend fun findBySetAndNumber(setId: String, collectorNumber: Int): CardEntity? =
        cardDao.findBySetAndNumber(setId, collectorNumber)

    suspend fun findByPublicCode(publicCode: String): CardEntity? =
        cardDao.findByPublicCode(publicCode)

    suspend fun getAllSetLabels(): List<String> = cardDao.getAllSetLabels()

    suspend fun getAllTypes(): List<String> = cardDao.getAllTypes()

    suspend fun getAllRarities(): List<String> = cardDao.getAllRarities()

    /**
     * Sync database from API with progress callback.
     * Falls back to bundled JSON if API fails.
     */
    suspend fun syncDatabase(
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Int> {
        return try {
            val allCards = mutableListOf<CardDTO>()

            // Fetch first page to get total pages
            val firstResponse = api.getCards(page = 1, size = 100)
            allCards.addAll(firstResponse.items)
            val totalPages = firstResponse.pages
            onProgress(1, totalPages)

            // Fetch remaining pages
            var currentPage = 2
            while (currentPage <= totalPages) {
                val response = api.getCards(page = currentPage, size = 100)
                allCards.addAll(response.items)
                onProgress(currentPage, totalPages)
                currentPage++
            }

            val entities = allCards.map { it.toEntity() }
            cardDao.deleteAll()
            cardDao.insertAll(entities)
            Timber.d("Synced %d cards from API", entities.size)
            Result.success(entities.size)
        } catch (e: Exception) {
            Timber.w(e, "API sync failed, falling back to bundled JSON")
            loadFromBundledJson()
        }
    }

    /**
     * Load cards from bundled res/raw/cards.json.
     */
    suspend fun loadFromBundledJson(): Result<Int> {
        return try {
            val inputStream = context.resources.openRawResource(R.raw.cards)
            val reader = InputStreamReader(inputStream)
            val response = Gson().fromJson(reader, PaginatedResponse::class.java)
            reader.close()

            val entities = response.items.map { it.toEntity() }
            cardDao.deleteAll()
            cardDao.insertAll(entities)
            Timber.d("Loaded %d cards from bundled JSON", entities.size)
            Result.success(entities.size)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load bundled JSON")
            Result.failure(e)
        }
    }
}
