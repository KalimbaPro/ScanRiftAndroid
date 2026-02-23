package com.scanrift.android.ui.screens.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.data.local.entity.DeckEntryWithCard
import com.scanrift.android.data.local.entity.DeckSection
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.util.Constants

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeckBuilderScreen(
    deckId: String,
    viewModel: DeckBuilderViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    LaunchedEffect(deckId) {
        viewModel.loadDeck(deckId)
    }

    val deck by viewModel.currentDeck.collectAsState()
    val entries by viewModel.deckEntries.collectAsState()
    val legend by viewModel.legendCard.collectAsState()
    val champion by viewModel.championCard.collectAsState()
    val validationErrors by viewModel.validationErrors.collectAsState()
    val browserCards by viewModel.browserCards.collectAsState()
    val browserSearchQuery by viewModel.browserSearchQuery.collectAsState()
    val browserTypeFilter by viewModel.browserTypeFilter.collectAsState()

    // Phone layout: tabs for Cards / Deck
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Cards", "Deck")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deck?.name ?: "Deck Builder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // TODO: share export
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Validation banner
            if (validationErrors.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            validationErrors.take(3).forEach { error ->
                                Text(
                                    error.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            if (validationErrors.size > 3) {
                                Text(
                                    "+${validationErrors.size - 3} more issues",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Tab selector
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                tabs.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size)
                    ) {
                        Text(label)
                    }
                }
            }

            when (selectedTab) {
                0 -> CardBrowserTab(
                    cards = browserCards,
                    searchQuery = browserSearchQuery,
                    typeFilter = browserTypeFilter,
                    onSearchChange = { viewModel.setBrowserSearchQuery(it) },
                    onTypeFilterChange = { viewModel.setBrowserTypeFilter(it) },
                    onAddCard = { card, section -> viewModel.addCardToDeck(card, section) },
                    canAddCard = { card, section -> viewModel.canAddCard(card, section) }
                )
                1 -> DeckEditorTab(
                    entries = entries,
                    legend = legend,
                    champion = champion,
                    onRemoveEntry = { viewModel.removeCardFromDeck(it.entry) },
                    onSetLegend = { /* navigate to legend picker */ },
                    onSetChampion = { /* navigate to champion picker */ }
                )
            }
        }
    }
}

@Composable
private fun CardBrowserTab(
    cards: List<CardEntity>,
    searchQuery: String,
    typeFilter: String?,
    onSearchChange: (String) -> Unit,
    onTypeFilterChange: (String?) -> Unit,
    onAddCard: (CardEntity, DeckSection) -> Unit,
    canAddCard: (CardEntity, DeckSection) -> Boolean
) {
    val types = listOf("Unit", "Spell", "Rune", "Battlefield")

    Column(modifier = Modifier.fillMaxSize()) {
        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Search cards") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        // Type filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = typeFilter == null,
                onClick = { onTypeFilterChange(null) },
                label = { Text("All") }
            )
            types.forEach { type ->
                FilterChip(
                    selected = typeFilter == type,
                    onClick = { onTypeFilterChange(if (typeFilter == type) null else type) },
                    label = { Text(type) }
                )
            }
        }

        // Card grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = Constants.UI.GRID_ITEM_MIN_WIDTH_DP.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(cards, key = { it.id }) { card ->
                val section = when (card.type) {
                    "Rune" -> DeckSection.RUNE
                    "Battlefield" -> DeckSection.BATTLEFIELD
                    else -> DeckSection.MAIN_DECK
                }
                CardThumbnail(
                    card = card,
                    showQuantity = false,
                    onClick = {
                        if (canAddCard(card, section)) {
                            onAddCard(card, section)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DeckEditorTab(
    entries: List<DeckEntryWithCard>,
    legend: CardEntity?,
    champion: CardEntity?,
    onRemoveEntry: (DeckEntryWithCard) -> Unit,
    onSetLegend: () -> Unit,
    onSetChampion: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Legend slot
        item {
            Text("Legend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (legend != null) {
                DeckCardRow(card = legend, quantity = 1, onRemove = {})
            } else {
                Text(
                    "Tap to set legend",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onSetLegend() }
                        .padding(vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // Champion slot
        item {
            Text("Champion", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (champion != null) {
                DeckCardRow(card = champion, quantity = 1, onRemove = {})
            } else {
                Text(
                    "Tap to set champion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onSetChampion() }
                        .padding(vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Sections
        for (section in DeckSection.entries) {
            val sectionEntries = entries.filter { it.entry.section == section.value }
            val sectionTotal = sectionEntries.sumOf { it.entry.quantity }

            val sectionLabel = when (section) {
                DeckSection.MAIN_DECK -> "Main Deck ($sectionTotal/40+)"
                DeckSection.RUNE -> "Runes ($sectionTotal/12)"
                DeckSection.BATTLEFIELD -> "Battlefields ($sectionTotal/3)"
                DeckSection.SIDEBOARD -> "Sideboard ($sectionTotal/8)"
            }

            if (sectionEntries.isNotEmpty() || section == DeckSection.MAIN_DECK) {
                item {
                    Text(
                        sectionLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                items(sectionEntries) { entryWithCard ->
                    DeckCardRow(
                        card = entryWithCard.card,
                        quantity = entryWithCard.entry.quantity,
                        onRemove = { onRemoveEntry(entryWithCard) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckCardRow(
    card: CardEntity,
    quantity: Int,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.name,
            modifier = Modifier
                .size(36.dp)
                .aspectRatio(Constants.UI.CARD_ASPECT_RATIO)
                .clip(RoundedCornerShape(4.dp))
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${card.type} \u2022 ${card.publicCode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            "\u00D7$quantity",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "Remove",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
