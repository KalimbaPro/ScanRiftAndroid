package com.scanrift.android.ui.screens.collection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.components.FoilOverlay
import com.scanrift.android.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    listId: String? = null,
    viewModel: CollectionViewModel = viewModel(),
    onCardClick: (CardEntity) -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    // Set list context when screen opens
    LaunchedEffect(listId) {
        viewModel.setListContext(listId)
    }

    val displayedCards by viewModel.displayedCards.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val ownershipFilter by viewModel.ownershipFilter.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val selectedCardIds by viewModel.selectedCardIds.collectAsState()
    val ownedQuantities by viewModel.ownedQuantities.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onSearch = { isSearchActive = false },
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text("Search cards...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {}

        // Toolbar: filter chips, view mode toggle, sort
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filter button
            AssistChip(
                onClick = { showFilterSheet = true },
                label = {
                    val count = filterState.activeFilterCount
                    Text(if (count > 0) "Filters ($count)" else "Filters")
                },
                leadingIcon = {
                    Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )

            Spacer(Modifier.width(8.dp))

            // Ownership filter chips
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OwnershipFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = ownershipFilter == filter,
                        onClick = { viewModel.setOwnershipFilter(filter) },
                        label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Sort button
            IconButton(onClick = { showSortMenu = true }) {
                Icon(Icons.Default.Sort, contentDescription = "Sort")
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(option.label)
                                if (sortOption == option) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        if (sortAscending) Icons.Default.ArrowUpward
                                        else Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            viewModel.setSortOption(option)
                            showSortMenu = false
                        }
                    )
                }
            }

            // View mode toggle
            IconButton(onClick = {
                viewModel.setViewMode(
                    if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                )
            }) {
                Icon(
                    if (viewMode == ViewMode.GRID) Icons.Default.ViewList
                    else Icons.Default.GridView,
                    contentDescription = "Toggle view"
                )
            }

            // Multi-select toggle
            IconButton(onClick = { viewModel.toggleMultiSelect() }) {
                Icon(
                    Icons.Default.CheckBox,
                    contentDescription = "Multi-select",
                    tint = if (isMultiSelectMode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Active filter pills
        AnimatedVisibility(visible = filterState.isActive) {
            ActiveFilterPills(
                filterState = filterState,
                onRemoveDomain = { viewModel.toggleDomainFilter(it) },
                onRemoveType = { viewModel.toggleTypeFilter(it) },
                onRemoveRarity = { viewModel.toggleRarityFilter(it) },
                onRemoveSet = { viewModel.toggleSetFilter(it) },
                onClearAll = { viewModel.clearFilters() }
            )
        }

        // Card count
        Text(
            text = "${displayedCards.size} cards",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Content
        if (displayedCards.isEmpty()) {
            EmptyCollectionView(
                hasFilters = filterState.isActive || searchQuery.isNotBlank()
            )
        } else {
            when (viewMode) {
                ViewMode.GRID -> CollectionGridView(
                    cards = displayedCards,
                    ownedQuantities = ownedQuantities,
                    isMultiSelectMode = isMultiSelectMode,
                    selectedCardIds = selectedCardIds,
                    onCardClick = { card ->
                        if (isMultiSelectMode) {
                            viewModel.toggleCardSelection(card.id)
                        } else {
                            onCardClick(card)
                        }
                    }
                )

                ViewMode.LIST -> CollectionListView(
                    cards = displayedCards,
                    ownedQuantities = ownedQuantities,
                    isMultiSelectMode = isMultiSelectMode,
                    selectedCardIds = selectedCardIds,
                    onCardClick = { card ->
                        if (isMultiSelectMode) {
                            viewModel.toggleCardSelection(card.id)
                        } else {
                            onCardClick(card)
                        }
                    }
                )
            }
        }
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            filterState = filterState,
            availableSets = viewModel.availableSets.collectAsState().value,
            availableTypes = viewModel.availableTypes.collectAsState().value,
            availableRarities = viewModel.availableRarities.collectAsState().value,
            onDomainToggle = { viewModel.toggleDomainFilter(it) },
            onTypeToggle = { viewModel.toggleTypeFilter(it) },
            onRarityToggle = { viewModel.toggleRarityFilter(it) },
            onSetToggle = { viewModel.toggleSetFilter(it) },
            onClearAll = { viewModel.clearFilters() },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
fun CollectionGridView(
    cards: List<CardEntity>,
    ownedQuantities: Map<String, Int>,
    isMultiSelectMode: Boolean,
    selectedCardIds: Set<String>,
    onCardClick: (CardEntity) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = Constants.UI.GRID_ITEM_MIN_WIDTH_DP.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(cards, key = { it.id }) { card ->
            CardThumbnail(
                card = card,
                quantity = ownedQuantities[card.id] ?: 0,
                isSelected = card.id in selectedCardIds,
                onClick = { onCardClick(card) }
            )
        }
    }
}

@Composable
fun CollectionListView(
    cards: List<CardEntity>,
    ownedQuantities: Map<String, Int>,
    isMultiSelectMode: Boolean,
    selectedCardIds: Set<String>,
    onCardClick: (CardEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(cards, key = { it.id }) { card ->
            CardListRow(
                card = card,
                quantity = ownedQuantities[card.id] ?: 0,
                isSelected = card.id in selectedCardIds,
                onClick = { onCardClick(card) }
            )
        }
    }
}

@Composable
private fun CardListRow(
    card: CardEntity,
    quantity: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isOwned = quantity > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Small thumbnail with foil overlay and greyscale for unowned
        Box(
            modifier = Modifier
                .size(40.dp)
                .aspectRatio(Constants.UI.CARD_ASPECT_RATIO)
                .clip(RoundedCornerShape(4.dp))
        ) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                colorFilter = if (!isOwned) ColorFilter.colorMatrix(
                    ColorMatrix().apply { setToSaturation(0f) }
                ) else null,
                alpha = if (!isOwned) 0.5f else 1f,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
            )
            if (isOwned && card.isAlwaysFoil) {
                FoilOverlay(modifier = Modifier.clip(RoundedCornerShape(4.dp)))
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                card.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (!isOwned) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${card.setLabel} \u2022 ${card.publicCode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isOwned) {
                Text(
                    "Not owned",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        if (quantity > 0) {
            Text(
                "\u00D7$quantity",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun EmptyCollectionView(hasFilters: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (hasFilters) "No cards match your filters" else "No cards yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasFilters) "Try adjusting your filters or search query."
            else "Load the card database from Settings to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActiveFilterPills(
    filterState: FilterState,
    onRemoveDomain: (String) -> Unit,
    onRemoveType: (String) -> Unit,
    onRemoveRarity: (String) -> Unit,
    onRemoveSet: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        filterState.domains.forEach { domain ->
            FilterChip(
                selected = true,
                onClick = { onRemoveDomain(domain) },
                label = { Text(domain) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
            )
        }
        filterState.types.forEach { type ->
            FilterChip(
                selected = true,
                onClick = { onRemoveType(type) },
                label = { Text(type) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
            )
        }
        filterState.rarities.forEach { rarity ->
            FilterChip(
                selected = true,
                onClick = { onRemoveRarity(rarity) },
                label = { Text(rarity) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
            )
        }
        filterState.sets.forEach { set ->
            FilterChip(
                selected = true,
                onClick = { onRemoveSet(set) },
                label = { Text(set) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
            )
        }
        if (filterState.isActive) {
            AssistChip(
                onClick = onClearAll,
                label = { Text("Clear all") }
            )
        }
    }
}

