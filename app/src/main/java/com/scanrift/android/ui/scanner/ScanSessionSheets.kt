package com.scanrift.android.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardLanguage
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckSection
import com.scanrift.android.ui.components.AdaptiveCardSheet
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.components.FoilBadge
import com.scanrift.android.ui.components.TappableQuantityStepper
import com.scanrift.android.ui.util.heavyImpact
import com.scanrift.android.ui.util.mediumImpact

private val FoilYellow = Color(0xFFFFD60A)

private data class DeckSheetRequest(
    val results: List<ScanResult>,
    val single: Boolean,
    val alsoToCollection: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSummarySheet(
    state: ScannerUiState,
    decks: List<Deck>,
    viewModel: ScannerViewModel,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var deckRequest by remember { mutableStateOf<DeckSheetRequest?>(null) }
    val isEmpty = state.results.isEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        SheetTitleBar(
            title = "Scan Summary",
            leading = {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            },
        )
        LazyColumn(Modifier.weight(1f, fill = false)) {
            item {
                SectionLabel("${state.scannedCount} cards scanned")
            }
            items(state.results, key = { it.id }) { result ->
                ScanResultRow(
                    result = result,
                    showCorrectHint = true,
                    onCorrect = { viewModel.startCorrection(result) },
                    onToggleFoil = { viewModel.toggleFoil(result.id) },
                    onLanguageChange = { viewModel.setLanguage(result.id, it) },
                    onQuantityChange = { viewModel.setQuantity(result.id, it) },
                    onRemove = { viewModel.remove(result.id) },
                    onAddToDeck = { deckRequest = DeckSheetRequest(listOf(result), single = true) },
                    onPickVersion = if (state.printingIndex.hasChoice(result.card)) {
                        { viewModel.openVersionPicker(VersionTarget.Row(result.id)) }
                    } else {
                        null
                    },
                )
            }
        }

        var menuOpen by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(if (isEmpty) Color.Gray else MaterialTheme.colorScheme.primary),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Add to Collection",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isEmpty) {
                        haptics.heavyImpact()
                        viewModel.addSessionToCollection()
                        onDismiss()
                    }
                    .padding(vertical = 14.dp),
            )
            Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.35f)))
            Box {
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = "More add options",
                    tint = Color.White,
                    modifier = Modifier
                        .clickable(enabled = !isEmpty) { menuOpen = true }
                        .padding(vertical = 14.dp)
                        .width(48.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Add all to a Deck…") },
                        leadingIcon = { Icon(Icons.Filled.LibraryAdd, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            deckRequest = DeckSheetRequest(state.results, single = false)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Collection + Deck…") },
                        leadingIcon = { Icon(Icons.Filled.Inventory, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            deckRequest = DeckSheetRequest(state.results, single = false, alsoToCollection = true)
                        },
                    )
                }
            }
        }
    }

    deckRequest?.let { request ->
        AddToDeckSheet(
            results = request.results,
            decks = decks,
            single = request.single,
            onDismiss = { deckRequest = null },
            onCommit = { deck, section ->
                viewModel.addToDeck(deck, section, request.results, request.alsoToCollection)
                deckRequest = null
                if (!request.single) {
                    viewModel.clearSession()
                    onDismiss()
                }
            },
        )
    }
}

@Composable
fun ScanResultRow(
    result: ScanResult,
    showCorrectHint: Boolean,
    onCorrect: () -> Unit,
    onToggleFoil: () -> Unit,
    onQuantityChange: (Int) -> Unit,
    onRemove: () -> Unit,
    onAddToDeck: (() -> Unit)? = null,
    onLanguageChange: ((CardLanguage) -> Unit)? = null,
    /** Opens the version picker; null when the card has a single printing. */
    onPickVersion: (() -> Unit)? = null,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onRemove()
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.SemiBold)
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onCorrect)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScanThumbnail(result.card, result.isFoil, result.language)
            Column(Modifier.weight(1f)) {
                Text(
                    result.card.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SetVersionLabel(result.card, onPickVersion, ambiguous = result.ambiguous)
                    if (result.isFoil) FoilBadge()
                    if (showCorrectHint) {
                        Text(
                            "Tap to correct",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
            }
            onLanguageChange?.let { LanguageChip(result.language, it) }
            FoilToggle(result, onToggleFoil)
            onAddToDeck?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Filled.LibraryAdd, contentDescription = "Add to deck", tint = MaterialTheme.colorScheme.primary)
                }
            }
            TappableQuantityStepper(
                quantity = result.quantity,
                onQuantityChange = onQuantityChange,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The row's flag; tapping it re-labels the whole row (merging into a twin if one exists). */
@Composable
private fun LanguageChip(language: CardLanguage, onChange: (CardLanguage) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { open = true },
            contentPadding = PaddingValues(horizontal = 6.dp),
            modifier = Modifier.semantics { contentDescription = "Language: ${language.displayName}" },
        ) {
            Text("${language.flag} ${language.code}", style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CardLanguage.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.flag}  ${option.displayName}") },
                    trailingIcon = if (option == language) ({ Icon(Icons.Filled.Check, contentDescription = "Selected") }) else null,
                    onClick = { open = false; if (option != language) onChange(option) },
                )
            }
        }
    }
}

