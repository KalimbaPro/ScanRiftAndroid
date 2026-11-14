package com.scanrift.android.ui.adaptive

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanrift.android.ui.theme.Dimens
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The pure arithmetic behind the collection's draggable two-pane split.
 *
 * A split is the **list pane's share of the scaffold width**, measured to the centre of
 * the divider — the same thing `PaneExpansionAnchor.Proportion` means. The list pane is
 * then `share * width - spacer / 2` wide and the detail pane gets the rest minus the
 * other half of the spacer.
 *
 * Anchors are recomputed from the width every time it changes, so neither pane can be
 * left under its floor ([Dimens.ListPaneMinWidth], [Dimens.DetailPaneMinWidth]) after a
 * rotation or an unfold. The saved share is a preference, not a position: it is mapped to
 * whichever anchor is nearest at the current width, and never rewritten by that mapping.
 */
object PaneSplit {

    /** Even split. */
    const val DEFAULT_PROPORTION = 0.5f

    /** Round-number stops between the floors, 30% to 70% in 5% steps. */
    val STOPS: List<Float> = (6..14).map { round3(it * 0.05f) }

    /**
     * Anchors closer than this to one of the floor anchors are dropped, so the user never
     * gets two stops a couple of dp apart.
     */
    const val MIN_ANCHOR_GAP = 0.02f

    /** A stored share that is not a finite number strictly inside (0, 1) is ignored. */
    fun sanitize(value: Float?): Float? = value?.takeIf { it.isFinite() && it > 0f && it < 1f }

    fun storedOrDefault(value: Float?): Float = sanitize(value) ?: DEFAULT_PROPORTION

    /**
     * The library measures shares from the **left** edge, but in a right-to-left layout
     * the list pane is on the right. Converts a list share to a left-pane share and back.
     */
    fun mirror(proportion: Float): Float = round3(1f - proportion)

    /**
     * The shares that keep both panes at or above their floors, or null when the width
     * cannot hold both floors plus the spacer. Rounded inward to three decimals so a
     * rounded anchor never lands a fraction of a dp under a floor.
     */
    fun proportionRange(
        totalWidth: Dp,
        spacer: Dp,
        minLeft: Dp = Dimens.ListPaneMinWidth,
        minRight: Dp = Dimens.DetailPaneMinWidth,
    ): ClosedFloatingPointRange<Float>? {
        if (totalWidth <= 0.dp) return null
        val half = spacer / 2
        val low = ceil3((minLeft + half) / totalWidth)
        val high = floor3((totalWidth - minRight - half) / totalWidth)
        return if (low <= high) low..high else null
    }

    /**
     * Whether [totalWidth] holds both panes at their floors plus the spacer. Below this
     * the collection is single-pane: a list squeezed under its floor wraps its title a
     * syllable per line, and an unresizable divider is worse than no divider.
     */
    fun fitsTwoPanes(
        totalWidth: Dp,
        spacer: Dp,
        minLeft: Dp = Dimens.ListPaneMinWidth,
        minRight: Dp = Dimens.DetailPaneMinWidth,
    ): Boolean = proportionRange(totalWidth, spacer, minLeft, minRight) != null

    /**
     * Caps the number of panes for the width actually available. [maxPartitions] is what
     * the window-based directive asked for; the result is 1 when two panes would not fit.
     */
    fun paneCount(maxPartitions: Int, totalWidth: Dp, spacer: Dp): Int =
        if (maxPartitions > 1 && !fitsTwoPanes(totalWidth, spacer)) 1 else maxPartitions

    /** [directive] with [paneCount] applied. */
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    fun directiveFor(directive: PaneScaffoldDirective, totalWidth: Dp): PaneScaffoldDirective {
        val panes = paneCount(directive.maxHorizontalPartitions, totalWidth, directive.horizontalPartitionSpacerSize)
        return if (panes == directive.maxHorizontalPartitions) directive else directive.copy(maxHorizontalPartitions = panes)
    }

