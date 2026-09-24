package com.scanrift.android.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.Domain
import com.scanrift.android.domain.model.Rarity

class SelectionActions(
    val onExit: () -> Unit,
    val onExportRiftboundGg: () -> Unit,
    val onExportCsv: () -> Unit,
    val onExportJson: () -> Unit,
    val onAddToCollection: () -> Unit,
    val onAddToList: () -> Unit,
    val onToggleSelectAll: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionTopBar(
    state: CollectionBrowseState,
    onBack: () -> Unit,
    onOpenFilters: () -> Unit,
    onOwnershipChange: (OwnershipFilter) -> Unit,
    onSortChange: (CollectionSortOption) -> Unit,
    onToggleDirection: () -> Unit,
    onViewModeChange: (CollectionViewMode) -> Unit,
    onShowUnownedInColorChange: (Boolean) -> Unit,
    selection: SelectionActions,
) {
    val controls = state.controls
    val title = state.listContext?.name ?: "Collection"
    if (controls.isSelecting) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = selection.onExit) { Icon(Icons.Filled.Close, contentDescription = "Done selecting") }
            },
            actions = {
                SelectionShareMenu(state, selection)
                TextButton(onClick = selection.onToggleSelectAll) {
                    Text(if (state.allSelected) "Deselect All" else "Select All")
                }
            },
        )
        return
    }
    TopAppBar(
        windowInsets = WindowInsets(0),
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onOpenFilters) {
                Icon(
                    if (controls.filters.isEmpty) Icons.Outlined.FilterAlt else Icons.Filled.FilterAlt,
                    contentDescription = "Filters",
                )
            }
            OwnershipMenu(controls.ownership, onOwnershipChange) { open ->
                IconButton(onClick = open) {
                    Icon(ownershipIcon(controls.ownership), contentDescription = "Ownership")
                }
            }
            SortViewMenu(state, onSortChange, onToggleDirection, onViewModeChange, onShowUnownedInColorChange)
        },
    )
}

@Composable
private fun SelectionShareMenu(state: CollectionBrowseState, selection: SelectionActions) {
    var open by remember { mutableStateOf(false) }
    val hasSelection = state.controls.selectedIds.isNotEmpty()
    Box {
        IconButton(onClick = { open = true }, enabled = hasSelection) {
            Icon(Icons.Filled.Share, contentDescription = "Share selection")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun run(action: () -> Unit): () -> Unit = { open = false; action() }
            MenuAction("Export for riftbound.gg", onClick = run(selection.onExportRiftboundGg))
            MenuAction("Export as CSV", onClick = run(selection.onExportCsv))
            MenuAction("Export as JSON", onClick = run(selection.onExportJson))
            if (state.selectedRows.any { !it.isOwned }) {
                HorizontalDivider()
                MenuAction("Add to Collection", Icons.Filled.Add, run(selection.onAddToCollection))
            }
            HorizontalDivider()
            MenuAction("Add to List", Icons.Filled.CreateNewFolder, run(selection.onAddToList))
        }
    }
}

@Composable
private fun MenuAction(label: String, icon: ImageVector? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null) } },
        onClick = onClick,
    )
}

@Composable
private fun OwnershipMenu(
    current: OwnershipFilter,
    onChange: (OwnershipFilter) -> Unit,
    anchor: @Composable (open: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        anchor { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            OwnershipFilter.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = if (option == current) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                    onClick = { open = false; onChange(option) },
                )
            }
        }
    }
}

@Composable
private fun SortViewMenu(
    state: CollectionBrowseState,
    onSortChange: (CollectionSortOption) -> Unit,
    onToggleDirection: () -> Unit,
    onViewModeChange: (CollectionViewMode) -> Unit,
    onShowUnownedInColorChange: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val controls = state.controls
    Box {
        IconButton(onClick = { open = true }) {
            Icon(viewModeIcon(controls.viewMode), contentDescription = "Sort and view")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortItems(controls, onSortChange, onToggleDirection)
            HorizontalDivider()
            MenuHeader("View Mode")
            CollectionViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    leadingIcon = { Icon(viewModeIcon(mode), contentDescription = null) },
                    trailingIcon = if (mode == controls.viewMode) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                    onClick = { onViewModeChange(mode) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Show colors for unowned") },
                leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                trailingIcon = {
                    Checkbox(checked = state.prefs.showUnownedInColor, onCheckedChange = null)
                },
                onClick = { onShowUnownedInColorChange(!state.prefs.showUnownedInColor) },
            )
        }
    }
}

@Composable
private fun SortItems(
    controls: CollectionControls,
    onSortChange: (CollectionSortOption) -> Unit,
    onToggleDirection: () -> Unit,
) {
    MenuHeader("Sort By")
    CollectionSortOption.entries.forEach { option ->
        DropdownMenuItem(
            text = { Text(option.label) },
            trailingIcon = if (option == controls.sortOption) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
            onClick = { onSortChange(option) },
        )
    }
    DropdownMenuItem(
        text = { Text(if (controls.sortAscending) "Ascending" else "Descending") },
        leadingIcon = { Icon(directionIcon(controls.sortAscending), contentDescription = null) },
        onClick = onToggleDirection,
    )
}

