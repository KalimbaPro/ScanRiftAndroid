package com.scanrift.android.ui.game

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.scanrift.android.ui.theme.ScanRiftBlue
import kotlin.math.abs
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.scanrift.android.ui.util.Haptic
import com.scanrift.android.ui.util.tapOrLongPress
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * The Game tab.
 *
 * Entirely new on Android — the previous build had no equivalent at all.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PointTrackerScreen(viewModel: PointTrackerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val immersive = LocalImmersiveMode.current
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var showSave by rememberSaveable { mutableStateOf(false) }
    var showRoster by rememberSaveable { mutableStateOf(false) }
    var pickerSeat by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickerShowsName by rememberSaveable { mutableStateOf(true) }
    val legends by viewModel.legends.collectAsStateWithLifecycle()
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val feedback: (HapticFeedbackType) -> Unit = { if (state.hapticsEnabled) haptics.performHapticFeedback(it) }

    // Hide the system bars *and* the app's navigation container in fullscreen, and
    // keep the screen awake — a 45-minute game shouldn't dim, which is an
    // Android-only improvement over iOS.
    ImmersiveEffect(enabled = state.isFullScreen)
    KeepScreenOn()

    DisposableEffect(state.isFullScreen) {
        immersive.value = state.isFullScreen
        onDispose { immersive.value = false }
    }

    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val edges = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout).asPaddingValues()
    val direction = LocalLayoutDirection.current
    val toggle = tween<Dp>(Motion.FULLSCREEN_TOGGLE_MS, easing = FastOutSlowInEasing)
    val top by animateDpAsState(if (state.isFullScreen) 0.dp else safe.calculateTopPadding(), toggle, label = "top")
    val bottom by animateDpAsState(if (state.isFullScreen) 0.dp else safe.calculateBottomPadding(), toggle, label = "bottom")

    SeatLayout(
        playerCount = state.players.size,
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = if (state.isFullScreen) 0.dp else safe.calculateStartPadding(direction),
                top = top,
                end = if (state.isFullScreen) 0.dp else safe.calculateEndPadding(direction),
                bottom = bottom,
            ),
        centerBar = {
            CenterControlBar(
                playerCount = state.players.size,
                isFullScreen = state.isFullScreen,
                canSave = state.recordablePlayers.isNotEmpty(),
                onReset = { confirmReset = true },
                onPlayers = { showRoster = true },
                onRandomize = { feedback(Haptic.Medium); viewModel.randomizeFirstPlayer() },
                onToggleFullScreen = { feedback(Haptic.Light); viewModel.setFullScreen(!state.isFullScreen) },
                onSave = { feedback(Haptic.Medium); showSave = true },
            )
        },
        seat = { index, rotation, legendRotation ->
            val player = state.players.getOrNull(index)
            if (player != null) {
                val isQuarterTurn = abs(abs(legendRotation % 180f) - 90f) < 0.5f
                PlayerSeat(
                    player = player,
                    index = index,
                    rotation = rotation,
                    legendRotation = legendRotation,
                    isStarting = state.startingPlayerIndex == index,
                    isChosen = state.startingPlayerIndex == index && !state.isRandomizing,
                    isFullBleed = state.isFullScreen,
                    edgeInset = when {
                        !state.isFullScreen || isQuarterTurn -> 0.dp
                        rotation == 180f -> edges.calculateTopPadding()
                        else -> edges.calculateBottomPadding()
                    },
                    legend = player.legendCardId?.let { state.legendsById[it] },
                    deckName = player.deckId?.let { id -> decks.firstOrNull { it.id == id }?.name },
                    inputMode = state.inputMode,
                    onAdd = { category -> feedback(Haptic.Light); viewModel.addPoint(index, category) },
                    onRemove = { category -> feedback(Haptic.Medium); viewModel.removePoint(index, category) },
                    onUndo = { feedback(Haptic.Medium); viewModel.undoPoint(index) },
                    onSetXp = { value -> feedback(Haptic.Selection); viewModel.setXp(index, value) },
                    onOpenSetup = { pickerShowsName = true; pickerSeat = index },
                    onDropPlayer = { draggedId -> feedback(Haptic.Selection); viewModel.swapPlayers(draggedId, index) },
                )
            }
        },
    )

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset Counters") },
            text = { Text("Reset all player scores to 0?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetCounters()
                    feedback(Haptic.Medium)
                    confirmReset = false
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }

    if (showRoster) {
        PlayerSetupSheet(
            players = state.players,
            legendsById = state.legendsById,
            decks = decks,
            onChangePlayerCount = viewModel::setPlayerCount,
            onRename = viewModel::rename,
            onPick = { seat -> pickerShowsName = false; pickerSeat = seat },
            onDismiss = { showRoster = false },
        )
    }

    pickerSeat?.let { seat ->
        state.players.getOrNull(seat)?.let { player ->
            LegendOrDeckPickerSheet(
                player = player,
                showNameField = pickerShowsName,
                legends = legends,
                decks = decks,
                onRename = { viewModel.rename(player.id, it) },
                onPickLegend = { viewModel.assignLegend(player.id, it) },
                onPickDeck = { viewModel.assignDeck(player.id, it) },
                onClear = { viewModel.clearAssignment(player.id) },
                onDismiss = { pickerSeat = null },
            )
        }
    }

    if (showSave) {
        GameRecordSheet(
            players = state.players,
            deckNames = decks.associate { it.id to it.name },
            onSkip = { showSave = false },
            onSave = { records -> viewModel.saveGame(records) { showSave = false } },
        )
    }
}

