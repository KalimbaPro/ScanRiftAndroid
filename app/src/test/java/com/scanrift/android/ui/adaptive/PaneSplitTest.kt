package com.scanrift.android.ui.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.scanrift.android.ui.theme.Dimens
import org.junit.Test

/** The arithmetic behind the collection's draggable split. */
class PaneSplitTest {

    private val spacer = 24.dp

    private fun listWidth(share: Float, total: Dp): Dp = total * share - spacer / 2
    private fun detailWidth(share: Float, total: Dp): Dp = total - total * share - spacer / 2

    @Test
    fun `every anchor keeps both panes at or above their floors`() {
        for (width in listOf(640.dp, 673.dp, 700.dp, 841.dp, 884.dp, 1280.dp)) {
            val anchors = PaneSplit.anchors(width, spacer)
            assertThat(anchors).isNotEmpty()
            anchors.forEach { share ->
                assertThat(listWidth(share, width).value).isAtLeast(Dimens.ListPaneMinWidth.value)
                assertThat(detailWidth(share, width).value).isAtLeast(Dimens.DetailPaneMinWidth.value)
            }
        }
    }

    @Test
    fun `anchors are sorted, distinct and spaced`() {
        val anchors = PaneSplit.anchors(884.dp, spacer)
        assertThat(anchors).isInStrictOrder()
        anchors.zipWithNext().forEach { (a, b) -> assertThat(b - a).isAtLeast(PaneSplit.MIN_ANCHOR_GAP - 1e-4f) }
    }

    @Test
    fun `an unfolded fold in portrait still gets a few stops`() {
        // 700dp: the floors leave about 76dp of travel.
        val anchors = PaneSplit.anchors(700.dp, spacer)
        assertThat(anchors.size).isAtLeast(2)
        assertThat(anchors).contains(0.5f)
        assertThat(PaneSplit.isResizable(anchors)).isTrue()
    }

    @Test
    fun `a wide screen offers the round stops between the floors`() {
        val anchors = PaneSplit.anchors(1280.dp, spacer)
        assertThat(anchors).containsAtLeast(0.3f, 0.4f, 0.5f, 0.6f, 0.7f)
    }

    @Test
    fun `too narrow for both floors is not resizable`() {
        // 280 + 320 + 24 = 624dp is the least that holds both.
        assertThat(PaneSplit.proportionRange(600.dp, spacer)).isNull()
        assertThat(PaneSplit.anchors(600.dp, spacer)).isEmpty()
        assertThat(PaneSplit.anchors(626.dp, spacer)).isEmpty()
        assertThat(PaneSplit.isResizable(emptyList())).isFalse()
        assertThat(PaneSplit.anchors(0.dp, spacer)).isEmpty()
    }

    @Test
    fun `right-to-left swaps which floor applies to which side`() {
        val ltr = PaneSplit.anchors(884.dp, spacer, Dimens.ListPaneMinWidth, Dimens.DetailPaneMinWidth)
        val rtl = PaneSplit.anchors(884.dp, spacer, Dimens.DetailPaneMinWidth, Dimens.ListPaneMinWidth)
        assertThat(rtl.first()).isGreaterThan(ltr.first())
        assertThat(PaneSplit.mirror(PaneSplit.mirror(0.417f))).isEqualTo(0.417f)
        assertThat(PaneSplit.mirror(0.4f)).isEqualTo(0.6f)
    }

    @Test
    fun `restore maps the saved share to the nearest anchor that fits`() {
        val narrow = PaneSplit.anchors(700.dp, spacer)
        val wide = PaneSplit.anchors(1280.dp, spacer)
        assertThat(PaneSplit.restoreTarget(wide, 0.3f)).isEqualTo(0.3f)
        assertThat(PaneSplit.restoreTarget(narrow, 0.3f)).isEqualTo(narrow.first())
        assertThat(PaneSplit.restoreTarget(narrow, 0.7f)).isEqualTo(narrow.last())
        assertThat(PaneSplit.restoreTarget(emptyList(), 0.5f)).isNull()
        assertThat(PaneSplit.nearestIndex(emptyList(), 0.5f)).isEqualTo(-1)
    }

