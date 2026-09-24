package com.scanrift.android.ui.scanner

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.service.scanning.CameraState
import com.scanrift.android.ui.adaptive.AdaptiveRules
import com.scanrift.android.ui.theme.Dimens
import com.scanrift.android.ui.util.heavyImpact
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val MatchGreen = Color(0xFF34C759)
private val ErrorRed = Color(0xFFFF3B30)
private val HintBlue = Color(0xFF0A84FF)
private val OverlayScrim = Color.Black.copy(alpha = 0.6f)
private val UpperQuarter = BiasAlignment(0f, -0.5f)

/**
 * The scanner.
 *
 * Adaptive rule here is deliberately stricter than elsewhere: the session sidebar only
 * appears at 840dp, not 600. At 700dp — an unfolded Fold in portrait — a 360dp sidebar
 * would leave 340dp of camera, which is less than the 300x420 guide plus margins. So
 * the inner display keeps the full-bleed scanner in portrait and gains the sidebar
 * only in landscape. The larger guide rect is a separate check, so portrait still gets
 * the benefit of the bigger screen.
 */
@Composable
fun ScannerScreen(viewModel: ScannerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    var showSummary by rememberSaveable { mutableStateOf(false) }

    val sheetOpen = showSummary || state.correctingId != null
    LaunchedEffect(sheetOpen) {
        if (sheetOpen) viewModel.pauseScanning() else viewModel.resumeScanning()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showSidebar = AdaptiveRules.useScannerSidebar(maxWidth)
        val largeGuide = AdaptiveRules.useLargeScanGuide(maxWidth, maxHeight)

        if (showSidebar) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    CameraLayer(viewModel, state, largeGuide, onOpenSummary = { showSummary = true }) {
                        LastScannedChip(state, viewModel)
                    }
                }
                VerticalDivider()
                SessionSidebar(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier
                        .width(Dimens.ScannerSidebarWidth)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
        } else {
            CameraLayer(viewModel, state, largeGuide, onOpenSummary = { showSummary = true }) {
                LastScannedChip(state, viewModel)
                if (state.scannedCount > 0) {
                    Button(
                        onClick = { showSummary = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                    ) {
                        Text("Review & Add (${state.scannedCount} cards)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showSummary) {
        SessionSummarySheet(state, decks, viewModel, onDismiss = { showSummary = false })
    }
    if (state.correctingId != null) {
        CardCorrectionSheet(state, viewModel)
    }
}

@Composable
private fun CameraLayer(
    viewModel: ScannerViewModel,
    state: ScannerUiState,
    largeGuide: Boolean,
    onOpenSummary: () -> Unit,
    bottomContent: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val cameraState by viewModel.cameraState.collectAsStateWithLifecycle()
    val torchOn by viewModel.torchEnabled.collectAsStateWithLifecycle()
    val hasFlash by viewModel.hasFlashUnit.collectAsStateWithLifecycle()
    var cameraAttempt by remember { mutableIntStateOf(0) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusTap by remember { mutableIntStateOf(0) }

    LaunchedEffect(cameraAttempt) {
        viewModel.startCamera(context, lifecycleOwner, previewView)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopScanning()
            viewModel.cameraService.stop()
        }
    }

    val cameraError = (cameraState as? CameraState.Failed)?.message

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        viewModel.cameraService.focusAt(previewView, offset.x, offset.y)
                        focusPoint = offset
                        focusTap++
                    }
                },
        )
        focusPoint?.let { FocusIndicator(it, focusTap) }

        CardDetectionOverlay(
            state = if (cameraError != null) ScannerOverlayState.ERROR else state.overlayState,
            guideWidth = if (largeGuide) Dimens.ScanGuideRegularWidth else Dimens.ScanGuideCompactWidth,
            guideHeight = if (largeGuide) Dimens.ScanGuideRegularHeight else Dimens.ScanGuideCompactHeight,
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                if (state.scannedCount > 0) {
                    ScannedCountPill(state.scannedCount, onOpenSummary, Modifier.align(Alignment.Center))
                }
                if (hasFlash) {
                    IconButton(onClick = viewModel::toggleTorch, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(
                            imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = "Torch",
                            tint = Color.White,
                        )
                    }
                }
            }
            if (state.debugEnabled) DebugOverlay(state.debug)
        }

        StateOverlay(
            overlayState = state.overlayState,
            matchedName = state.lastScanned?.name.orEmpty(),
            cameraError = cameraError,
            onRetry = { cameraAttempt++ },
            modifier = Modifier.align(UpperQuarter),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = bottomContent,
        )
    }
}

