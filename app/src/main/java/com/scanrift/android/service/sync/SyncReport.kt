package com.scanrift.android.service.sync

data class SyncReport(
    val added: Int = 0,
    val updated: Int = 0,
    val unchanged: Int = 0,
    val setsChecked: Int = 0,
    val setsRefetched: Int = 0,
    val duplicatesRemoved: Int = 0,
    val orphansRemoved: Int = 0,
) {
    val hasChanges: Boolean get() = added > 0 || updated > 0

    operator fun plus(other: SyncReport) = SyncReport(
        added = added + other.added,
        updated = updated + other.updated,
        unchanged = unchanged + other.unchanged,
        setsChecked = setsChecked + other.setsChecked,
        setsRefetched = setsRefetched + other.setsRefetched,
        duplicatesRemoved = duplicatesRemoved + other.duplicatesRemoved,
        orphansRemoved = orphansRemoved + other.orphansRemoved,
    )
}
