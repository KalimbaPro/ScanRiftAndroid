package com.scanrift.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun FoilOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "foil")

    val offsetX by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "foilOffset"
    )

    val rainbowColors = listOf(
        Color.Red.copy(alpha = 0.15f),
        Color.Yellow.copy(alpha = 0.15f),
        Color.Green.copy(alpha = 0.15f),
        Color.Cyan.copy(alpha = 0.15f),
        Color.Blue.copy(alpha = 0.15f),
        Color.Magenta.copy(alpha = 0.15f),
        Color.Red.copy(alpha = 0.15f)
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val brushWidth = size.width * 1.5f
        val startX = size.width * offsetX
        val gradient = Brush.linearGradient(
            colors = rainbowColors,
            start = Offset(startX, 0f),
            end = Offset(startX + brushWidth, size.height)
        )
        drawRect(
            brush = gradient,
            blendMode = BlendMode.Plus
        )
    }
}
