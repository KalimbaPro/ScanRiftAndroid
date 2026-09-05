package com.scanrift.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What the API last told us about a set, so the delta sync can tell "changed" from
 * "merely different".
 *
 * The naive check — refetch when `set.cardCount > localCount` — cannot work, because
 * the two numbers count different things. `cardCount` is the number of **raw records**
 * the API serves, and the local count is what survives de-duplication. Vendetta serves
 * 358 records that collapse to 227 real cards, so `358 > 227` is true forever and the
 * whole set refetches on every single sync. Origins' promos do the same at a smaller
 * scale (133 → 117).
 *
 * Storing the last-seen `cardCount` and refetching only when it *changes* compares
 * like with like, and also catches a set shrinking, which the `>` test never could.
 *
 * iOS has the same bug; this table is Android-only for now.
 */
@Entity(tableName = "synced_sets")
data class SyncedSetEntity(
    /** Raw API set id, not the normalised one — promos stay separate here. */
    @PrimaryKey val setId: String,
    /** The `card_count` the API reported when we last fetched this set. */
    val apiCardCount: Int,
    val lastSyncedAt: Long,
)
