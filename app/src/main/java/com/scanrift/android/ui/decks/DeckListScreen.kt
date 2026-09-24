package com.scanrift.android.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.domain.model.Deck
import com.scanrift.android.service.deck.DeckValidator
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.theme.Dimens
import com.scanrift.android.ui.theme.domainColor
import com.scanrift.android.ui.util.mediumImpact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    onOpenDeck: (String) -> Unit,
    viewModel: DeckListViewModel = hiltViewModel(),
) {
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    val owned by viewModel.ownedByCardId.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var pendingDelete by remember { mutableStateOf<Deck?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Decks") }, scrollBehavior = scrollBehavior) },
        floatingActionButton = {
            if (decks.isNotEmpty()) {
                FloatingActionButton(onClick = { viewModel.createDeck(onOpenDeck) }, shape = CircleShape) {
                    Icon(Icons.Filled.Add, contentDescription = "New deck")
                }
            }
        },
    ) { padding ->
        if (decks.isEmpty()) {
            EmptyDecks(Modifier.padding(padding)) { viewModel.createDeck(onOpenDeck) }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(Dimens.HubGridItemMin),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            items(decks, key = { it.id }) { deck ->
                DeckTile(
                    deck = deck,
                    missingCount = deck.missingCards(owned).sumOf { it.deficit },
                    onClick = { onOpenDeck(deck.id) },
                    onDelete = { pendingDelete = deck },
                )
            }
        }
    }

    pendingDelete?.let { deck ->
        DeleteDeckDialog(
            deck = deck,
            onConfirm = { viewModel.deleteDeck(deck); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun EmptyDecks(modifier: Modifier, onCreate: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Surface(
            onClick = { haptics.mediumImpact(); onCreate() },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Create your first deck",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("No Decks Yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Tap + to build your first deck",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(2f))
    }
}

@Composable
private fun DeckTile(deck: Deck, missingCount: Int, onClick: () -> Unit, onDelete: () -> Unit) {
    // Live validation rather than the weaker "40 cards and both slots" heuristic the
    // old build used, which called plainly illegal decks valid.
    val isValid = DeckValidator.validate(deck).isEmpty()

    var menuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
            // Long-press for delete, so the tile stays a single tap target for the
            // thing you actually do 99% of the time.
            onLongClick = { menuOpen = true },
        ),
    ) {
        Box {
            val legend = deck.legend
            val tint = legend?.domains?.firstOrNull()?.let { domainColor(it) } ?: MaterialTheme.colorScheme.primary
            val shape = RoundedCornerShape(12.dp)
            if (legend?.imageUrl != null) {
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
                        .clip(shape)
                        .background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = null, tint = tint, modifier = Modifier.size(40.dp))
                }
            }
            Icon(
                imageVector = if (isValid) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = if (isValid) "Legal deck" else "Has issues",
                tint = if (isValid) ValidGreen else WarningOrange,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                    .padding(6.dp)
                    .size(14.dp),
            )

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                    leadingIcon = {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                )
            }
        }
        Column(Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = deck.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DomainIcons(deck.legend?.domains.orEmpty(), 12.dp)
                Text(
                    "${deck.totalCardCount} cards",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (missingCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(
                            Icons.Outlined.ShoppingBag,
                            contentDescription = "Missing cards",
                            tint = MissingRed,
                            modifier = Modifier.size(11.dp),
                        )
                        Text("$missingCount", style = MaterialTheme.typography.bodySmall, color = MissingRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteDeckDialog(deck: Deck, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Deck") },
        text = { Text("Are you sure you want to delete \"${deck.name}\"?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