@Composable
private fun CenterControlBar(
    playerCount: Int,
    isFullScreen: Boolean,
    canSave: Boolean,
    onReset: () -> Unit,
    onPlayers: () -> Unit,
    onRandomize: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(Icons.Filled.Refresh, "Reset", onReset)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "Players", onClick = onPlayers)
                .semantics { contentDescription = "Players" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(32.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$playerCount",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        BarButton(Icons.Filled.Casino, "Randomize first player", onRandomize)
        BarButton(
            if (isFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            if (isFullScreen) "Exit full screen" else "Enter full screen",
            onToggleFullScreen,
        )
        if (canSave) BarButton(Icons.Filled.SaveAlt, "Save game to deck", onSave)
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
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
    isChosen: Boolean,
    isFullBleed: Boolean,
    edgeInset: Dp,
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
    val highlight = tween<Color>(Motion.HIGHLIGHT_BORDER_MS, easing = FastOutSlowInEasing)
    val borderColor by animateColorAsState(
        targetValue = when {
            isStarting -> StartingYellow
            isDropTarget -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        },
        animationSpec = highlight,
        label = "seatBorder",
    )
    val borderWidth = when {
        isStarting -> 4.dp
        isDropTarget -> 3.dp
        else -> 0.dp
    }
    val domainTint = legend?.domains?.firstOrNull()?.let { domainColor(it) }
    val hasArt = legend?.imageUrl != null
    val labelColor = if (hasArt || isSystemInDarkTheme()) Color.White else Color.Black
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
                        .padding(if (isFullBleed) 0.dp else 8.dp)
                        .clip(shape)
                        .background(
                            domainTint?.let { Brush.linearGradient(listOf(it.copy(alpha = 0.35f), it.copy(alpha = 0.15f))) }
                                ?: SolidColor(ScanRiftBlue.copy(alpha = 0.18f)),
                        )
                        .border(borderWidth, borderColor, shape),
                ) {
                    legend?.imageUrl?.let { url ->
                        coil3.compose.AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.matchParentSize(),
                        )
                        Box(
                            Modifier.matchParentSize()
                                .background(Color.Gray.copy(alpha = Constants.PointTracker.LEGEND_OVERLAY_OPACITY)),
                        )
                        if (isFullBleed) {
                            val height = constraints.maxHeight.toFloat()
                            Box(
                                Modifier.matchParentSize().background(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        0.5f to Color.Black.copy(alpha = 0.45f),
                                        1f to Color.Black.copy(alpha = 0.75f),
                                        startY = height / 2f,
                                        endY = height,
                                    ),
                                ),
                            )
                        }
                    }

                    val minDimension = min(maxWidth.value, maxHeight.value)
                    val buttonDiameter = min(60f, max(40f, minDimension * 0.16f)).dp
                    val scoreSize = max(56f, minDimension * 0.45f).sp
                    val picker = rememberScorePickerState()
                    val pixelsPerDp = LocalDensity.current.density

                    // Reset the picker when the seat changes hands, so a swap never
                    // leaves someone else's half-made choice hanging over the tile.
                    LaunchedEffect(player.id, inputMode) { picker.close() }

                    Text(
                        text = "${player.score}",
                        fontSize = scoreSize,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        style = LocalTextStyle.current.copy(
                            fontFeatureSettings = "tnum",
                            shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 2f * pixelsPerDp), 4f * pixelsPerDp),
                        ),
                        // The score deliberately does not animate; iOS kills its animation
                        // explicitly. The per-category counts below do animate.
                        modifier = Modifier
                            .align(Alignment.Center)
                            // Reserve only what is actually below: the breakdown bar is
                            // a good deal shorter than the button row plus its captions.
                            .padding(
                                bottom = when (inputMode) {
                                    ScoreInputMode.CATEGORY_BUTTONS -> buttonDiameter + 36.dp
                                    ScoreInputMode.TAP_ZONES -> buttonDiameter * 0.5f + 20.dp
                                },
                            ),
                    )

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
                    val chipPulse = remember { Animatable(1f) }
                    LaunchedEffect(isChosen) {
                        if (isChosen) {
                            chipPulse.animateTo(
                                1.12f,
                                infiniteRepeatable(tween(Motion.CHIP_PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                            )
                        } else {
                            chipPulse.animateTo(1f, tween(Motion.CHOSEN_FADE_MS, easing = FastOutSlowInEasing))
                        }
                    }
                    val chosenFade = tween<Color>(Motion.CHOSEN_FADE_MS, easing = FastOutSlowInEasing)
                    val chipColor by animateColorAsState(
                        if (isChosen) StartingYellow else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        chosenFade,
                        label = "chipColor",
                    )
                    val chipText by animateColorAsState(
                        if (isChosen) Color.Black else MaterialTheme.colorScheme.onSurface,
                        chosenFade,
                        label = "chipText",
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .scale(chipPulse.value)
                            .then(
                                if (isChosen) {
                                    Modifier.shadow(10.dp, CircleShape, ambientColor = StartingYellow, spotColor = StartingYellow)
                                } else {
                                    Modifier
                                },
                            )
                            .clip(CircleShape)
                            .background(chipColor)
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isChosen) {
                            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = player.name.ifEmpty { "Player" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isChosen) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = chipText,
                            maxLines = 1,
                        )
                        if (deckName != null) {
                            val deckColor = chipText.copy(alpha = 0.6f)
                            Text("•", style = MaterialTheme.typography.labelSmall, color = deckColor)
                            Text(
                                text = deckName,
                                style = MaterialTheme.typography.labelSmall,
                                color = deckColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    when (inputMode) {
                        ScoreInputMode.CATEGORY_BUTTONS -> Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 2.dp + edgeInset),
                            horizontalArrangement = Arrangement.spacedBy(buttonDiameter * 0.45f),
                        ) {
                            ScoreCategory.entries.forEach { category ->
                                CategoryScoreButton(
                                    category = category,
                                    count = player.count(category),
                                    diameter = buttonDiameter,
                                    labelColor = labelColor,
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
                                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp + edgeInset),
                        )
                    }

                    XpPill(
                        xp = player.xp,
                        onSetXp = onSetXp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp),
                    )

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
 * Where the row of category dots sits, and which one the finger is over.
 *
 * Positions are held in the tile's own pixel space — the layer lives inside both
 * rotation layers, so "up" and "right" are already the player's own.
 */
@Stable
private class ScorePickerState {
    var anchor by mutableStateOf(Offset.Unspecified)
    var pointer by mutableStateOf(Offset.Unspecified)
    var spacing by mutableFloatStateOf(0f)
    var dotRadius by mutableFloatStateOf(0f)

    /**
     * False until the finger has actually travelled.
     *
     * A dot can open under the touch point, so hit-testing from the moment it
     * appears would highlight whatever happens to sit under the thumb and
     * score it on release — turning "tap to open the dots" into "tap to score a random
     * category". Selection only arms once the drag starts.
     */
    var isArmed by mutableStateOf(false)

    val isOpen: Boolean get() = anchor.isSpecified

    /** The dot the finger is currently choosing, or null while the gesture is unarmed. */
    val hovered: ScoreCategory? get() = if (isArmed) categoryAt(pointer) else null

    fun center(index: Int): Offset =
        Offset(anchor.x + (index - 1) * spacing, anchor.y)

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

    fun open(at: Offset, size: IntSize, spacingPx: Float, dotRadiusPx: Float) {
        dotRadius = dotRadiusPx
        spacing = min(spacingPx, (size.width / 2f - dotRadiusPx - 6f).coerceAtLeast(0f))
        anchor = Offset(size.width / 2f, size.height / 2f)
        pointer = at
        isArmed = false
    }

    fun close() {
        anchor = Offset.Unspecified
        pointer = Offset.Unspecified
        isArmed = false
    }
}

@Composable
private fun rememberScorePickerState() = remember { ScorePickerState() }

/**
 * The whole tap-zone interaction, as one gesture.
 *
 * Left half takes a point back. Pressing the right half opens the three category dots
 * in a row across the middle of the tile; keep the finger down, slide onto one and lift to score it —
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
    val haptics = LocalHapticFeedback.current
    val dotRadiusPx = with(density) { dotDiameter.toPx() } / 2f
    val spacingPx = with(density) { (dotDiameter * 1.65f).toPx() }

    Box(
        Modifier
            .matchParentSize()
            .pointerInput(Unit) {
                val threshold = SCORE_GESTURE_THRESHOLD.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val openedNow = !state.isOpen
                    val undoing = openedNow && down.position.x < size.width / 2f

                    when {
                        undoing -> Unit
                        openedNow -> {
                            state.open(down.position, size, spacingPx, dotRadiusPx)
                            haptics.performHapticFeedback(Haptic.Light)
                        }
                        // The fan was already up, so this gesture is aimed at a dot from
                        // the start — no travel needed before it can select one.
                        else -> state.isArmed = true
                    }

                    if (!undoing) state.pointer = down.position
                    var travelled = 0f
                    drag(down.id) { change ->
                        travelled = (change.position - down.position).getDistance()
                        if (!undoing) {
                            if (travelled > threshold) state.isArmed = true
                            state.pointer = change.position
                        }
                        change.consume()
                    }

                    if (undoing) {
                        if (travelled < threshold && allowUndo) undo()
                        return@awaitEachGesture
                    }

                    // Order matters: a tap that merely opened the fan must not score,
                    // even though a dot may well have bloomed under the finger.
                    val opening = openedNow && travelled <= threshold
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
            modifier = Modifier.padding(horizontal = 18.dp).size(24.dp),
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
 * One dot, springing in with a short per-index delay so the three
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
            modifier = Modifier.size(diameter * 0.38f),
        )
    }
}

private const val FAN_STAGGER_MS = 45L

private val SCORE_GESTURE_THRESHOLD = 12.dp

private val StartingYellow = Color(0xFFFFD60A)

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

    BoxWithConstraints(modifier.height(cellHeight + 8.dp), contentAlignment = Alignment.Center) {
        if (points.isEmpty()) {
            Box(Modifier.matchParentSize().semantics { contentDescription = "No points yet" })
            return@BoxWithConstraints
        }

        val gap = 2.dp
        val inset = 3.dp
        // Fixed width until the rail runs out of room, then an equal share of what is left.
        val fitted = (maxWidth - inset * 2 - gap * (points.size - 1)) / points.size
        val cellWidth = min(cellHeight.value, fitted.value).coerceAtLeast(3f).dp
        val showIcons = cellWidth >= 13.dp

        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .border(1.dp, TrackFrame, RoundedCornerShape(6.dp))
                .padding(inset)
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
                modifier = Modifier.size(min(width.value, height.value).dp * 0.5f),
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
    val haptics = LocalHapticFeedback.current
    val trailing = TransformOrigin(1f, 0.5f)

    Row(
        modifier = modifier
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { } }
            .animateContentSize(Motion.pill())
            .padding(horizontal = if (expanded) 14.dp else 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = scaleIn(Motion.pill(), initialScale = 0.6f, transformOrigin = trailing) + fadeIn(),
            exit = scaleOut(Motion.pill(), targetScale = 0.6f, transformOrigin = trailing) + fadeOut(),
        ) {
            Row(Modifier.padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                XpStepButton(Icons.Filled.Remove, "Decrease XP", enabled = xp > 0) {
                    haptics.performHapticFeedback(Haptic.Light)
                    onSetXp(xp - 1)
                }
                XpStepButton(Icons.Filled.Add, "Increase XP", enabled = xp < Constants.PointTracker.XP_MAX) {
                    haptics.performHapticFeedback(Haptic.Light)
                    onSetXp(xp + 1)
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(min = 26.dp)
                .clickable {
                    haptics.performHapticFeedback(Haptic.Light)
                    expanded = !expanded
                }
                .semantics {
                    contentDescription = "${if (expanded) "Close XP editor" else "Edit XP"}, current value $xp"
                },
        ) {
            RollingNumber(xp, fontSize = 18.sp)
            Text(
                text = "XP",
                color = Color.White.copy(alpha = 0.75f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                lineHeight = 10.sp,
            )
        }
    }
}

@Composable
private fun XpStepButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun RollingNumber(value: Int, fontSize: TextUnit) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (slideInVertically(Motion.snappy()) { it * direction } + fadeIn()) togetherWith
                (slideOutVertically(Motion.snappy()) { -it * direction } + fadeOut())
        },
        label = "rollingNumber",
    ) { number ->
        Text(
            text = "$number",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            lineHeight = fontSize,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
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
    labelColor: Color,
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
                .shadow(5.dp, CircleShape)
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(category.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(diameter * 0.32f))
                RollingNumber(count, fontSize = (diameter.value * 0.28f).sp)
            }
        }
        Text(
            text = category.displayName.uppercase(),
            color = labelColor,
            fontSize = max(9f, diameter.value * 0.18f).sp,
            fontWeight = FontWeight.Bold,
            style = LocalTextStyle.current.copy(
                shadow = if (labelColor == Color.White) Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 4f) else null,
            ),
            modifier = Modifier.padding(top = 4.dp),
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
