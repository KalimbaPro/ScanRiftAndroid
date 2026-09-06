package com.scanrift.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.scanrift.android.data.local.entity.DeckEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity
import com.scanrift.android.data.local.entity.DeckEntryWithCard
import com.scanrift.android.data.local.entity.DeckWithEntries
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Upsert
    suspend fun upsertDeck(deck: DeckEntity)

    @Update
    suspend fun updateDeck(deck: DeckEntity)

    @Delete
    suspend fun deleteDeck(deck: DeckEntity)

    @Query("SELECT * FROM decks ORDER BY lastModifiedDate DESC")
    fun observeAll(): Flow<List<DeckEntity>>

    @Transaction
    @Query("SELECT * FROM decks ORDER BY lastModifiedDate DESC")
    fun observeAllWithEntries(): Flow<List<DeckWithEntries>>

    @Transaction
    @Query("SELECT * FROM decks WHERE id = :deckId")
    fun observeWithEntries(deckId: String): Flow<DeckWithEntries?>

    @Query("SELECT * FROM decks WHERE id = :deckId")
    suspend fun getById(deckId: String): DeckEntity?

    @Query("SELECT * FROM decks")
    suspend fun getAll(): List<DeckEntity>

    @Transaction
    @Query("SELECT * FROM decks")
    suspend fun getAllWithEntries(): List<DeckWithEntries>

    @Query("UPDATE decks SET lastModifiedDate = :timestamp WHERE id = :deckId")
    suspend fun touch(deckId: String, timestamp: Long)

    // ── Entries ──────────────────────────────────────────────────────────────

    @Upsert
    suspend fun upsertEntry(entry: DeckEntryEntity)

    @Upsert
    suspend fun upsertEntries(entries: List<DeckEntryEntity>)

    @Update
    suspend fun updateEntry(entry: DeckEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: DeckEntryEntity)

    @Query("SELECT * FROM deck_entries WHERE deckId = :deckId")
    suspend fun entriesForDeck(deckId: String): List<DeckEntryEntity>

    @Transaction
    @Query("SELECT * FROM deck_entries WHERE deckId = :deckId")
    fun observeEntriesWithCards(deckId: String): Flow<List<DeckEntryWithCard>>

    @Query("SELECT * FROM deck_entries WHERE deckId = :deckId AND cardId = :cardId AND section = :section LIMIT 1")
    suspend fun findEntry(deckId: String, cardId: String, section: String): DeckEntryEntity?

    @Query("SELECT * FROM deck_entries")
    suspend fun getAllEntries(): List<DeckEntryEntity>

    @Query("DELETE FROM deck_entries WHERE deckId = :deckId")
    suspend fun deleteAllEntriesForDeck(deckId: String)

    @Query("DELETE FROM deck_entries WHERE cardId IS NULL")
    suspend fun deleteOrphanedEntries(): Int
}
