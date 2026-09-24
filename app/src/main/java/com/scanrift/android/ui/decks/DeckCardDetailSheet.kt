package com.scanrift.android.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.DeckSection
import com.scanrift.android.service.deck.DeckValidator
import com.scanrift.android.ui.components.CardInfo
import com.scanrift.android.ui.components.CardTextSection
import com.scanrift.android.ui.components.TiltingCardImage
import com.scanrift.android.ui.components.TappableQuantityStepper
import com.scanrift.android.ui.util.mediumImpact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckCardDetailSheet(
    target: DetailTarget,
    state: DeckBuilderState,
    viewModel: DeckBuilderViewModel,
    onDismiss: () -> Unit,
) {
    val deck = state.deck ?: return
    val editorEntry = (target as? DetailTarget.Editor)?.let { editor -> deck.entries.firstOrNull { it.id == editor.entryId } }
    val card = when (target) {
        is DetailTarget.Browser -> target.card
        is DetailTarget.Editor -> editorEntry?.card
    }
    LaunchedEffect(card == null) { if (card == null) onDismiss() }
    if (card == null) return

    val haptics = LocalHapticFeedback.current
    val isBrowser = target is DetailTarget.Browser
    val blockReason = state.addBlockReason(card)
    val canAdd = isBrowser && blockReason == null
    val isEligibleChampion = DeckValidator.isEligibleChampion(card, deck.legend)
    val championLabel = if (deck.champion == null) "Set as Champion" else "Replace Champion"

    val add = {
        haptics.mediumImpact()
        viewModel.handleBrowserTap(card)
        if (card.type == CardType.LEGEND) onDismiss()
    }
    val setChampion = {
        haptics.mediumImpact()
        viewModel.setChampion(card)
        onDismiss()
    }
    val canAddToSideboard = card.type !in listOf(CardType.RUNE, CardType.BATTLEFIELD, CardType.LEGEND) &&
        DeckValidator.canCopy(deck, card, DeckSection.SIDEBOARD)
    val addMenu: @Composable (close: () -> Unit) -> Unit = { close ->
        MenuItem("Add to deck", Icons.Filled.Add) { close(); add() }
        if (canAddToSideboard) {
            MenuItem("Add to sideboard", Icons.Filled.MoveToInbox) {
                close(); haptics.mediumImpact(); viewModel.addCard(card, DeckSection.SIDEBOARD)
            }
        }
        if (isEligibleChampion) MenuItem(championLabel, Icons.Filled.Shield) { close(); setChampion() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text("Done") }
            Text(
                card.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(64.dp, 48.dp), contentAlignment = Alignment.Center) {
                if (canAdd) {
                    LongPressMenu(onClick = add, menu = addMenu) { modifier ->
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Add to Deck",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = modifier.padding(12.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TiltingCardImage(card, maxWidth = 250.dp)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val entry = editorEntry ?: state.entryFor(card)
                when {
                    entry != null -> TappableQuantityStepper(
                        quantity = entry.quantity,
                        onQuantityChange = { quantity ->
                            viewModel.setQuantity(entry, quantity)
                            if (!isBrowser && quantity == 0) onDismiss()
                        },
                        maxValue = DeckValidator.maxQuantity(deck, entry),
                        textStyle = MaterialTheme.typography.titleLarge,
                    )
                    canAdd -> LongPressMenu(onClick = add, menu = addMenu) { modifier ->
                        PrimaryActionLabel("Add to Deck", Icons.Filled.Add, modifier, inset = ADD_BUTTON_INSET)
                    }
                    isBrowser -> PrimaryActionLabel(
                        blockReason ?: "Cannot add",
                        null,
                        Modifier,
                        color = MaterialTheme.colorScheme.outline,
                        inset = ADD_BUTTON_INSET,
                    )
                }
                if (isEligibleChampion) {
                    PrimaryAction(championLabel, Icons.Filled.Shield, onClick = setChampion)
                }
            }

            CardInfo(card)
            CardTextSection(card)
        }
    }
}

@Composable
private fun LongPressMenu(
    onClick: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        content(Modifier.combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }))
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            menu { menuOpen = false }
        }
    }
}

@Composable
private fun PrimaryAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    PrimaryActionLabel(
        label,
        icon,
        Modifier.combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        pressed = interactionSource.collectIsPressedAsState().value,
    )
}

@Composable
private fun PrimaryActionLabel(
    label: String,
    icon: ImageVector?,
    modifier: Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    pressed: Boolean = false,
    inset: Dp = 0.dp,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = inset)
            .fillMaxWidth()
            .clip(shape)
            .then(modifier)
            .alpha(if (pressed) 0.8f else 1f)
            .background(color, shape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = Color.White) }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}

private val ADD_BUTTON_INSET = 40.dp
