package com.scanrift.android.service.backup

import com.scanrift.android.data.local.dao.CardListDao
import com.scanrift.android.data.local.dao.CollectionEntryDao
import com.scanrift.android.data.local.dao.DeckDao
import com.scanrift.android.data.local.dao.GameRecordDao
import javax.inject.Inject

/** Reads the database into a [CollectionSnapshot]. */
class SnapshotBuilder @Inject constructor(
    private val collectionEntryDao: CollectionEntryDao,
    private val cardListDao: CardListDao,
    private val deckDao: DeckDao,
    private val gameRecordDao: GameRecordDao,
) {

    suspend fun build(now: Long): CollectionSnapshot {
        // Orphaned rows (cardId nulled by a catalogue change) cannot be restored
        // anywhere, so they are dropped rather than written as nulls.
        val entries = collectionEntryDao.getAll().mapNotNull { entry ->
            val cardId = entry.cardId ?: return@mapNotNull null
            CollectionSnapshot.Entry(
                cardId = cardId,
                quantity = entry.quantity,
                isFoil = entry.isFoil,
                dateAdded = entry.dateAdded,
                condition = entry.condition,
                notes = entry.notes,
                folder = entry.folder,
            )
        }

        val decks = deckDao.getAllWithEntries().map { deck ->
            CollectionSnapshot.DeckExport(
                id = deck.deck.id,
                name = deck.deck.name,
                createdDate = deck.deck.createdDate,
                lastModifiedDate = deck.deck.lastModifiedDate,
                legendCardId = deck.deck.legendCardId,
                championCardId = deck.deck.championCardId,
                entries = deck.entries.mapNotNull { entryWithCard ->
                    val cardId = entryWithCard.entry.cardId ?: return@mapNotNull null
                    CollectionSnapshot.DeckEntryExport(
                        cardId = cardId,
                        quantity = entryWithCard.entry.quantity,
                        section = entryWithCard.entry.section,
                    )
                },
            )
        }

        val crossRefsByList = cardListDao.getAllCrossRefs().groupBy({ it.listId }, { it.cardId })
        val lists = cardListDao.getAll().map { list ->
            CollectionSnapshot.ListExport(
                id = list.id,
                name = list.name,
                colorHex = list.colorHex,
                systemType = list.systemType,
                isSystem = list.isSystem,
                createdDate = list.createdDate,
                cardIds = crossRefsByList[list.id].orEmpty(),
            )
        }

        val gameRecords = gameRecordDao.getAll().map { record ->
            CollectionSnapshot.GameRecordExport(
                id = record.id,
                date = record.date,
                name = record.name,
                result = record.result,
                playerName = record.playerName,
                opponentName = record.opponentName,
                pointsScored = record.pointsScored,
                pointsAllowed = record.pointsAllowed,
                ties = record.ties,
                conquerCount = record.conquerCount,
                holdCount = record.holdCount,
                abilityCount = record.abilityCount,
                notes = record.notes,
                deckId = record.deckId,
                legendId = record.legendId,
                opponentLegendId = record.opponentLegendId,
            )
        }

        return CollectionSnapshot(
            exportedAt = now,
            entries = entries,
            decks = decks,
            lists = lists,
            gameRecords = gameRecords,
        )
    }
}
