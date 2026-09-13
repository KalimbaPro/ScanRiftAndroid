package com.scanrift.android.ui.decks

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.core.Constants
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.DeckEntry
import com.scanrift.android.domain.model.DeckSection
import com.scanrift.android.ui.adaptive.AdaptiveRules
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.theme.Dimens

/**
 * The deck builder.
 *
 * Browser and editor sit side by side once the pane is 600dp or wider; below that they
 * share the space behind a segmented control, exactly as iOS does. The split keys off
 * the **pane's** width, so it behaves correctly whether this screen is full-window or
 * the detail half of a list-detail layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckBuilderScreen(
    deckId: String,
    onBack: () -> Unit,
    viewModel: DeckBuilderViewModel = hiltViewModel(),
) {
    LaunchedEffect(deckId) { viewModel.load(deckId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var exportMenuOpen by remember { mutableStateOf(false) }
    var importMenuOpen by remember { mutableStateOf(false) }
    var importMode by remember { mutableStateOf<DeckImportMode?>(null) }
    // Held while the replace warning is up, so the pasted text survives the dialog.
    var pendingImport by remember { mutableStateOf<Pair<DeckImportMode, String>?>(null) }

    fun share(text: String?) {
        if (text.isNullOrBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share deck"))
    }

    fun runImport(mode: DeckImportMode, raw: String) {
        when (mode) {
            DeckImportMode.TEXT -> viewModel.importText(raw)
            DeckImportMode.TTS -> viewModel.importTts(raw)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.deck?.name ?: "Deck") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { importMenuOpen = true }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Import deck")
                    }
                    DropdownMenu(expanded = importMenuOpen, onDismissRequest = { importMenuOpen = false }) {
                        DeckImportMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.title) },
                                onClick = { importMenuOpen = false; importMode = mode },
                            )
                        }
                    }

                    IconButton(onClick = { exportMenuOpen = true }) {
                        Icon(Icons.Filled.IosShare, contentDescription = "Export deck")
                    }
                    DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Export for TTS") },
                            onClick = { exportMenuOpen = false; share(viewModel.exportAsTts()) },
                        )
                        DropdownMenuItem(
                            text = { Text("Export as Text") },
                            onClick = { exportMenuOpen = false; share(viewModel.exportAsText()) },
                        )
                    }
                },
            )
        },
        bottomBar = { ValidationBanner(state) },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            if (AdaptiveRules.useSplitDeckBuilder(maxWidth)) {
                // The split starts even but is draggable — how much room the browser
                // versus the editor deserves depends on what you are doing, and on a
                // fold there is enough width for the choice to matter.
                var browserFraction by rememberSaveable { mutableFloatStateOf(0.5f) }
                val totalWidth = maxWidth
                val density = LocalDensity.current

                Row(Modifier.fillMaxSize()) {
                    CardBrowser(state, viewModel, Modifier.weight(browserFraction))
                    ResizeHandle(
                        onDrag = { deltaPx ->
                            val deltaFraction = with(density) { deltaPx.toDp() } / totalWidth
                            browserFraction = (browserFraction + deltaFraction).coerceIn(0.25f, 0.75f)
                        },
                    )
                    DeckEditor(state, viewModel, Modifier.weight(1f - browserFraction))
                }
            } else {
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                Column(Modifier.fillMaxSize()) {
                    SingleChoiceSegmentedButtonRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        listOf("Cards", "Deck").forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                            ) { Text(label) }
                        }
                    }
                    if (selectedTab == 0) {
                        CardBrowser(state, viewModel, Modifier.fillMaxSize())
                    } else {
                        DeckEditor(state, viewModel, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    importMode?.let { mode ->
        DeckImportSheet(
            mode = mode,
            onDismiss = { importMode = null },
            onImport = { raw ->
                importMode = null
                // An import describes a whole deck, so it replaces what is there. Say so
                // first when that would actually cost the user something.
                val deck = state.deck
                val hasContents = deck != null && (deck.entries.isNotEmpty() || deck.legend != null)
                if (hasContents) pendingImport = mode to raw else runImport(mode, raw)
            },
        )
    }

    pendingImport?.let { (mode, raw) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace this deck?") },
            text = {
                Text("Importing replaces the ${state.totalCards} cards already in this deck.")
            },
            confirmButton = {
                TextButton(onClick = { pendingImport = null; runImport(mode, raw) }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            },
        )
    }

    importResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::clearImportResult,
            title = { Text("Import complete") },
            text = {
                Column {
                    Text("Added ${result.cardsAdded} cards.")
                    if (result.skippedLines.isNotEmpty()) {
                        Text(
                            "Skipped ${result.skippedLines.size}: " +
                                result.skippedLines.take(5).joinToString(", ") +
                                if (result.skippedLines.size > 5) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "Cards from a set you have not synced yet will not resolve. " +
                                "Update the card database in Settings and import again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    result.notes.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::clearImportResult) { Text("OK") } },
        )
    }
}

/**
 * The draggable divider between the browser and the editor.
 *
 * Wider than the hairline it draws so it is actually grabbable, and it advertises a
 * resize cursor for anyone on a desktop-class device.
 */
