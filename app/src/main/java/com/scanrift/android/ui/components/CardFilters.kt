package com.scanrift.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanrift.android.R
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardFacetFilter
import com.scanrift.android.domain.model.CardFacets
import com.scanrift.android.domain.model.CardStat
import com.scanrift.android.domain.model.CardStatusFilter
import com.scanrift.android.domain.model.CardSupertype
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.Domain
import com.scanrift.android.domain.model.Rarity
import com.scanrift.android.ui.theme.Dimens
import com.scanrift.android.ui.theme.domainColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardFilterSheet(
    cards: List<Card>,
    filter: CardFacetFilter,
    onFilterChange: (CardFacetFilter) -> Unit,
    resultCount: Int,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    canReset: Boolean = !filter.isEmpty,
    showArtists: Boolean = false,
    yourCopies: (@Composable FlowRowScope.() -> Unit)? = null,
) {
    val facets = remember(cards) { CardFacets.of(cards) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        sheetMaxWidth = Dimens.SheetMaxWidth,
    ) {
        SheetHeader(title = "Filters", confirmLabel = "Done", onConfirm = onDismiss)
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            FilterGroup("Card") {
                FacetSection("Domain") {
                    DomainFilterBar(cards, filter.domains, { onFilterChange(filter.copy(domains = filter.domains.toggle(it))) })
                }
                if (facets.types.isNotEmpty() || facets.supertypes.isNotEmpty()) {
                    FacetSection("Type") {
                        ChipFlow {
                            facets.types.forEach { facet ->
                                FacetChip(facet.value, facet.value in filter.types, count = facet.count, icon = typeIcon(facet.value)) {
                                    onFilterChange(filter.copy(types = filter.types.toggle(facet.value)))
                                }
                            }
                            facets.supertypes.forEach { facet ->
                                FacetChip(
                                    facet.value, facet.value in filter.supertypes, count = facet.count,
                                    icon = supertypeIcon(facet.value),
                                ) {
                                    onFilterChange(filter.copy(supertypes = filter.supertypes.toggle(facet.value)))
                                }
                            }
                        }
                    }
                }
                facets.stats.forEach { (stat, buckets) ->
                    FacetSection(stat.label) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val selected = filter.stats[stat].orEmpty()
                            buckets.forEach { bucket ->
                                StatPill(stat, bucket, bucket in selected) {
                                    onFilterChange(filter.withStat(stat, selected.toggle(bucket)))
                                }
                            }
                        }
                    }
                }
            }
            FilterGroup("Printing") {
                FacetSection("Set") {
                    ChipFlow {
                        facets.sets.forEach { facet ->
                            FacetChip(facet.value, facet.value in filter.sets, count = facet.count) {
                                onFilterChange(filter.copy(sets = filter.sets.toggle(facet.value)))
                            }
                        }
                    }
                }
                if (facets.rarities.isNotEmpty()) {
                    FacetSection("Rarity") {
                        ChipFlow {
                            facets.rarities.forEach { facet ->
                                FacetChip(
                                    facet.value, facet.value in filter.rarities, count = facet.count,
                                    icon = rarityIcon(facet.value), tintIcon = false,
                                ) {
                                    onFilterChange(filter.copy(rarities = filter.rarities.toggle(facet.value)))
                                }
                            }
                        }
                    }
                }
                if (showArtists && facets.artists.isNotEmpty()) {
                    FacetSection("Artist") {
                        ChipFlow {
                            facets.artists.forEach { facet ->
                                FacetChip(facet.value, facet.value in filter.artists) {
                                    onFilterChange(filter.copy(artists = filter.artists.toggle(facet.value)))
                                }
                            }
                        }
                    }
                }
            }
            FilterGroup("Legality", footer = "Banned and errata follow your competitive region.") {
                ChipFlow {
                    facets.statuses.forEach { (status, count) ->
                        FacetChip(status.label, status in filter.statuses, count = count, tint = status.color) {
                            onFilterChange(filter.copy(statuses = filter.statuses.toggle(status)))
                        }
                    }
                }
            }
            yourCopies?.let { chips ->
                FilterGroup("Your copies", footer = "Filters owned copies by finish and printed language.") {
                    ChipFlow(chips)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onReset, enabled = canReset) { Text("Reset") }
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Show $resultCount cards") }
        }
    }
}

