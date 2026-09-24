package com.scanrift.android.ui.decks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanrift.android.core.Constants
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.domain.model.DeckEntry
import com.scanrift.android.domain.model.DeckSection
import com.scanrift.android.service.deck.DeckValidator
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.components.TappableQuantityStepper
import com.scanrift.android.ui.theme.Dimens
import com.scanrift.android.ui.util.lightImpact
import com.scanrift.android.ui.util.mediumImpact

@Composable
fun DeckEditorPanel(
    state: DeckBuilderState,
    viewModel: DeckBuilderViewModel,
    onOpenEntry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val deck = state.deck ?: return
    var sideboardExpanded by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .widthIn(max = Dimens.DeckEditorMaxWidth),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DeckNameSection(deck.name, viewModel::rename)
        HorizontalDivider()

        Column {
            DeckSectionHeader("Legend", if (deck.legend != null) 1 else 0, 1)
            SlotRow(deck.legend, "Choose a Legend from the card browser", viewModel::clearLegend) { legend ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { DomainIcons(legend.domains, 14.dp) }
            }
        }
        HorizontalDivider()

        Column {
            DeckSectionHeader("Champion", if (deck.champion != null) 1 else 0, 1)
            SlotRow(deck.champion, "Choose a Champion from the card browser", viewModel::clearChampion) { champion ->
                TagCapsules(champion.tags)
            }
        }
        HorizontalDivider()

        EntrySection(
            header = {
                DeckSectionHeader(
                    DeckSection.MAIN_DECK.displayName,
                    deck.count(DeckSection.MAIN_DECK),
                    Constants.Deck.MAIN_DECK_MINIMUM,
                    HeaderMode.MINIMUM,
                )
            },
            entries = deck.sortedEntries(DeckSection.MAIN_DECK),
            emptyText = "Add cards from the browser",
        ) { entry ->
            EntryRow(deck, entry, viewModel, onOpenEntry) { close ->
                val card = entry.card
                ContextItem("Copy to sideboard", Icons.Filled.LibraryAdd, card != null && DeckValidator.canCopy(deck, card, DeckSection.SIDEBOARD)) {
                    close(); viewModel.copyToSection(entry, DeckSection.SIDEBOARD)
                }
                ContextItem("Move to sideboard", Icons.Filled.MoveToInbox, DeckValidator.canMove(deck, entry, DeckSection.SIDEBOARD)) {
                    close(); viewModel.moveToSection(entry, DeckSection.SIDEBOARD)
                }
                RemoveItem { close(); viewModel.remove(entry) }
            }
        }
        HorizontalDivider()

        EntrySection(
            header = {
                DeckSectionHeader(DeckSection.RUNE.displayName, deck.count(DeckSection.RUNE), Constants.Deck.RUNE_COUNT)
            },
            entries = deck.sortedEntries(DeckSection.RUNE),
            emptyText = "Add runes from the browser",
        ) { entry -> EntryRow(deck, entry, viewModel, onOpenEntry) }
        HorizontalDivider()

        EntrySection(
            header = {
                DeckSectionHeader(
                    DeckSection.BATTLEFIELD.displayName,
                    deck.count(DeckSection.BATTLEFIELD),
                    Constants.Deck.BATTLEFIELD_COUNT,
                )
            },
            entries = deck.sortedEntries(DeckSection.BATTLEFIELD),
            emptyText = "Add battlefields from the browser",
        ) { entry -> EntryRow(deck, entry, viewModel, onOpenEntry, showStepper = false) }
        HorizontalDivider()

        EntrySection(
            header = {
                DeckSectionHeader(
                    DeckSection.SIDEBOARD.displayName,
                    deck.count(DeckSection.SIDEBOARD),
                    Constants.Deck.SIDEBOARD_MAXIMUM,
                    HeaderMode.MAXIMUM,
                    expanded = sideboardExpanded,
                    onToggle = { sideboardExpanded = !sideboardExpanded },
                )
            },
            entries = deck.sortedEntries(DeckSection.SIDEBOARD),
            emptyText = "Optional: add sideboard cards",
            expanded = sideboardExpanded,
        ) { entry ->
            EntryRow(deck, entry, viewModel, onOpenEntry) { close ->
                ContextItem("Move to main deck", Icons.Filled.Unarchive, DeckValidator.canMove(deck, entry, DeckSection.MAIN_DECK)) {
                    close(); viewModel.moveToSection(entry, DeckSection.MAIN_DECK)
                }
                RemoveItem { close(); viewModel.remove(entry) }
            }
        }
        HorizontalDivider()

        DeckStatsSection(deck)
        HorizontalDivider()

        DeckMissingCardsSection(state, viewModel)
    }
}

