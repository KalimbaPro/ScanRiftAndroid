package com.scanrift.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.Card
import com.scanrift.android.ui.theme.Dimens
import com.scanrift.android.ui.theme.Motion
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val TILT_DISTANCE = 150f
private const val MAX_TILT_DEGREES = 15f

@Composable
fun TiltingCardImage(card: Card, maxWidth: Dp, isOwned: Boolean = true, isFoil: Boolean = false) {
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(12.dp)
    val release: () -> Unit = { scope.launch { drag.animateTo(Offset.Zero, Motion.cardTilt()) } }

    CardThumbnail(
        card = card,
        quantity = if (isOwned) 1 else 0,
        isFoil = isFoil,
        cornerRadius = 12.dp,
        showQuantityBadge = false,
        modifier = Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .aspectRatio(if (card.isLandscape) 1f / Dimens.CARD_ASPECT_RATIO else Dimens.CARD_ASPECT_RATIO)
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = release, onDragCancel = release) { change, amount ->
                    change.consume()
                    scope.launch { drag.snapTo(drag.value + amount / density) }
                }
            }
            .graphicsLayer {
                val (dx, dy) = drag.value
                rotationX = -(dy / TILT_DISTANCE).coerceIn(-1f, 1f) * MAX_TILT_DEGREES
                rotationY = (dx / TILT_DISTANCE).coerceIn(-1f, 1f) * MAX_TILT_DEGREES
            }
            .dropShadow(shape) {
                val (dx, dy) = drag.value
                color = Color.Black
                alpha = 0.3f
                radius = (8f + abs(dx / 20f)) * density
                offset = Offset(-dx / 20f * density, -dy / 20f * density)
            },
    )
}
