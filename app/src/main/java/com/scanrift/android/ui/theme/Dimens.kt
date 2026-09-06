package com.scanrift.android.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Layout constants shared across screens.
 *
 * The width thresholds encode the plan's central adaptive rule: they are compared
 * against the width of the *pane* a composable lives in, not the window, because at
 * Medium window width a detail pane is only ~350dp across.
 */
object Dimens {
    /** Physical card aspect ratio (width / height). Matches iOS. */
    const val CARD_ASPECT_RATIO = 0.716f

    // Collection grid
    val GridItemMin = 100.dp
    val GridItemMax = 150.dp
    val GridSpacingCompact = 12.dp
    val GridSpacingRegular = 14.dp
    const val GRID_COLUMNS_REGULAR = 6
    const val GRID_COLUMNS_MIN = 2

    /** Guard against absurdly thin tiles in desktop-sized windows. */
    const val GRID_COLUMNS_MAX = 8

    // Hub / deck grids use an adaptive cell rather than a fixed count.
    val HubGridItemMin = 160.dp
    val HubGridItemMax = 250.dp

    // Card detail
    val DetailImageMax = 400.dp

    // Scanner
    val ScannerSidebarWidth = 360.dp
    val ScanGuideCompactWidth = 250.dp
    val ScanGuideCompactHeight = 350.dp
    val ScanGuideRegularWidth = 300.dp
    val ScanGuideRegularHeight = 420.dp

    // Content width caps, so nothing stretches absurdly on a tablet.
    val SettingsMaxWidth = 640.dp
    val SheetMaxWidth = 640.dp
    val CardDetailMaxWidth = 600.dp
    val DeckEditorMaxWidth = 700.dp
    val SessionSummaryMaxWidth = 720.dp

    /**
     * Pane-width thresholds. Deliberately not all the same number — see the plan.
     */
    val TwoPaneMinWidth = 600.dp
    val CardDetailTwoColumnMinWidth = 700.dp
    val DeckBuilderSplitMinWidth = 600.dp
    val ScannerSidebarMinWidth = 840.dp
    val LargeScanGuideMinDimension = 600.dp

    // Interaction timings, in milliseconds.
    const val LONG_PRESS_MULTI_SELECT_MS = 300L
    const val LONG_PRESS_SCORE_DECREMENT_MS = 400L
}
