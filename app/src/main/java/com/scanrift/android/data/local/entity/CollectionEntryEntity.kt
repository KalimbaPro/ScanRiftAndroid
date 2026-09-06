package com.scanrift.android.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.scanrift.android.domain.model.CardCondition

/**
 * One owned stack of a card.
 *
 * [cardId] is nullable with `ON DELETE SET NULL`, which is both the faithful
 * translation of iOS's optional SwiftData relationship and the structural fix for the
 * bug that made the old build dangerous: it used to `deleteAll()` the catalogue on
 * every sync, and the cascade took the user's whole collection with it. With SET NULL
 * there is no path from a catalogue refresh to data loss. Reads all inner-join `cards`,
 * so an orphaned row simply disappears from the UI while its quantity survives; a
 * maintenance pass reclaims rows whose card is genuinely gone.
 *
 * The unique index is iOS's `EntryKey` promoted to a database constraint, so scan-add,
 * import and snapshot restore are idempotent by construction rather than by discipline.
 */
@Entity(
    tableName = "collection_entries",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("cardId"),
        Index(value = ["cardId", "isFoil", "condition"], unique = true),
    ],
)
data class CollectionEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String?,
    val quantity: Int = 1,
    val isFoil: Boolean = false,
    val dateAdded: Long,
    /** Stores [CardCondition.value] verbatim, e.g. "Near Mint". */
    val condition: String = CardCondition.NEAR_MINT.value,
    val notes: String? = null,
    val folder: String? = null,
)
