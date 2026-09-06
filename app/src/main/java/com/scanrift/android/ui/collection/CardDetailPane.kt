package com.scanrift.android.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scanrift.android.ui.adaptive.AdaptiveRules
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.text.RichCardText
import com.scanrift.android.ui.theme.Dimens
import com.scanrift.android.ui.theme.domainColor
import com.scanrift.android.ui.theme.rarityColor

/**
 * Card detail.
 *
 * Two layouts, chosen by the **pane's** width rather than the window's. The threshold
 * is 700dp, not the 600dp used elsewhere: the image takes up to 400dp, so at 600 the
 * info column would be left with 200dp, which is not readable. That means on a folded
 * phone and in a narrow detail pane you get the single scrolling column, and the
 * side-by-side layout only appears when there is genuinely room for it.
 */
@Composable
fun CardDetailPane(
    displayCard: DisplayCard,
    modifier: Modifier = Modifier,
    onAddCopy: () -> Unit = {},
    onSetQuantity: (Int) -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (AdaptiveRules.useTwoColumnCardDetail(maxWidth)) {
            TwoColumnDetail(displayCard, onAddCopy, onSetQuantity)
        } else {
            SingleColumnDetail(displayCard, onAddCopy, onSetQuantity)
        }
    }
}

@Composable
private fun SingleColumnDetail(
    displayCard: DisplayCard,
    onAddCopy: () -> Unit,
    onSetQuantity: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .widthIn(max = Dimens.CardDetailMaxWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CardArt(displayCard, Modifier.fillMaxWidth(0.7f))
        OwnershipControls(displayCard, onAddCopy, onSetQuantity, Modifier.fillMaxWidth())
        CardInfo(displayCard, Modifier.fillMaxWidth())
    }
}

@Composable
private fun TwoColumnDetail(
    displayCard: DisplayCard,
    onAddCopy: () -> Unit,
    onSetQuantity: (Int) -> Unit,
) {
    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            CardArt(displayCard, Modifier.widthIn(max = Dimens.DetailImageMax))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The name lives in the content here, because the pane has no top bar of
            // its own in two-pane mode — same trick iOS uses on iPad.
            Text(
                text = displayCard.card.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            OwnershipControls(displayCard, onAddCopy, onSetQuantity, Modifier.fillMaxWidth())
            CardInfo(displayCard, Modifier.fillMaxWidth(), showName = false)
        }
    }
}

@Composable
private fun CardArt(displayCard: DisplayCard, modifier: Modifier = Modifier) {
    CardThumbnail(
        card = displayCard.card,
        quantity = displayCard.quantity.coerceAtLeast(1),
        isFoil = displayCard.entry?.isFoil == true,
        showUnownedInColor = true,
        // Landscape battlefields keep their native orientation in detail, unlike the grid.
        rotateLandscape = false,
        cornerRadius = 12.dp,
        showQuantityBadge = false,
        modifier = modifier.aspectRatio(
            if (displayCard.card.isLandscape) 1f / Dimens.CARD_ASPECT_RATIO else Dimens.CARD_ASPECT_RATIO,
        ),
    )
}

@Composable
private fun CardInfo(
    displayCard: DisplayCard,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
) {
    val card = displayCard.card
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showName) {
            Text(card.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            text = "${card.setLabel} • ${card.publicCode}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("Type", card.type, MaterialTheme.colorScheme.primary)
            InfoChip("Rarity", card.rarity, rarityColor(card.rarity))
            card.energy?.let { InfoChip("Energy", it.toString(), MaterialTheme.colorScheme.tertiary) }
            card.power?.let { InfoChip("Power", it.toString(), MaterialTheme.colorScheme.secondary) }
        }

        if (card.domains.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                card.domains.forEach { domain -> InfoChip("", domain, domainColor(domain)) }
            }
        }

        // plainText is the field that carries the :rb_xxx: / [Keyword] / (reminder)
        // markup — iOS renders that one too, deliberately, not richText.
        (card.plainText ?: card.richText)?.takeIf { it.isNotBlank() }?.let { text ->
            RichCardText(text, modifier = Modifier.fillMaxWidth())
        }

        card.artist?.let {
            Text(
                text = "Illustrated by $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Add a copy, or adjust how many you own, without leaving the card.
 *
 * Browsing the catalogue and recording what you own are the same activity in practice,
 * so the control lives on the card rather than behind a separate flow.
 */
@Composable
private fun OwnershipControls(
    displayCard: DisplayCard,
    onAddCopy: () -> Unit,
    onSetQuantity: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = displayCard.entry

    if (entry == null) {
        Button(onClick = onAddCopy, modifier = modifier) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Add to collection", modifier = Modifier.padding(start = 8.dp))
        }
        return
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text("In your collection", style = MaterialTheme.typography.labelMedium)
            Text(
                text = entry.condition.value + if (entry.isFoil) " · foil" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Stepping to zero removes the stack rather than leaving an empty one.
            IconButton(onClick = { onSetQuantity(entry.quantity - 1) }) {
                Icon(Icons.Filled.Remove, contentDescription = "One fewer")
            }
            Text(
                text = "${entry.quantity}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 28.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onSetQuantity(entry.quantity + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = "One more")
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String, tint: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}