@Composable
private fun MenuHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun CollectionSearchField(query: String, onChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current

    // The pane hands initial focus to its first focusable child, which is this field —
    // so opening the collection popped the keyboard over half the grid. Drop that
    // focus once, on first composition, without interfering with a real tap.
    LaunchedEffect(Unit) { focusManager.clearFocus(force = true) }
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Search cards", maxLines = 1) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
fun SearchOptionsBar(
    controls: CollectionControls,
    onSortChange: (CollectionSortOption) -> Unit,
    onToggleDirection: () -> Unit,
    onOwnershipChange: (OwnershipFilter) -> Unit,
    onOpenFilters: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        var sortOpen by remember { mutableStateOf(false) }
        Box {
            OptionCapsule("Sort: ${controls.sortOption.label}", directionIcon(controls.sortAscending)) { sortOpen = true }
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                SortItems(controls, onSortChange, onToggleDirection)
            }
        }
        OwnershipMenu(controls.ownership, onOwnershipChange) { open ->
            OptionCapsule(
                controls.ownership.label,
                if (controls.ownership == OwnershipFilter.OWNED) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                onClick = open,
            )
        }
        OptionCapsule(
            "Filters",
            if (controls.filters.isEmpty) Icons.Outlined.FilterAlt else Icons.Filled.FilterAlt,
            onClick = onOpenFilters,
        )
    }
}

@Composable
private fun OptionCapsule(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

/**
 * Active filters are shown as removable chips rather than only a count, so it is
 * always obvious why the grid is showing fewer cards than expected — the single most
 * common confusion in a filtered list.
 */
@Composable
fun ActiveFiltersBar(filters: CollectionFilters, onChange: (CollectionFilters) -> Unit, onClearAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filters.domains.sortedBy { Domain.filterOrder.indexOf(it) }.forEach {
            RemovableChip(it) { onChange(filters.copy(domains = filters.domains - it)) }
        }
        filters.types.sorted().forEach { RemovableChip(it) { onChange(filters.copy(types = filters.types - it)) } }
        filters.supertypes.sorted().forEach {
            RemovableChip(it) { onChange(filters.copy(supertypes = filters.supertypes - it)) }
        }
        if (filters.foilOnly) RemovableChip("Foil Only") { onChange(filters.copy(foilOnly = false)) }
        filters.rarities.sortedBy { Rarity.sortRank(it) }.forEach {
            RemovableChip(it) { onChange(filters.copy(rarities = filters.rarities - it)) }
        }
        filters.sets.sorted().forEach { RemovableChip(it) { onChange(filters.copy(sets = filters.sets - it)) } }
        if (filters.hasEnergyRange) {
            RemovableChip("Energy: ${filters.energyMin ?: 0}–${filters.energyMax ?: 99}") {
                onChange(filters.copy(energyMin = null, energyMax = null))
            }
        }
        if (filters.hasPowerRange) {
            RemovableChip("Power: ${filters.powerMin ?: 0}–${filters.powerMax ?: 99}") {
                onChange(filters.copy(powerMin = null, powerMax = null))
            }
        }
        filters.artists.sorted().forEach { RemovableChip(it) { onChange(filters.copy(artists = filters.artists - it)) } }
        Text(
            "Clear All",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.clickable(onClick = onClearAll).padding(4.dp),
        )
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = accent)
        Icon(
            Icons.Filled.Cancel,
            contentDescription = "Remove $label",
            tint = accent,
            modifier = Modifier.size(14.dp).clip(CircleShape).clickable(onClick = onRemove),
        )
    }
}

@Composable
fun InfoBar(state: CollectionBrowseState, modifier: Modifier = Modifier, horizontalPadding: Dp = 16.dp) {
    val controls = state.controls
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val caption = MaterialTheme.typography.labelMedium
        Column {
            Row {
                Text("${state.ownedCount} owned", style = caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (controls.ownership != OwnershipFilter.OWNED) {
                    Text(
                        " / ${state.cards.size} total",
                        style = caption,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (controls.isSelecting && controls.selectedIds.isNotEmpty()) {
                Text("${controls.selectedIds.size} selected", style = caption, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.weight(1f))
        when (controls.ownership) {
            OwnershipFilter.ALL -> "Showing all cards"
            OwnershipFilter.UNOWNED -> "Showing unowned"
            OwnershipFilter.OWNED -> null
        }?.let { Text(it, style = caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun ownershipIcon(filter: OwnershipFilter): ImageVector =
    if (filter == OwnershipFilter.OWNED) Icons.Filled.VisibilityOff else Icons.Filled.Visibility

private fun viewModeIcon(mode: CollectionViewMode): ImageVector = when (mode) {
    CollectionViewMode.GRID -> Icons.Filled.GridView
    CollectionViewMode.LIST -> Icons.AutoMirrored.Filled.List
}

private fun directionIcon(ascending: Boolean): ImageVector =
    if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward
