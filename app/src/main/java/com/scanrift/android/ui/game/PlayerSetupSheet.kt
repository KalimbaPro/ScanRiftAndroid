package com.scanrift.android.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.theme.Dimens

/**
 * Everything about one seat: rename the player, and bind them to a legend or a deck.
 *
 * Legend and deck are alternatives, not a hierarchy — plenty of games are played with
 * a legend you have not built a deck for yet, and a game recorded that way still
 * produces a matchup record. Picking a deck fills in its legend automatically; picking
 * a bare legend clears any deck.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSetupSheet(
    player: PlayerState,
    legends: List<Card>,
    decks: List<Deck>,
    legendsById: Map<String, Card>,
    onRename: (String) -> Unit,
    onPickLegend: (String) -> Unit,
    onPickDeck: (Deck) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var name by remember(player.id) { mutableStateOf(player.name) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = Dimens.SheetMaxWidth,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = Dimens.SheetMaxWidth)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; onRename(it) },
                label = { Text("Player name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CurrentAssignment(player, legendsById, decks)
                if (player.isRecordable) {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                listOf("Legend", "Deck").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(label) }
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text(if (tab == 0) "Search legends" else "Search decks") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            if (tab == 0) {
                val filtered = legends.filter { it.name.contains(search, ignoreCase = true) }
                if (filtered.isEmpty()) {
                    EmptyHint("No legends yet — sync the card database first.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(Dimens.GridItemMin),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(360.dp),
                    ) {
                        items(filtered, key = { it.id }) { legend ->
                            LegendTile(
                                legend = legend,
                                selected = player.legendCardId == legend.id,
                                onClick = { onPickLegend(legend.id) },
                            )
                        }
                    }
                }
            } else {
                val filtered = decks.filter { it.name.contains(search, ignoreCase = true) }
                if (filtered.isEmpty()) {
                    EmptyHint("No decks yet — build one in the Decks tab.")
                } else {
                    LazyColumn(Modifier.height(360.dp)) {
                        items(filtered, key = { it.id }) { deck ->
                            DeckRow(
                                deck = deck,
                                selected = player.deckId == deck.id,
                                onClick = { onPickDeck(deck) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentAssignment(
    player: PlayerState,
    legendsById: Map<String, Card>,
    decks: List<Deck>,
) {
    val deckName = player.deckId?.let { id -> decks.firstOrNull { it.id == id }?.name }
    val legendName = player.legendCardId?.let { legendsById[it]?.name }
    Column {
        Text(
            text = deckName ?: legendName ?: "Not assigned",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (deckName != null && legendName != null) {
            Text(
                text = legendName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendTile(legend: Card, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box {
            CardThumbnail(
                card = legend,
                quantity = 1,
                showQuantityBadge = false,
                modifier = Modifier.fillMaxWidth().aspectRatio(Dimens.CARD_ASPECT_RATIO),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
            }
        }
        Text(
            text = legend.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DeckRow(deck: Deck, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        deck.legend?.let { legend ->
            CardThumbnail(
                card = legend,
                quantity = 1,
                showQuantityBadge = false,
                cornerRadius = 4.dp,
                modifier = Modifier.width(36.dp).aspectRatio(Dimens.CARD_ASPECT_RATIO),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(deck.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                text = deck.legend?.name ?: "No legend set",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyHint(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}
