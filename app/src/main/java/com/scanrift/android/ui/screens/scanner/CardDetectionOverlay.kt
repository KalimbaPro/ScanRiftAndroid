package com.scanrift.android.ui.screens.scanner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

enum class ScannerOverlayState {
    IDLE,
    DETECTING,
    MATCHED,
    ERROR
}

@Composable
fun CardDetectionOverlay(
    state: ScannerOverlayState = ScannerOverlayState.IDLE,
    modifier: Modifier = Modifier
) {
    val targetColor = when (state) {
        ScannerOverlayState.IDLE -> Color.White.copy(alpha = 0.6f)
        ScannerOverlayState.DETECTING -> Color(0xFFFFD60A).copy(alpha = 0.8f) // Yellow
        ScannerOverlayState.MATCHED -> Color(0xFF34C759).copy(alpha = 0.9f) // Green
        ScannerOverlayState.ERROR -> Color(0xFFFF3B30).copy(alpha = 0.8f) // Red
    }

    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 300),
        label = "overlayColor"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val cardAspectRatio = 0.716f // Standard trading card ratio (width/height)
        val padding = size.minDimension * 0.08f

        // Calculate card rect centered in view
        val availableWidth = size.width - padding * 2
        val availableHeight = size.height - padding * 2

        val cardWidth: Float
        val cardHeight: Float
        if (availableWidth / availableHeight < cardAspectRatio) {
            cardWidth = availableWidth * 0.85f
            cardHeight = cardWidth / cardAspectRatio
        } else {
            cardHeight = availableHeight * 0.65f
            cardWidth = cardHeight * cardAspectRatio
        }

        val left = (size.width - cardWidth) / 2f
        val top = (size.height - cardHeight) / 2f

        val cornerRadius = cardWidth * 0.04f
        val strokeWidth = 3f
        val bracketLength = cardWidth * 0.15f

        // Draw rounded rectangle outline
        drawRoundRect(
            color = color.copy(alpha = color.alpha * 0.3f),
            topLeft = Offset(left, top),
            size = Size(cardWidth, cardHeight),
            cornerRadius = CornerRadius(cornerRadius),
            style = Stroke(width = strokeWidth)
        )

        // Draw corner brackets
        val bracketStrokeWidth = 4f

        // Top-left corner
        drawLine(
            color = color,
            start = Offset(left, top + cornerRadius),
            end = Offset(left, top + bracketLength),
            strokeWidth = bracketStrokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(left + cornerRadius, top),
            end = Offset(left + bracketLength, top),
            strokeWidth = bracketStrokeWidth,
            cap = StrokeCap.Round
        )

        // Top-right corner
        drawLine(
            color = color,
            start = Offset(left + cardWidth, top + cornerRadius),
            end = Offset(left + cardWidth, top + bracketLength),
            strokeWidth = bracketStrokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(left + cardWidth - cornerRadius, top),
            end = Offset(left + cardWidth - bracketLength, top),
            strokeWidth = bracketStrokeWidth,
            cap = StrokeCap.Round
        )

        // Bottom-left corner
        drawLine(
            color = color,
            start = Offset(left, top + cardHeight - cornerRadius),
            end = Offset(left, top + cardHeight - bracketLength),
            strokeWidth = bracketStrokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(left + cornerRadius, top + cardHeight),
            end = Offset(left + bracketLength, top + cardHeight),
            strokeWidth = bracketStrokeWidth,
            cap = StrokeCap.Round
        )

        // Bottom-right corner
        drawLine(
            color = color,
            start = Offset(left + cardWidth, top + cardHeight - cornerRadius),
            end = Offset(left + cardWidth, top + cardHeight - bracketLength),
            strokeWidth = bracketStrokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(left + cardWidth - cornerRadius, top + cardHeight),
            end = Offset(left + cardWidth - bracketLength, top + cardHeight),
            strokeWidth = bracketStrokeWidth,
            cap = StrokeCap.Round
        )
    }
}