@Composable
private fun ScannedCountPill(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(OverlayScrim)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$count cards scanned" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Layers, contentDescription = null, tint = Color.White)
        Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun FocusIndicator(point: Offset, tap: Int) {
    val scale = remember(tap) { Animatable(1.5f) }
    val alpha = remember(tap) { Animatable(1f) }
    LaunchedEffect(tap) {
        launch { scale.animateTo(1f, tween(300, easing = EaseOut)) }
        alpha.animateTo(0f, tween(300, delayMillis = 700, easing = EaseIn))
    }
    Box(
        Modifier
            .offset { IntOffset((point.x - 40.dp.toPx()).roundToInt(), (point.y - 40.dp.toPx()).roundToInt()) }
            .size(80.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .border(2.dp, Color.Yellow),
    )
}

@Composable
private fun StateOverlay(
    overlayState: ScannerOverlayState,
    matchedName: String,
    cameraError: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillModifier = Modifier
        .clip(RoundedCornerShape(12.dp))
        .background(OverlayScrim)
        .padding(16.dp)

    Box(modifier) {
        AnimatedVisibility(
            visible = cameraError == null && overlayState == ScannerOverlayState.PROCESSING,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            Row(pillModifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text("Reading card...", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }
        AnimatedVisibility(
            visible = cameraError == null && overlayState == ScannerOverlayState.MATCHED,
            enter = fadeIn(tween(200)) + scaleIn(tween(200)),
            exit = fadeOut(tween(200)) + scaleOut(tween(200)),
        ) {
            Row(pillModifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MatchGreen)
                Text(matchedName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
        if (cameraError != null) {
            Column(
                pillModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = ErrorRed)
                    Text(cameraError, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Retry", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun LastScannedChip(state: ScannerUiState, viewModel: ScannerViewModel) {
    AnimatedVisibility(
        visible = state.lastScanned != null,
        enter = slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
    ) {
        val card = state.lastScanned ?: return@AnimatedVisibility
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(OverlayScrim)
                .clickable(onClick = viewModel::startCorrectionForLastCard)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanThumbnail(card, isFoil = card.isAlwaysFoil)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(card.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                Text(card.setLabel, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                Text("Tap to correct", style = MaterialTheme.typography.labelSmall, color = HintBlue)
            }
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MatchGreen)
        }
    }
}

@Composable
private fun DebugOverlay(debug: ScannerDebugState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val small = MaterialTheme.typography.labelSmall
        Row {
            Text("DEBUG", color = Color.Yellow, style = small, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(debug.motionStatus, color = Color.Cyan, style = small)
        }
        Text("Time: ${debug.lastProcessTime}", color = Color.White, style = small)
        Text("Matches: ${debug.matchCount}", color = if (debug.matchCount > 0) MatchGreen else Color.White, style = small)
        HorizontalDivider(color = Color.White.copy(alpha = 0.5f))
        Text("Full OCR:", color = Color.White, style = small, fontWeight = FontWeight.Bold)
        Text(debug.ocrText.ifBlank { "(waiting for card)" }, color = Color.White, style = small, maxLines = 6)
    }
}

@Composable
private fun SessionSidebar(
    state: ScannerUiState,
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Scanned Cards", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.scannedCount > 0) {
                Text(
                    "${state.scannedCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()

        if (state.results.isEmpty()) {
            EmptyState(Icons.Outlined.Layers, "No Cards Yet", "Scanned cards will appear here", Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(state.results, key = { it.id }) { result ->
                    ScanResultRow(
                        result = result,
                        showCorrectHint = false,
                        onCorrect = { viewModel.startCorrection(result) },
                        onToggleFoil = { viewModel.toggleFoil(result.id) },
                        onQuantityChange = { viewModel.setQuantity(result.id, it) },
                        onRemove = { viewModel.remove(result.id) },
                    )
                }
            }
        }

        HorizontalDivider()
        Button(
            onClick = {
                haptics.heavyImpact()
                viewModel.addSessionToCollection()
            },
            enabled = state.results.isNotEmpty(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text("Add to Collection", fontWeight = FontWeight.SemiBold)
        }
    }
}
