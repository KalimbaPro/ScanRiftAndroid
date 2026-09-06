package com.scanrift.android.service.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.CardListCrossRef
import com.scanrift.android.data.local.entity.CardListEntity
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.local.entity.DeckEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity
import com.scanrift.android.data.local.entity.GameRecordEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Build a snapshot, serialise it, wipe everything, restore.
 *
 * This is the promise Settings makes to the user, so it is worth testing end to end
 * rather than only in pieces.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {

    private lateinit var db: ScanRiftDatabase
    private lateinit var builder: SnapshotBuilder
    private lateinit var merger: SnapshotMerger

    private val json = Json {
        ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false; prettyPrint = true
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ScanRiftDatabase::class.java,
        ).allowMainThreadQueries().build()
        builder = SnapshotBuilder(db.collectionEntryDao(), db.cardListDao(), db.deckDao(), db.gameRecordDao())
        merger = SnapshotMerger(db)
    }

    @After
    fun tearDown() = db.close()

    private fun card(id: String) = CardEntity(
        id = id, name = "Card $id", riftboundId = "ven-$id-166", publicCode = "VEN-001/166",
        collectorNumber = 1, energy = null, might = null, power = null,
        type = "Unit", supertype = null, rarity = "Common", domains = emptyList(),
        richText = null, plainText = null,
        setId = "VEN", setLabel = "Vendetta", sourceSetId = "VEN",
        imageUrl = null, artist = null, accessibilityText = null,
        cleanName = "Card $id", updatedOn = 1L,
    )

    private suspend fun seedUserData() {
        db.cardDao().upsertAll(listOf(card("c1"), card("c2"), card("legend")))

        db.collectionEntryDao().insert(CollectionEntryEntity(cardId = "c1", quantity = 4, dateAdded = 1_000L))
        db.collectionEntryDao().insert(
            CollectionEntryEntity(cardId = "c1", quantity = 1, isFoil = true, dateAdded = 1_000L),
        )
        db.collectionEntryDao().insert(
            CollectionEntryEntity(cardId = "c2", quantity = 2, condition = "Lightly Played", dateAdded = 1_000L),
        )

        db.cardListDao().upsert(
            CardListEntity(id = "wishlist", name = "Wishlist", colorHex = "#FFD60A", isSystem = true, systemType = "wishlist", createdDate = 1L),
        )
        db.cardListDao().addCardToList(CardListCrossRef("wishlist", "c2"))

        db.deckDao().upsertDeck(
            DeckEntity(id = "d1", name = "Akali Aggro", createdDate = 1L, lastModifiedDate = 5_000L, legendCardId = "legend"),
        )
        db.deckDao().upsertEntry(DeckEntryEntity(deckId = "d1", cardId = "c1", quantity = 3, section = "mainDeck"))
        db.deckDao().upsertEntry(DeckEntryEntity(deckId = "d1", cardId = "c2", quantity = 12, section = "rune"))

        db.gameRecordDao().upsert(
            GameRecordEntity(
                id = "g1", date = 9_000L, result = "win", deckId = "d1",
                pointsScored = 2, pointsAllowed = 1, conquerCount = 4, holdCount = 2, abilityCount = 1,
                legendId = "legend", opponentLegendId = "c2",
            ),
        )
    }

    @Test
    fun `a full round trip through JSON restores everything`() = runTest {
        seedUserData()

        val serialized = json.encodeToString(builder.build(now = 1_780_000_000_000L))

        // Wipe every user-owned table, keeping the catalogue as a fresh install would
        // have it after its first sync.
        db.collectionEntryDao().deleteAll()
        db.deckDao().getAll().forEach { db.deckDao().deleteDeck(it) }
        db.cardListDao().deleteByIds(listOf("wishlist"))
        db.gameRecordDao().getAll().forEach { db.gameRecordDao().delete(it) }
        assertThat(db.collectionEntryDao().getAll()).isEmpty()

        val result = merger.merge(json.decodeFromString<CollectionSnapshot>(serialized))

        assertThat(result.entriesAdded).isEqualTo(3)
        assertThat(result.decksAdded).isEqualTo(1)
        assertThat(result.listsAdded).isEqualTo(1)
        assertThat(result.gameRecordsAdded).isEqualTo(1)

        // Collection, including the foil and condition variants.
        val entries = db.collectionEntryDao().getAll()
        assertThat(entries).hasSize(3)
        assertThat(entries.single { it.cardId == "c1" && !it.isFoil }.quantity).isEqualTo(4)
        assertThat(entries.single { it.isFoil }.quantity).isEqualTo(1)
        assertThat(entries.single { it.condition == "Lightly Played" }.quantity).isEqualTo(2)

        // Deck with its legend and both sections.
        val deck = db.deckDao().getById("d1")!!
        assertThat(deck.name).isEqualTo("Akali Aggro")
        assertThat(deck.legendCardId).isEqualTo("legend")
        val deckEntries = db.deckDao().entriesForDeck("d1")
        assertThat(deckEntries.single { it.section == "mainDeck" }.quantity).isEqualTo(3)
        assertThat(deckEntries.single { it.section == "rune" }.quantity).isEqualTo(12)

        // The wishlist and its membership.
        assertThat(db.cardListDao().getById("wishlist")!!.isSystem).isTrue()
        assertThat(db.cardListDao().cardIdsForList("wishlist")).containsExactly("c2")

        // Game history, including the category breakdown iOS's JSON export drops.
        val game = db.gameRecordDao().getById("g1")!!
        assertThat(game.result).isEqualTo("win")
        assertThat(game.conquerCount).isEqualTo(4)
        assertThat(game.legendId).isEqualTo("legend")
        assertThat(game.opponentLegendId).isEqualTo("c2")
    }

    @Test
    fun `restoring the same file twice is idempotent`() = runTest {
        seedUserData()
        val serialized = json.encodeToString(builder.build(now = 1_780_000_000_000L))

        merger.merge(json.decodeFromString<CollectionSnapshot>(serialized))
        val second = merger.merge(json.decodeFromString<CollectionSnapshot>(serialized))

        // Nothing is newer than itself, so the second pass is a no-op.
        assertThat(second.entriesAdded).isEqualTo(0)
        assertThat(second.entriesUpdated).isEqualTo(0)
        assertThat(second.gameRecordsAdded).isEqualTo(0)
        assertThat(db.collectionEntryDao().getAll()).hasSize(3)
        assertThat(db.deckDao().entriesForDeck("d1")).hasSize(2)
    }

    @Test
    fun `orphaned rows are dropped from the snapshot rather than written as nulls`() = runTest {
        seedUserData()
        // A card leaving the catalogue nulls the FK; such a row cannot be restored
        // anywhere, so it must not appear in the file.
        db.cardDao().deleteByIds(listOf("c1"))

        val snapshot = builder.build(now = 1L)

        assertThat(snapshot.entries.map { it.cardId }).doesNotContain("c1")
        assertThat(snapshot.decks.single().entries.map { it.cardId }).doesNotContain("c1")
    }
}
