package com.scanrift.android.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs

/**
 * Rotates a seat's whole tile so the player it faces reads it upright.
 *
 * For a quarter turn the child has to be measured with **swapped** constraints — it
 * ends up rendered sideways, so its "width" is the parent's height. That is the same
 * trick iOS uses; the difference is that iOS needs two stacked `GeometryReader`s to get
 * z-ordering right, while Compose inverse-transforms pointer input through
 * `graphicsLayer`, so one layer both draws and hit-tests correctly.
 *
 * That last point is worth verifying on hardware: taps landing on the wrong button in
 * the rotated four-player seats is the most likely bug in this screen.
 */
@Composable
fun RotatedContent(
    degrees: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isQuarterTurn = abs(abs(degrees % 180f) - 90f) < 0.5f

    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val measurable = measurables.firstOrNull()
            ?: return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}

        val innerConstraints = if (isQuarterTurn) {
            Constraints.fixed(constraints.maxHeight, constraints.maxWidth)
        } else {
            Constraints.fixed(constraints.maxWidth, constraints.maxHeight)
        }

        val placeable = measurable.measure(innerConstraints)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeWithLayer(
                x = (constraints.maxWidth - placeable.width) / 2,
                y = (constraints.maxHeight - placeable.height) / 2,
            ) {
                rotationZ = degrees
            }
        }
    }
}
