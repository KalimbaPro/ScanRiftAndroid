package com.scanrift.android.ui.scanner

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanrift.android.ui.theme.Motion

/**
 * The scan guide.
 *
 * iOS draws live card edges from `VNDetectRectanglesRequest`; ML Kit has no equivalent
 * and OpenCV would add ~10MB per ABI for what is a decorative overlay. So this is the
 * static guide iOS falls back to when no rectangle is found, tinted by scanner state —
 * which carries the same "hold the card here, it's locked on" affordance for free.
 */
@Composable
fun CardDetectionOverlay(
    state: ScannerOverlayState,
    guideWidth: Dp,
    guideHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val targetColor = when (state) {
        ScannerOverlayState.IDLE, ScannerOverlayState.DETECTING -> Color.White.copy(alpha = 0.6f)
        ScannerOverlayState.PROCESSING -> Color(0xFFFFD60A).copy(alpha = 0.9f)
        ScannerOverlayState.MATCHED -> Color(0xFF34C759)
        ScannerOverlayState.ERROR -> Color(0xFFFF3B30)
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(Motion.OVERLAY_STATE_MS),
        label = "guideColor",
    )

    Canvas(modifier.fillMaxSize()) {
        val w = guideWidth.toPx().coerceAtMost(size.width * 0.9f)
        val h = guideHeight.toPx().coerceAtMost(size.height * 0.8f)
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val armLength = w * 0.15f
        val strokeWidth = 3.dp.toPx()

        // Dim everything outside the guide so the eye goes to the card.
        val scrim = Color.Black.copy(alpha = 0.45f)
        drawRect(scrim, size = Size(size.width, top))
        drawRect(scrim, topLeft = Offset(0f, top + h), size = Size(size.width, size.height - top - h))
        drawRect(scrim, topLeft = Offset(0f, top), size = Size(left, h))
        drawRect(scrim, topLeft = Offset(left + w, top), size = Size(size.width - left - w, h))

        // Corner brackets rather than a full outline, so the card stays unobscured.
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(color, Offset(x, y), Offset(x + dx * armLength, y), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(x, y), Offset(x, y + dy * armLength), strokeWidth, StrokeCap.Round)
        }
        corner(left, top, 1f, 1f)
        corner(left + w, top, -1f, 1f)
        corner(left, top + h, 1f, -1f)
        corner(left + w, top + h, -1f, -1f)
    }
}
