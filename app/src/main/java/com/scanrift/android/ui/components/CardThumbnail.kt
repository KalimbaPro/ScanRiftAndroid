package com.scanrift.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.scanrift.android.domain.model.Card

/**
 * A card image with all the ownership and print-variant treatments in one place.
 *
 * Three behaviours worth knowing, all matching iOS:
 *
 * - **Unowned cards** render desaturated *and* at 50% alpha. The `showUnownedInColor`
 *   preference only disables the desaturation — the fade always applies, because it is
 *   what makes ownership readable at a glance in a dense grid.
 * - **Landscape battlefields** are rotated 90° so they fill a portrait tile upright.
 *   This happens **only in the grid**; detail views and the deck browser show them in
 *   their native orientation.
 * - The alpha is applied to the image alone, never the parent, so the quantity badge
 *   stays at full opacity over a faded card.
 */
@Composable
fun CardThumbnail(
    card: Card,
    modifier: Modifier = Modifier,
    quantity: Int = 0,
    isFoil: Boolean = false,
    showUnownedInColor: Boolean = false,
    rotateLandscape: Boolean = false,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
    showQuantityBadge: Boolean = true,
) {
    val isOwned = quantity > 0
    val saturation = if (isOwned || showUnownedInColor) 1f else 0f
    val imageAlpha = if (isOwned) 1f else 0.5f
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (card.imageUrl == null) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (rotateLandscape && card.isLandscape) {
            // Swap the constraints, then rotate, so landscape art fills a portrait tile.
            BoxWithConstraints(Modifier.matchParentSize().clipToBounds()) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .requiredSize(width = maxHeight, height = maxWidth)
                        .graphicsLayer { rotationZ = 90f }
                        .alpha(imageAlpha),
                )
            }
        } else {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }),
                modifier = Modifier.matchParentSize().alpha(imageAlpha),
            )
        }

        // Rare, Epic and Showcase only exist as foils, so they always shimmer.
        val showFoil = isOwned && (isFoil || card.isAlwaysFoil)
        if (showFoil) {
            Box(Modifier.matchParentSize().clip(shape).foilSheen())
        }

        if (showQuantityBadge && quantity > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = quantity.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
