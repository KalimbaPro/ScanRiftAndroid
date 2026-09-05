package com.scanrift.android.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanrift.android.ui.theme.Dimens

/**
 * Width classes measured **per pane**, not per window.
 *
 * This is the central rule of the adaptive layout, and the easiest thing to get wrong.
 * At Medium *window* width the app shows two panes, so the detail pane is only ~350dp
 * across. A component that branched on the window class would try to render the
 * two-column card detail inside that. So every layout decision inside a pane keys off
 * the pane's own measured width, and only the navigation container and the pane count
 * key off the window.
 */
enum class ContentWidth {
    /** Phone-shaped: below 600dp. */
    Compact,

    /** 600-839dp. An unfolded Fold in portrait lands here. */
    Medium,

    /** 840dp and up: tablets, and an unfolded Fold in landscape. */
    Expanded,
    ;

    val isAtLeastMedium: Boolean get() = this != Compact
    val isExpanded: Boolean get() = this == Expanded

    companion object {
        fun fromWidth(width: Dp): ContentWidth = when {
            width < 600.dp -> Compact
            width < 840.dp -> Medium
            else -> Expanded
        }
    }
}

/**
 * Defaults to Compact so a composable rendered outside a provider degrades to the
 * phone layout rather than the tablet one.
 */
val LocalContentWidth = compositionLocalOf { ContentWidth.Compact }

/** Place at the top of **each pane**, not once at the root. */
@Composable
fun ProvideContentWidth(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        CompositionLocalProvider(
            LocalContentWidth provides ContentWidth.fromWidth(maxWidth),
            content = content,
        )
    }
}

/**
 * Per-screen thresholds.
 *
 * Deliberately not all the same number — each is the width at which that particular
 * layout stops being cramped, which is a different question per screen.
 */
object AdaptiveRules {

    /** Collection grid switches to a fixed 6 columns. */
    fun useFixedGridColumns(paneWidth: Dp): Boolean = paneWidth >= Dimens.TwoPaneMinWidth

    /**
     * Card detail becomes image-beside-info at 700dp, not 600: at 600 the image takes
     * 400 and the info column is left with 200dp, which is unreadable.
     */
    fun useTwoColumnCardDetail(paneWidth: Dp): Boolean = paneWidth >= Dimens.CardDetailTwoColumnMinWidth

    /** Deck builder shows browser and editor side by side. */
    fun useSplitDeckBuilder(paneWidth: Dp): Boolean = paneWidth >= Dimens.DeckBuilderSplitMinWidth

    /**
     * Scanner gains its 360dp session sidebar only at 840dp. At 700dp the sidebar
     * would leave 340dp of camera, less than the 300x420 guide rect plus margins — so
     * an unfolded Fold in portrait keeps the compact scanner and gets the sidebar only
     * in landscape.
     */
    fun useScannerSidebar(windowWidth: Dp): Boolean = windowWidth >= Dimens.ScannerSidebarMinWidth

    /** The larger scan guide is independent of the sidebar. */
    fun useLargeScanGuide(width: Dp, height: Dp): Boolean =
        minOf(width, height) >= Dimens.LargeScanGuideMinDimension

    /** Columns for the collection grid at a given pane width. */
    fun gridColumnCount(paneWidth: Dp): Int {
        if (useFixedGridColumns(paneWidth)) return Dimens.GRID_COLUMNS_REGULAR
        val available = paneWidth - 32.dp + Dimens.GridSpacingCompact
        val perItem = Dimens.GridItemMin + Dimens.GridSpacingCompact
        val computed = (available / perItem).toInt()
        return computed.coerceIn(Dimens.GRID_COLUMNS_MIN, Dimens.GRID_COLUMNS_MAX)
    }
}
