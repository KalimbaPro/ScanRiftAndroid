package com.scanrift.android.ui.collection

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Search, sort, ownership and filters above the collection grid.
 *
 * Active filters are shown as removable chips rather than only a count, so it is
 * always obvious why the grid is showing fewer cards than expected — the single most
 * common confusion in a filtered list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionToolbar(
    state: CollectionBrowseState,
    onSearchChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onOwnershipChange: (OwnershipFilter) -> Unit,
    onSortChange: (CollectionSortOption) -> Unit,
    onRemoveFilter: (CollectionFilters) -> Unit,
    onClearFilters: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    // The pane hands initial focus to its first focusable child, which is this field —
    // so opening the collection popped the keyboard over half the grid. Drop that
    // focus once, on first composition, without interfering with a real tap.
    LaunchedEffect(Unit) { focusManager.clearFocus(force = true) }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search cards", maxLines = 1) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = onOpenFilters,
                leadingIcon = { Icon(Icons.Filled.FilterList, contentDescription = null) },
                label = {
                    Text(
                        if (state.filters.isEmpty) "Filters" else "Filters (${state.filters.activeCount})",
                    )
                },
            )

            SortMenu(state.sortOption, state.sortAscending, onSortChange)

            OwnershipFilter.entries.forEach { option ->
                FilterChip(
                    selected = state.ownership == option,
                    onClick = { onOwnershipChange(option) },
                    label = { Text(option.label) },
                )
            }
        }

        if (!state.filters.isEmpty) {
            ActiveFilterChips(state.filters, onRemoveFilter, onClearFilters)
        }
    }
}

@Composable
private fun SortMenu(
    current: CollectionSortOption,
    ascending: Boolean,
    onSortChange: (CollectionSortOption) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { open = true },
        leadingIcon = { Icon(Icons.Filled.Sort, contentDescription = null) },
        label = { Text("${current.label} ${if (ascending) "↑" else "↓"}") },
    )
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        CollectionSortOption.entries.forEach { option ->
            DropdownMenuItem(
                text = {
                    // Re-picking the current option flips direction rather than doing
                    // nothing, so one control covers both.
                    Text(if (option == current) "${option.label} (reverse)" else option.label)
                },
                onClick = { onSortChange(option); open = false },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilterChips(
    filters: CollectionFilters,
    onRemoveFilter: (CollectionFilters) -> Unit,
    onClearFilters: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filters.domains.forEach { value ->
            RemovableChip(value) { onRemoveFilter(filters.copy(domains = filters.domains - value)) }
        }
        filters.types.forEach { value ->
            RemovableChip(value) { onRemoveFilter(filters.copy(types = filters.types - value)) }
        }
        filters.rarities.forEach { value ->
            RemovableChip(value) { onRemoveFilter(filters.copy(rarities = filters.rarities - value)) }
        }
        filters.sets.forEach { value ->
            RemovableChip(value) { onRemoveFilter(filters.copy(sets = filters.sets - value)) }
        }
        if (filters.foilOnly) {
            RemovableChip("Foil only") { onRemoveFilter(filters.copy(foilOnly = false)) }
        }
        TextButton(onClick = onClearFilters) {
            Text("Clear all", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $label") },
    )
}
