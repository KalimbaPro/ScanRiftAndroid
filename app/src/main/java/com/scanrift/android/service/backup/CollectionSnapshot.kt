package com.scanrift.android.service.backup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import com.scanrift.android.core.time.Iso8601

/**
 * The backup file, byte-compatible with the iOS `collection-snapshot.json`.
 *
 * This is the cross-platform contract that replaces iCloud on Android. A file written
 * here restores on iOS and vice versa, which is a better story than either iOS sync
 * system offered — so the field names, the date encoding and the merge rules all have
 * to match Swift's synthesized `Codable` output exactly.
 *
 * ## Versioning rule: additive only
 *
 * [version] stays 1. iOS never inspects it, and bumping it risks a future iOS-side
 * gate rejecting our files. Swift's synthesized decoder ignores unknown keys, so new
 * top-level arrays are safe in both directions — [gameRecords] is exactly that. Never
 * rename or remove a v1 key; bump only for a genuinely breaking change.
 */
@Serializable
data class CollectionSnapshot(
    val version: Int = CURRENT_VERSION,
    @Serializable(with = IsoInstantSerializer::class) val exportedAt: Long,
    val entries: List<Entry> = emptyList(),
    val decks: List<DeckExport> = emptyList(),
    val lists: List<ListExport> = emptyList(),

    /**
     * Android superset. iOS's snapshot has no game history, and its decoder ignores
     * the key; ours defaults it to empty when reading an iOS file. So both directions
     * work, and an Android-to-Android restore keeps the history iOS would drop.
     */
    val gameRecords: List<GameRecordExport> = emptyList(),
) {
    @Serializable
    data class Entry(
        val cardId: String,
        val quantity: Int,
        val isFoil: Boolean,
        @Serializable(with = IsoInstantSerializer::class) val dateAdded: Long,
        val condition: String,
        val notes: String? = null,
        val folder: String? = null,
    )

    @Serializable
    data class DeckExport(
        val id: String,
        val name: String,
        @Serializable(with = IsoInstantSerializer::class) val createdDate: Long,
        @Serializable(with = IsoInstantSerializer::class) val lastModifiedDate: Long,
        val legendCardId: String? = null,
        val championCardId: String? = null,
        val entries: List<DeckEntryExport> = emptyList(),
    )

    @Serializable
    data class DeckEntryExport(
        val cardId: String,
        val quantity: Int,
        val section: String,
    )

    @Serializable
    data class ListExport(
        val id: String,
        val name: String,
        val colorHex: String,
        val systemType: String? = null,
        val isSystem: Boolean,
        @Serializable(with = IsoInstantSerializer::class) val createdDate: Long,
        val cardIds: List<String> = emptyList(),
    )

    @Serializable
    data class GameRecordExport(
        val id: String,
        @Serializable(with = IsoInstantSerializer::class) val date: Long,
        val name: String? = null,
        val result: String,
        val playerName: String? = null,
        val opponentName: String? = null,
        val pointsScored: Int? = null,
        val pointsAllowed: Int? = null,
        val ties: Int? = null,
        val conquerCount: Int? = null,
        val holdCount: Int? = null,
        val abilityCount: Int? = null,
        val notes: String? = null,
        val deckId: String? = null,
        val legendId: String? = null,
        val opponentLegendId: String? = null,
    )

    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Epoch millis on the Kotlin side, ISO-8601 on the wire.
 *
 * **Writes are always truncated to whole seconds.** Swift decodes these with
 * `.iso8601`, which is `.withInternetDateTime` and rejects fractional seconds — one
 * sub-second timestamp anywhere in the file makes iOS throw while decoding the *entire*
 * snapshot, so a restore fails with no obvious cause. Reads stay lenient in both
 * directions.
 */
object IsoInstantSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("IsoInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeString(Iso8601.format(value))
    }

    override fun deserialize(decoder: Decoder): Long =
        Iso8601.parse(decoder.decodeString()) ?: 0L
}

/** Counts mirroring iOS's `RestoreResult`, including the summary string's shape. */
data class RestoreResult(
    val entriesAdded: Int = 0,
    val entriesUpdated: Int = 0,
    val entriesSkipped: Int = 0,
    val decksAdded: Int = 0,
    val decksUpdated: Int = 0,
    val listsAdded: Int = 0,
    val listsUpdated: Int = 0,
    val gameRecordsAdded: Int = 0,
) {
    val summary: String
        get() {
            val parts = buildList {
                if (entriesAdded + entriesUpdated > 0) {
                    add("$entriesAdded added, $entriesUpdated updated entries")
                }
                if (decksAdded + decksUpdated > 0) {
                    add("$decksAdded added, $decksUpdated updated decks")
                }
                if (listsAdded + listsUpdated > 0) {
                    add("$listsAdded added, $listsUpdated updated lists")
                }
                if (gameRecordsAdded > 0) add("$gameRecordsAdded added games")
                if (entriesSkipped > 0) add("$entriesSkipped skipped (unknown card)")
            }
            return if (parts.isEmpty()) "Nothing to restore" else parts.joinToString(", ")
        }
}
