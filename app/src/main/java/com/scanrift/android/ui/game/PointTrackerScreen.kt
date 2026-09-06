package com.scanrift.android.ui.game

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
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
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.core.Constants
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
    var setupSeat by rememberSaveable { mutableStateOf<Int?>(null) }
    val legends by viewModel.legends.collectAsStateWithLifecycle()
    val decks by viewModel.decks.collectAsStateWithLifecycle()

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
        modifier = Modifier
            .fillMaxSize()
            // Fullscreen is meant to run under the system bars; normal mode is not.
            .then(
                if (state.isFullScreen) {
                    Modifier
                } else {
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                },
            ),
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
                    legend = player.legendCardId?.let { state.legendsById[it] },
                    deckName = player.deckId?.let { id -> decks.firstOrNull { it.id == id }?.name },
                    onAdd = { category -> viewModel.addPoint(index, category) },
                    onRemove = { category -> viewModel.removePoint(index, category) },
                    onOpenSetup = { setupSeat = index },
                    onDropPlayer = { draggedId -> viewModel.swapPlayers(draggedId, index) },
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

    setupSeat?.let { seat ->
        state.players.getOrNull(seat)?.let { player ->
            PlayerSetupSheet(
                player = player,
                legends = legends,
                decks = decks,
                legendsById = state.legendsById,
                onRename = { viewModel.rename(player.id, it) },
                onPickLegend = { viewModel.assignLegend(player.id, it) },
                onPickDeck = { viewModel.assignDeck(player.id, it) },
                onClear = { viewModel.clearAssignment(player.id) },
                onDismiss = { setupSeat = null },
            )
        }
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

        // Player count decides the seat layout — 2 face to face, 3 with two side
        // seats, 4 in quadrants — so it is worth showing the options rather than
        // making you cycle blindly through them.
        var countMenuOpen by remember { mutableStateOf(false) }
        Box {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clip(CircleShape)
                    .clickable { countMenuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("$playerCount", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(expanded = countMenuOpen, onDismissRequest = { countMenuOpen = false }) {
                (Constants.PointTracker.MIN_PLAYERS..Constants.PointTracker.MAX_PLAYERS).forEach { count ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (count) {
                                    2 -> "2 players — face to face"
                                    3 -> "3 players — one top, two side"
                                    else -> "4 players — quadrants"
                                },
                            )
                        },
                        onClick = { onChangePlayerCount(count); countMenuOpen = false },
                    )
                }
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
@Composable
private fun PlayerSeat(
    player: PlayerState,
    index: Int,
    rotation: Float,
    legendRotation: Float,
    isStarting: Boolean,
    isCandidate: Boolean,
    isFullBleed: Boolean,
    legend: com.scanrift.android.domain.model.Card?,
    deckName: String?,
    onAdd: (ScoreCategory) -> Unit,
    onRemove: (ScoreCategory) -> Unit,
    onOpenSetup: () -> Unit,
    onDropPlayer: (String) -> Unit,
) {
    var isDropTarget by remember { mutableStateOf(false) }

    // Read through `rememberUpdatedState`, never captured directly.
    //
    // A `DragAndDropTarget` is a long-lived object, but the seat it belongs to swaps
    // occupants. Capturing `player` in the remember block froze the *first* occupant
    // into the target: after one swap every drop saw its own stale id, took the
    // `dragged == self` early return, and silently did nothing. Only the seat's
    // position is fixed; who sits in it is not.
    val seatOccupantId by rememberUpdatedState(player.id)
    val dropPlayer by rememberUpdatedState(onDropPlayer)
    val openSetup by rememberUpdatedState(onOpenSetup)

    // The whole tile is the target, not just its chip, so you have a seat-sized area
    // to aim at rather than a chip-sized one.
    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDropTarget = false
                val dragged = event.playerId() ?: return false
                if (dragged == seatOccupantId) return false
                dropPlayer(dragged)
                return true
            }

            override fun onEntered(event: DragAndDropEvent) { isDropTarget = true }
            override fun onExited(event: DragAndDropEvent) { isDropTarget = false }
            override fun onEnded(event: DragAndDropEvent) { isDropTarget = false }
        }
    }
    val borderColor by animateColorAsState(
        targetValue = when {
            isDropTarget -> MaterialTheme.colorScheme.primary
            isStarting -> Color(0xFFFFD60A)
            isCandidate -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        },
        label = "seatBorder",
    )
    val tint = legend?.domains?.firstOrNull()?.let { domainColor(it) } ?: MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(if (isFullBleed) 0.dp else 18.dp)

    // The drop target lives *outside* the rotation, on a plain box filling the seat.
    //
    // Compose locates a drop target from `positionInRoot()` plus the node's untransformed
    // size, so a target inside a rotated `graphicsLayer` reports a rectangle that has
    // been moved but not turned. In the quadrant layout that made dropping on the
    // bottom-right seat register as a hit on the top-right one. Touch hit-testing does
    // honour the transform, which is why taps were unaffected and only drops went astray.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                },
                target = dropTarget,
            ),
    ) {
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
                    legend?.imageUrl?.let { url ->
                        coil3.compose.AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.matchParentSize(),
                        )
                        // A flat veil rather than a gradient: the score has to stay legible
                        // over whatever the art happens to be.
                        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))
                    }

                    val minDimension = min(maxWidth.value, maxHeight.value)
                    val buttonDiameter = min(60f, max(40f, minDimension * 0.16f)).dp
                    val scoreSize = max(56f, minDimension * 0.45f).sp

                    // The name chip does double duty: tap opens this seat's setup, and
                    // long-press drags it onto another seat to swap the two. It is the
                    // only tap target on the tile, since the rest belongs to the score
                    // buttons.
                    val chipLabel = deckName ?: legend?.name
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            // Tap and long-press-drag share one detector, deliberately.
                            //
                            // `clickable` and the current `dragAndDropSource(transferData)`
                            // cannot coexist on the same element: that overload's start
                            // detector runs a tap gesture with `onTap = null` and consumes
                            // the press, and its detector is not a public parameter. Put
                            // the source inside and taps die; put it outside and drags do.
                            // The deprecated suspend overload hands us the pointer scope,
                            // so one `detectTapGestures` can own both gestures — and it
                            // still draws the chip itself as the drag shadow, which is
                            // exactly the affordance we want.
                            //
                            // Like the drop target, this handler is captured once and
                            // never refreshed, so it reads the occupant through the
                            // updated state rather than closing over `player`.
                            // `block =` is required: a bare trailing lambda is ambiguous
                            // against the `transferData` overload.
                            .dragAndDropSource(block = {
                                detectTapGestures(
                                    onTap = { openSetup() },
                                    onLongPress = {
                                        startTransfer(
                                            DragAndDropTransferData(
                                                ClipData.newPlainText(PLAYER_DRAG_LABEL, seatOccupantId),
                                            ),
                                        )
                                    },
                                )
                            })
                            // The gesture above is invisible to accessibility services, so
                            // the chip advertises its tap action explicitly.
                            .semantics {
                                role = Role.Button
                                onClick(label = "Open player setup") { openSetup(); true }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = player.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        // Only shown once there is something to show — an empty seat gets
                        // its name and nothing else.
                        if (chipLabel != null) {
                            Text(
                                text = chipLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

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

/** Label on the drag clip; also what identifies our own drags from someone else's. */
private const val PLAYER_DRAG_LABEL = "scanrift/playerId"

private fun DragAndDropEvent.playerId(): String? =
    runCatching { toAndroidDragEvent().clipData?.getItemAt(0)?.text?.toString() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
