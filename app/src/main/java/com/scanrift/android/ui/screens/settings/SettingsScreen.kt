package com.scanrift.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanrift.android.service.import_.ImportResult
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val syncState by viewModel.syncState.collectAsState()
    val cardCount by viewModel.cardCount.collectAsState()
    val totalCollectionCards by viewModel.totalCollectionCards.collectAsState()
    val uniqueCollectionCards by viewModel.uniqueCollectionCards.collectAsState()
    val lastSyncDate by viewModel.lastSyncDate.collectAsState()
    val hapticFeedback by viewModel.hapticFeedback.collectAsState()
    val soundFeedback by viewModel.soundFeedback.collectAsState()
    val autoAddToCollection by viewModel.autoAddToCollection.collectAsState()
    val debugMode by viewModel.debugMode.collectAsState()
    val mergeResult by viewModel.mergeResult.collectAsState()
    val importResult by viewModel.importResult.collectAsState()

    val importContext = LocalContext.current
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val contentResolver = importContext.contentResolver
            val data = contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: return@let
            val fileName = it.lastPathSegment ?: "file"
            val extension = when {
                fileName.endsWith(".json", ignoreCase = true) -> "json"
                fileName.endsWith(".csv", ignoreCase = true) -> "csv"
                fileName.endsWith(".txt", ignoreCase = true) -> "txt"
                else -> "csv"
            }
            viewModel.importCollection(data, extension)
        }
    }

    var showClearConfirmation by remember { mutableStateOf(false) }
    var showMergeConfirmation by remember { mutableStateOf(false) }

    // Auto-reset success state after 3 seconds
    LaunchedEffect(syncState) {
        if (syncState is SyncState.Success) {
            delay(3000)
            viewModel.resetSyncState()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Card Database Section
            SectionHeader("Card Database")
            DatabaseStatusRow(cardCount, lastSyncDate)
            Spacer(Modifier.height(8.dp))
            SyncButton(syncState, onSync = { viewModel.syncDatabase() })
            Text(
                text = "Load the card database to enable card scanning.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            SectionDivider()

            // Scanning Settings
            SectionHeader("Scanning")
            SettingsToggle("Haptic Feedback", hapticFeedback) {
                viewModel.setHapticFeedback(it)
            }
            SettingsToggle("Sound Feedback", soundFeedback) {
                viewModel.setSoundFeedback(it)
            }
            SettingsToggle("Auto-add to Collection", autoAddToCollection) {
                viewModel.setAutoAddToCollection(it)
            }

            SectionDivider()

            // Developer
            SectionHeader("Developer")
            SettingsToggle("Debug Mode", debugMode) {
                viewModel.setDebugMode(it)
            }
            Text(
                text = "Shows OCR output and motion detection status while scanning.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            SectionDivider()

            // Collection Statistics
            SectionHeader("Collection Statistics")
            StatRow("Total Cards", "${totalCollectionCards ?: 0}")
            StatRow("Unique Cards", "$uniqueCollectionCards")

            SectionDivider()

            // Data Management
            SectionHeader("Data Management")
            OutlinedButton(
                onClick = { showMergeConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = (totalCollectionCards ?: 0) > 0
            ) {
                Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Merge Duplicate Cards")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showClearConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = (totalCollectionCards ?: 0) > 0,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Clear Collection")
            }

            SectionDivider()

            // Import / Export
            SectionHeader("Import / Export")
            OutlinedButton(
                onClick = {
                    importFileLauncher.launch(arrayOf(
                        "application/json",
                        "text/csv",
                        "text/comma-separated-values",
                        "text/plain"
                    ))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Import Collection")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.exportAsRiftboundGG(importContext) },
                modifier = Modifier.fillMaxWidth(),
                enabled = (totalCollectionCards ?: 0) > 0
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Export for riftbound.gg")
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { viewModel.exportAsCSV(importContext) },
                modifier = Modifier.fillMaxWidth(),
                enabled = (totalCollectionCards ?: 0) > 0
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Export as CSV")
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { viewModel.exportAsJSON(importContext) },
                modifier = Modifier.fillMaxWidth(),
                enabled = (totalCollectionCards ?: 0) > 0
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Export as JSON")
            }

            SectionDivider()

            // About
            SectionHeader("About")
            StatRow("Version", "1.0.0")
            Spacer(Modifier.height(4.dp))
            val context = LocalContext.current
            TextButton(
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://riftbound.gg")
                    )
                    context.startActivity(intent)
                }
            ) {
                Text("Visit riftbound.gg")
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Clear Collection confirmation dialog
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear Collection") },
            text = {
                Text("Are you sure you want to remove all cards from your collection? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCollection()
                        showClearConfirmation = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Merge confirmation dialog
    if (showMergeConfirmation) {
        AlertDialog(
            onDismissRequest = { showMergeConfirmation = false },
            title = { Text("Merge Duplicates") },
            text = {
                Text("This will combine duplicate collection entries for the same card, adding their quantities together. Continue?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.mergeDuplicates()
                        showMergeConfirmation = false
                    }
                ) {
                    Text("Merge")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Merge result dialog
    mergeResult?.let { count ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMergeResult() },
            title = { Text("Merge Complete") },
            text = {
                Text(
                    if (count > 0) "Merged $count duplicate entries."
                    else "No duplicates found."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissMergeResult() }) {
                    Text("OK")
                }
            }
        )
    }

    // Import result dialog
    importResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportResult() },
            title = { Text("Import Complete") },
            text = {
                Column {
                    Text(result.summary)
                    if (result.skippedNames.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Skipped ${result.skippedNames.size} cards",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImportResult() }) {
                    Text("OK")
                }
            }
        )
    }

    // Sync error dialog
    if (syncState is SyncState.Error) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Load Error") },
            text = { Text((syncState as SyncState.Error).message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun DatabaseStatusRow(cardCount: Int, lastSyncTimestamp: Long?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Cards in Database", style = MaterialTheme.typography.bodyMedium)
            val syncText = if (lastSyncTimestamp != null) {
                val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                "Last loaded: ${dateFormat.format(Date(lastSyncTimestamp))}"
            } else {
                "Never loaded"
            }
            Text(
                text = syncText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "$cardCount",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (cardCount == 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SyncButton(syncState: SyncState, onSync: () -> Unit) {
    when (syncState) {
        is SyncState.Idle -> {
            Button(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Load Card Database")
            }
        }

        is SyncState.Syncing -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(12.dp))
                val text = if (syncState.totalPages > 0) {
                    "Loading... (${syncState.page}/${syncState.totalPages})"
                } else {
                    "Loading..."
                }
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }

        is SyncState.Success -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Loaded ${syncState.cardCount} cards!")
            }
        }

        is SyncState.Error -> {
            Button(
                onClick = onSync,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Retry Load")
            }
        }
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
