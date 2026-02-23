package com.scanrift.android.ui.screens.collection

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanrift.android.R

private val DOMAINS = listOf("Order", "Chaos", "Body", "Mind", "Calm", "Fury")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    filterState: FilterState,
    availableSets: List<String>,
    availableTypes: List<String>,
    availableRarities: List<String>,
    onDomainToggle: (String) -> Unit,
    onTypeToggle: (String) -> Unit,
    onRarityToggle: (String) -> Unit,
    onSetToggle: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filters", style = MaterialTheme.typography.titleLarge)
                if (filterState.isActive) {
                    TextButton(onClick = onClearAll) {
                        Text("Clear All")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Domain section
            FilterSection(title = "Domain") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DOMAINS.forEach { domain ->
                        FilterChip(
                            selected = domain in filterState.domains,
                            onClick = { onDomainToggle(domain) },
                            leadingIcon = {
                                domainIconRes(domain)?.let { resId ->
                                    Image(
                                        painter = painterResource(resId),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            label = { Text(domain) }
                        )
                    }
                }
            }

            // Type section
            if (availableTypes.isNotEmpty()) {
                FilterSection(title = "Type") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        availableTypes.forEach { type ->
                            FilterChip(
                                selected = type in filterState.types,
                                onClick = { onTypeToggle(type) },
                                leadingIcon = {
                                    typeIconRes(type)?.let { resId ->
                                        Image(
                                            painter = painterResource(resId),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                label = { Text(type) }
                            )
                        }
                    }
                }
            }

            // Rarity section
            if (availableRarities.isNotEmpty()) {
                FilterSection(title = "Rarity") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        availableRarities.forEach { rarity ->
                            FilterChip(
                                selected = rarity in filterState.rarities,
                                onClick = { onRarityToggle(rarity) },
                                leadingIcon = {
                                    rarityIconRes(rarity)?.let { resId ->
                                        Image(
                                            painter = painterResource(resId),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                label = { Text(rarity) }
                            )
                        }
                    }
                }
            }

            // Set section
            if (availableSets.isNotEmpty()) {
                FilterSection(title = "Set") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        availableSets.forEach { set ->
                            FilterChip(
                                selected = set in filterState.sets,
                                onClick = { onSetToggle(set) },
                                label = { Text(set) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

private fun typeIconRes(type: String): Int? = when (type.lowercase()) {
    "unit" -> R.drawable.ic_type_unit
    "gear" -> R.drawable.ic_type_gear
    "spell" -> R.drawable.ic_type_spell
    "legend" -> R.drawable.ic_type_legend
    "rune" -> R.drawable.ic_type_rune
    "champion" -> R.drawable.ic_type_champion
    "battlefield" -> R.drawable.ic_type_battlefield
    else -> null
}

private fun rarityIconRes(rarity: String): Int? = when (rarity.lowercase()) {
    "common" -> R.drawable.ic_rarity_common
    "uncommon" -> R.drawable.ic_rarity_uncommon
    "rare" -> R.drawable.ic_rarity_rare
    "epic" -> R.drawable.ic_rarity_epic
    "showcase" -> R.drawable.ic_rarity_showcase
    else -> null
}

private fun domainIconRes(domain: String): Int? = when (domain.lowercase()) {
    "fury" -> R.drawable.ic_rune_fury
    "calm" -> R.drawable.ic_rune_calm
    "mind" -> R.drawable.ic_rune_mind
    "chaos" -> R.drawable.ic_rune_chaos
    "body" -> R.drawable.ic_rune_body
    "order" -> R.drawable.ic_rune_order
    else -> null
}
