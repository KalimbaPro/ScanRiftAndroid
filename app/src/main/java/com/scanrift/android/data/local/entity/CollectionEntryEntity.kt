package com.scanrift.android.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "collection_entries",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["cardId"])]
)
data class CollectionEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val quantity: Int = 1,
    val isFoil: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val condition: String = CardCondition.NEAR_MINT.value,
    val notes: String? = null,
    val folder: String? = null
)

enum class CardCondition(val value: String, val shortName: String) {
    MINT("Mint", "M"),
    NEAR_MINT("Near Mint", "NM"),
    LIGHTLY_PLAYED("Lightly Played", "LP"),
    MODERATELY_PLAYED("Moderately Played", "MP"),
    HEAVILY_PLAYED("Heavily Played", "HP"),
    DAMAGED("Damaged", "DMG");

    companion object {
        fun fromValue(value: String): CardCondition =
            entries.firstOrNull { it.value == value } ?: NEAR_MINT
    }
}
