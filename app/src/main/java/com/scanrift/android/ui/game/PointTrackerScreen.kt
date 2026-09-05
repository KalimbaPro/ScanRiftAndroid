package com.scanrift.android.ui.game

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.domain.model.ScoreCategory
import com.scanrift.android.ui.LocalImmersiveMode
import com.scanrift.android.ui.theme.Dimens
import com.scanrift.android.ui.theme.ScoreAbility
import com.scanrift.android.ui.theme.ScoreConquer
import com.scanrift.android.ui.theme.ScoreHold
import com.scanrift.android.ui.theme.domainColor
import com.scanrift.android.ui.util.tapOrLongPress
import kotlin.math.max
import kotlin.math.min

/**
 * The Game tab.
 *
 * Entirely new on Android — the previous build had no equivalent at all.
 */
@Composable
fun PointTrackerScreen(viewModel: PointTrackerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val immersive = LocalImmersiveMode.current
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var showSave by rememberSaveable { mutableStateOf(false) }

    // Hide the system bars *and* the app's navigation container in fullscreen, and
    // keep the screen awake — a 45-minute game shouldn't dim, which is an
    // Android-only improvement over iOS.
    ImmersiveEffect(enabled = state.isFullScreen)
    KeepScreenOn()

    DisposableEffect(state.isFullScreen) {
        immersive.value = state.isFullScreen
        onDispose { immersive.value = false }
    }

    SeatLayout(
        playerCount = state.players.size,
        modifier = Modifier.fillMaxSize(),
        centerBar = {
            CenterControlBar(
                playerCount = state.players.size,
                isFullScreen = state.isFullScreen,
                canSave = state.recordablePlayers.isNotEmpty(),
                onReset = { confirmReset = true },
                onRandomize = viewModel::randomizeFirstPlayer,
                onToggleFullScreen = { viewModel.setFullScreen(!state.isFullScreen) },
                onSave = { showSave = true },
                onChangePlayerCount = viewModel::setPlayerCount,
            )
        },
        seat = { index, rotation, legendRotation ->
            val player = state.players.getOrNull(index)
            if (player != null) {
                PlayerSeat(
                    player = player,
                    index = index,
                    rotation = rotation,
                    legendRotation = legendRotation,
                    isStarting = state.startingPlayerIndex == index && !state.isRandomizing,
                    isCandidate = state.startingPlayerIndex == index && state.isRandomizing,
                    isFullBleed = state.isFullScreen,
                    legendDomain = player.legendCardId
                        ?.let { state.legendsById[it] }?.domains?.firstOrNull(),
                    onAdd = { category -> viewModel.addPoint(index, category) },
                    onRemove = { category -> viewModel.removePoint(index, category) },
                )
            }
        },
    )

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset scores?") },
            text = { Text("Every player goes back to zero.") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetCounters(); confirmReset = false }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }

    if (showSave) {
        SaveGameDialog(
            onDismiss = { showSave = false },
            onSave = { name -> viewModel.saveGame(name) { showSave = false } },
        )
    }
}

