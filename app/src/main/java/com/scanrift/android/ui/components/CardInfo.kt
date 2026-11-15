package com.scanrift.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanrift.android.R
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.Domain
import com.scanrift.android.ui.text.RichCardText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardInfo(card: Card, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CardStatusPills(card)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoBadge("Set", card.setLabel)
            InfoBadge("Rarity", card.rarity)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoBadge("Type", card.type)
            card.supertype?.let { InfoBadge("Supertype", it) }
            card.energy?.let { InfoBadge("Energy", "$it") }
            card.power?.let { InfoBadge("Power", "$it") }
        }
        if (card.domains.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Domains:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                card.domains.forEach { domain ->
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        domainIcon(domain)?.let {
                            Image(painterResource(it), contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Text(domain, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (card.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tags:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                card.tags.forEach { tag ->
                    Text(
                        tag,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFFF9500).copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        card.artist?.let {
            Text("Art by $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal val StatusBannedRed = Color(0xFFD70015)
internal val StatusErrataOrange = Color(0xFFFF9500)

/**
 * "Banned" and "Errata" pills right under the image, so a card's status reads at a
 * glance instead of only from the corner sash and the Errata card further down.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardStatusPills(card: Card) {
    val errata = card.errata?.isNotBlank() == true
    if (!card.banned && !errata) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (card.banned) StatusPill("Banned", StatusBannedRed)
        if (errata) {
            val date = card.errataDate?.let { " · ${formatErrataDate(it)}" }.orEmpty()
            StatusPill("Errata$date", StatusErrataOrange)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun InfoBadge(label: String, value: String) {
    Column(
        modifier = Modifier
            .widthIn(min = 60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CardTextSection(card: Card) {
    // plainText is the field that carries the :rb_xxx: / [Keyword] / (reminder)
    // markup — iOS renders that one too, deliberately, not richText.
    val text = (card.plainText ?: card.richText)?.takeIf { it.isNotBlank() } ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        RichCardText(text, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Official errata, shown above the card text so it is seen first. The printed card is
 * wrong; this is the text that applies. Styled like [CardTextSection] with an accent
 * edge so it reads as a correction rather than a second description.
 */
@Composable
fun ErrataSection(card: Card) {
    val errata = card.errata?.takeIf { it.isNotBlank() } ?: return
    val accent = Color(0xFFFF9500)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Errata", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = accent, modifier = Modifier.weight(1f))
            card.errataDate?.let { date ->
                Text(
                    formatErrataDate(date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Riot changed this card's text. This is the version that applies, whatever the printed card says.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RichCardText(errata, modifier = Modifier.fillMaxWidth())
    }
}

/** `2026-10-09` → the user's medium date; anything unparseable is shown as sent. */
private fun formatErrataDate(date: String): String = runCatching {
    val parsed = java.time.LocalDate.parse(date)
    java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).format(parsed)
}.getOrDefault(date)

@DrawableRes
fun domainIcon(domain: String): Int? = when (domain) {
    Domain.FURY -> R.drawable.ic_rune_fury
    Domain.CALM -> R.drawable.ic_rune_calm
    Domain.MIND -> R.drawable.ic_rune_mind
    Domain.CHAOS -> R.drawable.ic_rune_chaos
    Domain.BODY -> R.drawable.ic_rune_body
    Domain.ORDER -> R.drawable.ic_rune_order
    else -> null
}
