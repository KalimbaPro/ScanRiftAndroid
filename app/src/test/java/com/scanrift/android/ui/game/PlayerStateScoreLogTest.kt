package com.scanrift.android.ui.game

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.domain.model.ScoreCategory
import org.junit.Test

/**
 * The score log is what makes the tap-zone layout's single decrement button
 * unambiguous, so its arithmetic is pinned here rather than left to the UI.
 */
class PlayerStateScoreLogTest {

    private fun player() = PlayerState(name = "Player 1")

    @Test
    fun `undo takes points back in the order they were scored`() {
        val player = player()
            .scored(ScoreCategory.HOLD)
            .scored(ScoreCategory.HOLD)
            .scored(ScoreCategory.CONQUER)

        assertThat(player.score).isEqualTo(3)

        val afterOne = player.undone()
        assertThat(afterOne.conquer).isEqualTo(0)
        assertThat(afterOne.hold).isEqualTo(2)

        val afterTwo = afterOne.undone()
        assertThat(afterTwo.hold).isEqualTo(1)
        assertThat(afterTwo.score).isEqualTo(1)
    }

    @Test
    fun `undo floors at zero and leaves the log empty`() {
        val player = player().scored(ScoreCategory.ABILITY).undone().undone().undone()

        assertThat(player.score).isEqualTo(0)
        assertThat(player.scoreLog).isEmpty()
    }

    @Test
    fun `removing a named category drops its last log entry, not the newest one`() {
        val player = player()
            .scored(ScoreCategory.CONQUER)
            .scored(ScoreCategory.HOLD)
            .unscored(ScoreCategory.CONQUER)

        assertThat(player.conquer).isEqualTo(0)
        assertThat(player.hold).isEqualTo(1)
        assertThat(player.scoreLog).containsExactly(ScoreCategory.HOLD)
    }

    @Test
    fun `removing a category with no points does nothing`() {
        val player = player().scored(ScoreCategory.HOLD).unscored(ScoreCategory.CONQUER)

        assertThat(player.score).isEqualTo(1)
        assertThat(player.scoreLog).containsExactly(ScoreCategory.HOLD)
    }

    @Test
    fun `undo falls back to the largest category when the log cannot answer`() {
        // A roster restored from a build with no log, or counts reached some other way:
        // the decrement still has to do something visible rather than silently no-op.
        val player = PlayerState(name = "Player 1", conquer = 1, hold = 3, ability = 2)

        val after = player.undone()
        assertThat(after.hold).isEqualTo(2)
        assertThat(after.score).isEqualTo(5)
    }

    @Test
    fun `a stale log entry for an empty category does not block the undo`() {
        val player = PlayerState(
            name = "Player 1",
            conquer = 2,
            scoreLog = listOf(ScoreCategory.ABILITY),
        )

        val after = player.undone()
        assertThat(after.conquer).isEqualTo(1)
        assertThat(after.scoreLog).doesNotContain(ScoreCategory.ABILITY)
    }

    @Test
    fun `ordered points replay the scoring sequence one entry per point`() {
        val player = player()
            .scored(ScoreCategory.CONQUER)
            .scored(ScoreCategory.ABILITY)
            .scored(ScoreCategory.CONQUER)
            .scored(ScoreCategory.HOLD)

        assertThat(player.orderedPoints()).containsExactly(
            ScoreCategory.CONQUER,
            ScoreCategory.ABILITY,
            ScoreCategory.CONQUER,
            ScoreCategory.HOLD,
        ).inOrder()
    }

    @Test
    fun `ordered points always has exactly one entry per point`() {
        // A roster restored from a build with no log: the track still has to draw the
        // right number of cells rather than an empty rail over a non-zero score.
        val player = PlayerState(name = "Player 1", conquer = 2, hold = 1)

        val ordered = player.orderedPoints()
        assertThat(ordered).hasSize(3)
        assertThat(ordered.count { it == ScoreCategory.CONQUER }).isEqualTo(2)
        assertThat(ordered.count { it == ScoreCategory.HOLD }).isEqualTo(1)
    }

    @Test
    fun `ordered points ignores log entries the counts cannot back`() {
        val player = PlayerState(
            name = "Player 1",
            conquer = 1,
            scoreLog = listOf(ScoreCategory.ABILITY, ScoreCategory.CONQUER, ScoreCategory.HOLD),
        )

        assertThat(player.orderedPoints()).containsExactly(ScoreCategory.CONQUER)
    }

    @Test
    fun `undo removes the last cell the track drew`() {
        val player = player()
            .scored(ScoreCategory.HOLD)
            .scored(ScoreCategory.ABILITY)
            .scored(ScoreCategory.CONQUER)

        val before = player.orderedPoints()
        val after = player.undone().orderedPoints()

        assertThat(after).isEqualTo(before.dropLast(1))
    }

    @Test
    fun `both layouts write the same log so switching mid-game stays consistent`() {
        // Classic layout scores two, tap-zone layout undoes one.
        val player = player()
            .scored(ScoreCategory.ABILITY)
            .scored(ScoreCategory.CONQUER)
            .undone()

        assertThat(player.conquer).isEqualTo(0)
        assertThat(player.ability).isEqualTo(1)
        assertThat(player.scoreLog).containsExactly(ScoreCategory.ABILITY)
    }
}
