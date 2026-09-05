package com.scanrift.android.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.Domain
import com.scanrift.android.ui.theme.Dimens

/**
 * Filters for the collection browser.
 *
 * Facet values are supplied by the ViewModel from the cards actually in scope, so a
 * newly synced set shows up here without any code change, and a list holding two card
 * types doesn't offer six.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CollectionFilterSheet(
    state: CollectionBrowseState,
    onFiltersChange: (CollectionFilters) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filters = state.filters

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = Dimens.SheetMaxWidth,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = Dimens.SheetMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (!filters.isEmpty) {
                    TextButton(onClick = onClearAll) { Text("Clear all") }
                }
            }

            // Colourless is deliberately absent from the domain filter, matching iOS —
            // it is the absence of a domain rather than a choice. It is still in the
            // data, so those cards remain reachable with the filter off.
            FilterSection(
                title = "Domain",
                values = Domain.filterOrder,
                selected = filters.domains,
                onToggle = { onFiltersChange(filters.copy(domains = filters.domains.toggle(it))) },
            )
            FilterSection(
                title = "Type",
                values = state.availableTypes,
                selected = filters.types,
                onToggle = { onFiltersChange(filters.copy(types = filters.types.toggle(it))) },
            )
            FilterSection(
                title = "Rarity",
                values = state.availableRarities,
                selected = filters.rarities,
                onToggle = { onFiltersChange(filters.copy(rarities = filters.rarities.toggle(it))) },
            )
            FilterSection(
                title = "Set",
                values = state.availableSets,
                selected = filters.sets,
                onToggle = { onFiltersChange(filters.copy(sets = filters.sets.toggle(it))) },
            )

            SectionTitle("Foil")
            FilterChip(
                selected = filters.foilOnly,
                onClick = { onFiltersChange(filters.copy(foilOnly = !filters.foilOnly)) },
                label = { Text("Foil only") },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    title: String,
    values: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (values.isEmpty()) return
    SectionTitle(title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(
                selected = value in selected,
                onClick = { onToggle(value) },
                label = { Text(value) },
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