/**
 * The row's set label. With other printings available it becomes a small chip with a
 * chevron that opens the version picker; otherwise plain text.
 */
@Composable
fun SetVersionLabel(card: Card, onPickVersion: (() -> Unit)?, ambiguous: Boolean = false) {
    if (onPickVersion == null) {
        Text(
            card.setLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        return
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (ambiguous) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onPickVersion)
            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (ambiguous) {
                    "${card.setLabel}. Version uncertain, check it"
                } else {
                    "${card.setLabel}. Choose another version"
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (ambiguous) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
        Text(
            "${card.setId} #${card.publicCodePrefix.substringAfter('-')}${if (ambiguous) " ?" else ""}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (ambiguous) FontWeight.Bold else null,
            color = content,
            maxLines = 1,
        )
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
fun ScanThumbnail(card: Card, isFoil: Boolean, language: CardLanguage? = null) {
    CardThumbnail(
        card = card,
        quantity = 1,
        isFoil = isFoil,
        language = language,
        showQuantityBadge = false,
        cornerRadius = 4.dp,
        modifier = Modifier.size(width = 40.dp, height = 56.dp),
    )
}

@Composable
private fun FoilToggle(result: ScanResult, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, enabled = !result.card.isAlwaysFoil) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = "Toggle foil",
            tint = if (result.isFoil) FoilYellow else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CardCorrectionSheet(state: ScannerUiState, viewModel: ScannerViewModel) {
    val focusManager = LocalFocusManager.current

    AdaptiveCardSheet(onDismiss = viewModel::dismissCorrection) {
        SheetTitleBar(
            title = "Correct Card",
            leading = { TextButton(onClick = viewModel::dismissCorrection) { Text("Cancel") } },
        )

        state.correctingPageItem?.let { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (item.card == null) "Read from the page (no match):" else "Current:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(item.card?.name ?: item.code, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                }
                Text("${item.language.flag} ${item.language.code}", style = MaterialTheme.typography.labelMedium)
            }
        }

        state.correcting?.let { current ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Current:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(current.card.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        if (current.isFoil) FoilBadge()
                    }
                }
                FoilToggle(current) { viewModel.toggleFoil(current.id) }
                TappableQuantityStepper(
                    quantity = current.quantity,
                    onQuantityChange = { quantity ->
                        viewModel.setQuantity(current.id, quantity)
                        if (quantity == 0) viewModel.dismissCorrection()
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        val correctingCard = state.correctingPageItem?.card ?: state.correcting?.card
        if (correctingCard != null && state.printingIndex.hasChoice(correctingCard)) {
            OtherVersionsRow(
                current = correctingCard,
                printings = state.printingIndex.printingsOf(correctingCard),
                onSelect = viewModel::applyCorrection,
            )
        }

        TextField(
            value = state.correctionQuery,
            onValueChange = viewModel::searchForCorrection,
            placeholder = { Text("Search for correct card...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.correctionQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchForCorrection("") }) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.correctionResults.isEmpty() && state.correctionQuery.isNotEmpty()) {
            EmptyState(Icons.Filled.Search, "No Cards Found", "Try a different search term")
        } else {
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(state.correctionResults, key = { it.id }) { card ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.applyCorrection(card) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ScanThumbnail(card, isFoil = false)
                        Column(Modifier.weight(1f)) {
                            Text(card.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${card.setLabel} • ${card.publicCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToDeckSheet(
    results: List<ScanResult>,
    decks: List<Deck>,
    single: Boolean,
    onDismiss: () -> Unit,
    onCommit: (Deck, DeckSection) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var section by rememberSaveable { mutableStateOf(DeckSection.MAIN_DECK) }
    val selected = decks.firstOrNull { it.id == selectedId } ?: decks.firstOrNull()
    val total = results.sumOf { it.quantity }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        SheetTitleBar(
            title = "Add to Deck",
            leading = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            trailing = {
                TextButton(
                    enabled = selected != null && total > 0,
                    onClick = {
                        haptics.mediumImpact()
                        selected?.let { onCommit(it, section) }
                    },
                ) { Text(if (single) "Add" else "Add $total", fontWeight = FontWeight.SemiBold) }
            },
        )

        if (decks.isEmpty()) {
            EmptyState(Icons.Outlined.Layers, "No Decks Yet", "Create a deck in the Decks tab first.")
            return@ModalBottomSheet
        }

        LazyColumn(Modifier.weight(1f, fill = false)) {
            item {
                SectionLabel(if (single) "Card" else "Cards")
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (single) {
                        Text(results.first().card.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("×$total", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("${results.size} distinct card${if (results.size == 1) "" else "s"}", modifier = Modifier.weight(1f))
                        Text("×$total total", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SectionLabel("Deck")
            }
            items(decks, key = { it.id }) { deck ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedId = deck.id }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(deck.name, style = MaterialTheme.typography.bodyLarge)
                        deck.legend?.let {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (deck.id == selected?.id) {
                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                SectionLabel("Section")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    DeckSection.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == section,
                            onClick = { section = option },
                            shape = SegmentedButtonDefaults.itemShape(index, DeckSection.entries.size),
                            icon = {},
                        ) { Text(option.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetTitleBar(
    title: String,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { leading() }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center))
        Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
