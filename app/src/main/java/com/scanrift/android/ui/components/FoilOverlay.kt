package com.scanrift.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanrift.android.ui.theme.Motion

/**
 * The animated foil sheen.
 *
 * **One clock for the whole app.** Every `rememberInfiniteTransition` is its own
 * animation clock, and the collection grid can show thirty foil cards at once —
 * thirty clocks each invalidating a Canvas every frame is the most likely source of
 * scroll jank here. The phase is hoisted to the root instead, and read *inside* the
 * `drawWithContent` lambda, where reading a State re-runs only the draw phase and
 * skips recomposition entirely.
 */
val LocalFoilPhase: androidx.compose.runtime.ProvidableCompositionLocal<State<Float>> =
    staticCompositionLocalOf { mutableFloatStateOf(0.5f) }

@Composable
fun ProvideFoilPhase(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "foil")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.FOIL_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "foilPhase",
    )
    CompositionLocalProvider(LocalFoilPhase provides phase, content = content)
}

private val SheenColors = listOf(
    Color(0xFFFF2D55).copy(alpha = 0.30f),
    Color(0xFF007AFF).copy(alpha = 0.25f),
    Color(0xFF34C759).copy(alpha = 0.20f),
    Color(0xFFFFD60A).copy(alpha = 0.25f),
    Color(0xFFAF52DE).copy(alpha = 0.30f),
    Color(0xFFFF2D55).copy(alpha = 0.30f),
)

/**
 * Draws the sheen over whatever this modifier is applied to.
 *
 * @param minAnimatedWidth below this the sheen is static. At grid-thumbnail scale the
 *   animation is invisible, so it would be pure cost.
 */
@Composable
fun Modifier.foilSheen(
    enabled: Boolean = true,
    minAnimatedWidth: Dp = 120.dp,
): Modifier {
    if (!enabled) return this
    val phase = LocalFoilPhase.current
    val minWidth = minAnimatedWidth
    return this
        // BlendMode.Plus needs an offscreen layer to composite predictably.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val value = if (size.width < minWidth.toPx()) 0.5f else phase.value
            drawRect(
                brush = Brush.linearGradient(
                    colors = SheenColors,
                    start = Offset(value * size.width, 0f),
                    end = Offset((value + 0.5f) * size.width, size.height),
                ),
                blendMode = BlendMode.Plus,
            )
        }
}
