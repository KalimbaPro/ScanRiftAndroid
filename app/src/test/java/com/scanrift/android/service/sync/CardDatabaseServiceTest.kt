package com.scanrift.android.service.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.local.entity.DeckEntity
import com.scanrift.android.data.local.entity.DeckEntryEntity
import com.scanrift.android.data.remote.datasource.RiftboundDataSource
import com.scanrift.android.data.remote.dto.LegalityItemDto
import com.scanrift.android.data.remote.dto.LegalityResponseDto
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
    fun `a refresh cleans up pre-existing duplicates the api no longer serves`() = runTest {
        seedRawDuplicates()
        // The API serves nothing: only the de-duplication pass can touch the rows.
        source.sets = listOf(venSet(cardCount = 2))

        service.refreshCatalogue("global")

        assertThat(db.cardDao().getAll().map { it.riftboundId })
            .containsExactly("ven-021-166", "ven-021a-166")
    }

    @Test
    fun `de-duplication never deletes a row the collection references`() = runTest {
        seedRawDuplicates()
        // Mark the older, less complete stub as owned.
        db.collectionEntryDao().insert(CollectionEntryEntity(cardId = "stub-021", quantity = 1, dateAdded = 1L))
        source.sets = listOf(venSet(cardCount = 2))

        service.refreshCatalogue("global")

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

        service.refreshCatalogue("global")

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

        service.refreshCatalogue("global")

        // They must merge into one stack of 5 — iOS would have left two rows, which the
        // unique index makes impossible here.
        val entries = db.collectionEntryDao().getAll()
        assertThat(entries).hasSize(1)
        assertThat(entries.single().quantity).isEqualTo(5)
    }

    @Test
    fun `a refresh fetches every card and the sets, not set by set`() = runTest {
        source.cards = AkaliFixture.decode()
        source.sets = listOf(venSet(cardCount = 4))

        val count = service.refreshCatalogue("global")

        assertThat(count).isEqualTo(2)
        assertThat(source.fetchAllCalls).isEqualTo(1)
        assertThat(source.fetchedSetIds).isEmpty()
        assertThat(db.cardDao().count()).isEqualTo(2)
    }

    @Test
    fun `a refresh rewrites every card even when updated_on is unchanged`() = runTest {
        // A tag change can come from a legality or localisation edit that leaves
        // updated_on alone, so nothing may be skipped on it.
        source.cards = AkaliFixture.decode()
        service.refreshCatalogue("global")

        source.cards = AkaliFixture.decode().map { it.copy(name = "${it.name} v2") }
        service.refreshCatalogue("global")

        assertThat(db.cardDao().getAll().map { it.name }).containsExactly(
            "Akali, Deadly Weapon v2", "Akali, Deadly Weapon (Alternate Art) v2",
        )
    }

    @Test
    fun `a refresh never deletes a card the api stopped serving`() = runTest {
        source.cards = AkaliFixture.decode()
        service.refreshCatalogue("global")
        db.collectionEntryDao().insert(CollectionEntryEntity(cardId = "final-021a", quantity = 1, dateAdded = 1L))

        source.cards = AkaliFixture.decode().filter { it.riftboundId == "ven-021-166" }
        service.refreshCatalogue("global")

        assertThat(db.cardDao().getAll().map { it.riftboundId })
            .containsExactly("ven-021-166", "ven-021a-166")
        assertThat(db.collectionEntryDao().getAll().single().cardId).isEqualTo("final-021a")
    }

    @Test
    fun `a refresh applies the region's legality after the cards`() = runTest {
        source.cards = AkaliFixture.decode()
        source.legality["kr"] = LegalityResponseDto(
            region = "kr",
            items = listOf(LegalityItemDto(id = "final-021", banned = true, bannedFormats = listOf("standard"))),
        )

        service.refreshCatalogue("kr")

        assertThat(source.legalityCalls).isEqualTo(1)
        assertThat(db.cardDao().getById("final-021")!!.banned).isTrue()
        assertThat(db.cardDao().getById("final-021a")!!.banned).isFalse()
    }

    @Test
    fun `a failing step fails the whole refresh`() = runTest {
        val failing = object : RiftboundDataSource by source {
            override suspend fun fetchLegality(region: String): LegalityResponseDto? = error("HTTP 503")
        }
        source.cards = AkaliFixture.decode()
        val failingService = CardDatabaseService(db, db.cardDao(), db.maintenanceDao(), failing, Dispatchers.Unconfined)

        val result = runCatching { failingService.refreshCatalogue("global") }

        assertThat(result.exceptionOrNull()).hasMessageThat().isEqualTo("HTTP 503")
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
