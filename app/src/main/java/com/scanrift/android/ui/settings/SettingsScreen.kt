package com.scanrift.android.ui.settings

import com.scanrift.android.ui.util.shareFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.BuildConfig
import com.scanrift.android.core.Constants
import com.scanrift.android.domain.model.ScoreInputMode
import com.scanrift.android.service.sync.BootstrapState
import com.scanrift.android.ui.theme.Dimens
import com.scanrift.android.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Settings.
 *
 * Same sections as iOS and in the same order, except that its two iCloud sections are
 * replaced by one **Backup** section. The destination is whatever the system file
 * picker returns, so putting the file in a Drive or Dropbox folder is the user's
 * choice rather than something this app has to implement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::backUp) }

    val restoreLauncher = rememberLauncherForActivityResult(
        // Many providers report .json as octet-stream, so the wildcard is necessary.
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::restore) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importCollection) }

    LaunchedEffect(viewModel) {
        viewModel.sharedExports.collect { export -> context.shareFile(export.file, export.mimeType) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Settings") }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .widthIn(max = Dimens.SettingsMaxWidth),
        ) {
            SectionHeader("Card Database")
            DatabaseStatusRow(state.cardCount, state.lastDatabaseSync)
            SyncRow(
                state = syncState,
                catalogueIsEmpty = state.cardCount == 0,
                onLoad = viewModel::loadCardDatabase,
                onCheckForUpdates = viewModel::checkForUpdates,
                onSettled = viewModel::resetSyncState,
            )
            SectionFooter("Load the card database to enable card scanning.")

            SectionDivider()
            SectionHeader("Scanning")
            ToggleRow("Haptic Feedback", state.hapticFeedback, viewModel::setHapticFeedback)
            ToggleRow("Sound Feedback", state.soundFeedback, viewModel::setSoundFeedback)
            ToggleRow("Auto-add to Collection", state.autoAddToCollection, viewModel::setAutoAddToCollection)

            SectionDivider()
            SectionHeader("Appearance")
            ToggleRow(
                title = "Show unowned cards in colour",
                subtitle = "Unowned cards stay dimmed either way",
                checked = state.showUnownedInColor,
                onCheckedChange = viewModel::setShowUnownedInColor,
            )
            ToggleRow(
                title = "Use system colours",
                subtitle = "Material You. Off by default because wallpaper colours clash with the domain palette",
                checked = state.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )

            SectionDivider()
            SectionHeader("Game")
            ScoreInputModeRow(state.scoreInputMode, viewModel::setScoreInputMode)

            SectionDivider()
            SectionHeader("Developer")
            ToggleRow("Debug Mode", state.debugMode, viewModel::setDebugMode)
            ToggleRow("Lucho Parameter", state.luchoParameter, viewModel::setLuchoParameter)
            SectionFooter("Shows OCR output and motion detection status while scanning.")

            SectionDivider()
            SectionHeader("Collection Statistics")
            StatRow("Total Cards", state.totalCards.toString())
            StatRow("Unique Cards", state.uniqueCards.toString())

            SectionDivider()
            SectionHeader("Data Management")
            ActionRow(
                label = "Clear Collection",
                icon = Icons.Filled.DeleteForever,
                destructive = true,
                enabled = state.hasEntries,
                onClick = { confirmClear = true },
            )

            SectionDivider()
            SectionHeader("Import")
            ActionRow(
                label = "Import Collection",
                icon = Icons.Filled.FileOpen,
                enabled = state.cardCount > 0,
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
            )

            SectionDivider()
            SectionHeader("Export")
            ActionRow(
                label = "Export for riftbound.gg",
                icon = Icons.Filled.UploadFile,
                enabled = state.hasEntries,
                onClick = { viewModel.export(ExportFormat.RIFTBOUND_GG) },
            )
            ActionRow(
                label = "Export as CSV",
                icon = Icons.Filled.TableChart,
                enabled = state.hasEntries,
                onClick = { viewModel.export(ExportFormat.CSV) },
            )
            ActionRow(
                label = "Export All as JSON",
                icon = Icons.Filled.Description,
                enabled = state.hasEntries || state.hasDecks,
                onClick = { viewModel.export(ExportFormat.JSON) },
            )
            SectionFooter(
                "\"Export All as JSON\" includes your collection plus every deck and its game history. " +
                    "CSV exports cover the collection only.",
            )

            SectionDivider()
            SectionHeader("Backup")
            ToggleRow(
                title = "Automatic backup",
                subtitle = "Save a snapshot to your backup file every 5 minutes.",
                checked = state.cloudSnapshotAutoSync,
                onCheckedChange = viewModel::setCloudSnapshotAutoSync,
            )
            ActionRow(
                label = "Back Up Now",
                icon = Icons.Filled.CloudUpload,
                onClick = { viewModel.backUpNow { backupLauncher.launch(Constants.FileNames.BACKUP_SNAPSHOT) } },
            )
            ActionRow(
                label = "Restore from Backup",
                icon = Icons.Filled.CloudDownload,
                onClick = { restoreLauncher.launch(arrayOf("application/json", "text/json", "*/*")) },
            )
            ActionRow(
                label = "Choose Backup File",
                icon = Icons.Filled.FolderOpen,
                onClick = { backupLauncher.launch(Constants.FileNames.BACKUP_SNAPSHOT) },
            )
            StatRow("Backup File", state.backupFileName ?: "Not chosen")
            StatRow("Last Backup", state.lastBackup.formatted())
            SectionFooter(
                "A JSON snapshot of your collection, lists, decks and game history, saved to a file you " +
                    "choose. Put it in a Drive or Dropbox folder to keep it off this device. The format " +
                    "matches the iOS app, so a backup from either restores on the other. Restoring merges " +
                    "and never deletes anything.",
            )

            SectionDivider()
            SectionHeader("About")
            StatRow("Version", BuildConfig.VERSION_NAME)
            ActionRow(
                label = "Riftbound Website",
                icon = Icons.Filled.Language,
                onClick = { uriHandler.openUri("https://riftbound.com") },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear Collection") },
            text = { Text("Are you sure you want to remove all cards from your collection? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCollection(); confirmClear = false }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    (syncState as? BootstrapState.Failed)?.let { failed ->
        MessageDialog("Load Error", failed.message, viewModel::dismissSyncError)
    }

    OperationDialog(operation, viewModel::dismissOperation)
}

@Composable
private fun OperationDialog(operation: DataOperation, onDismiss: () -> Unit) {
    when (operation) {
        DataOperation.None -> Unit
        DataOperation.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Working…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("This can take a moment for a large collection.")
                }
            },
            confirmButton = {},
        )
        is DataOperation.ImportComplete -> MessageDialog("Import Complete", operation.message, onDismiss)
        is DataOperation.ImportFailed -> MessageDialog("Import Error", operation.message, onDismiss)
        is DataOperation.RestoreComplete -> MessageDialog("Restore from Backup", operation.message, onDismiss)
        is DataOperation.BackupFailed -> MessageDialog("Backup Failed", operation.message, onDismiss)
    }
}

