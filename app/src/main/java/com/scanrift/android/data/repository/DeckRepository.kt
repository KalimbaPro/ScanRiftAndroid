package com.scanrift.android.data.repository

import com.scanrift.android.data.local.dao.DeckDao
import com.scanrift.android.data.local.dao.GameRecordDao
import com.scanrift.android.data.local.entity.DeckEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity
import com.scanrift.android.data.local.mapper.toDomain
import com.scanrift.android.di.IoDispatcher
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckEntry
import com.scanrift.android.domain.model.DeckSection
import com.scanrift.android.domain.model.GameRecord
import com.scanrift.android.service.deck.DeckValidator
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class DeckRepository @Inject constructor(
    private val deckDao: DeckDao,
    private val gameRecordDao: GameRecordDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    fun observeDecks(): Flow<List<Deck>> =
        deckDao.observeAllWithEntries().map { rows -> rows.map { it.toDomain() } }

    fun observeDeck(deckId: String): Flow<Deck?> =
        deckDao.observeWithEntries(deckId).map { it?.toDomain() }

    fun observeGameRecords(deckId: String): Flow<List<GameRecord>> =
        gameRecordDao.observeForDeck(deckId).map { rows -> rows.map { it.toDomain() } }

    suspend fun createDeck(name: String = "New Deck", now: Long = System.currentTimeMillis()): String =
        withContext(io) {
            val id = UUID.randomUUID().toString()
            deckDao.upsertDeck(DeckEntity(id = id, name = name, createdDate = now, lastModifiedDate = now))
            id
        }

    suspend fun deleteDeck(deck: Deck) = withContext(io) {
        deckDao.getById(deck.id)?.let { deckDao.deleteDeck(it) }
    }

    suspend fun rename(deckId: String, name: String) = withContext(io) {
        deckDao.getById(deckId)?.let { deckDao.upsertDeck(it.copy(name = name, lastModifiedDate = now())) }
    }

    suspend fun setLegend(deckId: String, cardId: String?) = withContext(io) {
        deckDao.getById(deckId)?.let { deckDao.upsertDeck(it.copy(legendCardId = cardId, lastModifiedDate = now())) }
    }

    /**
     * Setting a champion also puts one copy in the main deck, matching iOS.
     *
     * The existing-entry lookup is by card **id** while the copy limit counts by
     * `cleanName`. That mismatch is intentional: an alternate-art printing of the same
     * champion is a different row but the same card for limit purposes.
     */
    suspend fun setChampion(deckId: String, card: Card?) = withContext(io) {
        val deck = deckDao.getById(deckId) ?: return@withContext
        deckDao.upsertDeck(deck.copy(championCardId = card?.id, lastModifiedDate = now()))
        if (card == null) return@withContext

        val existing = deckDao.findEntry(deckId, card.id, DeckSection.MAIN_DECK.value)
        if (existing == null) {
            deckDao.upsertEntry(
                DeckEntryEntity(deckId = deckId, cardId = card.id, quantity = 1, section = DeckSection.MAIN_DECK.value),
            )
        }
    }

    /**
     * Replaces a deck's contents wholesale — the import path.
     *
     * Deliberately not a loop over [addCard]: that adds one card at a time, silently
     * returns false past a copy limit, and would leave a half-built deck behind if a
     * later line failed. The caller has already resolved and clamped everything, so
     * this writes the finished picture in one transaction.
     */
    suspend fun replaceContents(
        deckId: String,
        legendCardId: String?,
        championCardId: String?,
        entries: List<DeckEntryEntity>,
    ) = withContext(io) {
        val deck = deckDao.getById(deckId) ?: return@withContext
        deckDao.replaceContents(
            deck.copy(
                legendCardId = legendCardId,
                championCardId = championCardId,
                lastModifiedDate = now(),
            ),
            entries,
        )
    }

    suspend fun addCard(
        deck: Deck,
        card: Card,
        section: DeckSection = DeckValidator.inferSection(card),
        quantity: Int = 1,
    ): Boolean = withContext(io) {
        val limit = DeckValidator.maxCopies(card)
        if (DeckValidator.copiesInDeck(deck, card) + quantity > limit) return@withContext false

        // Battlefields are singleton by name: one copy, and never a second printing of
        // the same battlefield. The deck needs exactly three *different* ones.
        if (section == DeckSection.BATTLEFIELD) {
            val alreadyPresent = deck.entries.any {
                it.section == DeckSection.BATTLEFIELD && it.card?.cleanName == card.cleanName
            }
            if (alreadyPresent) return@withContext false
        }

        val existing = deckDao.findEntry(deck.id, card.id, section.value)
        if (existing == null) {
            deckDao.upsertEntry(
                DeckEntryEntity(deckId = deck.id, cardId = card.id, quantity = quantity, section = section.value),
            )
        } else if (section == DeckSection.BATTLEFIELD) {
            return@withContext false
        } else {
            deckDao.upsertEntry(existing.copy(quantity = existing.quantity + quantity))
        }
        deckDao.touch(deck.id, now())
        true
    }

    suspend fun setEntryQuantity(deck: Deck, entry: DeckEntry, quantity: Int) = withContext(io) {
        val cardId = entry.cardId ?: return@withContext
        val stored = deckDao.findEntry(deck.id, cardId, entry.section.value) ?: return@withContext

        // A battlefield is singleton; the stepper must not be able to push it past one.
        if (entry.section == DeckSection.BATTLEFIELD && quantity > 1) return@withContext

        if (quantity <= 0) {
            deckDao.deleteEntry(stored)
            // Removing the last copy of the champion clears the slot, as on iOS.
            val champion = deck.champion
            if (champion != null && champion.cleanName == entry.card?.cleanName) {
                val remaining = deck.entries.any {
                    it.id != entry.id && it.card?.cleanName == champion.cleanName
                }
                if (!remaining) {
                    deckDao.getById(deck.id)?.let { deckDao.upsertDeck(it.copy(championCardId = null)) }
                }
            }
        } else {
            deckDao.upsertEntry(stored.copy(quantity = quantity))
        }
        deckDao.touch(deck.id, now())
    }

    /**
     * Moves copies between sections.
     *
     * The target is incremented **before** the source is decremented, so the champion
     * cleanup in [setEntryQuantity] still sees the card in the deck and does not
     * spuriously clear the slot.
     */
    suspend fun moveToSection(deck: Deck, entry: DeckEntry, target: DeckSection) = withContext(io) {
        val cardId = entry.cardId ?: return@withContext
        if (entry.section == target) return@withContext

        val existingTarget = deckDao.findEntry(deck.id, cardId, target.value)
        if (existingTarget == null) {
            deckDao.upsertEntry(
                DeckEntryEntity(deckId = deck.id, cardId = cardId, quantity = 1, section = target.value),
            )
        } else {
            deckDao.upsertEntry(existingTarget.copy(quantity = existingTarget.quantity + 1))
        }
        setEntryQuantity(deck, entry, entry.quantity - 1)
    }

    private fun now() = System.currentTimeMillis()
}
