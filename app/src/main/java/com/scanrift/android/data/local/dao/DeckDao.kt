package com.scanrift.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.scanrift.android.data.local.entity.DeckEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity
import com.scanrift.android.data.local.entity.DeckEntryWithCard
import com.scanrift.android.data.local.entity.DeckWithEntries
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    // Deck CRUD
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: DeckEntity)

    @Update
    suspend fun updateDeck(deck: DeckEntity)

    @Delete
    suspend fun deleteDeck(deck: DeckEntity)

    @Query("SELECT * FROM decks ORDER BY lastModifiedDate DESC")
    fun getAllDecks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeckById(id: String): DeckEntity?

    @Transaction
    @Query("SELECT * FROM decks WHERE id = :id")
    fun getDeckWithEntries(id: String): Flow<DeckWithEntries?>

    @Transaction
    @Query("SELECT * FROM decks ORDER BY lastModifiedDate DESC")
    fun getAllDecksWithEntries(): Flow<List<DeckWithEntries>>

    // DeckEntry CRUD
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DeckEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: DeckEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: DeckEntryEntity)

    @Query("SELECT * FROM deck_entries WHERE deckId = :deckId")
    suspend fun getEntriesForDeck(deckId: String): List<DeckEntryEntity>

    @Transaction
    @Query("SELECT * FROM deck_entries WHERE deckId = :deckId")
    fun getEntriesWithCardsForDeck(deckId: String): Flow<List<DeckEntryWithCard>>

    @Query("SELECT * FROM deck_entries WHERE deckId = :deckId AND cardId = :cardId AND section = :section LIMIT 1")
    suspend fun findEntry(deckId: String, cardId: String, section: String): DeckEntryEntity?

    @Query("DELETE FROM deck_entries WHERE deckId = :deckId")
    suspend fun deleteAllEntriesForDeck(deckId: String)
}
