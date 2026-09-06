package com.scanrift.android.service.backup

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The cross-platform wire format.
 *
 * These tests exist because getting any of this subtly wrong produces a file that
 * looks fine and fails silently on the other platform.
 */
class SnapshotSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private fun sampleSnapshot(exportedAt: Long = 1_780_000_000_123L) = CollectionSnapshot(
        exportedAt = exportedAt,
        entries = listOf(
            CollectionSnapshot.Entry(
                cardId = "card-1", quantity = 3, isFoil = true,
                dateAdded = 1_770_000_000_456L, condition = "Near Mint",
            ),
        ),
        decks = listOf(
            CollectionSnapshot.DeckExport(
                id = "deck-1", name = "Akali", createdDate = 1L, lastModifiedDate = 1_770_000_000_789L,
                legendCardId = "legend-1",
                entries = listOf(CollectionSnapshot.DeckEntryExport("card-1", 2, "mainDeck")),
            ),
        ),
        lists = listOf(
            CollectionSnapshot.ListExport(
                id = "list-1", name = "Wishlist", colorHex = "#FFD60A",
                systemType = "wishlist", isSystem = true, createdDate = 1L, cardIds = listOf("card-1"),
            ),
        ),
        gameRecords = listOf(
            CollectionSnapshot.GameRecordExport(id = "game-1", date = 2L, result = "win"),
        ),
    )

    @Test
    fun `no timestamp anywhere carries fractional seconds`() {
        // The single most important assertion in the file. Swift decodes with .iso8601,
        // which is .withInternetDateTime and rejects fractional seconds — one such
        // timestamp makes iOS throw while decoding the entire snapshot, so the restore
        // fails with no visible cause.
        val encoded = json.encodeToString(sampleSnapshot())

        Regex("\"[^\"]*\\d{4}-\\d{2}-\\d{2}T[^\"]*\"").findAll(encoded).forEach { match ->
            assertThat(match.value).doesNotContain(".")
        }
    }

    @Test
    fun `timestamps are emitted as ISO-8601 UTC`() {
        val encoded = json.encodeToString(sampleSnapshot(exportedAt = 1_780_000_000_123L))
        // Whole seconds, UTC, Z suffix — the exact shape Swift's .iso8601 accepts.
        assertThat(encoded).contains("\"exportedAt\": \"2026-05-28T20:26:40Z\"")
        assertThat(encoded).contains("\"dateAdded\": \"2026-02-02T02:40:00Z\"")
    }

    @Test
    fun `absent optionals are omitted rather than written as null`() {
        // Matches Swift's synthesized Codable output.
        val encoded = json.encodeToString(sampleSnapshot())
        assertThat(encoded).doesNotContain("null")
    }

    @Test
    fun `the version stays at one`() {
        assertThat(sampleSnapshot().version).isEqualTo(1)
        assertThat(json.encodeToString(sampleSnapshot())).contains("\"version\": 1")
    }

    @Test
    fun `a round trip preserves every field at seconds precision`() {
        val original = sampleSnapshot()
        val decoded: CollectionSnapshot = json.decodeFromString(json.encodeToString(original))

        assertThat(decoded.entries.single().cardId).isEqualTo("card-1")
        assertThat(decoded.entries.single().quantity).isEqualTo(3)
        assertThat(decoded.entries.single().isFoil).isTrue()
        assertThat(decoded.entries.single().condition).isEqualTo("Near Mint")
        assertThat(decoded.decks.single().entries.single().section).isEqualTo("mainDeck")
        assertThat(decoded.lists.single().systemType).isEqualTo("wishlist")
        assertThat(decoded.gameRecords.single().result).isEqualTo("win")
        // Sub-second precision is intentionally lost.
        assertThat(decoded.exportedAt).isEqualTo(1_780_000_000_000L)
    }

    @Test
    fun `an iOS-shaped file without gameRecords decodes`() {
        // iOS's snapshot has no game history at all; ours must still load it.
        val iosFile = """
        {
          "version": 1,
          "exportedAt": "2026-06-05T12:34:56Z",
          "entries": [
            {"cardId": "card-1", "quantity": 2, "isFoil": false,
             "dateAdded": "2026-06-01T10:00:00Z", "condition": "Near Mint"}
          ],
          "decks": [],
          "lists": []
        }
        """.trimIndent()

        val decoded: CollectionSnapshot = json.decodeFromString(iosFile)

        assertThat(decoded.entries).hasSize(1)
        assertThat(decoded.gameRecords).isEmpty()
    }

    @Test
    fun `an iOS file with fractional seconds still decodes here`() {
        // Reads stay lenient in both directions even though writes never emit them.
        val file = """
        {"version":1,"exportedAt":"2026-06-05T12:34:56.789Z","entries":[],"decks":[],"lists":[]}
        """.trimIndent()

        assertThat(json.decodeFromString<CollectionSnapshot>(file).exportedAt).isGreaterThan(0L)
    }

    @Test
    fun `an unknown future key does not break decoding`() {
        // The additive-only versioning rule depends on this being true both ways.
        val file = """
        {"version":1,"exportedAt":"2026-06-05T12:34:56Z","entries":[],"decks":[],"lists":[],
         "somethingAddedLater":[{"a":1}]}
        """.trimIndent()

        assertThat(json.decodeFromString<CollectionSnapshot>(file).entries).isEmpty()
    }

    @Test
    fun `restore summary reads like the iOS one`() {
        assertThat(RestoreResult().summary).isEqualTo("Nothing to restore")
        assertThat(
            RestoreResult(entriesAdded = 3, entriesUpdated = 1, decksAdded = 2, entriesSkipped = 5).summary,
        ).isEqualTo("3 added, 1 updated entries, 2 added, 0 updated decks, 5 skipped (unknown card)")
    }
}
