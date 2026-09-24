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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanrift.android.core.Constants
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.theme.Dimens

@Composable
fun PlayerSetupSheet(
    players: List<PlayerState>,
    legendsById: Map<String, Card>,
    decks: List<Deck>,
    onChangePlayerCount: (Int) -> Unit,
    onRename: (String, String) -> Unit,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    GameSheet(title = "Setup", onDismiss = onDismiss, dismissLabel = "Done") {
        SectionHeader("Players")
        val counts = (Constants.PointTracker.MIN_PLAYERS..Constants.PointTracker.MAX_PLAYERS).toList()
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            counts.forEachIndexed { index, count ->
                SegmentedButton(
                    selected = players.size == count,
                    onClick = { onChangePlayerCount(count) },
                    shape = SegmentedButtonDefaults.itemShape(index, counts.size),
                ) { Text("$count") }
            }
        }

        SectionHeader("Roster")
        players.forEachIndexed { index, player ->
            var name by remember(player.id) { mutableStateOf(player.name) }
            val deck = player.deckId?.let { id -> decks.firstOrNull { it.id == id } }
            val legendName = player.legendCardId?.let { legendsById[it]?.name }
            Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; onRename(player.id, it) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { onPick(index) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        when {
                            deck != null -> AssignmentLabel(deck.name, legendName)
                            legendName != null -> AssignmentLabel(legendName, "Legend")
                            else -> Text("Pick legend or deck")
                        }
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

@Composable
private fun AssignmentLabel(title: String, subtitle: String?) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    if (subtitle != null) {
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
fun LegendOrDeckPickerSheet(
    player: PlayerState,
    showNameField: Boolean,
    legends: List<Card>,
    decks: List<Deck>,
    onRename: (String) -> Unit,
    onPickLegend: (String) -> Unit,
    onPickDeck: (Deck) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var name by remember(player.id) { mutableStateOf(player.name) }
    val query = search.trim()

    GameSheet(
        title = if (showNameField) "Player" else "Choose",
        onDismiss = onDismiss,
        scrollable = false,
        action = {
            TextButton(onClick = { onClear(); onDismiss() }) {
                Text("Clear", color = MaterialTheme.colorScheme.error)
            }
        },
    ) {
        if (showNameField) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; onRename(it) },
                    label = { Text("Player name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Pick a Legend", "From a Deck").forEachIndexed { index, label ->
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
            placeholder = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (tab == 0) {
            if (legends.isEmpty()) {
                EmptyHint(Icons.Filled.WorkspacePremium, "No legends loaded", "Sync the card database first.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(Dimens.GridItemMin),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.height(420.dp),
                ) {
                    items(legends.filter { it.name.contains(query, ignoreCase = true) }, key = { it.id }) { legend ->
                        LegendTile(
                            legend = legend,
                            selected = player.legendCardId == legend.id,
                            onClick = { onPickLegend(legend.id); onDismiss() },
                        )
                    }
                }
            }
        } else if (decks.isEmpty()) {
            EmptyHint(Icons.Filled.Style, "No decks yet", "Build a deck in the Decks tab to use it here.")
        } else {
            LazyColumn(Modifier.height(420.dp)) {
                items(decks.filter { it.name.contains(query, ignoreCase = true) }, key = { it.id }) { deck ->
                    DeckRow(
                        deck = deck,
                        selected = player.deckId == deck.id,
                        onClick = { onPickDeck(deck); onDismiss() },
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendTile(legend: Card, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            CardThumbnail(
                card = legend,
                quantity = 1,
                showQuantityBadge = false,
                cornerRadius = 4.dp,
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
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
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
        Box(Modifier.size(width = 36.dp, height = 50.dp)) {
            deck.legend?.let { legend ->
                CardThumbnail(
                    card = legend,
                    quantity = 1,
                    showQuantityBadge = false,
                    cornerRadius = 4.dp,
                    modifier = Modifier.width(36.dp).aspectRatio(Dimens.CARD_ASPECT_RATIO),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(deck.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                text = deck.legend?.name ?: "No legend",
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
fun EmptyHint(icon: ImageVector, title: String, message: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
