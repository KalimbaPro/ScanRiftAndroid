package com.scanrift.android.ui.game

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.core.Constants
import com.scanrift.android.domain.model.ScoreCategory
import com.scanrift.android.domain.model.ScoreInputMode
import com.scanrift.android.ui.LocalImmersiveMode
import com.scanrift.android.ui.theme.Dimens
import com.scanrift.android.ui.theme.Motion
import com.scanrift.android.ui.theme.TrackFrame
import com.scanrift.android.ui.theme.domainColor
import com.scanrift.android.ui.util.tapOrLongPress
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

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
                    inputMode = state.inputMode,
                    onAdd = { category -> viewModel.addPoint(index, category) },
                    onRemove = { category -> viewModel.removePoint(index, category) },
                    onUndo = { viewModel.undoPoint(index) },
                    onSetXp = { value -> viewModel.setXp(index, value) },
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
    inputMode: ScoreInputMode,
    onAdd: (ScoreCategory) -> Unit,
    onRemove: (ScoreCategory) -> Unit,
    onUndo: () -> Unit,
    onSetXp: (Int) -> Unit,
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
                    val picker = rememberScorePickerState()

                    // Reset the picker when the seat changes hands, so a swap never
                    // leaves someone else's half-made choice hanging over the tile.
                    LaunchedEffect(player.id, inputMode) { picker.close() }

                    // The gesture layer sits directly over the art and under everything
                    // else, so the name chip and the XP pill still win the hit test.
                    if (inputMode == ScoreInputMode.TAP_ZONES) {
                        ScoreTapLayer(
                            state = picker,
                            canUndo = player.score > 0,
                            dotDiameter = buttonDiameter * 1.05f,
                            onUndo = onUndo,
                            onScore = onAdd,
                        )
                    }

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

                    XpPill(
                        xp = player.xp,
                        onSetXp = onSetXp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
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
                            // Reserve only what is actually below: the breakdown bar is
                            // a good deal shorter than the button row plus its captions.
                            .padding(
                                bottom = when (inputMode) {
                                    ScoreInputMode.CATEGORY_BUTTONS -> buttonDiameter + 36.dp
                                    ScoreInputMode.TAP_ZONES -> buttonDiameter * 0.55f + 20.dp
                                },
                            ),
                    )

                    when (inputMode) {
                        ScoreInputMode.CATEGORY_BUTTONS -> Row(
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

                        // Without the permanent category counts, the breakdown bar is
                        // the only thing that says what the total is made of — and it is
                        // what makes "undo the last point" legible as it shrinks.
                        ScoreInputMode.TAP_ZONES -> ScoreTrack(
                            player = player,
                            cellHeight = buttonDiameter * 0.5f,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }

                    // Drawn last so the dots sit over the score and the track, but it
                    // takes no input of its own — the layer below owns the whole gesture.
                    if (inputMode == ScoreInputMode.TAP_ZONES) {
                        ScorePickerDots(state = picker, dotDiameter = buttonDiameter * 1.05f)
                    }
                }
            }
        }
    }
}

/**
 * Where the fan of category dots sits, and which one the finger is over.
 *
 * Positions are held in the tile's own pixel space — the layer lives inside both
 * rotation layers, so "up" and "right" are already the player's own.
 */
@Stable
private class ScorePickerState {
    var anchor by mutableStateOf(Offset.Unspecified)
    var pointer by mutableStateOf(Offset.Unspecified)
    var radius by mutableFloatStateOf(0f)
    var dotRadius by mutableFloatStateOf(0f)

    /**
     * False until the finger has actually travelled.
     *
     * Opening the fan puts a dot close to the touch point, so hit-testing from the
     * moment it appears would highlight whatever happens to sit under the thumb and
     * score it on release — turning "tap to open the dots" into "tap to score a random
     * category". Selection only arms once the drag starts.
     */
    var isArmed by mutableStateOf(false)

    val isOpen: Boolean get() = anchor.isSpecified

