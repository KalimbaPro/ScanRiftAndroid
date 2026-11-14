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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.scanrift.android.domain.model.ScanLanguage
import com.scanrift.android.service.scanning.CameraState
import com.scanrift.android.service.scanning.MultiCardScanner
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
 * From 600dp of available width the camera gets a session sidebar beside it, so an
 * unfolded Fold in portrait (~700dp) shows camera + session and refolding returns to
 * the full-bleed scanner live. The sidebar narrows to 40% of the width (max 360dp) so
 * the camera keeps room for the guide. The larger guide rect is a separate check.
 *
 * Two modes: **Single** streams 720p frames and scans one card at a time; **Page**
 * photographs (or imports) a whole binder page and opens [PageReviewScreen].
 */
@Composable
fun ScannerScreen(viewModel: ScannerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    var showSummary by rememberSaveable { mutableStateOf(false) }

    val sheetOpen = showSummary || state.isCorrecting
    LaunchedEffect(sheetOpen) {
        if (sheetOpen) viewModel.pauseScanning() else viewModel.resumeScanning()
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::importPage)
    }
    val pickPhoto = {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    BackHandler(enabled = state.page != null) { viewModel.dismissPage() }

    if (state.page != null) {
        PageReviewScreen(state, viewModel, onAdded = { showSummary = true })
    } else BoxWithConstraints(Modifier.fillMaxSize()) {
        val showSidebar = AdaptiveRules.useScannerSidebar(maxWidth)
        val sidebarWidth = AdaptiveRules.scannerSidebarWidth(maxWidth)
        val largeGuide = AdaptiveRules.useLargeScanGuide(maxWidth, maxHeight)

        if (showSidebar) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    CameraLayer(viewModel, state, largeGuide, onOpenSummary = { showSummary = true }, onPickPhoto = pickPhoto) {
                        LastScannedChip(state, viewModel)
                        ScanPillRow(state, viewModel)
                    }
                }
                VerticalDivider()
                SessionSidebar(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier
                        .width(sidebarWidth)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
        } else {
            CameraLayer(viewModel, state, largeGuide, onOpenSummary = { showSummary = true }, onPickPhoto = pickPhoto) {
                LastScannedChip(state, viewModel)
                ScanPillRow(state, viewModel)
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
    if (state.isCorrecting) {
        CardCorrectionSheet(state, viewModel)
    }
}

@Composable
private fun CameraLayer(
    viewModel: ScannerViewModel,
    state: ScannerUiState,
    largeGuide: Boolean,
    onOpenSummary: () -> Unit,
    onPickPhoto: () -> Unit,
    bottomContent: @Composable ColumnScope.() -> Unit,
) {
    val pageMode = state.scanMode == ScanMode.PAGE
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val cameraState by viewModel.cameraState.collectAsStateWithLifecycle()
    val torchOn by viewModel.torchEnabled.collectAsStateWithLifecycle()
    val hasFlash by viewModel.hasFlashUnit.collectAsStateWithLifecycle()
    var cameraAttempt by remember { mutableIntStateOf(0) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusTap by remember { mutableIntStateOf(0) }

    // Restarted on a mode change: page mode binds a full-resolution capture instead
    // of the frame analyser.
    LaunchedEffect(cameraAttempt, pageMode) {
        viewModel.startCamera(context, lifecycleOwner, previewView, pageMode)
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

        if (pageMode) {
            PageGuideOverlay(busy = state.isCapturing)
        } else {
            CardDetectionOverlay(
                state = if (cameraError != null) ScannerOverlayState.ERROR else state.overlayState,
                guideWidth = if (largeGuide) Dimens.ScanGuideRegularWidth else Dimens.ScanGuideCompactWidth,
                guideHeight = if (largeGuide) Dimens.ScanGuideRegularHeight else Dimens.ScanGuideCompactHeight,
            )
        }

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
            ScanModeSwitch(state.scanMode, viewModel::setScanMode, Modifier.align(Alignment.CenterHorizontally))
            if (state.debugEnabled && !pageMode) DebugOverlay(state.debug)
        }

        if (!pageMode) StateOverlay(
            overlayState = state.overlayState,
            matchedName = state.lastScanned?.name.orEmpty(),
            cameraError = cameraError,
            onRetry = { cameraAttempt++ },
            modifier = Modifier.align(UpperQuarter),
        )

        if (pageMode) {
            if (cameraError != null) {
                StateOverlay(state.overlayState, "", cameraError, onRetry = { cameraAttempt++ }, modifier = Modifier.align(UpperQuarter))
            }
            PageShutterRow(
                busy = state.isCapturing,
                enabled = cameraError == null,
                onCapture = viewModel::capturePage,
                onPickPhoto = onPickPhoto,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 24.dp),
            )
        } else {
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
            ScanThumbnail(card, isFoil = state.lastResult?.isFoil ?: card.isAlwaysFoil, language = state.lastLanguage)
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
                        onLanguageChange = { viewModel.setLanguage(result.id, it) },
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

/**
 * ManaBox-style quick controls for the card just scanned:
 * `[Normal|Foil] [SET #number] [flag CODE ▾] [+1]`.
 *
 * The language chip is also the scanner's language setting (`scanLanguage`): Auto
 * detects per card, a fixed choice overrides detection for every later scan and
 * re-labels the card just scanned. In Auto it shows what was detected.
 */
@Composable
private fun ScanPillRow(state: ScannerUiState, viewModel: ScannerViewModel) {
    val card = state.lastScanned
    AnimatedVisibility(
        visible = card != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
    ) {
        if (card == null) return@AnimatedVisibility
        val last = state.lastResult
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val foil = last?.isFoil ?: card.isAlwaysFoil
            ScanPill(
                label = if (foil) "Foil" else "Normal",
                highlighted = foil,
                enabled = last != null && !card.isAlwaysFoil,
                onClick = viewModel::toggleLastFoil,
            )
            ScanPill(
                label = "${card.setId} #${card.publicCodePrefix.substringAfter('-')}",
                onClick = viewModel::startCorrectionForLastCard,
            )
            Box {
                var open by remember { mutableStateOf(false) }
                val shown = state.scanLanguage.language ?: state.lastLanguage
                val auto = state.scanLanguage == ScanLanguage.AUTO
                ScanPill(
                    label = "${shown.flag} ${shown.code}${if (auto) " · Auto" else ""} \u25BE",
                    onClick = { open = true },
                    description = "Scan language: ${if (auto) "Auto, detected ${shown.displayName}" else shown.displayName}",
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    ScanLanguage.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(option.language?.let { "${it.flag}  ${it.displayName}" } ?: "Auto (detect)")
                            },
                            trailingIcon = if (option == state.scanLanguage) {
                                { Icon(Icons.Filled.Check, contentDescription = "Selected") }
                            } else {
                                null
                            },
                            onClick = { open = false; viewModel.setScanLanguage(option) },
                        )
                    }
                }
            }
            ScanPill(label = "+1", onClick = viewModel::incrementLast, description = "Add one more copy")
        }
    }
}

