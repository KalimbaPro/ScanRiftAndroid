package com.scanrift.android.service.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.local.entity.DeckEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity
import com.scanrift.android.data.remote.dto.RiftboundSetDto
import com.scanrift.android.fake.AkaliFixture
import com.scanrift.android.fake.FakeRiftboundDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Database-level sync behaviour, ported from iOS `CardDatabaseDeduplicationTests`.
 *
 * The API returns several records for the same physical card during a set's rollout —
 * a preliminary stub and a finalised record sharing a `riftbound_id` — and sync has to
 * collapse them without ever discarding a row the user's data points at.
 */
@RunWith(RobolectricTestRunner::class)
class CardDatabaseServiceTest {

    private lateinit var db: ScanRiftDatabase
    private lateinit var source: FakeRiftboundDataSource
    private lateinit var service: CardDatabaseService

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ScanRiftDatabase::class.java,
        ).allowMainThreadQueries().build()
        source = FakeRiftboundDataSource()
        service = CardDatabaseService(
            db = db,
            cardDao = db.cardDao(),
            maintenanceDao = db.maintenanceDao(),
            syncedSetDao = db.syncedSetDao(),
            source = source,
            io = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() = db.close()

    private fun venSet(cardCount: Int) =
        RiftboundSetDto(id = "s-ven", name = "Vendetta", setId = "VEN", cardCount = cardCount)

    /** Stores all four raw records, simulating a sync that predates de-duplication. */
    private suspend fun seedRawDuplicates() {
        db.cardDao().upsertAll(AkaliFixture.decode().map { it.toEntity() })
    }

    @Test
    fun `a full fetch collapses the four records to two rows`() = runTest {
        source.cards = AkaliFixture.decode()

        service.fetchAndStoreCards()

        val all = db.cardDao().getAll()
        assertThat(all).hasSize(2)
        assertThat(all.map { it.riftboundId })
            .containsExactly("ven-021-166", "ven-021a-166")
    }

    @Test
    fun `the finalised record of each printing is the one kept`() = runTest {
        source.cards = AkaliFixture.decode()

        service.fetchAndStoreCards()

        val byRiftboundId = db.cardDao().getAll().associateBy { it.riftboundId }
        assertThat(byRiftboundId.getValue("ven-021-166").cleanName).isEqualTo("Akali Deadly Weapon")
        assertThat(byRiftboundId.getValue("ven-021a-166").alternateArt).isTrue()
    }

    @Test
    fun `delta sync cleans up pre-existing duplicates without refetching the set`() = runTest {
        seedRawDuplicates()
        // card_count matches what a de-duplicated catalogue would hold, so the refetch
        // check does not fire — only the unconditional cleanup pass should run.
        source.sets = listOf(venSet(cardCount = 2))

        val report = service.syncDelta()

        assertThat(report.setsRefetched).isEqualTo(0)
        assertThat(source.fetchedSetIds).isEmpty()
        assertThat(db.cardDao().getAll().map { it.riftboundId })
            .containsExactly("ven-021-166", "ven-021a-166")
    }

    @Test
    fun `de-duplication never deletes a row the collection references`() = runTest {
        seedRawDuplicates()
        // Mark the older, less complete stub as owned.
        db.collectionEntryDao().insert(CollectionEntryEntity(cardId = "stub-021", quantity = 1, dateAdded = 1L))
        source.sets = listOf(venSet(cardCount = 2))

        service.syncDelta()

        val all = db.cardDao().getAll()
        assertThat(all).hasSize(2)
        val kept = all.single { it.riftboundId == "ven-021-166" }
        assertThat(kept.id).isEqualTo("stub-021")
        // And the collection entry still points at a real card.
        assertThat(db.collectionEntryDao().getAll().single().cardId).isEqualTo("stub-021")
    }

    @Test
    fun `de-duplication repoints deck entries onto the surviving row`() = runTest {
        seedRawDuplicates()
        db.deckDao().upsertDeck(DeckEntity(id = "deck-1", createdDate = 1L, lastModifiedDate = 1L))
        db.deckDao().upsertEntry(DeckEntryEntity(deckId = "deck-1", cardId = "final-021", quantity = 3))
        // The stub is unreferenced, so the finalised row wins and nothing needs moving;
        // reference the stub instead to force a repoint.
        db.deckDao().upsertEntry(DeckEntryEntity(deckId = "deck-1", cardId = "stub-021a", quantity = 2))
        source.sets = listOf(venSet(cardCount = 2))

        service.syncDelta()

        val entries = db.deckDao().getAllEntries()
        val survivingIds = db.cardDao().getAll().map { it.id }.toSet()
        assertThat(entries).hasSize(2)
        entries.forEach { assertThat(it.cardId).isIn(survivingIds) }
        // No quantity was lost in the move.
        assertThat(entries.sumOf { it.quantity }).isEqualTo(5)
    }

    @Test
    fun `repointing merges colliding collection stacks instead of violating the index`() = runTest {
        seedRawDuplicates()
        // Two rows with the same (isFoil, condition) pointing at duplicates of one card.
        db.collectionEntryDao().insert(CollectionEntryEntity(cardId = "final-021", quantity = 2, dateAdded = 1L))
        db.collectionEntryDao().insert(CollectionEntryEntity(cardId = "stub-021", quantity = 3, dateAdded = 1L))
        source.sets = listOf(venSet(cardCount = 2))

        service.syncDelta()

        // They must merge into one stack of 5 — iOS would have left two rows, which the
        // unique index makes impossible here.
        val entries = db.collectionEntryDao().getAll()
        assertThat(entries).hasSize(1)
        assertThat(entries.single().quantity).isEqualTo(5)
    }

    @Test
    fun `a set is refetched only when the api reports more cards than we hold`() = runTest {
        source.cards = AkaliFixture.decode()
        source.sets = listOf(venSet(cardCount = 2))

        // Empty catalogue: 2 > 0, so the set is fetched.
        val first = service.syncDelta()
        assertThat(first.setsRefetched).isEqualTo(1)
        assertThat(first.added).isEqualTo(2)

        // Now we hold 2 of 2, so a second pass fetches nothing.
        source.fetchedSetIds.clear()
        val second = service.syncDelta()
        assertThat(second.setsRefetched).isEqualTo(0)
        assertThat(source.fetchedSetIds).isEmpty()
    }

    @Test
    fun `promo sets do not refetch forever`() = runTest {
        // The live iOS bug: local counts are keyed on the normalised set id ("PROMO")
        // but compared against the raw id from /sets ("OPP"), so the count reads zero
        // and every promo set refetches on every sync. sourceSetId fixes it.
        val promo = AkaliFixture.decode().first { it.id == "final-021" }.let {
            it.copy(id = "promo-1", riftboundId = "opp-001-100", set = it.set.copy(setId = "OPP", label = "Promos"))
        }
        source.cards = listOf(promo)
        source.sets = listOf(RiftboundSetDto(id = "s-opp", name = "Promos", setId = "OPP", cardCount = 1))

        service.syncDelta()
        assertThat(db.cardDao().getAll().single().setId).isEqualTo("PROMO")
        assertThat(db.cardDao().getAll().single().sourceSetId).isEqualTo("OPP")

        source.fetchedSetIds.clear()
        val second = service.syncDelta()
        assertThat(second.setsRefetched).isEqualTo(0)
    }

    @Test
    fun `a set is not refetched forever just because dedup shrank it`() = runTest {
        // The bug this guards, which iOS still has: card_count counts the raw records
        // the API serves, while the local count is what survives de-duplication.
        // Vendetta serves 358 records that collapse to 227 real cards, so a
        // `cardCount > localCount` test is true forever and refetches the whole set on
        // every sync. Here the 4 Akali records collapse to 2 against a count of 4.
        source.cards = AkaliFixture.decode()
        source.sets = listOf(venSet(cardCount = 4))

        val first = service.syncDelta()
        assertThat(first.setsRefetched).isEqualTo(1)
        assertThat(db.cardDao().count()).isEqualTo(2)

        // 4 > 2 still holds, but the API's number has not changed, so nothing refetches.
        source.fetchedSetIds.clear()
        val second = service.syncDelta()
        assertThat(second.setsRefetched).isEqualTo(0)
        assertThat(source.fetchedSetIds).isEmpty()
    }

    @Test
    fun `a set is refetched when the api count changes in either direction`() = runTest {
        source.cards = AkaliFixture.decode()
        source.sets = listOf(venSet(cardCount = 4))
        service.syncDelta()

        // Growth.
        source.fetchedSetIds.clear()
        source.sets = listOf(venSet(cardCount = 5))
        assertThat(service.syncDelta().setsRefetched).isEqualTo(1)

        // And shrinkage, which a `>` test could never have caught.
        source.fetchedSetIds.clear()
        source.sets = listOf(venSet(cardCount = 3))
        assertThat(service.syncDelta().setsRefetched).isEqualTo(1)
    }

    @Test
    fun `an unchanged updated_on skips the write`() = runTest {
        source.cards = AkaliFixture.decode()
        source.sets = listOf(venSet(cardCount = 2))
        service.syncDelta()

        // Force a refetch by changing the API's count, but return the same data.
        source.sets = listOf(venSet(cardCount = 99))
        val report = service.syncDelta()

        assertThat(report.setsRefetched).isEqualTo(1)
        assertThat(report.unchanged).isEqualTo(2)
        assertThat(report.updated).isEqualTo(0)
        assertThat(report.added).isEqualTo(0)
        assertThat(report.hasChanges).isFalse()
    }

    @Test
    fun `a changed updated_on rewrites the row`() = runTest {
        source.cards = AkaliFixture.decode()
        source.sets = listOf(venSet(cardCount = 2))
        service.syncDelta()

        source.cards = AkaliFixture.decode().map {
            it.copy(
                name = "${it.name} v2",
                metadata = it.metadata.copy(updatedOn = "2027-01-01T00:00:00+00:00"),
            )
        }
        source.sets = listOf(venSet(cardCount = 99))
        val report = service.syncDelta()

        assertThat(report.updated).isEqualTo(2)
        assertThat(report.hasChanges).isTrue()
        assertThat(db.cardDao().getAll().map { it.name }).containsExactly(
            "Akali, Deadly Weapon v2", "Akali, Deadly Weapon (Alternate Art) v2",
        )
    }

    @Test
    fun `public code is derived when the api omits it`() = runTest {
        // Every card the live API returns lacks public_code, and the scanner matches on
        // it — so a broken derivation means no card ever scans.
        source.cards = AkaliFixture.decode()
        service.fetchAndStoreCards()

        val codes = db.cardDao().getAll().map { it.publicCode }
        assertThat(codes).containsExactly("VEN-021/166", "VEN-021a/166")
    }
}
