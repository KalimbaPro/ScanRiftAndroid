package com.scanrift.android.ui.screens.scanner

import android.Manifest
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.service.scanning.CameraService
import com.scanrift.android.ui.components.QuantityStepper
import com.scanrift.android.util.Constants

@ExperimentalGetImage
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = viewModel()
) {
    val context = LocalContext.current
    val isScanning by viewModel.isScanning.collectAsState()
    val lastScannedCard by viewModel.lastScannedCard.collectAsState()
    val scanSession by viewModel.scanSession.collectAsState()
    val debugState by viewModel.debugState.collectAsState()
    val debugMode by viewModel.debugMode.collectAsState()
    val showCorrectionSheet by viewModel.showCorrectionSheet.collectAsState()
    val showSessionSummary by viewModel.showSessionSummary.collectAsState()
    val overlayState by viewModel.overlayState.collectAsState()

    // Simple permission check using a remembered state
    var hasCameraPermission by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }

    // Check permission on launch
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionRequested = true
    }

    if (!permissionRequested && !hasCameraPermission) {
        // Request permission
        androidx.compose.runtime.LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission && permissionRequested) {
        // Permission denied
        CameraPermissionDenied(
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
        )
        return
    }

    if (!hasCameraPermission) {
        // Waiting for permission result
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Requesting camera permission...")
        }
        return
    }

    // Camera + Scanner UI
    val cameraService = remember { CameraService() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        viewModel.startScanning()
        onDispose {
            viewModel.stopScanning()
            cameraService.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    cameraService.startCamera(
                        context = ctx,
                        lifecycleOwner = lifecycleOwner,
                        previewView = previewView,
                        onFrameAnalyzed = { imageProxy ->
                            viewModel.processFrame(imageProxy)
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Card detection overlay
        CardDetectionOverlay(
            state = overlayState,
            modifier = Modifier.fillMaxSize()
        )

        // Top overlay: status and controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Scan count
                Text(
                    text = "Scanned: ${scanSession.totalScanned}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )

                Row {
                    // Session summary
                    IconButton(onClick = { viewModel.showSessionSummary() }) {
                        Icon(Icons.Default.List, contentDescription = "Session", tint = Color.White)
                    }

                    // Torch toggle
                    IconButton(onClick = { cameraService.toggleTorch() }) {
                        Icon(
                            if (cameraService.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle torch",
                            tint = Color.White
                        )
                    }
                }
            }

            // Motion status
            Text(
                text = debugState.motionStatus,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Debug overlay
        if (debugMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 100.dp, start = 8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text("OCR:", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
                Text(
                    debugState.ocrText.take(200),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
                Text("Match: ${debugState.lastMatchedCode}", color = Color.Green, style = MaterialTheme.typography.labelSmall)
            }
        }

        // Bottom overlay: last scanned card
        lastScannedCard?.let { card ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = card.imageUrl,
                        contentDescription = card.name,
                        modifier = Modifier
                            .size(60.dp)
                            .aspectRatio(Constants.UI.CARD_ASPECT_RATIO)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(card.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${card.setLabel} \u2022 ${card.publicCode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { viewModel.showCorrection() }) {
                        Icon(Icons.Default.Edit, contentDescription = "Correct")
                    }
                }
            }
        }
    }

    // Correction bottom sheet
    if (showCorrectionSheet) {
        CardCorrectionSheet(
            onDismiss = { viewModel.dismissCorrection() },
            onCardSelected = { viewModel.selectCorrectedCard(it) },
            searchCards = { viewModel.searchCards(it) }
        )
    }

    // Session summary
    if (showSessionSummary) {
        SessionSummarySheet(
            session = scanSession,
            onDismiss = { viewModel.dismissSessionSummary() },
            onUpdateQuantity = { index, qty -> viewModel.updateQuantity(index, qty) },
            onToggleFoil = { index -> viewModel.toggleFoil(index) },
            onRemove = { index -> viewModel.removeScannedCard(index) },
            onAddToCollection = { viewModel.addSessionToCollection() }
        )
    }
}

@Composable
private fun CameraPermissionDenied(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "ScanRift needs camera access to scan trading cards.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardCorrectionSheet(
    onDismiss: () -> Unit,
    onCardSelected: (CardEntity) -> Unit,
    searchCards: (String) -> List<CardEntity>
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CardEntity>>(emptyList()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Select Correct Card",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    results = searchCards(it)
                },
                label = { Text("Search by name or code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn {
                items(results) { card ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCardSelected(card) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = card.imageUrl,
                            contentDescription = card.name,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(card.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${card.setLabel} \u2022 ${card.publicCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionSummarySheet(
    session: ScanSession,
    onDismiss: () -> Unit,
    onUpdateQuantity: (Int, Int) -> Unit = { _, _ -> },
    onToggleFoil: (Int) -> Unit = {},
    onRemove: (Int) -> Unit = {},
    onAddToCollection: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Session Summary",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "${session.totalScanned} cards scanned (${session.results.size} unique)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(session.results.size) { index ->
                    val result = session.results[index]
                    val card = result.card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thumbnail
                        AsyncImage(
                            model = card.imageUrl,
                            contentDescription = card.name,
                            modifier = Modifier
                                .size(48.dp)
                                .aspectRatio(Constants.UI.CARD_ASPECT_RATIO)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Spacer(Modifier.width(10.dp))

                        // Card info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                card.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${card.setLabel} \u2022 ${card.publicCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    card.rarity,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Foil toggle
                        IconButton(
                            onClick = { onToggleFoil(index) },
                            enabled = !card.isAlwaysFoil,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Foil",
                                tint = if (result.isFoil) Color(0xFFFFD60A)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Quantity stepper
                        QuantityStepper(
                            quantity = result.quantity,
                            onQuantityChange = { onUpdateQuantity(index, it) },
                            minValue = 1
                        )

                        // Delete
                        IconButton(
                            onClick = { onRemove(index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Add to Collection button
            if (session.results.isNotEmpty()) {
                Button(
                    onClick = {
                        onAddToCollection()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add All to Collection")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

