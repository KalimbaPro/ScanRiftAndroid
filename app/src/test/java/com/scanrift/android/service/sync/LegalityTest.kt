package com.scanrift.android.service.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.entity.CardIdentity
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.remote.dto.LegalityItemDto
import com.scanrift.android.data.remote.dto.LegalityResponseDto
import com.scanrift.android.fake.AkaliFixture
import com.scanrift.android.fake.FakeRiftboundDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Pure mapping of a `/legality` payload onto local ids. */
class LegalityResolverTest {

    private val cards = listOf(
        CardIdentity("a", "ogn-001-298", "OGN", null, "A"),
        CardIdentity("b", "ogn-002-298", "OGN", null, "B"),
        CardIdentity("b-stub", "ogn-002-298", "OGN", null, "B"),
    )

    @Test
    fun `an item matches by id`() {
        val result = LegalityResolver.resolve(
            listOf(LegalityItemDto(id = "a", banned = true, bannedFormats = listOf("standard"))),
            cards,
        )
        assertThat(result.keys).containsExactly("a")
        assertThat(result.getValue("a").banned).isTrue()
        assertThat(result.getValue("a").bannedFormats).containsExactly("standard")
    }

    @Test
    fun `an unknown id falls back to every row with that riftbound id`() {
        val result = LegalityResolver.resolve(
            listOf(LegalityItemDto(id = "api-only", riftboundId = "ogn-002-298", errata = "New", errataDate = "2026-01-01")),
            cards,
        )
        assertThat(result.keys).containsExactly("b", "b-stub")
        assertThat(result.getValue("b").errata).isEqualTo("New")
        assertThat(result.getValue("b").banned).isFalse()
    }

    @Test
    fun `items for cards we do not hold are dropped`() {
        val result = LegalityResolver.resolve(listOf(LegalityItemDto(id = "zzz", riftboundId = "rad-999-167", banned = true)), cards)
        assertThat(result).isEmpty()
    }

    @Test
    fun `blank errata is treated as none`() {
        val result = LegalityResolver.resolve(listOf(LegalityItemDto(id = "a", errata = " ")), cards)
        assertThat(result.getValue("a").errata).isNull()
    }
}

/** Applying legality to the catalogue: an UPDATE over every card, never a delete. */
@RunWith(RobolectricTestRunner::class)
class LegalityApplicationTest {

    private lateinit var db: ScanRiftDatabase
    private lateinit var source: FakeLegalitySource
    private lateinit var service: CardDatabaseService

    private class FakeLegalitySource : com.scanrift.android.data.remote.datasource.RiftboundDataSource by FakeRiftboundDataSource() {
        var responses = mutableMapOf<String, LegalityResponseDto>()
        override suspend fun fetchLegality(region: String): LegalityResponseDto? = responses[region]
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ScanRiftDatabase::class.java)
            .allowMainThreadQueries().build()
        source = FakeLegalitySource()
        service = CardDatabaseService(db, db.cardDao(), db.maintenanceDao(), source, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a region's list bans what it lists and clears everything else`() = runTest {
        // final-021 arrives banned from the card endpoint (global default)...
        val cards = AkaliFixture.decode().take(2).map { it.toEntity() }
        db.cardDao().upsertAll(listOf(cards[0].copy(banned = true, errata = "old"), cards[1]))
        db.collectionEntryDao().insert(CollectionEntryEntity(cardId = "final-021", quantity = 2, dateAdded = 1L))

        // ...but Korea's list only has errata on the alternate art.
        source.responses["kr"] = LegalityResponseDto(
            region = "kr",
            items = listOf(LegalityItemDto(id = "final-021a", errata = "Errata text", errataDate = "2026-10-09")),
        )
        val applied = service.refreshLegality("kr")

        assertThat(applied).isEqualTo(1)
        val byId = db.cardDao().getAll().associateBy { it.id }
        assertThat(byId.getValue("final-021").banned).isFalse()
        assertThat(byId.getValue("final-021").errata).isNull()
        assertThat(byId.getValue("final-021a").errata).isEqualTo("Errata text")
        assertThat(byId.getValue("final-021a").errataDate).isEqualTo("2026-10-09")
        // Nothing was deleted and the owned copies are untouched.
        assertThat(byId).hasSize(2)
        assertThat(db.collectionEntryDao().getAll().single().cardId).isEqualTo("final-021")
    }

    @Test
    fun `switching region re-bans`() = runTest {
        db.cardDao().upsertAll(AkaliFixture.decode().take(1).map { it.toEntity() })
        source.responses["global"] = LegalityResponseDto(
            items = listOf(LegalityItemDto(id = "final-021", banned = true, bannedFormats = listOf("standard", "2v2"))),
        )
        source.responses["kr"] = LegalityResponseDto(region = "kr", items = emptyList())

        service.refreshLegality("global")
        assertThat(db.cardDao().getById("final-021")!!.banned).isTrue()
        assertThat(db.cardDao().getById("final-021")!!.bannedFormats).containsExactly("standard", "2v2")

        service.refreshLegality("kr")
        assertThat(db.cardDao().getById("final-021")!!.banned).isFalse()
        assertThat(db.cardDao().getById("final-021")!!.bannedFormats).isEmpty()
    }

    @Test
    fun `a source without a legality endpoint leaves the columns alone`() = runTest {
        db.cardDao().upsertAll(AkaliFixture.decode().take(1).map { it.toEntity().copy(banned = true) })
        assertThat(service.refreshLegality("tc")).isNull()
        assertThat(db.cardDao().getById("final-021")!!.banned).isTrue()
    }
}
