package com.scanrift.android.ui.scanner

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.ui.adaptive.AdaptiveRules
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.theme.Dimens

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
    val torchOn by viewModel.torchEnabled.collectAsStateWithLifecycle()
    val hasFlash by viewModel.hasFlashUnit.collectAsStateWithLifecycle()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showSidebar = AdaptiveRules.useScannerSidebar(maxWidth)
        val largeGuide = AdaptiveRules.useLargeScanGuide(maxWidth, maxHeight)

        if (showSidebar) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    CameraLayer(viewModel, state, torchOn, hasFlash, largeGuide)
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
            Box(Modifier.fillMaxSize()) {
                CameraLayer(viewModel, state, torchOn, hasFlash, largeGuide)
                if (state.results.isNotEmpty()) {
                    Button(
                        onClick = { viewModel.addSessionToCollection() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(24.dp)
                            .fillMaxWidth(0.8f),
                    ) {
                        Text("Add ${state.scannedCount} card${if (state.scannedCount == 1) "" else "s"}")
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraLayer(
    viewModel: ScannerViewModel,
    state: ScannerUiState,
    torchOn: Boolean,
    hasFlash: Boolean,
    largeGuide: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    LaunchedEffect(Unit) {
        viewModel.cameraService.start(context, lifecycleOwner, previewView, viewModel::onFrame)
        viewModel.startScanning()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopScanning()
            viewModel.cameraService.stop()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        CardDetectionOverlay(
            state = state.overlayState,
            guideWidth = if (largeGuide) Dimens.ScanGuideRegularWidth else Dimens.ScanGuideCompactWidth,
            guideHeight = if (largeGuide) Dimens.ScanGuideRegularHeight else Dimens.ScanGuideCompactHeight,
        )

        // Top bar: scanned count and torch.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Scanned: ${state.scannedCount}",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (hasFlash) {
                IconButton(onClick = viewModel::toggleTorch) {
                    Icon(
                        imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = "Torch",
                        tint = Color.White,
                    )
                }
            }
        }

        // Motion status, so it is obvious why a scan is or isn't firing.
        Text(
            text = state.debug.motionStatus,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 56.dp),
        )

        if (state.debugEnabled) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(8.dp),
            ) {
                Text("DEBUG", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
                Text(
                    "conf ${"%.2f".format(state.debug.lastConfidence)} · ${state.debug.lastMatchedCode}",
                    color = Color.Cyan,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    state.debug.ocrText.ifBlank { "(no text)" },
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 6,
                )
            }
        }
    }
}

@Composable
private fun SessionSidebar(
    state: ScannerUiState,
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text(
            text = "Scanned cards",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        HorizontalDivider()

        if (state.results.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Scanned cards will appear here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(state.results, key = { _, item -> item.card.id }) { index, result ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CardThumbnail(
                            card = result.card,
                            quantity = result.quantity,
                            isFoil = result.isFoil,
                            showQuantityBadge = false,
                            modifier = Modifier.width(40.dp).then(Modifier.padding(0.dp)),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(result.card.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                "${result.card.setLabel} • ${result.card.publicCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Text("×${result.quantity}", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { viewModel.remove(index) }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove",
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Button(
            onClick = { viewModel.addSessionToCollection() },
            enabled = state.results.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text("Add to collection")
        }
    }
}
