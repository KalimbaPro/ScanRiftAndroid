package com.scanrift.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * **Unused.** What the API last reported per set, from when sync decided freshness by
 * comparing `/sets` card counts. The `/version` data tag replaced that check, and
 * nothing reads or writes this table any more.
 *
 * The entity stays registered so the Room schema is unchanged — dropping the table
 * would need a migration for no benefit. Do not build on it.
 */
@Entity(tableName = "synced_sets")
data class SyncedSetEntity(
    /** Raw API set id, not the normalised one — promos stay separate here. */
    @PrimaryKey val setId: String,
    /** The `card_count` the API reported when we last fetched this set. */
    val apiCardCount: Int,
    val lastSyncedAt: Long,
)
