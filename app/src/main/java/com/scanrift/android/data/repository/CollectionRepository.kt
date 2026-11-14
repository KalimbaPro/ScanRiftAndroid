package com.scanrift.android.data.repository

import com.scanrift.android.data.local.dao.CardDao
import com.scanrift.android.data.local.dao.CollectionEntryDao
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.local.mapper.toDomain
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardCondition
import com.scanrift.android.domain.model.CardLanguage
import com.scanrift.android.domain.model.CollectionEntry
import com.scanrift.android.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class CollectionRepository @Inject constructor(
    private val entryDao: CollectionEntryDao,
    private val cardDao: CardDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    fun observeEntries(): Flow<List<CollectionEntry>> =
        entryDao.observeAllWithCards().map { rows -> rows.map { it.toDomain() } }

    fun observeOwnedQuantities(): Flow<Map<String, Int>> =
        entryDao.observeOwnedQuantities().map { rows -> rows.associate { it.cardId to it.quantity } }

    fun observeTotalCardCount(): Flow<Int> = entryDao.observeTotalCardCount().map { it ?: 0 }

    fun observeUniqueCardCount(): Flow<Int> = entryDao.observeUniqueCardCount()

    fun observeAllCards(): Flow<List<Card>> =
        cardDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeCardCount(): Flow<Int> = cardDao.observeCount()

    fun observeCard(cardId: String): Flow<Card?> =
        cardDao.observeById(cardId).map { it?.toDomain() }

    suspend fun entriesForCard(cardId: String): List<CollectionEntry> = withContext(io) {
        entryDao.getForCard(cardId).map { it.toDomain() }
    }

    /**
     * Adds copies, merging into the existing stack for the same identity
     * `(card, isFoil, condition, language)`.
     *
     * Callers pass the card so `isAlwaysFoil` can default the foil flag the way the
     * scanner does.
     */
    suspend fun addCopies(
        card: Card,
        quantity: Int = 1,
        isFoil: Boolean = card.isAlwaysFoil,
        condition: CardCondition = CardCondition.NEAR_MINT,
        language: CardLanguage = CardLanguage.EN,
        now: Long = System.currentTimeMillis(),
    ) = withContext(io) {
        if (quantity <= 0) return@withContext
        val existing = entryDao.findEntry(card.id, isFoil, condition.value, language.value)
        if (existing == null) {
            entryDao.insert(
                CollectionEntryEntity(
                    cardId = card.id, quantity = quantity, isFoil = isFoil,
                    dateAdded = now, condition = condition.value, language = language.value,
                ),
            )
        } else {
            entryDao.update(existing.copy(quantity = existing.quantity + quantity))
        }
    }

    private suspend fun stored(entry: CollectionEntry): CollectionEntryEntity? =
        entry.cardId?.let { entryDao.findEntry(it, entry.isFoil, entry.condition.value, entry.language.value) }

    /** Sets an exact quantity; zero or less deletes the stack. */
    suspend fun setQuantity(entry: CollectionEntry, quantity: Int) = withContext(io) {
        val stored = stored(entry) ?: return@withContext
        if (quantity <= 0) entryDao.delete(stored) else entryDao.update(stored.copy(quantity = quantity))
    }

    suspend fun adjustQuantity(
        card: Card,
        entry: CollectionEntry?,
        delta: Int,
        now: Long = System.currentTimeMillis(),
    ) = withContext(io) {
        entryDao.adjustQuantity(
            cardId = card.id,
            isFoil = entry?.isFoil ?: card.isAlwaysFoil,
            condition = (entry?.condition ?: CardCondition.NEAR_MINT).value,
            language = (entry?.language ?: CardLanguage.EN).value,
            delta = delta,
            now = now,
        )
    }

    suspend fun setCondition(entry: CollectionEntry, condition: CardCondition) =
        rekey(entry) { it.copy(condition = condition.value) }

    suspend fun setLanguage(entry: CollectionEntry, language: CardLanguage) =
        rekey(entry) { it.copy(language = language.value) }

    /**
     * Condition and language are part of the identity, so changing either can collide
     * with an existing stack; merge into it rather than violating the unique index.
     */
    private suspend fun rekey(entry: CollectionEntry, change: (CollectionEntryEntity) -> CollectionEntryEntity) =
        withContext(io) {
            val stored = stored(entry) ?: return@withContext
            val changed = change(stored)
            if (changed == stored) return@withContext
            val target = entryDao.findEntry(stored.cardId!!, changed.isFoil, changed.condition, changed.language)
            if (target == null) {
                entryDao.update(changed)
            } else {
                entryDao.update(target.copy(quantity = target.quantity + stored.quantity))
                entryDao.delete(stored)
            }
        }

    suspend fun delete(entry: CollectionEntry) = withContext(io) {
        stored(entry)?.let { entryDao.delete(it) }
    }

    suspend fun clearCollection() = withContext(io) { entryDao.deleteAll() }
}
