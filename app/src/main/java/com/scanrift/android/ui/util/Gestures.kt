package com.scanrift.android.ui.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tap versus long-press with a caller-chosen threshold.
 *
 * `combinedClickable` is hardwired to `ViewConfiguration.longPressTimeoutMillis`
 * (500ms), and this app needs two different, shorter durations: 300ms to enter
 * multi-select in the collection, and 400ms to decrement a score category. Hence a
 * hand-rolled gesture loop.
 *
 * @param onPressChange drives press-state visuals such as the 0.85 scale.
 */
fun Modifier.tapOrLongPress(
    longPressMs: Long,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onPressChange: (Boolean) -> Unit = {},
): Modifier = pointerInput(longPressMs) {
    awaitEachGesture {
        awaitFirstDown()
        onPressChange(true)

        val up = withTimeoutOrNull(longPressMs) { waitForUpOrCancellation() }
        onPressChange(false)

        if (up == null) {
            onLongPress()
            // Swallow the eventual release so it doesn't also register as a tap.
            waitForUpOrCancellation()
        } else {
            onTap()
        }
    }
}
