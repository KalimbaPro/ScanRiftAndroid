package com.scanrift.android.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.service.deck.DeckValidator
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.theme.Dimens
import com.scanrift.android.ui.theme.domainColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    onOpenDeck: (String) -> Unit,
    viewModel: DeckListViewModel = hiltViewModel(),
) {
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = { LargeTopAppBar(title = { Text("Decks") }, scrollBehavior = scrollBehavior) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createDeck(onOpenDeck) }) {
                Icon(Icons.Filled.Add, contentDescription = "New deck")
            }
        },
    ) { padding ->
        if (decks.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No decks yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap + to build your first one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(Dimens.HubGridItemMin),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            items(decks, key = { it.id }) { deck ->
                DeckTile(deck = deck, onClick = { onOpenDeck(deck.id) })
            }
        }
    }
}

@Composable
private fun DeckTile(deck: Deck, onClick: () -> Unit) {
    // Live validation rather than the weaker "40 cards and both slots" heuristic the
    // old build used, which called plainly illegal decks valid.
    val isValid = DeckValidator.validate(deck).isEmpty()
    val totalCards = deck.entries.sumOf { it.quantity }

    Column(Modifier.clickable(onClick = onClick)) {
        Box {
            val legend = deck.legend
            if (legend != null) {
                CardThumbnail(
                    card = legend,
                    quantity = 1,
                    showQuantityBadge = false,
                    cornerRadius = 12.dp,
                    modifier = Modifier.fillMaxWidth().aspectRatio(Dimens.CARD_ASPECT_RATIO),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(Dimens.CARD_ASPECT_RATIO)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No legend", style = MaterialTheme.typography.bodySmall)
                }
            }
            Icon(
                imageVector = if (isValid) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = if (isValid) "Legal deck" else "Has issues",
                tint = if (isValid) ValidGreen else WarningOrange,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
        Text(
            text = deck.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            deck.legend?.domains?.forEach { domain ->
                Box(
                    Modifier
                        .size(10.dp)
                        .background(domainColor(domain), CircleShape),
                )
            }
            Text(
                "$totalCards cards",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val ValidGreen = Color(0xFF34C759)
private val WarningOrange = Color(0xFFFF9500)
