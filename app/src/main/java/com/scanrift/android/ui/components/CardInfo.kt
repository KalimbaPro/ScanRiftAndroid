package com.scanrift.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.Card
import com.scanrift.android.ui.text.RichCardText
import com.scanrift.android.ui.theme.domainColor
import com.scanrift.android.ui.theme.rarityColor

@Composable
fun CardInfo(
    card: Card,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
) {
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
