package com.scanrift.android.ui.text

import com.google.common.truth.Truth.assertThat
import com.scanrift.android.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric only for the R.drawable ids; the parser itself is pure. */
@RunWith(RobolectricTestRunner::class)
class CardTextParserTest {

    private fun plain(segments: List<CardTextParser.Segment>) =
        segments.filterIsInstance<CardTextParser.Segment.PlainText>().joinToString("") { it.text }

    @Test
    fun `plain text passes through untouched`() {
        val segments = CardTextParser.parse("Deal 3 damage to a unit.")
        assertThat(plain(segments)).isEqualTo("Deal 3 damage to a unit.")
    }

    @Test
    fun `rune icons are recognised`() {
        val segments = CardTextParser.parse("Pay :rb_rune_fury: to play me.")
        val icon = segments.filterIsInstance<CardTextParser.Segment.Icon>().single()
        assertThat(icon.drawableRes).isEqualTo(R.drawable.ic_rune_fury)
    }

    @Test
    fun `legacy short codes still resolve`() {
        val segments = CardTextParser.parse(":rb_fury: and :rb_calm:")
        assertThat(segments.filterIsInstance<CardTextParser.Segment.Icon>().map { it.drawableRes })
            .containsExactly(R.drawable.ic_rune_fury, R.drawable.ic_rune_calm)
    }

    @Test
    fun `energy costs parse their number`() {
        val segments = CardTextParser.parse("Pay :rb_energy_3: to play me.")
        assertThat(segments.filterIsInstance<CardTextParser.Segment.EnergyCost>().single().value)
            .isEqualTo(3)
    }

    @Test
    fun `adjacent icon codes both parse`() {
        // ":rb_energy_1::rb_rune_fury:" is real card text — the codes touch, with no
        // separator, so a greedy pattern has to stop at the first closing colon.
        val segments = CardTextParser.parse(":rb_energy_1::rb_rune_fury:")
        assertThat(segments.filterIsInstance<CardTextParser.Segment.EnergyCost>().single().value).isEqualTo(1)
        assertThat(segments.filterIsInstance<CardTextParser.Segment.Icon>().single().drawableRes)
            .isEqualTo(R.drawable.ic_rune_fury)
    }

    @Test
    fun `keywords become badges`() {
        val segments = CardTextParser.parse("[Accelerate] something")
        assertThat(segments.filterIsInstance<CardTextParser.Segment.Keyword>().single().label)
            .isEqualTo("Accelerate")
    }

    @Test
    fun `reminder text keeps its parentheses and still parses its icons`() {
        // The bug this pins: recursing with the parentheses attached made the inner
        // pass match the same parenthetical again, so it emitted the whole thing —
        // icon codes and all — as literal text.
        val segments = CardTextParser.parse(
            "[Accelerate] (You may pay :rb_energy_1::rb_rune_fury: as an additional cost.)",
        )

        val reminder = segments.filterIsInstance<CardTextParser.Segment.Reminder>().single()
        assertThat(plain(reminder.segments)).startsWith("(")
        assertThat(plain(reminder.segments)).endsWith(")")
        assertThat(plain(reminder.segments)).doesNotContain(":rb_")
        assertThat(reminder.segments.filterIsInstance<CardTextParser.Segment.EnergyCost>()).hasSize(1)
        assertThat(reminder.segments.filterIsInstance<CardTextParser.Segment.Icon>()).hasSize(1)
    }

    @Test
    fun `an unknown code falls through as literal text rather than vanishing`() {
        val segments = CardTextParser.parse("Something :rb_not_a_real_icon: here")
        assertThat(plain(segments)).contains(":rb_not_a_real_icon:")
    }

    @Test
    fun `escaped newlines become real ones`() {
        val segments = CardTextParser.parse("Line one\\nLine two")
        assertThat(plain(segments)).isEqualTo("Line one\nLine two")
    }

    @Test
    fun `keyword colour groups match iOS`() {
        assertThat(CardTextParser.keywordStyle("Accelerate").first).isEqualTo(0xFF24A189)
        assertThat(CardTextParser.keywordStyle("Shield").first).isEqualTo(0xFFC9346B)
        assertThat(CardTextParser.keywordStyle("Deflect").first).isEqualTo(0xFF9AB331)
        assertThat(CardTextParser.keywordStyle("Mighty").first).isEqualTo(0xFF6B7071)
        // Unknown keywords take the neutral grey rather than disappearing.
        assertThat(CardTextParser.keywordStyle("Whatever").first).isEqualTo(0xFF6B7071)
    }

    @Test
    fun `keyword matching is case insensitive`() {
        assertThat(CardTextParser.keywordStyle("accelerate")).isEqualTo(CardTextParser.keywordStyle("ACCELERATE"))
    }

    @Test
    fun `empty text produces nothing`() {
        assertThat(CardTextParser.parse("")).isEmpty()
    }
}