    /** The dot the finger is currently choosing, or null while the gesture is unarmed. */
    val hovered: ScoreCategory? get() = if (isArmed) categoryAt(pointer) else null

    /**
     * The arc. Dots bloom up and out from the touch point rather than in a straight
     * row, so the middle one is not hidden under the finger that opened them.
     */
    fun center(index: Int): Offset {
        val radians = Math.toRadians(FAN_ANGLES[index].toDouble())
        return Offset(
            anchor.x + (radius * cos(radians)).toFloat(),
            anchor.y + (radius * sin(radians)).toFloat(),
        )
    }

    /** The dot under [point], with a generous slop so sliding between them feels sticky. */
    fun categoryAt(point: Offset): ScoreCategory? {
        if (!isOpen || point.isUnspecified) return null
        val reach = dotRadius * 1.45f
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        ScoreCategory.entries.indices.forEach { index ->
            val distance = (point - center(index)).getDistance()
            if (distance <= reach && distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best?.let { ScoreCategory.entries[it] }
    }

    /**
     * Keeps the whole fan on the tile. Without this, opening near an edge throws two of
     * the three dots off-screen and the gesture has nothing to land on.
     *
     * The bounds come from [FAN_ANGLES] rather than being hardcoded, so re-aiming the
     * fan cannot silently leave the clamp describing the old geometry.
     */
    fun open(at: Offset, size: IntSize, radiusPx: Float, dotRadiusPx: Float) {
        radius = radiusPx
        dotRadius = dotRadiusPx
        val margin = dotRadiusPx + 6f

        var minDx = 0f
        var maxDx = 0f
        var minDy = 0f
        var maxDy = 0f
        FAN_ANGLES.forEach { angle ->
            val radians = Math.toRadians(angle.toDouble())
            val dx = (radiusPx * cos(radians)).toFloat()
            val dy = (radiusPx * sin(radians)).toFloat()
            minDx = min(minDx, dx)
            maxDx = max(maxDx, dx)
            minDy = min(minDy, dy)
            maxDy = max(maxDy, dy)
        }

        anchor = Offset(
            x = clamp(at.x, margin - minDx, size.width - margin - maxDx),
            y = clamp(at.y, margin - minDy, size.height - margin - maxDy),
        )
        pointer = at
        isArmed = false
    }

    /** Centres instead of clamping when the tile is too small to hold the fan at all. */
    private fun clamp(value: Float, low: Float, high: Float) =
        if (low <= high) value.coerceIn(low, high) else (low + high) / 2f

    fun close() {
        anchor = Offset.Unspecified
        pointer = Offset.Unspecified
        isArmed = false
    }

    private companion object {
        /**
         * Where the fan points and how wide it opens, in screen angles — negative is
         * upward, so -90 aims it straight up out of the touch, the spread splitting
         * evenly either side of the finger.
         */
        const val FAN_CENTER = -90f
        const val FAN_SPREAD = 50f
        val FAN_ANGLES = floatArrayOf(FAN_CENTER - FAN_SPREAD, FAN_CENTER, FAN_CENTER + FAN_SPREAD)
    }
}

@Composable
private fun rememberScorePickerState() = remember { ScorePickerState() }

/**
 * The whole tap-zone interaction, as one gesture.
 *
 * Left half takes a point back. Pressing the right half blooms the three category dots
 * out of the touch point; keep the finger down, slide onto one and lift to score it —
 * the same press-drag-release the Pinterest reaction picker uses. Lifting without moving
 * leaves the dots up so they can be tapped instead, which is what you want when the
 * phone is flat on a table and you are not holding it.
 *
 * One `pointerInput` owns all of it rather than two `clickable` zones plus an overlay:
 * a drag that starts on the zone and ends on a dot is a single gesture, and splitting it
 * across composables would end it the moment the finger left the zone.
 */
@Composable
private fun BoxScope.ScoreTapLayer(
    state: ScorePickerState,
    canUndo: Boolean,
    dotDiameter: androidx.compose.ui.unit.Dp,
    onUndo: () -> Unit,
    onScore: (ScoreCategory) -> Unit,
) {
    val undo by rememberUpdatedState(onUndo)
    val score by rememberUpdatedState(onScore)
    val allowUndo by rememberUpdatedState(canUndo)
    val density = LocalDensity.current
    val dotRadiusPx = with(density) { dotDiameter.toPx() } / 2f
    val radiusPx = with(density) { (dotDiameter * 1.65f).toPx() }

    Box(
        Modifier
            .matchParentSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val openedNow = !state.isOpen

                    if (openedNow) {
                        if (down.position.x < size.width / 2f) {
                            // Left half: a plain button, so it only fires if the finger
                            // lifts on the same side it went down.
                            val up = waitForUpOrCancellation()
                            if (up != null && up.position.x < size.width / 2f && allowUndo) undo()
                            return@awaitEachGesture
                        }
                        state.open(down.position, size, radiusPx, dotRadiusPx)
                    } else {
                        // The fan was already up, so this gesture is aimed at a dot from
                        // the start — no travel needed before it can select one.
                        state.isArmed = true
                    }

                    state.pointer = down.position
                    var travelled = 0f
                    drag(down.id) { change ->
                        travelled += change.positionChange().getDistance()
                        if (travelled > viewConfiguration.touchSlop) state.isArmed = true
                        state.pointer = change.position
                        change.consume()
                    }

                    // Order matters: a tap that merely opened the fan must not score,
                    // even though a dot may well have bloomed under the finger.
                    val opening = openedNow && travelled <= viewConfiguration.touchSlop
                    val picked = if (opening) null else state.categoryAt(state.pointer)
                    when {
                        picked != null -> { state.close(); score(picked) }
                        opening -> Unit
                        else -> state.close()
                    }
                }
            },
    ) {
        // Screen-position markers, purely decorative — the layer above owns the input.
        Row(Modifier.matchParentSize()) {
            ZoneMarker(Icons.Filled.Remove, enabled = canUndo, Alignment.CenterStart, Modifier.weight(1f))
            ZoneMarker(Icons.Filled.Add, enabled = true, Alignment.CenterEnd, Modifier.weight(1f))
        }
        // TalkBack cannot press-and-drag, so both actions are exposed explicitly.
        Box(
            Modifier.matchParentSize().semantics {
                customActions = ScoreCategory.entries.map { category ->
                    CustomAccessibilityAction("Score ${category.displayName}") { score(category); true }
                } + CustomAccessibilityAction("Take back the last point") { undo(); true }
            },
        )
    }
}