@Composable
private fun ResizeHandle(onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(12.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { onDrag(it) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider(Modifier.fillMaxHeight())
    }
}

@Composable
private fun ValidationBanner(state: DeckBuilderState) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val errors = state.validationErrors

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = errors.isNotEmpty()) { expanded = !expanded },
    ) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (errors.isEmpty()) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (errors.isEmpty()) BannerGreen else BannerOrange,
            )
            Text(
                text = if (errors.isEmpty()) {
                    "Deck is legal"
                } else {
                    "${errors.size} issue${if (errors.size == 1) "" else "s"}"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text("${state.totalCards} cards", style = MaterialTheme.typography.bodySmall)
        }
        AnimatedVisibility(visible = expanded && errors.isNotEmpty()) {
            Column(Modifier.padding(start = 48.dp, end = 16.dp, bottom = 16.dp)) {
                errors.forEach { error ->
                    Text(
                        text = "• ${error.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardBrowser(
    state: DeckBuilderState,
    viewModel: DeckBuilderViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            placeholder = { Text("Search cards") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyColumnOfFilters(state, viewModel)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(Dimens.GridItemMin),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.browserCards, key = { it.id }) { card ->
                BrowserCard(card = card, remaining = viewModel.remainingCopies(card)) {
                    viewModel.addCard(card)
                }
            }
        }
    }
}

@Composable
private fun LazyColumnOfFilters(state: DeckBuilderState, viewModel: DeckBuilderViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.typeFilter == null,
            onClick = { viewModel.setTypeFilter(null) },
            label = { Text("All") },
        )
        DeckBuilderViewModel.browserFilters.forEach { filter ->
            FilterChip(
                selected = state.typeFilter == filter,
                onClick = { viewModel.setTypeFilter(if (state.typeFilter == filter) null else filter) },
                label = { Text(filter) },
            )
        }
    }
}

@Composable
private fun BrowserCard(card: Card, remaining: Int, onAdd: () -> Unit) {
    Column(Modifier.clickable(enabled = remaining > 0, onClick = onAdd)) {
        Box {
            CardThumbnail(
                card = card,
                quantity = if (remaining > 0) 1 else 0,
                showQuantityBadge = false,
                modifier = Modifier.fillMaxWidth().aspectRatio(Dimens.CARD_ASPECT_RATIO),
            )
            Text(
                text = "$remaining left",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DeckEditor(
    state: DeckBuilderState,
    viewModel: DeckBuilderViewModel,
    modifier: Modifier = Modifier,
) {
    val deck = state.deck ?: return
    LazyColumn(modifier.padding(horizontal = 16.dp)) {
        item {
            SlotRow(
                label = "Legend",
                card = deck.legend,
                emptyHint = "Pick one from the Legend tab",
                onClear = { viewModel.setLegend(null) },
            )
            SlotRow(
                label = "Champion",
                card = deck.champion,
                emptyHint = if (deck.legend == null) {
                    "Pick one from the Champion tab"
                } else {
                    "Pick one of ${deck.legend!!.tags.firstOrNull() ?: "this legend"}'s champions"
                },
                onClear = { viewModel.setChampion(null) },
            )
        }

        DeckSection.entries.forEach { section ->
            val entries = deck.entries.filter { it.section == section }
            item(key = "header-${section.value}") {
                SectionHeader(section, state.count(section))
            }
            items(entries, key = { it.id }) { entry ->
                EntryRow(entry = entry, onQuantityChange = { viewModel.setQuantity(entry, it) })
            }
        }
    }
}

/** One of the two singleton slots. Shows the card's art once filled. */
@Composable
private fun SlotRow(label: String, card: Card?, emptyHint: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (card != null) {
            CardThumbnail(
                card = card,
                quantity = 1,
                showQuantityBadge = false,
                cornerRadius = 4.dp,
                modifier = Modifier.width(44.dp).aspectRatio(Dimens.CARD_ASPECT_RATIO),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                text = card?.name ?: emptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = if (card == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        if (card != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Clear $label")
            }
        }
    }
}

/** Shows current/target with the target's rule: exact, minimum or maximum. */
@Composable
private fun SectionHeader(section: DeckSection, current: Int) {
    val (target, satisfied) = when (section) {
        DeckSection.MAIN_DECK ->
            "${Constants.Deck.MAIN_DECK_MINIMUM}+" to (current >= Constants.Deck.MAIN_DECK_MINIMUM)
        DeckSection.RUNE ->
            "${Constants.Deck.RUNE_COUNT}" to (current == Constants.Deck.RUNE_COUNT)
        DeckSection.BATTLEFIELD ->
            "${Constants.Deck.BATTLEFIELD_COUNT}" to (current == Constants.Deck.BATTLEFIELD_COUNT)
        DeckSection.SIDEBOARD ->
            "${Constants.Deck.SIDEBOARD_MAXIMUM} max" to (current <= Constants.Deck.SIDEBOARD_MAXIMUM)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = section.displayName.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$current/$target",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (satisfied) BannerGreen else MaterialTheme.colorScheme.error,
        )
    }
    HorizontalDivider()
}

@Composable
private fun EntryRow(entry: DeckEntry, onQuantityChange: (Int) -> Unit) {
    val card = entry.card ?: return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardThumbnail(
            card = card,
            quantity = 1,
            showQuantityBadge = false,
            cornerRadius = 4.dp,
            modifier = Modifier.width(36.dp).aspectRatio(Dimens.CARD_ASPECT_RATIO),
        )
        Column(Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                "${card.type} • ${card.publicCode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onQuantityChange(entry.quantity - 1) }) { Text("−") }
        Text("${entry.quantity}", style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = { onQuantityChange(entry.quantity + 1) }) { Text("+") }
    }
}

private val BannerGreen = androidx.compose.ui.graphics.Color(0xFF34C759)
private val BannerOrange = androidx.compose.ui.graphics.Color(0xFFFF9500)