@Composable
private fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = message.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun DatabaseStatusRow(cardCount: Int, lastLoaded: Long?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cards in Database", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = lastLoaded?.let { "Last loaded: ${it.formatted()}" } ?: "Never loaded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = cardCount.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (cardCount == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SyncRow(
    state: BootstrapState,
    catalogueIsEmpty: Boolean,
    onLoad: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onSettled: () -> Unit,
) {
    val sync = if (catalogueIsEmpty) onLoad else onCheckForUpdates
    when (state) {
        BootstrapState.Idle -> ActionRow(
            label = if (catalogueIsEmpty) "Load Card Database" else "Check for Updates",
            icon = Icons.Filled.Refresh,
            onClick = sync,
        )
        BootstrapState.CheckingForUpdates -> ProgressRow("Checking for updates…")
        BootstrapState.SeedingBundle -> ProgressRow("Loading…")
        is BootstrapState.Syncing -> ProgressRow(
            if (state.total > 0) "Updating set ${state.done} of ${state.total}…" else "Loading…",
        )
        BootstrapState.UpToDate -> SettledRow("Up to date", onSettled)
        is BootstrapState.Updated -> SettledRow(
            "Updated ${state.cardCount} card${if (state.cardCount == 1) "" else "s"}!",
            onSettled,
        )
        is BootstrapState.Failed -> ActionRow(label = "Retry", icon = Icons.Filled.Refresh, destructive = true, onClick = sync)
    }
}

@Composable
private fun ProgressRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettledRow(label: String, onSettled: () -> Unit) {
    LaunchedEffect(label) {
        delay(SETTLED_DISPLAY_MS)
        onSettled()
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SectionHeader(title: String) {
    // `LocalLocale`, not `Locale.getDefault()`: the latter is not observable state, so
    // a header uppercased at first composition would keep the old locale's casing after
    // the user changes language. Turkish is the case that actually differs.
    Text(
        text = title.uppercase(LocalLocale.current.platformLocale),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SectionFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(Modifier.padding(top = 12.dp))
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The point tracker's two seat layouts.
 *
 * A segmented row rather than a switch: neither option is "on", and the description
 * under it has to change with the choice for the labels to mean anything.
 */
@Composable
private fun ScoreInputModeRow(
    selected: ScoreInputMode,
    onSelect: (ScoreInputMode) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Scoring Layout", style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            ScoreInputMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ScoreInputMode.entries.size),
                ) { Text(mode.displayName) }
            }
        }
        Text(
            selected.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(
    label: String,
    icon: ImageVector,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val content = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            color = if (enabled) content else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
        )
    }
}

private const val SETTLED_DISPLAY_MS = 3_000L
private const val DISABLED_ALPHA = 0.38f

private fun Long?.formatted(): String =
    this?.let { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(it)) } ?: "Never"
