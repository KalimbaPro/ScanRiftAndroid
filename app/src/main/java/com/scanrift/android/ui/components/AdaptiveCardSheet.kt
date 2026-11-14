package com.scanrift.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanrift.android.ui.adaptive.AdaptiveRules
import com.scanrift.android.ui.theme.Dimens

/**
 * Whether a single-card sheet should open as a centred pop-up rather than a bottom sheet.
 *
 * This is one of the few decisions keyed off the **window**, not the pane: a sheet or a
 * dialog is laid over the whole window, so the pane it was opened from is irrelevant.
 */
@Composable
fun shouldShowCardPopUp(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return with(density) { AdaptiveRules.useCardPopUp(size.width.toDp(), size.height.toDp()) }
}

/**
 * The container for any sheet that shows one card.
 *
 * On a wide window (unfolded Fold, tablet) a bottom sheet spans the screen, covers the
 * content it was opened from and opens half-expanded, which crops the card art. There
 * it becomes a centred [Dialog] instead: width capped at [Dimens.CardPopUpMaxWidth],
 * height wrapping the content, dimmed scrim, dismissed by an outside tap or back. On a
 * compact window it stays the [ModalBottomSheet] a phone user expects.
 *
 * [content] runs in a [ColumnScope] in both branches, so `Modifier.weight(1f, fill =
 * false)` on a list inside it works either way: the dialog bounds the column by the
 * window height just as the sheet does. Put the card image first and let the details
 * scroll, so the whole card shows without scrolling.
 *
 * Not for settings, filter or import sheets: those are forms, not cards, and stay sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveCardSheet(
    onDismiss: () -> Unit,
    skipPartiallyExpanded: Boolean = false,
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    popUp: Boolean = shouldShowCardPopUp(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (popUp) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .padding(Dimens.CardPopUpMargin)
                    .widthIn(max = Dimens.CardPopUpMaxWidth)
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 8.dp), content = content)
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
            sheetMaxWidth = sheetMaxWidth,
            content = content,
        )
    }
}
