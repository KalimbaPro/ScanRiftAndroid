package com.scanrift.android.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.CardListCrossRef
import com.scanrift.android.data.local.entity.CardListEntity
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.local.entity.DeckEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity
import com.scanrift.android.data.local.entity.GameRecordEntity
import com.scanrift.android.domain.model.CardCondition
import com.scanrift.android.domain.model.DeckSection
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the schema decisions that exist to prevent data loss.
 *
 * The headline case is the CASCADE regression: the previous Android build wiped the
 * catalogue on every sync, and because the child tables cascaded, it took the user's
 * collection, decks and lists with it. These tests pin the SET NULL behaviour that
 * makes that failure mode structurally impossible.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaIntegrityTest {

    private lateinit var db: ScanRiftDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ScanRiftDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun card(id: String, riftboundId: String = "ogn-021-298") = CardEntity(
        id = id,
        name = "Test Card",
        riftboundId = riftboundId,
        publicCode = "OGN-021/298",
        collectorNumber = 21,
        energy = 3, might = null, power = 2,
        type = "Unit", supertype = null, rarity = "Common",
        domains = listOf("Fury"),
        richText = null, plainText = null,
        setId = "OGN", setLabel = "Origins", sourceSetId = "OGN",
        imageUrl = null, artist = null, accessibilityText = null,
        cleanName = "Test Card",
        updatedOn = 1_000L,
    )

    @Test
    fun `deleting a card keeps the collection entry and nulls its card reference`() = runTest {
        db.cardDao().upsert(card("card-1"))
        db.collectionEntryDao().insert(
            CollectionEntryEntity(cardId = "card-1", quantity = 3, dateAdded = 1L),
        )

        db.cardDao().deleteByIds(listOf("card-1"))

        val entries = db.collectionEntryDao().getAll()
        assertThat(entries).hasSize(1)
        assertThat(entries.single().cardId).isNull()
        // The quantity survives, so a catalogue refresh can never silently lose counts.
        assertThat(entries.single().quantity).isEqualTo(3)
    }

    @Test
    fun `deleting a card keeps the deck and empties its legend and champion slots`() = runTest {
        db.cardDao().upsert(card("legend-1", "ogn-001-298"))
        db.cardDao().upsert(card("champ-1", "ogn-002-298"))
        db.deckDao().upsertDeck(
            DeckEntity(
                id = "deck-1", name = "Test Deck",
                createdDate = 1L, lastModifiedDate = 1L,
                legendCardId = "legend-1", championCardId = "champ-1",
            ),
        )

        db.cardDao().deleteByIds(listOf("legend-1", "champ-1"))

        val deck = db.deckDao().getById("deck-1")
        assertThat(deck).isNotNull()
        assertThat(deck!!.legendCardId).isNull()
        assertThat(deck.championCardId).isNull()
    }

    @Test
    fun `deleting a card keeps deck entries but drops list membership`() = runTest {
        db.cardDao().upsert(card("card-1"))
        db.deckDao().upsertDeck(DeckEntity(id = "deck-1", createdDate = 1L, lastModifiedDate = 1L))
        db.deckDao().upsertEntry(
            DeckEntryEntity(deckId = "deck-1", cardId = "card-1", quantity = 2, section = DeckSection.MAIN_DECK.value),
        )
        db.cardListDao().upsert(CardListEntity(id = "list-1", name = "Faves", createdDate = 1L))
        db.cardListDao().addCardToList(CardListCrossRef(listId = "list-1", cardId = "card-1"))

        db.cardDao().deleteByIds(listOf("card-1"))

        // Deck entries survive with a null card, mirroring iOS's optional relationship.
        val deckEntries = db.deckDao().getAllEntries()
        assertThat(deckEntries).hasSize(1)
        assertThat(deckEntries.single().cardId).isNull()

        // List membership is a join row, so it cascades away — the list itself remains.
        assertThat(db.cardListDao().cardIdsForList("list-1")).isEmpty()
        assertThat(db.cardListDao().getById("list-1")).isNotNull()
    }

    @Test
    fun `deleting a deck cascades its entries but only unlinks its game records`() = runTest {
        db.cardDao().upsert(card("card-1"))
        db.deckDao().upsertDeck(DeckEntity(id = "deck-1", createdDate = 1L, lastModifiedDate = 1L))
        db.deckDao().upsertEntry(DeckEntryEntity(deckId = "deck-1", cardId = "card-1"))
        db.gameRecordDao().upsert(
            GameRecordEntity(id = "game-1", date = 10L, result = "win", deckId = "deck-1", legendId = "card-1"),
        )

        db.deckDao().deleteDeck(db.deckDao().getById("deck-1")!!)

        assertThat(db.deckDao().getAllEntries()).isEmpty()

        // History outlives the deck: the record stays, unlinked. legendId is a plain
        // string, not a FK, so the matchup survives even without the card.
        val records = db.gameRecordDao().getAll()
        assertThat(records).hasSize(1)
        assertThat(records.single().deckId).isNull()
        assertThat(records.single().legendId).isEqualTo("card-1")
    }

    @Test
    fun `a catalogue refresh by upsert leaves the collection completely intact`() = runTest {
        // The regression this whole schema exists to prevent: the old build ran
        // deleteAll() + insertAll() on every sync and the cascade ate the collection.
        db.cardDao().upsert(card("card-1"))
        db.collectionEntryDao().insert(CollectionEntryEntity(cardId = "card-1", quantity = 4, dateAdded = 1L))
        db.deckDao().upsertDeck(DeckEntity(id = "deck-1", createdDate = 1L, lastModifiedDate = 1L))
        db.deckDao().upsertEntry(DeckEntryEntity(deckId = "deck-1", cardId = "card-1", quantity = 2))

        // Simulate a full sync: every card re-upserted with fresh data.
        db.cardDao().upsertAll(listOf(card("card-1").copy(name = "Renamed", updatedOn = 2_000L)))

        assertThat(db.cardDao().count()).isEqualTo(1)
        assertThat(db.cardDao().getById("card-1")!!.name).isEqualTo("Renamed")
        assertThat(db.collectionEntryDao().getAll().single().quantity).isEqualTo(4)
        assertThat(db.deckDao().getAllEntries().single().quantity).isEqualTo(2)
    }

    @Test
    fun `the collection identity triple is unique`() = runTest {
        db.cardDao().upsert(card("card-1"))
        db.collectionEntryDao().insert(
            CollectionEntryEntity(cardId = "card-1", isFoil = false, condition = CardCondition.NEAR_MINT.value, dateAdded = 1L),
        )

        // Same card, different foil or condition: distinct stacks, both allowed.
        db.collectionEntryDao().insert(
            CollectionEntryEntity(cardId = "card-1", isFoil = true, condition = CardCondition.NEAR_MINT.value, dateAdded = 1L),
        )
        db.collectionEntryDao().insert(
            CollectionEntryEntity(cardId = "card-1", isFoil = false, condition = CardCondition.MINT.value, dateAdded = 1L),
        )
        assertThat(db.collectionEntryDao().getAll()).hasSize(3)

        // The same triple twice must be rejected rather than silently duplicated.
        val duplicate = runCatching {
            db.collectionEntryDao().insert(
                CollectionEntryEntity(cardId = "card-1", isFoil = false, condition = CardCondition.NEAR_MINT.value, dateAdded = 2L),
            )
        }
        assertThat(duplicate.isFailure).isTrue()
    }

    @Test
    fun `duplicate riftbound ids are allowed so de-duplication can resolve them`() = runTest {
        // The API returns a preliminary and a finalised record sharing a riftboundId;
        // 16 such pairs are in the bundled catalogue. A unique index here would throw
        // during upsert, before de-duplication ever ran.
        db.cardDao().upsert(card("stub-021", riftboundId = "ogn-021-298"))
        db.cardDao().upsert(card("final-021", riftboundId = "ogn-021-298"))

        assertThat(db.cardDao().count()).isEqualTo(2)
        assertThat(db.cardDao().duplicatedRiftboundIds()).containsExactly("ogn-021-298")
    }

    @Test
    fun `referenced card ids span collection decks legends and champions but not lists`() = runTest {
        listOf("in-collection", "in-deck", "is-legend", "is-champion", "in-list-only")
            .forEachIndexed { i, id -> db.cardDao().upsert(card(id, "ogn-${i}00-298")) }

        db.collectionEntryDao().insert(CollectionEntryEntity(cardId = "in-collection", dateAdded = 1L))
        db.deckDao().upsertDeck(
            DeckEntity(
                id = "deck-1", createdDate = 1L, lastModifiedDate = 1L,
                legendCardId = "is-legend", championCardId = "is-champion",
            ),
        )
        db.deckDao().upsertEntry(DeckEntryEntity(deckId = "deck-1", cardId = "in-deck"))
        db.cardListDao().upsert(CardListEntity(id = "list-1", name = "L", createdDate = 1L))
        db.cardListDao().addCardToList(CardListCrossRef(listId = "list-1", cardId = "in-list-only"))

        // List membership is deliberately excluded, matching iOS's `isReferenced`.
        assertThat(db.cardDao().referencedCardIds())
            .containsExactly("in-collection", "in-deck", "is-legend", "is-champion")
    }
}
