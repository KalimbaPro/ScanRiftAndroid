package com.scanrift.android.ui.decks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.components.AddToListSheet
import com.scanrift.android.ui.util.mediumImpact

@Composable
fun DeckMissingCardsSection(state: DeckBuilderState, viewModel: DeckBuilderViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showAddToList by remember { mutableStateOf(false) }
    val missing = state.missingCards
    var addedToWishlist by remember(missing) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitleRow("Missing Cards", expanded, onToggle = { expanded = !expanded }) {
            Text(
                "${state.missingCount}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (missing.isEmpty()) ValidGreen else MissingRed,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(SECTION_TOGGLE_MS)),
            exit = shrinkVertically(tween(SECTION_TOGGLE_MS)),
        ) {
            if (missing.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ValidGreen, modifier = Modifier.size(16.dp))
                    Text(
                        "You own all cards in this deck",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    missing.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CardThumbnail(
                                card = item.card,
                                quantity = 1,
                                showQuantityBadge = false,
                                cornerRadius = 4.dp,
                                modifier = Modifier.size(width = 32.dp, height = 45.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.card.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    item.card.type,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "need ${item.deficit}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MissingRed,
                            )
                        }
                    }
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (addedToWishlist) {
                            MissingAction("Added", Icons.Filled.Check, ValidGreen, ValidGreen.copy(alpha = 0.15f), Modifier.weight(1f), null)
                        } else {
                            MissingAction(
                                "Add to Wishlist",
                                Icons.Outlined.StarOutline,
                                MaterialTheme.colorScheme.onSurface,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                Modifier.weight(1f),
                            ) {
                                haptics.mediumImpact()
                                viewModel.addMissingCardsToWishlist()
                                addedToWishlist = true
                            }
                        }
                        MissingAction(
                            "Add to List",
                            Icons.Outlined.CreateNewFolder,
                            MaterialTheme.colorScheme.onSurface,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            Modifier.weight(1f),
                        ) { showAddToList = true }
                    }
                }
            }
        }
    }

    if (showAddToList) {
        val lists by viewModel.customLists.collectAsStateWithLifecycle()
        AddToListSheet(
            cards = missing.map { it.card },
            lists = lists,
            onToggle = viewModel::toggleList,
            onSaveList = viewModel::saveList,
            onDismiss = { showAddToList = false },
        )
    }
}

@Composable
private fun MissingAction(
    label: String,
    icon: ImageVector,
    content: Color,
    background: Color,
    modifier: Modifier,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(8.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = content)
    }
}
