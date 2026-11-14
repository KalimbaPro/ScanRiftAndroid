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

    // Legend printing chooser: narrowest tile that still reads as card art.
    val LegendPrintingItemMin = 96.dp
    const val LEGEND_PRINTING_COLUMNS_MIN = 2
    const val LEGEND_PRINTING_COLUMNS_MAX = 6

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
     * Single-card sheets become a centred pop-up on a wide window: see
     * `AdaptiveCardSheet`. Width is capped so the card does not float in a slab of
     * empty surface; the margin keeps the scrim visible on every side.
     */
    val CardPopUpMaxWidth = 560.dp
    val CardPopUpMargin = 24.dp
    val CardPopUpMinWindowWidth = 600.dp

    /** Below this a landscape phone would squash the card into a letterbox: keep the sheet. */
    val CardPopUpMinWindowHeight = 480.dp

    /**
     * Pane-width thresholds. Deliberately not all the same number — see the plan.
     */
    val TwoPaneMinWidth = 600.dp
    val CardDetailTwoColumnMinWidth = 700.dp
    val DeckBuilderSplitMinWidth = 600.dp
    val ScannerSidebarMinWidth = 600.dp
    val LargeScanGuideMinDimension = 600.dp

    /** Thumbnail + a readable name (~180dp) + language, foil, add-to-deck and stepper (~300dp). */
    val ScanRowInlineControlsMinWidth = 520.dp

    /**
     * Floors for the collection's draggable split. Below 260dp the list pane's toolbar
     * wraps; below 300dp the card image in the detail pane gets smaller than the
     * thumbnail it was opened from. Together with the 24dp spacer that is 584dp, which
     * an unfolded Fold held upright (~621dp beside the rail) still clears, so the
     * device the split exists for keeps its two panes, while a 7" tablet in portrait
     * (520dp beside the rail) stays single-pane.
     */
    val ListPaneMinWidth = 260.dp
    val DetailPaneMinWidth = 300.dp

    /**
     * Floating buttons over scrolling content. A list under a FAB pads its end by
     * [FabContentClearance] — the button, the margin below it and the same gap again
     * above it — so the last item can always be scrolled clear of the button.
     */
    val FabSize = 56.dp
    val FabMargin = 16.dp
    val FabContentClearance = FabSize + FabMargin * 2

    /** The card detail's floating quantity stepper sits this far from the pane's bottom-end corner. */
    val QuantityStepperMargin = 24.dp
    val QuantityStepperContentClearance = FabSize + QuantityStepperMargin + FabMargin

    // Interaction timings, in milliseconds.
    const val LONG_PRESS_MULTI_SELECT_MS = 300L
}