@Composable
private fun FilterGroup(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
        footer?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FacetSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun StatPill(stat: CardStat, bucket: Int, selected: Boolean, onClick: () -> Unit) {
    val label = CardStat.label(bucket)
    Box(
        Modifier
            .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            .selectable(selected, role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = "${stat.label} $label" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
fun FacetChip(
    label: String,
    selected: Boolean,
    count: Int? = null,
    @DrawableRes icon: Int? = null,
    tintIcon: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val content = if (selected) tint else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) tint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .border(1.dp, if (selected) tint else Color.Transparent, CircleShape)
            .selectable(selected, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let {
            if (tintIcon) {
                Icon(painterResource(it), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            } else {
                Image(painterResource(it), contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
        )
        count?.let {
            Text("$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DomainFilterBar(
    cards: List<Card>,
    selection: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts = remember(cards) {
        Domain.filterOrder.associateWith { domain -> cards.count { domain in it.domains } }
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Domain.filterOrder.forEach { domain ->
            val selected = domain in selection
            val tint = domainColor(domain)
            val prominent = selection.isEmpty() || selected
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (selected) tint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .border(1.dp, if (selected) tint else Color.Transparent, shape)
                    .selectable(selected, role = Role.Checkbox) { onToggle(domain) }
                    .semantics { contentDescription = domain }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                domainIcon(domain)?.let {
                    Image(
                        painterResource(it),
                        contentDescription = null,
                        colorFilter = if (prominent) null else DESATURATED,
                        modifier = Modifier.size(26.dp).alpha(if (prominent) 1f else 0.6f),
                    )
                }
                Text(
                    "${counts.getValue(domain)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Active filters are shown as removable chips rather than only a count, so it is
 * always obvious why the grid is showing fewer cards than expected — the single most
 * common confusion in a filtered list.
 */
@Composable
fun ActiveFiltersBar(
    filter: CardFacetFilter,
    onChange: (CardFacetFilter) -> Unit,
    onClearAll: () -> Unit,
    leading: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        CardStatusFilter.entries.filter { it in filter.statuses }.forEach { status ->
            RemovableChip(status.label, accent = status.color) { onChange(filter.copy(statuses = filter.statuses - status)) }
        }
        Domain.filterOrder.filter { it in filter.domains }.forEach {
            RemovableChip(it, accent = domainColor(it)) { onChange(filter.copy(domains = filter.domains - it)) }
        }
        filter.types.sorted().forEach { RemovableChip(it) { onChange(filter.copy(types = filter.types - it)) } }
        filter.supertypes.sorted().forEach { RemovableChip(it) { onChange(filter.copy(supertypes = filter.supertypes - it)) } }
        CardStat.entries.forEach { stat ->
            val selected = filter.stats[stat].orEmpty()
            selected.sorted().forEach { bucket ->
                RemovableChip("${stat.label} ${CardStat.label(bucket)}") { onChange(filter.withStat(stat, selected - bucket)) }
            }
        }
        filter.sets.sorted().forEach { RemovableChip(it) { onChange(filter.copy(sets = filter.sets - it)) } }
        filter.rarities.sortedBy { Rarity.sortRank(it) }.forEach {
            RemovableChip(it) { onChange(filter.copy(rarities = filter.rarities - it)) }
        }
        filter.artists.sorted().forEach { RemovableChip(it) { onChange(filter.copy(artists = filter.artists - it)) } }
        Text(
            "Clear All",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.clickable(onClick = onClearAll).padding(4.dp),
        )
    }
}

@Composable
fun RemovableChip(label: String, accent: Color = MaterialTheme.colorScheme.primary, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = accent)
        Icon(
            Icons.Filled.Cancel,
            contentDescription = "Remove $label",
            tint = accent,
            modifier = Modifier.size(14.dp).clip(CircleShape).clickable(onClick = onRemove),
        )
    }
}

private val DESATURATED = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.2f) })

val CardStatusFilter.color: Color
    get() = when (this) {
        CardStatusFilter.BANNED -> StatusBannedRed
        CardStatusFilter.ERRATA -> StatusErrataOrange
    }

fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

@DrawableRes
private fun typeIcon(type: String): Int? = when (type) {
    CardType.UNIT -> R.drawable.ic_type_unit
    CardType.SPELL -> R.drawable.ic_type_spell
    CardType.GEAR -> R.drawable.ic_type_gear
    CardType.LEGEND -> R.drawable.ic_type_legend
    CardType.BATTLEFIELD -> R.drawable.ic_type_battlefield
    CardType.RUNE -> R.drawable.ic_type_rune
    else -> null
}

@DrawableRes
private fun supertypeIcon(supertype: String): Int? = when (supertype) {
    CardSupertype.CHAMPION -> R.drawable.ic_type_champion
    CardSupertype.SIGNATURE -> R.drawable.ic_type_spell
    CardSupertype.TOKEN -> R.drawable.ic_type_unit
    else -> null
}

@DrawableRes
private fun rarityIcon(rarity: String): Int? = when (rarity) {
    Rarity.COMMON -> R.drawable.ic_rarity_common
    Rarity.UNCOMMON -> R.drawable.ic_rarity_uncommon
    Rarity.RARE -> R.drawable.ic_rarity_rare
    Rarity.EPIC -> R.drawable.ic_rarity_epic
    Rarity.SHOWCASE -> R.drawable.ic_rarity_showcase
    else -> null
}
