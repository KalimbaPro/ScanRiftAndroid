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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Restore is a merge, not a replace.
 *
 * Every test here is really the same question: can restoring an old backup destroy
 * something the user has now? The answer has to be no in every case.
 */
@RunWith(RobolectricTestRunner::class)
class SnapshotMergerTest {

    private lateinit var db: ScanRiftDatabase
    private lateinit var merger: SnapshotMerger

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ScanRiftDatabase::class.java,
        ).allowMainThreadQueries().build()
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

    private suspend fun seedCards(vararg ids: String) =
        db.cardDao().upsertAll(ids.map { card(it) })

    private fun snapshot(
        entries: List<CollectionSnapshot.Entry> = emptyList(),
        decks: List<CollectionSnapshot.DeckExport> = emptyList(),
        lists: List<CollectionSnapshot.ListExport> = emptyList(),
        gameRecords: List<CollectionSnapshot.GameRecordExport> = emptyList(),
    ) = CollectionSnapshot(
        exportedAt = 1_000L, entries = entries, decks = decks, lists = lists, gameRecords = gameRecords,
    )

    private fun entry(cardId: String, quantity: Int, dateAdded: Long, condition: String = "Near Mint", isFoil: Boolean = false) =
        CollectionSnapshot.Entry(cardId, quantity, isFoil, dateAdded, condition)

    // ── Entries ──────────────────────────────────────────────────────────────

    @Test
    fun `a newer dateAdded wins`() = runTest {
        seedCards("c1")
        db.collectionEntryDao().insert(CollectionEntryEntity(cardId = "c1", quantity = 1, dateAdded = 100L))

        val result = merger.merge(snapshot(entries = listOf(entry("c1", quantity = 7, dateAdded = 200L))))

        assertThat(result.entriesUpdated).isEqualTo(1)
        assertThat(db.collectionEntryDao().getAll().single().quantity).isEqualTo(7)
    }

    @Test
    fun `an older dateAdded is a silent no-op`() = runTest {
        // Restoring a stale backup must never roll back a quantity the user has since
        // increased. iOS does not count this as skipped either.
        seedCards("c1")
        db.collectionEntryDao().insert(CollectionEntryEntity(cardId = "c1", quantity = 9, dateAdded = 500L))

        val result = merger.merge(snapshot(entries = listOf(entry("c1", quantity = 1, dateAdded = 100L))))

        assertThat(db.collectionEntryDao().getAll().single().quantity).isEqualTo(9)
        assertThat(result.entriesUpdated).isEqualTo(0)
        assertThat(result.entriesSkipped).isEqualTo(0)
    }

    @Test
    fun `an unknown card id is skipped and counted`() = runTest {
        val result = merger.merge(snapshot(entries = listOf(entry("missing", 1, 100L))))

        assertThat(result.entriesSkipped).isEqualTo(1)
        assertThat(db.collectionEntryDao().getAll()).isEmpty()
    }

    @Test
    fun `foil and condition variants are separate stacks`() = runTest {
        seedCards("c1")
        val result = merger.merge(
            snapshot(
                entries = listOf(
                    entry("c1", 1, 100L, condition = "Near Mint", isFoil = false),
                    entry("c1", 2, 100L, condition = "Near Mint", isFoil = true),
                    entry("c1", 3, 100L, condition = "Mint", isFoil = false),
                ),
            ),
        )

        assertThat(result.entriesAdded).isEqualTo(3)
        assertThat(db.collectionEntryDao().getAll()).hasSize(3)
    }

    @Test
    fun `two rows with the same key in one file merge instead of colliding`() = runTest {
        // A hand-edited or concatenated file can contain the same key twice. Without
        // the mutating index this would violate the unique constraint and abort the
        // whole restore.
        seedCards("c1")
        val result = merger.merge(
            snapshot(entries = listOf(entry("c1", 1, 100L), entry("c1", 5, 200L))),
        )

        assertThat(db.collectionEntryDao().getAll()).hasSize(1)
        assertThat(db.collectionEntryDao().getAll().single().quantity).isEqualTo(5)
        assertThat(result.entriesAdded).isEqualTo(1)
        assertThat(result.entriesUpdated).isEqualTo(1)
    }

    @Test
    fun `restoring never removes an entry the snapshot does not mention`() = runTest {
        seedCards("c1", "c2")
        db.collectionEntryDao().insert(CollectionEntryEntity(cardId = "c2", quantity = 4, dateAdded = 100L))

        merger.merge(snapshot(entries = listOf(entry("c1", 1, 100L))))

        assertThat(db.collectionEntryDao().getAll()).hasSize(2)
    }

    // ── Decks ────────────────────────────────────────────────────────────────

    @Test
    fun `a newer deck fully replaces its entries rather than merging them`() = runTest {
        // A deck is a designed whole; a half-merged one would be worse than either
        // version, so the newer side wins outright.
        seedCards("c1", "c2")
        db.deckDao().upsertDeck(DeckEntity(id = "d1", name = "Old", createdDate = 1L, lastModifiedDate = 100L))
        db.deckDao().upsertEntry(DeckEntryEntity(deckId = "d1", cardId = "c1", quantity = 3))

        merger.merge(
            snapshot(
                decks = listOf(
                    CollectionSnapshot.DeckExport(
                        id = "d1", name = "New", createdDate = 1L, lastModifiedDate = 200L,
                        entries = listOf(CollectionSnapshot.DeckEntryExport("c2", 2, "mainDeck")),
                    ),
                ),
            ),
        )

        assertThat(db.deckDao().getById("d1")!!.name).isEqualTo("New")
        val entries = db.deckDao().getAllEntries()
        assertThat(entries).hasSize(1)
        assertThat(entries.single().cardId).isEqualTo("c2")
    }

    @Test
    fun `an older deck is left completely alone`() = runTest {
        seedCards("c1", "c2")
        db.deckDao().upsertDeck(DeckEntity(id = "d1", name = "Current", createdDate = 1L, lastModifiedDate = 500L))
        db.deckDao().upsertEntry(DeckEntryEntity(deckId = "d1", cardId = "c1", quantity = 3))

        val result = merger.merge(
            snapshot(
                decks = listOf(
                    CollectionSnapshot.DeckExport(
                        id = "d1", name = "Stale", createdDate = 1L, lastModifiedDate = 100L,
                        entries = listOf(CollectionSnapshot.DeckEntryExport("c2", 9, "mainDeck")),
                    ),
                ),
            ),
        )

        assertThat(db.deckDao().getById("d1")!!.name).isEqualTo("Current")
        assertThat(db.deckDao().getAllEntries().single().cardId).isEqualTo("c1")
        assertThat(result.decksUpdated).isEqualTo(0)
    }

    @Test
    fun `a deck referencing a card we do not have still restores, minus that card`() = runTest {
        seedCards("c1")
        merger.merge(
            snapshot(
                decks = listOf(
                    CollectionSnapshot.DeckExport(
                        id = "d1", name = "Partial", createdDate = 1L, lastModifiedDate = 1L,
                        legendCardId = "unknown-legend",
                        entries = listOf(
                            CollectionSnapshot.DeckEntryExport("c1", 1, "mainDeck"),
                            CollectionSnapshot.DeckEntryExport("unknown", 1, "mainDeck"),
                        ),
                    ),
                ),
            ),
        )

        val deck = db.deckDao().getById("d1")
        assertThat(deck).isNotNull()
        assertThat(deck!!.legendCardId).isNull()
        assertThat(db.deckDao().getAllEntries()).hasSize(1)
    }

    // ── Lists ────────────────────────────────────────────────────────────────

    @Test
    fun `lists are union only and never lose a card`() = runTest {
        seedCards("c1", "c2", "c3")
        db.cardListDao().upsert(CardListEntity(id = "l1", name = "Mine", colorHex = "#FF0000", createdDate = 1L))
        db.cardListDao().addCardToList(CardListCrossRef("l1", "c1"))

        val result = merger.merge(
            snapshot(
                lists = listOf(
                    CollectionSnapshot.ListExport(
                        id = "l1", name = "Renamed", colorHex = "#00FF00", isSystem = false,
                        createdDate = 1L, cardIds = listOf("c2", "c3"),
                    ),
                ),
            ),
        )

        // c1 survives even though the snapshot omits it, and the name/colour are not
        // overwritten — the local list stays the user's.
        assertThat(db.cardListDao().cardIdsForList("l1")).containsExactly("c1", "c2", "c3")
        assertThat(db.cardListDao().getById("l1")!!.name).isEqualTo("Mine")
        assertThat(result.listsUpdated).isEqualTo(1)
    }

    @Test
    fun `a list that adds nothing new is not counted as updated`() = runTest {
        seedCards("c1")
        db.cardListDao().upsert(CardListEntity(id = "l1", name = "Mine", createdDate = 1L))
        db.cardListDao().addCardToList(CardListCrossRef("l1", "c1"))

        val result = merger.merge(
            snapshot(
                lists = listOf(
                    CollectionSnapshot.ListExport(
                        id = "l1", name = "Mine", colorHex = "#FF9500", isSystem = false,
                        createdDate = 1L, cardIds = listOf("c1"),
                    ),
                ),
            ),
        )

        assertThat(result.listsUpdated).isEqualTo(0)
    }

    // ── Game records ─────────────────────────────────────────────────────────

    @Test
    fun `game records are insert-if-absent and never overwritten`() = runTest {
        // A record has no mutation timestamp, so last-write-wins is not expressible;
        // they are treated as immutable.
        db.gameRecordDao().upsert(GameRecordEntity(id = "g1", date = 100L, result = "win", notes = "mine"))

        val result = merger.merge(
            snapshot(
                gameRecords = listOf(
                    CollectionSnapshot.GameRecordExport(id = "g1", date = 100L, result = "loss", notes = "theirs"),
                    CollectionSnapshot.GameRecordExport(id = "g2", date = 200L, result = "draw"),
                ),
            ),
        )

        assertThat(result.gameRecordsAdded).isEqualTo(1)
        assertThat(db.gameRecordDao().getById("g1")!!.notes).isEqualTo("mine")
        assertThat(db.gameRecordDao().getById("g2")).isNotNull()
    }

    @Test
    fun `a game record whose deck is missing still lands in global history`() = runTest {
        merger.merge(
            snapshot(
                gameRecords = listOf(
                    CollectionSnapshot.GameRecordExport(
                        id = "g1", date = 100L, result = "win",
                        deckId = "deck-we-do-not-have", legendId = "legend-1", opponentLegendId = "legend-2",
                    ),
                ),
            ),
        )

        val record = db.gameRecordDao().getById("g1")
        assertThat(record).isNotNull()
        assertThat(record!!.deckId).isNull()
        // The matchup is the point of the record, and survives the missing deck.
        assertThat(record.legendId).isEqualTo("legend-1")
        assertThat(record.opponentLegendId).isEqualTo("legend-2")
    }

    @Test
    fun `an empty snapshot changes nothing and reports nothing`() = runTest {
        seedCards("c1")
        db.collectionEntryDao().insert(CollectionEntryEntity(cardId = "c1", quantity = 2, dateAdded = 1L))

        val result = merger.merge(snapshot())

        assertThat(result.summary).isEqualTo("Nothing to restore")
        assertThat(db.collectionEntryDao().getAll()).hasSize(1)
    }
}
