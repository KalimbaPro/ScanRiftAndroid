package com.scanrift.android.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.scanrift.android.domain.model.DeckSection

/**
 * Legend and champion are `SET NULL` rather than cascade: losing a card from the
 * catalogue must empty the slot, never delete the deck.
 */
@Entity(
    tableName = "decks",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["legendCardId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["championCardId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("legendCardId"), Index("championCardId"), Index("lastModifiedDate")],
)
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String = "New Deck",
    val createdDate: Long,
    val lastModifiedDate: Long,
    val legendCardId: String? = null,
    val championCardId: String? = null,
)

/**
 * A card in a deck section.
 *
 * `deckId` cascades (iOS `.cascade`) so deleting a deck removes its entries, while
 * `cardId` is SET NULL for the same reason as the collection.
 */
@Entity(
    tableName = "deck_entries",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("deckId"),
        Index("cardId"),
        Index(value = ["deckId", "cardId", "section"], unique = true),
    ],
)
data class DeckEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: String,
    val cardId: String?,
    val quantity: Int = 1,
    /** Stores [DeckSection.value] verbatim, e.g. "mainDeck". */
    val section: String = DeckSection.MAIN_DECK.value,
)
