package com.scanrift.android.ui.scanner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardPrintings
import com.scanrift.android.ui.adaptive.AdaptiveRules
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.theme.Dimens

/**
 * ManaBox-style version picker: every printing of the scanned card as a full image,
 * captioned with set, collector code and variant, filterable by set. Tapping one
 * replaces the scan (see [ScannerViewModel.selectVersion]).
 *
 * Columns come from the grid's measured width inside the sheet, never the window
 * ([AdaptiveRules.printingColumns]), so an unfolded Fold gets a wider grid live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardVersionPicker(
    current: Card,
    printings: List<Card>,
    onSelect: (Card) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable(current.id) { mutableStateOf("") }
    val shown = remember(printings, query) { CardPrintings.filterPrintings(printings, query) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) { Text("Cancel") }
            Column(Modifier.align(Alignment.Center).padding(horizontal = 88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Choose Version", style = MaterialTheme.typography.titleMedium)
                Text(
                    CardPrintings.baseName(current.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Filter sets") },
            leadingIcon = { Icon(Icons.Filled.FilterList, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Cancel, contentDescription = "Clear filter") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (shown.isEmpty()) {
            Text(
                "No version matches “${query.trim()}”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                val columns = AdaptiveRules.printingColumns(maxWidth - 32.dp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.GridSpacingCompact),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(shown, key = { it.id }) { card ->
                        VersionTile(card, selected = card.id == current.id, onClick = { onSelect(card) })
                    }
                }
            }
        }
    }
}

/**
 * "Other versions" strip for the correction sheet: the same printings as a horizontal
 * row of small tiles. Hidden by the caller when the card has a single printing.
 */
@Composable
fun OtherVersionsRow(current: Card, printings: List<Card>, onSelect: (Card) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            "Other versions",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.GridSpacingCompact),
        ) {
            items(printings, key = { it.id }) { card ->
                VersionTile(card, selected = card.id == current.id, onClick = { onSelect(card) }, width = 88.dp)
            }
        }
    }
}

@Composable
private fun VersionTile(card: Card, selected: Boolean, onClick: () -> Unit, width: Dp? = null) {
    val variant = CardPrintings.variantLabel(card)
    val description = "${card.setLabel}, ${card.publicCode}, $variant${if (selected) ", current version" else ""}"
    val shape = RoundedCornerShape(6.dp)
    Column(
        (if (width != null) Modifier.width(width) else Modifier)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = selected
                onClick { onClick(); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            // CardThumbnail keeps the judge-promo placeholder and the banned band.
            CardThumbnail(
                card = card,
                quantity = 1,
                isFoil = false,
                showQuantityBadge = false,
                cornerRadius = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(Dimens.CARD_ASPECT_RATIO)
                    .then(
                        if (selected) {
                            Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape)
                        } else {
                            Modifier
                        },
                    ),
            )
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
            }
        }
        Text(
            card.setLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            card.publicCode,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            variant,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
