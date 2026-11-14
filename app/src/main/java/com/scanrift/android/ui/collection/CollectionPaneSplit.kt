package com.scanrift.android.ui.collection

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import com.scanrift.android.core.log.Log
import com.scanrift.android.ui.adaptive.PaneSplit
import com.scanrift.android.ui.adaptive.SplitDragClamp
import com.scanrift.android.ui.theme.Dimens
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The collection's draggable divider: the adaptive library's own pane expansion, fed
 * width-dependent anchors, a drag clamp, and a saved preference.
 *
 * Everything here is in the library's terms — shares of the **left** pane, measured to
 * the divider's centre. [listOnLeft] is false in a right-to-left layout, where the list
 * sits on the right; the saved preference is always the list's share.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Stable
internal class CollectionPaneSplit(
    val state: PaneExpansionState,
    val anchors: List<Float>,
    private val expansionAnchors: List<PaneExpansionAnchor>,
    val listOnLeft: Boolean,
    val clamp: SplitDragClamp,
    private val widthPx: () -> Int,
) {
    val isResizable: Boolean get() = PaneSplit.isResizable(anchors)

    /** The settled left-pane share, or null mid-restore and when single-pane. */
    val current: Float?
        get() = (state.currentAnchor as? PaneExpansionAnchor.Proportion)?.proportion?.takeIf { it in anchors }

    /** The list's settled share as a whole percent, for the accessibility state. */
    val listPercent: Int?
        get() = current?.let { ((if (listOnLeft) it else PaneSplit.mirror(it)) * 100).roundToInt() }

    fun anchorFor(share: Float): PaneExpansionAnchor? = expansionAnchors.getOrNull(anchors.indexOf(share))

    /** The settled divider position, which is where every drag starts from. */
    fun settledPositionPx(): Int? = current?.let { PaneSplit.positionPx(it, widthPx()) }

    fun defaultShare(): Float? =
        PaneSplit.restoreTarget(anchors, leftShare(PaneSplit.DEFAULT_PROPORTION))

    fun leftShare(listShare: Float): Float = if (listOnLeft) listShare else PaneSplit.mirror(listShare)
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun rememberCollectionPaneSplit(
    totalWidth: Dp,
    widthPx: Int,
    spacer: Dp,
    twoPanes: Boolean,
    listOnLeft: Boolean,
    storedListShare: Float?,
    onPersist: (Float) -> Unit,
): CollectionPaneSplit {
    val anchors = remember(totalWidth, spacer, twoPanes, listOnLeft) {
        if (!twoPanes) {
            emptyList()
        } else if (listOnLeft) {
            PaneSplit.anchors(totalWidth, spacer, Dimens.ListPaneMinWidth, Dimens.DetailPaneMinWidth)
        } else {
            PaneSplit.anchors(totalWidth, spacer, Dimens.DetailPaneMinWidth, Dimens.ListPaneMinWidth)
        }
    }
    val expansionAnchors = remember(anchors) { anchors.map { PaneExpansionAnchor.Proportion(it) } }
    val clamp = remember { SplitDragClamp() }
    val latestAnchors by rememberUpdatedState(anchors)
    val latestWidthPx by rememberUpdatedState(widthPx)
    val latestStored by rememberUpdatedState(storedListShare)
    val latestOnPersist by rememberUpdatedState(onPersist)

    fun toLeft(listShare: Float) = if (listOnLeft) listShare else PaneSplit.mirror(listShare)

    val restoreShare = toLeft(storedListShare ?: PaneSplit.DEFAULT_PROPORTION)
    val state = rememberPaneExpansionState(
        anchors = expansionAnchors,
        initialAnchoredIndex = PaneSplit.nearestIndex(anchors, restoreShare),
        consumeDragDelta = { delta ->
            val bounds = latestAnchors
            if (bounds.size < 2) {
                delta
            } else {
                clamp.consume(
                    delta,
                    PaneSplit.positionPx(bounds.first(), latestWidthPx),
                    PaneSplit.positionPx(bounds.last(), latestWidthPx),
                )
            }
        },
    )
    val split = remember(state, anchors, expansionAnchors, listOnLeft) {
        CollectionPaneSplit(state, anchors, expansionAnchors, listOnLeft, clamp) { latestWidthPx }
    }

    // Restore on launch, unfold and rotation — whenever the anchors change — and once
    // more when DataStore's first read lands, which can be a frame after the first
    // layout. Deliberately not keyed on the stored value itself: saving a drag changes
    // it, and re-animating then would fight the settle animation already running.
    val loaded = storedListShare != null
    LaunchedEffect(split, loaded) {
        if (!split.isResizable) {
            // Single pane, or two panes too narrow for both floors: hand the layout back
            // to the scaffold's default split rather than keep a stale drag offset.
            state.clear()
            return@LaunchedEffect
        }
        val target = PaneSplit.restoreTarget(split.anchors, toLeft(latestStored ?: PaneSplit.DEFAULT_PROPORTION))
        val anchor = target?.let(split::anchorFor) ?: return@LaunchedEffect
        try {
            state.animateTo(anchor)
        } catch (e: IllegalArgumentException) {
            // The state has not taken the new anchor list yet; its own restore applies
            // the same initial anchor, so nothing is lost.
            Log.general.w("Pane split restore skipped: ${e.message}")
        }
    }

    // Persist what the user settles on. The settled anchor also changes when the screen
    // restores, and saving that would overwrite a wide-screen choice with whatever a
    // narrow screen could manage; `toPersist` tells the two apart.
    LaunchedEffect(state) {
        snapshotFlow { (state.currentAnchor as? PaneExpansionAnchor.Proportion)?.proportion }
            .collect { settled ->
                val stored = latestStored ?: return@collect
                val save = PaneSplit.toPersist(settled, latestAnchors, toLeft(stored)) ?: return@collect
                latestOnPersist(toLeft(save))
            }
    }
    return split
}

/**
 * The visible divider. Dragging is the library's; on top of it:
 * - a double-tap resets to the even split,
 * - left/right arrow keys step between anchors once the handle has keyboard focus,
 * - TalkBack gets "Widen list", "Narrow list" and "Reset width" as custom actions.
 *
 * No `onClick` semantics on purpose: `paneExpansionDraggable` turns an `onClick` into a
 * `clickable`, which would make every single tap jump to the next anchor and swallow the
 * first half of a double-tap.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun ThreePaneScaffoldScope.CollectionPaneDragHandle(split: CollectionPaneSplit) {
    val state = split.state
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current
    val latestSplit by rememberUpdatedState(split)

    fun moveTo(share: Float?) {
        val anchor = share?.let(latestSplit::anchorFor) ?: return
        scope.launch { state.animateTo(anchor) }
    }
    fun step(steps: Int) = moveTo(PaneSplit.step(latestSplit.anchors, latestSplit.current, steps))
    fun reset() = moveTo(latestSplit.defaultShare())
    // Widening the list moves the divider away from the list's side.
    val widen = if (split.listOnLeft) 1 else -1
    val percent = split.listPercent

    VerticalDragHandle(
        modifier = Modifier
            // Ahead of the draggable so it sees the press on the Initial pass, before any
            // drag delta: that is where the clamp re-syncs to the settled position. It
            // consumes nothing, so the drag is unaffected; a drag consumes the release,
            // which `waitForUpOrCancellation` reports as null, so a drag is never a tap.
            .pointerInput(Unit) {
                var lastTapUptime = 0L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    latestSplit.settledPositionPx()?.let(latestSplit.clamp::begin)
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    if (up.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) {
                        return@awaitEachGesture
                    }
                    if (down.uptimeMillis - lastTapUptime <= viewConfiguration.doubleTapTimeoutMillis) {
                        lastTapUptime = 0L
                        reset()
                    } else {
                        lastTapUptime = up.uptimeMillis
                    }
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> step(-1).let { true }
                    Key.DirectionRight -> step(1).let { true }
                    Key.MoveHome, Key.Spacebar, Key.Enter -> reset().let { true }
                    else -> false
                }
            }
            .focusable(interactionSource = interactionSource)
            .paneExpansionDraggable(
                state = state,
                minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
                interactionSource = interactionSource,
                semanticsProperties = {
                    contentDescription = "Resize the card list"
                    if (percent != null) stateDescription = "List takes $percent percent of the width"
                    customActions = listOf(
                        CustomAccessibilityAction("Widen list") { step(widen); true },
                        CustomAccessibilityAction("Narrow list") { step(-widen); true },
                        CustomAccessibilityAction("Reset width") { reset(); true },
                    )
                },
            ),
        interactionSource = interactionSource,
    )
}