@Composable
private fun DeckNameSection(name: String, onRename: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var editableName by remember { mutableStateOf(name) }

    if (editing) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val commit = { onRename(editableName); editing = false }
            OutlinedTextField(
                value = editableName,
                onValueChange = { editableName = it },
                placeholder = { Text("Deck Name") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = commit) { Text("Done") }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { editableName = name; editing = true }) {
                Icon(Icons.Outlined.Edit, contentDescription = "Rename deck", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SlotRow(card: Card?, emptyText: String, onClear: () -> Unit, detail: @Composable (Card) -> Unit) {
    if (card == null) {
        EmptyText(emptyText, Modifier.padding(vertical = 8.dp), MaterialTheme.typography.bodyMedium)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CardThumbnail(
            card = card,
            quantity = 1,
            showQuantityBadge = false,
            cornerRadius = 6.dp,
            modifier = Modifier.size(width = 44.dp, height = 62.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            detail(card)
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Filled.Cancel, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EntrySection(
    header: @Composable () -> Unit,
    entries: List<DeckEntry>,
    emptyText: String,
    expanded: Boolean = true,
    row: @Composable (DeckEntry) -> Unit,
) {
    Column {
        header()
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(SECTION_TOGGLE_MS)),
            exit = shrinkVertically(tween(SECTION_TOGGLE_MS)),
        ) {
            Column {
                if (entries.isEmpty()) EmptyText(emptyText)
                entries.forEach { row(it) }
            }
        }
    }
}

@Composable
private fun EmptyText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
) {
    Text(
        text = text,
        style = style,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun EntryRow(
    deck: Deck,
    entry: DeckEntry,
    viewModel: DeckBuilderViewModel,
    onOpenEntry: (Long) -> Unit,
    showStepper: Boolean = true,
    menu: (@Composable ((close: () -> Unit) -> Unit))? = null,
) {
    val card = entry.card ?: return
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onOpenEntry(entry.id) },
                    onLongClick = if (menu != null) ({ menuOpen = true }) else null,
                )
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CardThumbnail(
                card = card,
                quantity = 1,
                showQuantityBadge = false,
                cornerRadius = 4.dp,
                modifier = Modifier.size(width = 36.dp, height = 50.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.energy?.let { energy ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bolt, contentDescription = "Energy", tint = WarningOrange, modifier = Modifier.size(12.dp))
                            Text("$energy", style = MaterialTheme.typography.bodySmall, color = WarningOrange)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { DomainIcons(card.domains, 12.dp) }
                    Text(card.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (showStepper) {
                TappableQuantityStepper(
                    quantity = entry.quantity,
                    onQuantityChange = { viewModel.setQuantity(entry, it) },
                    maxValue = DeckValidator.maxQuantity(deck, entry),
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
            } else {
                IconButton(onClick = { viewModel.remove(entry) }) {
                    Icon(Icons.Filled.Cancel, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (menu != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                menu { menuOpen = false }
            }
        }
    }
}

@Composable
private fun ContextItem(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = { haptics.lightImpact(); onClick() },
    )
}

@Composable
private fun RemoveItem(onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    DropdownMenuItem(
        text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        onClick = { haptics.mediumImpact(); onClick() },
    )
}
