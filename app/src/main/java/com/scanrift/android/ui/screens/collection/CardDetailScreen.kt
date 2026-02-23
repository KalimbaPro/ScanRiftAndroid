package com.scanrift.android.ui.screens.collection

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.scanrift.android.R
import com.scanrift.android.data.local.entity.CardCondition
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.ui.components.FoilBadge
import com.scanrift.android.ui.components.QuantityStepper
import com.scanrift.android.ui.components.FoilOverlay
import com.scanrift.android.ui.components.RichCardText
import com.scanrift.android.ui.theme.DomainBody
import com.scanrift.android.ui.theme.DomainCalm
import com.scanrift.android.ui.theme.DomainChaos
import com.scanrift.android.ui.theme.DomainFury
import com.scanrift.android.ui.theme.DomainMind
import com.scanrift.android.ui.theme.DomainOrder
import com.scanrift.android.ui.theme.RarityCommon
import com.scanrift.android.ui.theme.RarityEpic
import com.scanrift.android.ui.theme.RarityRare
import com.scanrift.android.ui.theme.RarityShowcase
import com.scanrift.android.ui.theme.RarityUncommon
import com.scanrift.android.util.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardDetailScreen(
    card: CardEntity,
    quantity: Int = 0,
    condition: String? = null,
    dateAdded: Long? = null,
    onBack: () -> Unit = {},
    onAddToCollection: () -> Unit = {},
    onQuantityChange: (Int) -> Unit = {},
    onConditionChange: (String) -> Unit = {}
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showConditionMenu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Card image with 3D tilt
            CardImageWithTilt(
                imageUrl = card.imageUrl,
                contentDescription = card.accessibilityText ?: card.name,
                showFoil = card.isAlwaysFoil,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(Constants.UI.CARD_ASPECT_RATIO)
            )

            Spacer(Modifier.height(16.dp))

            // Card name and set info
            Text(
                text = card.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${card.setLabel} \u2022 ${card.publicCode}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // Info badges row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoBadge(
                    label = card.type,
                    iconRes = typeIconRes(card.type),
                    isTemplateIcon = true
                )
                InfoBadge(
                    label = card.rarity,
                    color = rarityColor(card.rarity),
                    iconRes = rarityIconRes(card.rarity)
                )

                card.domains.forEach { domain ->
                    InfoBadge(
                        label = domain,
                        color = domainColor(domain),
                        iconRes = domainIconRes(domain)
                    )
                }

                card.energy?.let { InfoBadge(label = "Energy: $it") }
                card.might?.let { InfoBadge(label = "Might: $it") }
                card.power?.let { InfoBadge(label = "Power: $it") }

                if (card.isAlwaysFoil) {
                    FoilBadge()
                }
            }

            card.supertype?.let { supertype ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = supertype,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Collection management
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            if (quantity > 0) {
                // "In Your Collection" header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "In Your Collection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (card.isAlwaysFoil) {
                        Spacer(Modifier.width(8.dp))
                        FoilBadge()
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quantity stepper
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Quantity",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        QuantityStepper(
                            quantity = quantity,
                            onQuantityChange = { newQty ->
                                if (newQty < quantity && quantity == 1) {
                                    showDeleteConfirmation = true
                                } else {
                                    onQuantityChange(newQty)
                                }
                            },
                            minValue = 0
                        )
                    }

                    // Condition picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Condition",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Box {
                            TextButton(onClick = { showConditionMenu = true }) {
                                val condObj = CardCondition.fromValue(
                                    condition ?: CardCondition.NEAR_MINT.value
                                )
                                Text(condObj.shortName)
                            }
                            DropdownMenu(
                                expanded = showConditionMenu,
                                onDismissRequest = { showConditionMenu = false }
                            ) {
                                CardCondition.entries.forEach { cond ->
                                    DropdownMenuItem(
                                        text = { Text("${cond.shortName} - ${cond.value}") },
                                        onClick = {
                                            onConditionChange(cond.value)
                                            showConditionMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Date added
                dateAdded?.let { timestamp ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Added ${formatDate(timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Add to Collection button for unowned cards
                Button(
                    onClick = onAddToCollection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp)
                ) {
                    Text("Add to Collection")
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()

            // Card text
            card.plainText?.let { plainText ->
                Spacer(Modifier.height(16.dp))
                RichCardText(
                    text = plainText,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Artist
            card.artist?.let { artist ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Illustrated by $artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tags
            if (card.tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    card.tags.forEach { tag ->
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Remove Card") },
            text = { Text("Are you sure you want to remove ${card.name} from your collection?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onQuantityChange(0)
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CardImageWithTilt(
    imageUrl: String?,
    contentDescription: String,
    showFoil: Boolean,
    modifier: Modifier = Modifier
) {
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    val rotationX by animateFloatAsState(
        targetValue = dragY * -0.1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "rotX"
    )
    val rotationY by animateFloatAsState(
        targetValue = dragX * 0.1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "rotY"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                this.rotationX = rotationX.coerceIn(-15f, 15f)
                this.rotationY = rotationY.coerceIn(-15f, 15f)
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                    },
                    onDragEnd = {
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragCancel = {
                        dragX = 0f
                        dragY = 0f
                    }
                )
            }
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
        )

        if (showFoil) {
            FoilOverlay(modifier = Modifier.clip(RoundedCornerShape(12.dp)))
        }
    }
}

@Composable
fun InfoBadge(
    label: String,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    iconRes: Int? = null,
    isTemplateIcon: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        iconRes?.let { resId ->
            Image(
                painter = painterResource(resId),
                contentDescription = null,
                colorFilter = if (isTemplateIcon) ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun rarityColor(rarity: String): Color = when (rarity) {
    "Common" -> RarityCommon
    "Uncommon" -> RarityUncommon
    "Rare" -> RarityRare
    "Epic" -> RarityEpic
    "Showcase" -> RarityShowcase
    else -> RarityCommon
}

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun domainColor(domain: String): Color = when (domain) {
    "Order" -> DomainOrder
    "Chaos" -> DomainChaos
    "Body" -> DomainBody
    "Mind" -> DomainMind
    "Calm" -> DomainCalm
    "Fury" -> DomainFury
    else -> Color.Gray
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
