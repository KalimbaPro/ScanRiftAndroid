package com.scanrift.android.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "card_lists",
    indices = [Index("systemType")],
)
data class CardListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String = "#FF9500",
    val isSystem: Boolean = false,
    /** Only known value is "wishlist"; exactly one such list exists per install. */
    val systemType: String? = null,
    val createdDate: Long,
)

/**
 * List membership. Both sides cascade: deleting a list or a card drops the membership
 * row, which is the join-table equivalent of iOS's `.nullify` on a to-many relation.
 */
@Entity(
    tableName = "card_list_cross_ref",
    primaryKeys = ["listId", "cardId"],
    foreignKeys = [
        ForeignKey(
            entity = CardListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cardId")],
)
data class CardListCrossRef(
    val listId: String,
    val cardId: String,
)
