package com.scanrift.android.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardCondition
import com.scanrift.android.domain.model.CollectionEntry
import com.scanrift.android.ui.adaptive.AdaptiveRules
import com.scanrift.android.ui.components.CardInfo
import com.scanrift.android.ui.components.CardTextSection
import com.scanrift.android.ui.components.FoilBadge
import com.scanrift.android.ui.components.TiltingCardImage
import com.scanrift.android.ui.theme.Dimens
import java.text.DateFormat
import java.util.Date

private val CardImageMaxWidth = 300.dp

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
fun CardDetailPage(
    card: Card,
    entry: CollectionEntry?,
    onConditionChange: (CollectionEntry, CardCondition) -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val isFoil = entry?.isFoil == true || card.isAlwaysFoil
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (AdaptiveRules.useTwoColumnCardDetail(maxWidth)) {
            val paneHeight = maxHeight
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().padding(16.dp), contentAlignment = Alignment.Center) {
                    TiltingCardImage(card, Dimens.DetailImageMax, entry != null, isFoil)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = paneHeight)
                        .padding(16.dp)
                        .padding(bottom = bottomInset),
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                ) {
                    Text(card.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    CardInfo(card)
                    entry?.let { CollectionInfoSection(it, onConditionChange) }
                    CardTextSection(card)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = bottomInset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TiltingCardImage(card, CardImageMaxWidth, entry != null, isFoil)
                CardInfo(card)
                entry?.let { CollectionInfoSection(it, onConditionChange) }
                CardTextSection(card)
            }
        }
    }
}

@Composable
private fun CollectionInfoSection(entry: CollectionEntry, onConditionChange: (CollectionEntry, CardCondition) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("In Your Collection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (entry.isFoil) FoilBadge()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Quantity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "×${entry.quantity}",
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Condition", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ConditionPicker(entry.condition) { onConditionChange(entry, it) }
            }
        }
        Text(
            "Added ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.dateAdded))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
    }
}

@Composable
private fun ConditionPicker(current: CardCondition, onChange: (CardCondition) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(current.shortName)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CardCondition.entries.forEach { condition ->
                DropdownMenuItem(
                    text = { Text(condition.shortName) },
                    onClick = { open = false; if (condition != current) onChange(condition) },
                )
            }
        }
    }
}

