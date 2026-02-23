package com.scanrift.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.scanrift.android.data.local.entity.CardEntity
import com.scanrift.android.util.Constants

@Composable
fun CardThumbnail(
    card: CardEntity,
    quantity: Int = 0,
    isSelected: Boolean = false,
    showQuantity: Boolean = true,
    onClick: () -> Unit = {}
) {
    val isOwned = quantity > 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(Constants.UI.CARD_ASPECT_RATIO)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(
                    3.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
    ) {
        // Card image - greyscale + dimmed when not owned
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.accessibilityText ?: card.name,
            contentScale = ContentScale.Crop,
            colorFilter = if (!isOwned) ColorFilter.colorMatrix(
                ColorMatrix().apply { setToSaturation(0f) }
            ) else null,
            alpha = if (!isOwned) 0.5f else 1f,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
        )

        // Foil shimmer overlay
        if (isOwned && card.isAlwaysFoil) {
            FoilOverlay(modifier = Modifier.clip(RoundedCornerShape(8.dp)))
        }

        // Quantity badge
        if (showQuantity && quantity > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$quantity",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Foil badge
        if (card.isAlwaysFoil) {
            FoilBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            )
        }

        // Selection overlay
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun FoilBadge(modifier: Modifier = Modifier) {
    Text(
        text = "FOIL",
        modifier = modifier
            .background(
                Color(0xFFFFD700).copy(alpha = 0.9f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        color = Color.Black,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold
    )
}
