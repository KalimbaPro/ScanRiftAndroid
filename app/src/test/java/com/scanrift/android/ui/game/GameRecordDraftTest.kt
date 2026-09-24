package com.scanrift.android.ui.game

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.GameRecord
import com.scanrift.android.domain.model.GameResult
import com.scanrift.android.domain.model.ScoreCategory
import kotlinx.serialization.json.Json
import org.junit.Test

class GameRecordDraftTest {

    private val alice = PlayerState(id = "a", name = "Alice", deckId = "deck-a", legendCardId = "legend-a")
        .scored(ScoreCategory.CONQUER).scored(ScoreCategory.HOLD)
    private val bob = PlayerState(id = "b", name = "Bob", legendCardId = "legend-b")
    private val carol = PlayerState(id = "c", name = "Carol")

    @Test
    fun `best of three records one entry per bound player from their own side`() {
        val rounds = listOf(RoundOutcome.LEFT_WON, RoundOutcome.TIED, RoundOutcome.RIGHT_WON)

        val records = bestOfThreeRecords(listOf(alice, bob), rounds, "Finals")

        assertThat(records).hasSize(2)
        val (left, right) = records
        assertThat(left.pointsScored).isEqualTo(1)
        assertThat(left.pointsAllowed).isEqualTo(1)
        assertThat(left.ties).isEqualTo(1)
        assertThat(left.result).isEqualTo(GameResult.DRAW)
        assertThat(left.opponentName).isEqualTo("Bob")
        assertThat(left.opponentLegendId).isEqualTo("legend-b")
        assertThat(left.conquer).isEqualTo(1)
        assertThat(left.gameName).isEqualTo("Finals")
        assertThat(right.legendId).isEqualTo("legend-b")
        assertThat(right.opponentLegendId).isEqualTo("legend-a")
    }

    @Test
    fun `best of three skips a player bound to neither deck nor legend`() {
        val rounds = listOf(RoundOutcome.RIGHT_WON, RoundOutcome.RIGHT_WON, RoundOutcome.UNSET)

        val records = bestOfThreeRecords(listOf(alice, carol), rounds, "")

        assertThat(records.map { it.playerId }).containsExactly("a")
        assertThat(records.single().result).isEqualTo(GameResult.LOSS)
        assertThat(records.single().pointsAllowed).isEqualTo(2)
    }

    @Test
    fun `free for all gives every tied leader a win and leaves the opponent blank`() {
        val dave = PlayerState(id = "d", name = "Dave", deckId = "deck-d").scored(ScoreCategory.ABILITY).scored(ScoreCategory.ABILITY)

        val records = freeForAllRecords(listOf(alice, bob, carol, dave))

        assertThat(records.map { it.playerId }).containsExactly("a", "b", "d").inOrder()
        assertThat(records.map { it.result }).containsExactly(GameResult.WIN, GameResult.LOSS, GameResult.WIN).inOrder()
        assertThat(records[1].pointsAllowed).isEqualTo(2)
        assertThat(records.all { it.opponentName.isEmpty() && it.opponentLegendId == null }).isTrue()
    }

    @Test
    fun `saving blanks out empty text, zero ties and an empty breakdown`() {
        val pending = freeForAllRecords(listOf(bob, carol)).single().copy(gameName = "  ", opponentName = " ")

        val record = pending.toGameRecord("id", 42L)

        assertThat(record.name).isNull()
        assertThat(record.opponentName).isNull()
        assertThat(record.ties).isNull()
        assertThat(record.hasCategoryBreakdown).isFalse()
        assertThat(record.deckId).isNull()
        assertThat(record.legendId).isEqualTo("legend-b")
    }

    @Test
    fun `saving keeps the whole breakdown once any category scored`() {
        val record = freeForAllRecords(listOf(alice, bob)).first().toGameRecord("id", 0L)

        assertThat(record.conquerCount).isEqualTo(1)
        assertThat(record.holdCount).isEqualTo(1)
        assertThat(record.abilityCount).isEqualTo(0)
    }

    @Test
    fun `a record that fits in three rounds is rebuilt wins first then losses then ties`() {
        val record = GameRecord(id = "r", date = 0L, pointsScored = 1, pointsAllowed = 1, ties = 1)

        assertThat(roundsOf(record))
            .containsExactly(RoundOutcome.LEFT_WON, RoundOutcome.RIGHT_WON, RoundOutcome.TIED).inOrder()
        assertThat(roundsOf(record.copy(pointsScored = 2, ties = null)))
            .containsExactly(RoundOutcome.LEFT_WON, RoundOutcome.LEFT_WON, RoundOutcome.RIGHT_WON).inOrder()
        assertThat(roundsOf(record.copy(pointsScored = null, pointsAllowed = null, ties = null))).isEqualTo(unsetRounds)
    }

    @Test
    fun `a record with more than three points is not a best of three`() {
        assertThat(roundsOf(GameRecord(id = "r", date = 0L, pointsScored = 8, pointsAllowed = 5))).isNull()
    }

    @Test
    fun `editing rounds rewrites the score, ties and result`() {
        val record = GameRecord(id = "r", date = 0L, result = GameResult.LOSS, ties = 1)

        val edited = record.withRounds(listOf(RoundOutcome.LEFT_WON, RoundOutcome.LEFT_WON, RoundOutcome.UNSET))

        assertThat(edited.pointsScored).isEqualTo(2)
        assertThat(edited.pointsAllowed).isEqualTo(0)
        assertThat(edited.ties).isNull()
        assertThat(edited.result).isEqualTo(GameResult.WIN)
    }

    @Test
    fun `a roster saved without a score log still decodes`() {
        val roster = Json { ignoreUnknownKeys = true }
            .decodeFromString<List<PlayerState>>("""[{"id":"a","name":"Alice","conquer":2}]""")

        assertThat(roster.single().scoreLog).isEmpty()
        assertThat(roster.single().undone().conquer).isEqualTo(1)
    }
}
