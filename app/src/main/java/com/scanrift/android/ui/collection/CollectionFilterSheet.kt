package com.scanrift.android.ui.collection

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanrift.android.R
import com.scanrift.android.domain.model.CardSupertype
import com.scanrift.android.domain.model.CardType
import com.scanrift.android.domain.model.Domain
import com.scanrift.android.domain.model.Rarity
import com.scanrift.android.ui.components.domainIcon
import com.scanrift.android.ui.components.SectionLabel
import com.scanrift.android.ui.components.SheetHeader
import com.scanrift.android.ui.theme.Dimens
import com.scanrift.android.ui.theme.RarityShowcase
import kotlin.math.roundToInt

/**
 * Filters for the collection browser.
 *
 * Facet values are supplied by the ViewModel from the cards actually in scope, so a
 * newly synced set shows up here without any code change, and a list holding two card
 * types doesn't offer six.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionFilterSheet(
    state: CollectionBrowseState,
    onFiltersChange: (CollectionFilters) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val filters = state.controls.filters
    val facets = state.facets

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        sheetMaxWidth = Dimens.SheetMaxWidth,
    ) {
        SheetHeader(title = "Filters", confirmLabel = "Done", onConfirm = onDismiss)
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            toggleSection("Domain", facets.domains, filters.domains, { domainIcon(it) }, tint = false) {
                onFiltersChange(filters.copy(domains = filters.domains.toggle(it)))
            }
            toggleSection("Type", facets.types, filters.types, { typeIcon(it) }, tint = true) {
                onFiltersChange(filters.copy(types = filters.types.toggle(it)))
            }
            toggleRows(facets.supertypes, filters.supertypes, { supertypeIcon(it) }, tint = true) {
                onFiltersChange(filters.copy(supertypes = filters.supertypes.toggle(it)))
            }
            toggleSection("Rarity", facets.rarities, filters.rarities, { rarityIcon(it) }, tint = false) {
                onFiltersChange(filters.copy(rarities = filters.rarities.toggle(it)))
            }
            item { SectionLabel("Foil", Modifier.padding(horizontal = 20.dp)) }
            item {
                FilterRow(
                    label = "Foil Only",
                    selected = filters.foilOnly,
                    leading = {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = if (filters.foilOnly) RarityShowcase else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = { onFiltersChange(filters.copy(foilOnly = !filters.foilOnly)) },
                )
            }
            toggleSection("Set", facets.sets, filters.sets, { null }, tint = false) {
                onFiltersChange(filters.copy(sets = filters.sets.toggle(it)))
            }
            facets.energy?.let { bounds ->
                item { SectionLabel("Energy", Modifier.padding(horizontal = 20.dp)) }
                item {
                    RangeRow(bounds, filters.energyMin, filters.energyMax) { lo, hi ->
                        onFiltersChange(filters.copy(energyMin = lo, energyMax = hi))
                    }
                }
            }
            facets.power?.let { bounds ->
                item { SectionLabel("Power", Modifier.padding(horizontal = 20.dp)) }
                item {
                    RangeRow(bounds, filters.powerMin, filters.powerMax) { lo, hi ->
                        onFiltersChange(filters.copy(powerMin = lo, powerMax = hi))
                    }
                }
            }
            if (state.prefs.luchoParameter) {
                toggleSection("Artist", facets.artists, filters.artists, { null }, tint = false) {
                    onFiltersChange(filters.copy(artists = filters.artists.toggle(it)))
                }
            }
            item {
                TextButton(onClick = onClearAll, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Clear All Filters", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun LazyListScope.toggleSection(
    title: String,
    facets: List<Facet>,
    selected: Set<String>,
    icon: (String) -> Int?,
    tint: Boolean,
    onToggle: (String) -> Unit,
) {
    if (facets.isEmpty()) return
    item(key = "header-$title") { SectionLabel(title, Modifier.padding(horizontal = 20.dp)) }
    toggleRows(facets, selected, icon, tint, keyPrefix = title, onToggle = onToggle)
}

private fun LazyListScope.toggleRows(
    facets: List<Facet>,
    selected: Set<String>,
    icon: (String) -> Int?,
    tint: Boolean,
    keyPrefix: String = "supertype",
    onToggle: (String) -> Unit,
) {
    items(facets, key = { "$keyPrefix-${it.value}" }) { facet ->
        FilterRow(
            label = facet.value,
            count = facet.count,
            selected = facet.value in selected,
            leading = icon(facet.value)?.let { res -> { FacetIcon(res, tint) } },
            onClick = { onToggle(facet.value) },
        )
    }
}

@Composable
private fun FacetIcon(@DrawableRes res: Int, tint: Boolean) {
    if (tint) {
        Icon(painterResource(res), contentDescription = null, modifier = Modifier.size(20.dp))
    } else {
        Image(painterResource(res), contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FilterRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        count?.let {
            Text("$it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (selected) "Selected" else null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RangeRow(bounds: IntRange, min: Int?, max: Int?, onChange: (Int?, Int?) -> Unit) {
    val lo = min ?: bounds.first
    val hi = max ?: bounds.last
    val caption = MaterialTheme.typography.labelSmall
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$lo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text("$hi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        RangeSlider(
            value = lo.toFloat()..hi.toFloat(),
            onValueChange = { range ->
                val newLo = range.start.roundToInt()
                val newHi = range.endInclusive.roundToInt()
                onChange(newLo.takeIf { it != bounds.first }, newHi.takeIf { it != bounds.last })
            },
            valueRange = bounds.first.toFloat()..bounds.last.toFloat(),
            steps = (bounds.last - bounds.first - 1).coerceAtLeast(0),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${bounds.first}", style = caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${bounds.last}", style = caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

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

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
