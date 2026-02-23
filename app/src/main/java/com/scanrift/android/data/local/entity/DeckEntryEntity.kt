package com.scanrift.android.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "deck_entries",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["deckId"]),
        Index(value = ["cardId"])
    ]
)
data class DeckEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: String,
    val cardId: String,
    val quantity: Int = 1,
    val section: String = DeckSection.MAIN_DECK.value
)

enum class DeckSection(val value: String) {
    MAIN_DECK("mainDeck"),
    RUNE("rune"),
    BATTLEFIELD("battlefield"),
    SIDEBOARD("sideboard");

    companion object {
        fun fromValue(value: String): DeckSection =
            entries.firstOrNull { it.value == value } ?: MAIN_DECK
    }
}
