package com.scanrift.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.core.Constants
import com.scanrift.android.service.sync.BootstrapState
import com.scanrift.android.ui.theme.Dimens
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.ui.platform.LocalLocale
import java.util.Locale

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
    val bootstrap by viewModel.bootstrapState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::backUp) }

    val restoreLauncher = rememberLauncherForActivityResult(
        // Many providers report .json as octet-stream, so the wildcard is necessary.
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::restore) }

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
            StatRow("Cards in database", state.cardCount.toString(), highlight = state.cardCount == 0)
            StatRow("Last updated", state.lastDatabaseSync.formatted())
            BootstrapRow(bootstrap)

            SectionDivider()
            SectionHeader("Scanning")
            ToggleRow("Haptic feedback", state.hapticFeedback, viewModel::setHapticFeedback)
            ToggleRow("Sound feedback", state.soundFeedback, viewModel::setSoundFeedback)
            ToggleRow("Auto-add to collection", state.autoAddToCollection, viewModel::setAutoAddToCollection)

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
            SectionHeader("Collection")
            StatRow("Total cards", state.totalCards.toString())
            StatRow("Unique cards", state.uniqueCards.toString())

            SectionDivider()
            SectionHeader("Backup")
            Text(
                text = "A single JSON file holding your collection, lists, decks and game " +
                    "history. Save it anywhere — a Drive or Dropbox folder syncs it for you. " +
                    "The format matches the iOS app, so a backup from either restores on the other.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ActionRow(
                label = "Back up everything",
                icon = Icons.Filled.CloudUpload,
                onClick = { backupLauncher.launch(Constants.FileNames.BACKUP_SNAPSHOT) },
            )
            ActionRow(
                label = "Restore from backup",
                icon = Icons.Filled.CloudDownload,
                onClick = { restoreLauncher.launch(arrayOf("application/json", "text/json", "*/*")) },
            )
            StatRow("Last backup", state.lastBackup.formatted())

            SectionDivider()
            SectionHeader("Developer")
            ToggleRow("Debug mode", state.debugMode, viewModel::setDebugMode)

            SectionDivider()
            SectionHeader("Danger zone")
            ActionRow(
                label = "Clear collection",
                icon = Icons.Filled.DeleteForever,
                destructive = true,
                onClick = { confirmClear = true },
            )

            SectionDivider()
            SectionHeader("About")
            StatRow("Version", com.scanrift.android.BuildConfig.VERSION_NAME)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear collection?") },
            text = { Text("Every owned card is removed. Decks and lists are kept. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCollection(); confirmClear = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
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
        is DataOperation.BackupComplete -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Backup saved") },
            text = { Text("Wrote ${operation.itemCount} items.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is DataOperation.RestoreComplete -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Restore complete") },
            text = { Text(operation.result.summary) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is DataOperation.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("That didn't work") },
            text = { Text(operation.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}

@Composable
private fun BootstrapRow(state: BootstrapState) {
    val label = when (state) {
        BootstrapState.Idle -> null
        BootstrapState.SeedingBundle -> "Loading the bundled card database…"
        BootstrapState.CheckingForUpdates -> "Checking for updates…"
        is BootstrapState.Syncing -> "Updating set ${state.done} of ${state.total}…"
        is BootstrapState.Ready -> null
        is BootstrapState.Failed -> state.message
    } ?: return

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state is BootstrapState.Failed) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        } else {
            CircularProgressIndicator(modifier = Modifier.padding(2.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
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
private fun SectionDivider() {
    HorizontalDivider(Modifier.padding(top = 12.dp))
}

@Composable
private fun StatRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun Long?.formatted(): String =
    this?.let { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(it)) } ?: "Never"
