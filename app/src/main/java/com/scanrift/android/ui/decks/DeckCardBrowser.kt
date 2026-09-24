package com.scanrift.android.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import com.scanrift.android.core.Constants
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.service.deck.DeckValidator
import com.scanrift.android.ui.theme.Dimens

@Composable
fun DeckCardBrowser(
    state: DeckBuilderState,
    viewModel: DeckBuilderViewModel,
    onOpenCard: (Card) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = state.filters
    Column(modifier) {
        SearchField(filters.searchQuery, viewModel::setSearchQuery)
        FilterPills(filters.typeFilter, viewModel::setTypeFilter)

        if (state.deck?.legend != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show all cards", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(checked = filters.showAllCards, onCheckedChange = viewModel::setShowAllCards)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(Dimens.GridItemMin),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.browserCards, key = { it.id }) { card ->
                val deck = state.deck
                val dimmed = state.addBlockReason(card) != null ||
                    (!filters.showAllCards && !DeckValidator.isCardLegalForDeck(card, deck?.legend))
                BrowserCell(
                    card = card,
                    copies = deck?.let { DeckValidator.copiesInDeck(it, card) } ?: 0,
                    dimmed = dimmed,
                    onClick = { onOpenCard(card) },
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search cards...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Cancel,
                contentDescription = "Clear search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).clickable { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun FilterPills(selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill("All", selected == null) { onSelect(null) }
        DeckBuilderViewModel.browserFilters.forEach { filter ->
            Pill(filter, selected == filter) { onSelect(if (selected == filter) null else filter) }
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun BrowserCell(card: Card, copies: Int, dimmed: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(Dimens.CARD_ASPECT_RATIO)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.name,
            contentScale = if (card.isLandscape) ContentScale.Fit else ContentScale.Crop,
            colorFilter = if (dimmed) {
                ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(DIMMED_SATURATION) })
            } else {
                null
            },
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), shape)
                .alpha(if (dimmed) DIMMED_ALPHA else 1f),
        )
        if (copies > 0) {
            val badge = if (card.type == CardType.RUNE || card.type == CardType.BATTLEFIELD) {
                "×$copies"
            } else {
                "$copies/${Constants.Deck.MAX_COPIES_PER_NAME}"
            }
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private const val DIMMED_SATURATION = 0.3f
private const val DIMMED_ALPHA = 0.5f
