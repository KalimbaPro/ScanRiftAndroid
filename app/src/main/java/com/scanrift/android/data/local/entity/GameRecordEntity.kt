package com.scanrift.android.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.scanrift.android.domain.model.GameResult

/**
 * A recorded game. Entirely new on Android — the old build had no equivalent.
 *
 * [deckId] is nullable and `SET NULL` (iOS `.nullify`): a legend-only game has no deck,
 * and deleting a deck must not erase its history, only unlink it.
 *
 * [legendId] and [opponentLegendId] are plain card-id strings rather than foreign keys,
 * matching iOS. A legend-vs-legend matchup record is worth keeping even if the card
 * later disappears from the catalogue, and a FK would silently null it.
 */
@Entity(
    tableName = "game_records",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("deckId"), Index("date")],
)
data class GameRecordEntity(
    @PrimaryKey val id: String,
    val date: Long,
    val name: String? = null,
    /** Stores [GameResult.value] verbatim: "win" | "loss" | "draw". */
    val result: String = GameResult.DRAW.value,
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