@Composable
private fun ZoneMarker(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    alignment: Alignment,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxHeight(), contentAlignment = alignment) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 0.5f else 0.2f),
            modifier = Modifier.padding(horizontal = 16.dp).size(28.dp),
        )
    }
}

/**
 * The dots themselves, drawn at the positions [ScorePickerState] computed.
 *
 * Takes no input: the gesture that opened them is still running, and a `clickable` here
 * would fight it for the pointer.
 */
@Composable
private fun BoxScope.ScorePickerDots(
    state: ScorePickerState,
    dotDiameter: androidx.compose.ui.unit.Dp,
) {
    if (!state.isOpen) return
    val hovered = state.hovered

    // Scrim, so the tile reads as "pick one" rather than "something is floating here".
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))

    ScoreCategory.entries.forEachIndexed { index, category ->
        FannedCategoryDot(
            category = category,
            index = index,
            center = state.center(index),
            diameter = dotDiameter,
            isHovered = category == hovered,
        )
    }
}

/**
 * One dot, springing out of the touch point with a short per-index delay so the three
 * arrive in sequence rather than as a block.
 */
@Composable
private fun BoxScope.FannedCategoryDot(
    category: ScoreCategory,
    index: Int,
    center: Offset,
    diameter: androidx.compose.ui.unit.Dp,
    isHovered: Boolean,
) {
    val bloom = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * FAN_STAGGER_MS)
        bloom.animateTo(1f, Motion.press())
    }
    val hoverScale by animateFloatAsState(
        targetValue = if (isHovered) 1.22f else 1f,
        animationSpec = Motion.press(),
        label = "dotHover",
    )
    val density = LocalDensity.current
    val radiusPx = with(density) { diameter.toPx() } / 2f

    Box(
        modifier = Modifier
            .offset {
                IntOffset((center.x - radiusPx).roundToInt(), (center.y - radiusPx).roundToInt())
            }
            .size(diameter)
            .scale(bloom.value * hoverScale)
            .alpha(bloom.value)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(category.color, category.color.copy(alpha = 0.75f))))
            .then(
                if (isHovered) Modifier.border(3.dp, Color.White, CircleShape) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(diameter * 0.44f),
        )
    }
}