@Composable
private fun SaveGameDialog(onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this game?") },
        text = {
            Column {
                Text("One record is saved per player bound to a deck or a legend.")
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Game name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.ifBlank { null }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Skip") } },
    )
}

@Composable
private fun CenterControlBar(
    playerCount: Int,
    isFullScreen: Boolean,
    canSave: Boolean,
    onReset: () -> Unit,
    onRandomize: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onSave: () -> Unit,
    onChangePlayerCount: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onReset) { Icon(Icons.Filled.Refresh, contentDescription = "Reset scores") }

        // Tap cycles 2 → 3 → 4 → 2, which is quicker than opening a sheet for three options.
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .clip(CircleShape)
                .clickable { onChangePlayerCount(if (playerCount >= 4) 2 else playerCount + 1) },
            contentAlignment = Alignment.Center,
        ) {
            Text("$playerCount", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        IconButton(onClick = onRandomize) { Icon(Icons.Filled.Casino, contentDescription = "Random first player") }
        IconButton(onClick = onToggleFullScreen) {
            Icon(
                imageVector = if (isFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = "Toggle fullscreen",
            )
        }
        if (canSave) {
            IconButton(onClick = onSave) { Icon(Icons.Filled.SaveAlt, contentDescription = "Save game") }
        }
    }
}

@Composable
private fun PlayerSeat(
    player: PlayerState,
    index: Int,
    rotation: Float,
    legendRotation: Float,
    isStarting: Boolean,
    isCandidate: Boolean,
    isFullBleed: Boolean,
    legendDomain: String?,
    onAdd: (ScoreCategory) -> Unit,
    onRemove: (ScoreCategory) -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isStarting -> Color(0xFFFFD60A)
            isCandidate -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        },
        label = "seatBorder",
    )
    val tint = legendDomain?.let { domainColor(it) } ?: MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(if (isFullBleed) 0.dp else 18.dp)

    // One rotation layer wrapping everything — Compose hit-tests through graphicsLayer,
    // so no second layer is needed for the buttons to receive taps.
    RotatedContent(degrees = rotation, modifier = Modifier.fillMaxSize()) {
        RotatedContent(degrees = legendRotation, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isFullBleed) 0.dp else 6.dp)
                    .clip(shape)
                    .background(
                        Brush.linearGradient(listOf(tint.copy(alpha = 0.35f), tint.copy(alpha = 0.15f))),
                    )
                    .border(if (borderColor == Color.Transparent) 0.dp else 4.dp, borderColor, shape),
            ) {
                val minDimension = min(maxWidth.value, maxHeight.value)
                val buttonDiameter = min(60f, max(40f, minDimension * 0.16f)).dp
                val scoreSize = max(56f, minDimension * 0.45f).sp

                Text(
                    text = player.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                )

                Text(
                    text = "${player.score}",
                    fontSize = scoreSize,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    // The score deliberately does not animate; iOS kills its animation
                    // explicitly. The per-category counts below do animate.
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = buttonDiameter + 36.dp),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(buttonDiameter * 0.45f),
                ) {
                    ScoreCategory.entries.forEach { category ->
                        CategoryScoreButton(
                            category = category,
                            count = player.count(category),
                            diameter = buttonDiameter,
                            onAdd = { onAdd(category) },
                            onRemove = { onRemove(category) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tap adds a point, long-press removes one.
 *
 * The long-press has to fire at 400ms, but `combinedClickable` is hardwired to the
 * system timeout (500ms), so this drives its own gesture loop.
 */
@Composable
private fun CategoryScoreButton(
    category: ScoreCategory,
    count: Int,
    diameter: androidx.compose.ui.unit.Dp,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val color = when (category) {
        ScoreCategory.CONQUER -> ScoreConquer
        ScoreCategory.HOLD -> ScoreHold
        ScoreCategory.ABILITY -> ScoreAbility
    }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = com.scanrift.android.ui.theme.Motion.press(),
        label = "pressScale",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(diameter)
                .scale(scale)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.75f))))
                .tapOrLongPress(
                    longPressMs = Dimens.LONG_PRESS_SCORE_DECREMENT_MS,
                    onTap = onAdd,
                    onLongPress = onRemove,
                    onPressChange = { pressed = it },
                )
                // TalkBack cannot perform a long press, so the decrement needs an
                // explicit action or it is unreachable.
                .semantics {
                    contentDescription = "${category.displayName}, $count points"
                    customActions = listOf(
                        CustomAccessibilityAction("Add point") { onAdd(); true },
                        CustomAccessibilityAction("Remove point") { onRemove(); true },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$count",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (diameter.value * 0.36f).sp,
            )
        }
        Text(
            text = category.displayName.uppercase(),
            color = Color.White,
            fontSize = max(9f, diameter.value * 0.18f).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ImmersiveEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (enabled) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