    /**
     * Anchors for a width, as left-pane shares: the two floors plus every round stop strictly between them.
     * Fewer than two anchors means the split is not resizable at this width.
     */
    fun anchors(
        totalWidth: Dp,
        spacer: Dp,
        minLeft: Dp = Dimens.ListPaneMinWidth,
        minRight: Dp = Dimens.DetailPaneMinWidth,
    ): List<Float> {
        val range = proportionRange(totalWidth, spacer, minLeft, minRight) ?: return emptyList()
        val low = range.start
        val high = range.endInclusive
        if (high - low < MIN_ANCHOR_GAP) return emptyList()
        val inner = STOPS.filter { it - low >= MIN_ANCHOR_GAP && high - it >= MIN_ANCHOR_GAP }
        return listOf(low) + inner + high
    }

    fun isResizable(anchors: List<Float>): Boolean = anchors.size >= 2

    /** Index of the anchor nearest [target]; -1 when there are none. */
    fun nearestIndex(anchors: List<Float>, target: Float): Int =
        anchors.indices.minByOrNull { abs(anchors[it] - target) } ?: -1

    /** The anchor a saved share restores to at this width, or null when there are none. */
    fun restoreTarget(anchors: List<Float>, stored: Float): Float? =
        anchors.getOrNull(nearestIndex(anchors, stored))

    /**
     * What to save when the settled anchor changes, or null to save nothing.
     *
     * A change to exactly the anchor the screen restores [stored] to is the screen's own
     * doing — a launch, an unfold, a rotation — and saving it would overwrite a 30%
     * chosen on a wide screen with the 42% that is the closest a narrow one can manage.
     * Anything else came from the user. [stored] null means the preference has not loaded
     * yet, and nothing is saved until it has.
     */
    fun toPersist(settled: Float?, anchors: List<Float>, stored: Float?): Float? {
        if (settled == null || stored == null || settled !in anchors) return null
        return if (settled == restoreTarget(anchors, stored)) null else settled
    }

    /** The anchor [steps] away from [current], clamped to the ends; for keys and actions. */
    fun step(anchors: List<Float>, current: Float?, steps: Int): Float? {
        if (anchors.isEmpty()) return null
        val from = if (current == null) nearestIndex(anchors, DEFAULT_PROPORTION) else nearestIndex(anchors, current)
        return anchors[(from + steps).coerceIn(0, anchors.lastIndex)]
    }

    /** Where the library puts the divider's centre for a share, in px (its own rounding). */
    fun positionPx(proportion: Float, totalPx: Int): Int =
        (totalPx * proportion).roundToInt().coerceIn(0, totalPx)

    private fun round3(value: Float): Float = (value * 1000f).roundToInt() / 1000f
    private fun ceil3(value: Float): Float = ceil(value * 1000f - 1e-3f) / 1000f
    private fun floor3(value: Float): Float = floor(value * 1000f + 1e-3f) / 1000f
}

/**
 * Keeps a divider drag inside `[minPx, maxPx]`.
 *
 * `PaneExpansionState` only coerces a drag to `[0, width]`, so dragging all the way
 * collapses a pane, and the only hook it offers is `consumeDragDelta`, which sees deltas
 * but never the position. This mirrors the position instead: [begin] at the start of
 * each gesture from the settled anchor, then every delta advanced exactly as the library
 * advances it — `(position + delta).toInt()` — so the two stay in lockstep.
 */
class SplitDragClamp {
    private var position: Int = UNSET

    fun begin(positionPx: Int) {
        position = positionPx
    }

    /** Returns the part of [delta] the library should apply. */
    fun consume(delta: Float, minPx: Int, maxPx: Int): Float {
        if (position == UNSET || minPx > maxPx) return delta
        val target = (position + delta).coerceIn(minPx.toFloat(), maxPx.toFloat())
        val applied = target - position
        position = (position + applied).toInt()
        return applied
    }

    private companion object {
        const val UNSET = Int.MIN_VALUE
    }
}