    @Test
    fun `restoring never overwrites the saved share`() {
        val narrow = PaneSplit.anchors(700.dp, spacer)
        // The screen restored a wide-screen 30% to the narrow screen's floor.
        assertThat(PaneSplit.toPersist(narrow.first(), narrow, stored = 0.3f)).isNull()
        // Not loaded yet, nothing settled, or an anchor from a previous width.
        assertThat(PaneSplit.toPersist(0.5f, narrow, stored = null)).isNull()
        assertThat(PaneSplit.toPersist(null, narrow, stored = 0.5f)).isNull()
        assertThat(PaneSplit.toPersist(0.35f, narrow, stored = 0.5f)).isNull()
    }

    @Test
    fun `a user choice is persisted`() {
        val wide = PaneSplit.anchors(1280.dp, spacer)
        assertThat(PaneSplit.toPersist(0.6f, wide, stored = 0.4f)).isEqualTo(0.6f)
        assertThat(PaneSplit.toPersist(0.5f, wide, stored = 0.3f)).isEqualTo(0.5f)
    }

    @Test
    fun `stored values are sanitised and defaulted`() {
        assertThat(PaneSplit.storedOrDefault(null)).isEqualTo(PaneSplit.DEFAULT_PROPORTION)
        assertThat(PaneSplit.storedOrDefault(Float.NaN)).isEqualTo(PaneSplit.DEFAULT_PROPORTION)
        assertThat(PaneSplit.storedOrDefault(0f)).isEqualTo(PaneSplit.DEFAULT_PROPORTION)
        assertThat(PaneSplit.storedOrDefault(1.2f)).isEqualTo(PaneSplit.DEFAULT_PROPORTION)
        assertThat(PaneSplit.storedOrDefault(0.35f)).isEqualTo(0.35f)
    }

    @Test
    fun `stepping walks the anchors and stops at the ends`() {
        val anchors = listOf(0.4f, 0.45f, 0.5f)
        assertThat(PaneSplit.step(anchors, 0.45f, 1)).isEqualTo(0.5f)
        assertThat(PaneSplit.step(anchors, 0.5f, 1)).isEqualTo(0.5f)
        assertThat(PaneSplit.step(anchors, 0.4f, -1)).isEqualTo(0.4f)
        assertThat(PaneSplit.step(anchors, null, -1)).isEqualTo(0.45f)
        assertThat(PaneSplit.step(emptyList(), 0.5f, 1)).isNull()
    }

    @Test
    fun `position uses the library's rounding`() {
        assertThat(PaneSplit.positionPx(0.5f, 1001)).isEqualTo(501)
        assertThat(PaneSplit.positionPx(1f, 1000)).isEqualTo(1000)
    }

    @Test
    fun `the drag clamp stops at the floors and mirrors the library's truncation`() {
        val clamp = SplitDragClamp()
        // Not begun: passes deltas through untouched.
        assertThat(clamp.consume(-500f, 300, 700)).isEqualTo(-500f)

        clamp.begin(500)
        assertThat(clamp.consume(-150f, 300, 700)).isEqualTo(-150f) // at 350
        assertThat(clamp.consume(-150f, 300, 700)).isEqualTo(-50f) // held at 300
        assertThat(clamp.consume(-10f, 300, 700)).isEqualTo(0f)
        assertThat(clamp.consume(10.7f, 300, 700)).isWithin(1e-4f).of(10.7f) // library lands on 310
        assertThat(clamp.consume(500f, 300, 700)).isEqualTo(390f) // from 310 to the 700 cap
        assertThat(clamp.consume(1f, 300, 700)).isEqualTo(0f)

        // A new gesture re-syncs from the settled anchor.
        clamp.begin(400)
        assertThat(clamp.consume(-200f, 300, 700)).isEqualTo(-100f)
    }

    @Test
    fun `the drag clamp lets go when the bounds are inverted`() {
        val clamp = SplitDragClamp()
        clamp.begin(500)
        assertThat(clamp.consume(-50f, 700, 300)).isEqualTo(-50f)
    }
}