private const val FAN_STAGGER_MS = 45L

/**
 * The scoring track: one cell per point, in the order the points were taken.
 *
 * A proportional "3 conquer, 2 hold" summary cannot show sequence, and sequence is what
 * the tap-zone layout needs — the single decrement button takes the *last* point back, so
 * you have to be able to see which one that is. The rightmost cell is always the one the
 * next undo removes.
 *
 * Cells keep a fixed size while they fit, so the rail visibly grows as the game goes on;
 * past that they share the width equally and drop their icons rather than overflowing.
 * A 99-point game is legal, even if no real one gets there.
 */
@Composable
private fun ScoreTrack(
    player: PlayerState,
    cellHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val points = remember(player.scoreLog, player.conquer, player.hold, player.ability) {
        player.orderedPoints()
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (points.isEmpty()) return@BoxWithConstraints

        val gap = 2.dp
        // Fixed width until the rail runs out of room, then an equal share of what is left.
        val fitted = (maxWidth - gap * (points.size - 1)) / points.size
        val cellWidth = min(cellHeight.value, fitted.value).coerceAtLeast(3f).dp
        val showIcons = cellWidth >= 13.dp

        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .border(1.dp, TrackFrame, RoundedCornerShape(6.dp))
                .padding(3.dp)
                .animateContentSize(Motion.snappy())
                .semantics {
                    contentDescription = "Scored " + points.joinToString(", ") { it.displayName }
                },
        ) {
            points.forEach { category ->
                ScoreTrackCell(category, cellWidth, cellHeight, showIcons)
            }
        }
    }
}

@Composable
private fun ScoreTrackCell(
    category: ScoreCategory,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    showIcon: Boolean,
) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(3.dp))
            .background(
                Brush.verticalGradient(
                    listOf(category.color, category.color.copy(alpha = 0.78f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (showIcon) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(min(width.value, height.value).dp * 0.62f),
            )
        }
    }
}

/**
 * The XP counter, ported from iOS.
 *
 * Collapsed it is just the number; tapping expands it into a stepper. It lives inside
 * the rotation layers so it turns with the seat, and it swallows its own taps so they
 * never fall through to the scoring zone underneath.
 */
@Composable
private fun XpPill(
    xp: Int,
    onSetXp: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.Black.copy(alpha = 0.45f))
            .animateContentSize(Motion.pill())
            .padding(horizontal = if (expanded) 6.dp else 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (expanded) {
            XpStepButton(
                symbol = "\u2212",
                label = "Decrease XP",
                enabled = xp > 0,
                onClick = { onSetXp(xp - 1) },
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "Toggle the XP stepper") { expanded = !expanded }
                .padding(horizontal = 4.dp),
        ) {
            Text(
                text = "$xp",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
            )
            Text(
                text = "XP",
                color = Color.White.copy(alpha = 0.75f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
            )
        }

        if (expanded) {
            XpStepButton(
                symbol = "+",
                label = "Increase XP",
                enabled = xp < Constants.PointTracker.XP_MAX,
                onClick = { onSetXp(xp + 1) },
            )
        }
    }
}

@Composable
private fun XpStepButton(
    symbol: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
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
    val color = category.color
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
