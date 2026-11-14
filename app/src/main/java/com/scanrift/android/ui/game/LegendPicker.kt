package com.scanrift.android.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.LegendGroup
import com.scanrift.android.domain.model.LegendPrintings
import com.scanrift.android.ui.adaptive.AdaptiveRules
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.theme.Dimens

/*
 * Legend picking for game seats and game records: one entry per legend, and a printing
 * chooser when that legend has more than one printing. Port of iOS
 * `LegendOrDeckPickerSheet` / `LegendPrintingChooser`.
 *
 * The chooser replaces the list inside the same sheet rather than stacking a second
 * sheet, the way iOS pushes it onto the sheet's navigation stack.
 */

/** What a tap on a legend entry does: pick the only printing, or open the chooser. */
fun LegendGroup.onTap(pick: (Card) -> Unit, choose: (LegendGroup) -> Unit) {
    if (hasChoice) choose(this) else pick(defaultPrinting)
}

/** "3 printings", or null when the legend has only one. */
val LegendGroup.printingsHint: String?
    get() = if (hasChoice) "${printings.size} printings" else null

@Composable
fun LegendGroupTile(group: LegendGroup, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            CardThumbnail(
                card = group.defaultPrinting,
                quantity = 1,
                showQuantityBadge = false,
                cornerRadius = 4.dp,
                modifier = Modifier.fillMaxWidth().aspectRatio(Dimens.CARD_ASPECT_RATIO),
            )
            if (selected) SelectedMark(Modifier.align(Alignment.TopEnd))
        }
        Text(
            text = group.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        group.printingsHint?.let { hint ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(
                    Icons.Outlined.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
                Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LegendGroupRow(group: LegendGroup, thumbWidth: Dp, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardThumbnail(
            card = group.defaultPrinting,
            quantity = 1,
            showQuantityBadge = false,
            cornerRadius = 2.dp,
            modifier = Modifier.size(width = thumbWidth, height = thumbWidth / Dimens.CARD_ASPECT_RATIO),
        )
        Column(Modifier.weight(1f)) {
            Text(group.name)
            group.printingsHint?.let { hint ->
                Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (group.hasChoice) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * Every printing of [group] as a grid, labelled by set and variant. Picking one hands
 * back that exact card, so the seat shows the art the player actually brought.
 *
 * The column count comes from the width this grid is given, not the window: it sits in
 * a bottom sheet capped at [Dimens.SheetMaxWidth] on a phone, and in a pop-up capped at
 * [Dimens.CardPopUpMaxWidth] on a wide window, so the window is far wider than the chooser.
 */
@Composable
fun LegendPrintingChooser(
    group: LegendGroup,
    selectedCardId: String?,
    onSelect: (Card) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All legends")
            }
            Text(
                group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val columns = AdaptiveRules.legendPrintingColumns(maxWidth)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.GridSpacingCompact),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(group.printings, key = { it.id }) { card ->
                    PrintingTile(card, selected = card.id == selectedCardId, onClick = { onSelect(card) })
                }
            }
        }
    }
}

@Composable
private fun PrintingTile(card: Card, selected: Boolean, onClick: () -> Unit) {
    val description = "${card.name}, ${LegendPrintings.printingLabel(card)}"
    Column(
        Modifier
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = selected
                onClick { onClick(); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            CardThumbnail(
                card = card,
                quantity = 1,
                showQuantityBadge = false,
                cornerRadius = 6.dp,
                modifier = Modifier.fillMaxWidth().aspectRatio(Dimens.CARD_ASPECT_RATIO),
            )
            if (selected) SelectedMark(Modifier.align(Alignment.TopEnd))
        }
        Text(
            card.setLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            LegendPrintings.variantLabel(card),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SelectedMark(modifier: Modifier) {
    Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = "Selected",
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(4.dp),
    )
}