@Composable
private fun ScanPill(
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    description: String? = null,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = when {
            !enabled -> Color.White.copy(alpha = 0.5f)
            highlighted -> Color.Black
            else -> Color.White
        },
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (highlighted) Color(0xFFFFD60A) else OverlayScrim)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    )
}

@Composable
private fun ScanModeSwitch(mode: ScanMode, onChange: (ScanMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(CircleShape).background(OverlayScrim).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ScanMode.entries.forEach { option ->
            val selected = option == mode
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.Black else Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { onChange(option) }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .semantics { contentDescription = "${option.label} scan mode${if (selected) ", selected" else ""}" },
            )
        }
    }
}

/** A 3×3 frame for a binder page: three cards wide, three tall, card-shaped pockets. */
@Composable
private fun PageGuideOverlay(busy: Boolean) {
    val color = if (busy) Color(0xFFFFD60A) else Color.White.copy(alpha = 0.7f)
    Canvas(Modifier.fillMaxSize()) {
        val pageAspect = MultiCardScanner.CARD_ASPECT // 3 × 63 wide over 3 × 88 tall
        var w = size.width * 0.92f
        var h = w / pageAspect
        if (h > size.height * 0.72f) {
            h = size.height * 0.72f
            w = h * pageAspect
        }
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val scrim = Color.Black.copy(alpha = 0.35f)
        drawRect(scrim, size = androidx.compose.ui.geometry.Size(size.width, top))
        drawRect(scrim, topLeft = Offset(0f, top + h), size = androidx.compose.ui.geometry.Size(size.width, size.height - top - h))
        val stroke = 1.5.dp.toPx()
        for (i in 0..3) {
            drawLine(color, Offset(left + w * i / 3f, top), Offset(left + w * i / 3f, top + h), stroke)
            drawLine(color, Offset(left, top + h * i / 3f), Offset(left + w, top + h * i / 3f), stroke)
        }
    }
}

@Composable
private fun PageShutterRow(
    busy: Boolean,
    enabled: Boolean,
    onCapture: () -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onPickPhoto,
            modifier = Modifier.size(52.dp).clip(CircleShape).background(OverlayScrim),
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = "Scan a page from your photos", tint = Color.White)
        }
        Box(
            modifier = Modifier
                .size(76.dp)
                .border(4.dp, Color.White, CircleShape)
                .padding(7.dp)
                .clip(CircleShape)
                .background(if (enabled && !busy) Color.White else Color.White.copy(alpha = 0.4f))
                .clickable(enabled = enabled && !busy, onClick = onCapture)
                .semantics { contentDescription = "Photograph the page" },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        Text(
            "3×3",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.size(52.dp).wrapContentSize(Alignment.Center),
        )
    }
}
