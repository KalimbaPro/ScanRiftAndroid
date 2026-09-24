package com.scanrift.android.ui.game

import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.GameRecord
import com.scanrift.android.domain.model.GameResult
import com.scanrift.android.domain.model.Rarity

enum class RoundOutcome { UNSET, LEFT_WON, RIGHT_WON, TIED }

val List<RoundOutcome>.leftWins: Int get() = count { it == RoundOutcome.LEFT_WON }
val List<RoundOutcome>.rightWins: Int get() = count { it == RoundOutcome.RIGHT_WON }
val List<RoundOutcome>.ties: Int get() = count { it == RoundOutcome.TIED }

val unsetRounds: List<RoundOutcome> = List(3) { RoundOutcome.UNSET }

fun resultOf(mine: Int, theirs: Int): GameResult = when {
    mine > theirs -> GameResult.WIN
    mine < theirs -> GameResult.LOSS
    else -> GameResult.DRAW
}

fun roundsOf(record: GameRecord): List<RoundOutcome>? {
    val scored = record.pointsScored ?: 0
    val allowed = record.pointsAllowed ?: 0
    val ties = record.ties ?: 0
    if (scored + allowed + ties > 3) return null
    return (List(scored) { RoundOutcome.LEFT_WON } + List(allowed) { RoundOutcome.RIGHT_WON } +
        List(ties) { RoundOutcome.TIED }).plus(unsetRounds).take(3)
}

fun GameRecord.withRounds(rounds: List<RoundOutcome>): GameRecord = copy(
    pointsScored = rounds.leftWins,
    pointsAllowed = rounds.rightWins,
    ties = rounds.ties.takeIf { it > 0 },
    result = resultOf(rounds.leftWins, rounds.rightWins),
)

data class PendingGameRecord(
    val playerId: String,
    val deckId: String?,
    val playerName: String,
    val pointsScored: Int,
    val pointsAllowed: Int,
    val ties: Int = 0,
    val conquer: Int,
    val hold: Int,
    val ability: Int,
    val result: GameResult,
    val gameName: String = "",
    val opponentName: String = "",
    val legendId: String?,
    val opponentLegendId: String?,
) {
    fun toGameRecord(id: String, date: Long): GameRecord {
        val hasBreakdown = conquer + hold + ability > 0
        return GameRecord(
            id = id,
            date = date,
            name = gameName.trim().ifEmpty { null },
            result = result,
            playerName = playerName,
            opponentName = opponentName.trim().ifEmpty { null },
            pointsScored = pointsScored,
            pointsAllowed = pointsAllowed,
            ties = ties.takeIf { it > 0 },
            conquerCount = conquer.takeIf { hasBreakdown },
            holdCount = hold.takeIf { hasBreakdown },
            abilityCount = ability.takeIf { hasBreakdown },
            deckId = deckId,
            legendId = legendId,
            opponentLegendId = opponentLegendId,
        )
    }
}

fun bestOfThreeRecords(
    players: List<PlayerState>,
    rounds: List<RoundOutcome>,
    gameName: String,
): List<PendingGameRecord> = players.take(2).mapIndexedNotNull { index, player ->
    if (!player.isRecordable) return@mapIndexedNotNull null
    val opponent = players[1 - index]
    val wins = if (index == 0) rounds.leftWins else rounds.rightWins
    val losses = if (index == 0) rounds.rightWins else rounds.leftWins
    PendingGameRecord(
        playerId = player.id,
        deckId = player.deckId,
        playerName = player.name,
        pointsScored = wins,
        pointsAllowed = losses,
        ties = rounds.ties,
        conquer = player.conquer,
        hold = player.hold,
        ability = player.ability,
        result = resultOf(wins, losses),
        gameName = gameName,
        opponentName = opponent.name,
        legendId = player.legendCardId,
        opponentLegendId = opponent.legendCardId,
    )
}

fun freeForAllRecords(players: List<PlayerState>): List<PendingGameRecord> {
    val topScore = players.maxOfOrNull { it.score } ?: 0
    return players.filter { it.isRecordable }.map { player ->
        PendingGameRecord(
            playerId = player.id,
            deckId = player.deckId,
            playerName = player.name,
            pointsScored = player.score,
            pointsAllowed = players.filter { it.id != player.id }.maxOfOrNull { it.score } ?: 0,
            conquer = player.conquer,
            hold = player.hold,
            ability = player.ability,
            result = if (player.score >= topScore) GameResult.WIN else GameResult.LOSS,
            legendId = player.legendCardId,
            opponentLegendId = null,
        )
    }
}

fun List<Card>.pickableLegends(): List<Card> = filter { card ->
    card.type == CardType.LEGEND &&
        !card.name.contains("(Metal)") &&
        !card.isAlternateArt &&
        card.rarity != Rarity.PROMO
}.sortedBy { it.name }
