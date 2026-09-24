package com.scanrift.android.ui.decks

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanrift.android.R
import com.scanrift.android.domain.model.Domain
import com.scanrift.android.ui.theme.domainColor

internal val ValidGreen = Color(0xFF34C759)
internal val WarningOrange = Color(0xFFFF9500)
internal val MissingRed = Color(0xFFFF3B30)
internal val TagOrange = Color(0xFFFF9500)

internal const val SECTION_TOGGLE_MS = 200

enum class HeaderMode { EXACT, MINIMUM, MAXIMUM }

@Composable
fun DomainIcons(domains: List<String>, size: Dp) {
    domains.forEach { domain ->
        val icon = when (domain) {
            Domain.BODY -> R.drawable.ic_rune_body
            Domain.CALM -> R.drawable.ic_rune_calm
            Domain.CHAOS -> R.drawable.ic_rune_chaos
            Domain.FURY -> R.drawable.ic_rune_fury
            Domain.MIND -> R.drawable.ic_rune_mind
            Domain.ORDER -> R.drawable.ic_rune_order
            else -> null
        }
        if (icon != null) {
            Image(painterResource(icon), contentDescription = domain, modifier = Modifier.size(size))
        } else {
            Box(Modifier.size(size).background(domainColor(domain), CircleShape))
        }
    }
}

@Composable
fun DeckSectionHeader(
    title: String,
    current: Int,
    target: Int,
    mode: HeaderMode = HeaderMode.EXACT,
    expanded: Boolean? = null,
    onToggle: () -> Unit = {},
) {
    val satisfied = when (mode) {
        HeaderMode.EXACT -> current == target
        HeaderMode.MINIMUM -> current >= target
        HeaderMode.MAXIMUM -> current <= target
    }
    SectionTitleRow(title = title, expanded = expanded, onToggle = onToggle) {
        Text(
            text = "$current/$target",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (satisfied) ValidGreen else MissingRed,
        )
    }
}

@Composable
fun SectionTitleRow(
    title: String,
    expanded: Boolean?,
    onToggle: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val clickModifier = if (expanded == null) {
        Modifier
    } else {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onToggle,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().then(clickModifier).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing()
        if (expanded != null) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun TagCapsules(tags: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.forEach { tag ->
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(TagOrange.copy(alpha = 0.2f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
