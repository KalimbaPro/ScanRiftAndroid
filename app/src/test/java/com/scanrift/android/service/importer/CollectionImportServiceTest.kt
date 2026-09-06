package com.scanrift.android.service.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.data.local.mapper.toDomain
import com.scanrift.android.service.export.CollectionExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionImportServiceTest {

    private lateinit var db: ScanRiftDatabase
    private lateinit var service: CollectionImportService

    private val now = 1_780_000_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ScanRiftDatabase::class.java,
        ).allowMainThreadQueries().build()
        service = CollectionImportService(db, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    private fun card(
        id: String,
        name: String,
        setId: String = "VEN",
        setLabel: String = "Vendetta",
        collectorNumber: Int = 21,
        alternateArt: Boolean = false,
    ) = CardEntity(
        id = id, name = name, riftboundId = "ven-$collectorNumber-166",
        publicCode = "VEN-021/166", collectorNumber = collectorNumber,
        energy = null, might = null, power = null,
        type = "Unit", supertype = null, rarity = "Epic", domains = emptyList(),
        richText = null, plainText = null,
        setId = setId, setLabel = setLabel, sourceSetId = setId,
        imageUrl = null, artist = null, accessibilityText = null,
        cleanName = name, alternateArt = alternateArt, updatedOn = 1L,
    )

    // ── Format detection ─────────────────────────────────────────────────────

    @Test
    fun `format is sniffed from content not just the extension`() {
        // SAF providers routinely report octet-stream and a .txt name.
        assertThat(service.detectFormat("""[{"name":"x"}]""", "txt")).isEqualTo(ImportFormat.JSON)
        assertThat(service.detectFormat("""{"collection":[]}""", null)).isEqualTo(ImportFormat.JSON)
        assertThat(service.detectFormat("CardId, Normal, Foil, Name, Set\n", "txt"))
            .isEqualTo(ImportFormat.RIFTBOUND_GG)
        assertThat(service.detectFormat("Name,Set,Rarity,Quantity,Condition,Date Added\n", "csv"))
            .isEqualTo(ImportFormat.CSV)
    }

    @Test
    fun `a json extension wins even when the content looks like csv`() {
        assertThat(service.detectFormat("[]", "json")).isEqualTo(ImportFormat.JSON)
    }

    // ── riftbound.gg ─────────────────────────────────────────────────────────

    @Test
    fun `riftbound gg import creates separate normal and foil stacks`() = runTest {
        db.cardDao().upsert(card("c1", "Akali"))

        val result = service.import(
            "CardId, Normal, Foil, Name, Set\nVEN-021, 3, 2, \"Akali\", \"Vendetta\"\n",
            "csv", now,
        )

        assertThat(result.created).isEqualTo(2)
        val entries = db.collectionEntryDao().getAll()
        assertThat(entries.single { !it.isFoil }.quantity).isEqualTo(3)
        assertThat(entries.single { it.isFoil }.quantity).isEqualTo(2)
    }

    @Test
    fun `riftbound gg import skips a zero quantity rather than creating an empty stack`() = runTest {
        db.cardDao().upsert(card("c1", "Akali"))

        service.import("CardId, Normal, Foil, Name, Set\nVEN-021, 4, 0, \"Akali\", \"Vendetta\"\n", "csv", now)

        assertThat(db.collectionEntryDao().getAll()).hasSize(1)
    }

    @Test
    fun `riftbound gg lookup prefers the base printing over alternate art`() = runTest {
        // SET-NNN is ambiguous now that promos share a set id and alt arts reuse
        // numbers; iOS picks non-deterministically, we pick the base printing.
        db.cardDao().upsertAll(
            listOf(
                card("alt", "Akali (Alt)", alternateArt = true),
                card("base", "Akali", alternateArt = false),
            ),
        )

        service.import("CardId, Normal, Foil, Name, Set\nVEN-021, 1, 0, \"Akali\", \"Vendetta\"\n", "csv", now)

        assertThat(db.collectionEntryDao().getAll().single().cardId).isEqualTo("base")
    }

    @Test
    fun `an unknown card is skipped and named in the result`() = runTest {
        val result = service.import(
            "CardId, Normal, Foil, Name, Set\nVEN-999, 1, 0, \"Nobody\", \"Vendetta\"\n", "csv", now,
        )

        assertThat(result.skipped).isEqualTo(1)
        assertThat(result.skippedNames).containsExactly("Nobody")
        assertThat(db.collectionEntryDao().getAll()).isEmpty()
    }

    // ── Generic CSV ──────────────────────────────────────────────────────────

    @Test
    fun `csv import reads quantity and condition`() = runTest {
        db.cardDao().upsert(card("c1", "Akali"))

        service.import(
            "Name,Set,Rarity,Quantity,Condition,Date Added\n" +
                "\"Akali\",\"Vendetta\",\"Epic\",5,\"Lightly Played\",\"2026-01-01T00:00:00Z\"\n",
            "csv", now,
        )

        val entry = db.collectionEntryDao().getAll().single()
        assertThat(entry.quantity).isEqualTo(5)
        assertThat(entry.condition).isEqualTo("Lightly Played")
    }

    @Test
    fun `csv import prefers the card from the named set`() = runTest {
        db.cardDao().upsertAll(
            listOf(
                card("ogn", "Akali", setId = "OGN", setLabel = "Origins"),
                card("ven", "Akali", setId = "VEN", setLabel = "Vendetta"),
            ),
        )

        service.import(
            "Name,Set,Rarity,Quantity,Condition,Date Added\n\"Akali\",\"Vendetta\",\"Epic\",1,\"Near Mint\",\"\"\n",
            "csv", now,
        )

        assertThat(db.collectionEntryDao().getAll().single().cardId).isEqualTo("ven")
    }

    @Test
    fun `an unrecognised condition falls back to near mint`() = runTest {
        db.cardDao().upsert(card("c1", "Akali"))

        service.import(
            "Name,Set,Rarity,Quantity,Condition,Date Added\n\"Akali\",\"Vendetta\",\"Epic\",1,\"Pristine\",\"\"\n",
            "csv", now,
        )

        assertThat(db.collectionEntryDao().getAll().single().condition).isEqualTo("Near Mint")
    }

    // ── JSON ─────────────────────────────────────────────────────────────────

    @Test
    fun `json import accepts the legacy bare array`() = runTest {
        db.cardDao().upsert(card("c1", "Akali"))

        val result = service.import(
            """[{"name":"Akali","id":"c1","quantity":2,"condition":"Near Mint"}]""", "json", now,
        )

        assertThat(result.created).isEqualTo(1)
        assertThat(db.collectionEntryDao().getAll().single().quantity).isEqualTo(2)
    }

    @Test
    fun `json import accepts a full export object and ignores its decks`() = runTest {
        // Decks belong to the backup/restore path, which has the merge rules for them.
        db.cardDao().upsert(card("c1", "Akali"))

        service.import(
            """{"exported_at":"2026-01-01T00:00:00Z",
                "collection":[{"name":"Akali","id":"c1","quantity":3,"is_foil":true}],
                "decks":[{"id":"d1","name":"Ignored"}]}""",
            "json", now,
        )

        assertThat(db.collectionEntryDao().getAll().single().isFoil).isTrue()
        assertThat(db.deckDao().getAll()).isEmpty()
    }

    @Test
    fun `json import falls back to name when the id is unknown`() = runTest {
        db.cardDao().upsert(card("real-id", "Akali"))

        service.import("""[{"name":"Akali","id":"stale-id","quantity":1}]""", "json", now)

        assertThat(db.collectionEntryDao().getAll().single().cardId).isEqualTo("real-id")
    }

    // ── Additive semantics and the duplicate-row bugfix ──────────────────────

    @Test
    fun `importing adds to an existing stack rather than replacing it`() = runTest {
        db.cardDao().upsert(card("c1", "Akali"))
        db.collectionEntryDao().insert(CollectionEntryEntity(cardId = "c1", quantity = 4, dateAdded = 1L))

        val result = service.import("""[{"name":"Akali","id":"c1","quantity":3}]""", "json", now)

        assertThat(result.updated).isEqualTo(1)
        assertThat(db.collectionEntryDao().getAll().single().quantity).isEqualTo(7)
    }

    @Test
    fun `two rows for the same card in one file merge into one stack`() = runTest {
        // The bug iOS documents: using a pre-import snapshot of the index means each
        // row misses the other and you get duplicates.
        db.cardDao().upsert(card("c1", "Akali"))

        val result = service.import(
            """[{"name":"Akali","id":"c1","quantity":2},{"name":"Akali","id":"c1","quantity":3}]""",
            "json", now,
        )

        val entries = db.collectionEntryDao().getAll()
        assertThat(entries).hasSize(1)
        assertThat(entries.single().quantity).isEqualTo(5)
        assertThat(result.created).isEqualTo(1)
    }

    @Test
    fun `a round trip through the exporter reproduces the same quantities`() = runTest {
        db.cardDao().upsert(card("c1", "Akali"))
        db.collectionEntryDao().insert(
            CollectionEntryEntity(cardId = "c1", quantity = 3, isFoil = false, dateAdded = now),
        )
        db.collectionEntryDao().insert(
            CollectionEntryEntity(cardId = "c1", quantity = 1, isFoil = true, dateAdded = now),
        )

        val entries = db.collectionEntryDao().getAllWithCards().map { it.toDomain() }
        val csv = CollectionExporter.exportAsRiftboundGg(entries)

        db.collectionEntryDao().deleteAll()
        service.import(csv, "csv", now)

        val restored = db.collectionEntryDao().getAll()
        assertThat(restored.single { !it.isFoil }.quantity).isEqualTo(3)
        assertThat(restored.single { it.isFoil }.quantity).isEqualTo(1)
    }
}
