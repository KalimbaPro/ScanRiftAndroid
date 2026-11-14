package com.scanrift.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardLanguage

/**
 * A card image with all the ownership and print-variant treatments in one place.
 *
 * Three behaviours worth knowing, all matching iOS:
 *
 * - **Unowned cards** render exactly like owned ones: full colour, no fade. They used
 *   to be greyscale behind a "show unowned in colour" toggle; the quantity badge alone
 *   now marks ownership.
 * - **Landscape battlefields** are rotated 90° so they fill a portrait tile upright.
 *   This happens **only in the grid**; detail views and the deck browser show them in
 *   their native orientation.
 * - A card banned in the user's competitive region gets a red **BANNED** band across
 *   the image. It lives here, in the one shared thumbnail, so the collection grid and
 *   list, deck views, the scanner and the detail image all show it without each screen
 *   having to remember to.
 */
@Composable
fun CardThumbnail(
    card: Card,
    modifier: Modifier = Modifier,
    quantity: Int = 0,
    isFoil: Boolean = false,
    rotateLandscape: Boolean = false,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
    showQuantityBadge: Boolean = true,
    showBannedBand: Boolean = true,
    /**
     * The owned copy's language. Chinese and Korean copies show their localised art when
     * the API has it, and every non-English copy gets a small flag badge. Null for
     * "not a specific copy" (catalogue rows, deck slots).
     */
    language: CardLanguage? = null,
) {
    val imageUrl = card.imageUrl(language ?: CardLanguage.EN)
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (card.isJudgePromo) {
            JudgePromoPlaceholder(card, Modifier.matchParentSize())
        } else if (imageUrl == null) {
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
                    model = imageUrl,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .requiredSize(width = maxHeight, height = maxWidth)
                        .graphicsLayer { rotationZ = 90f },
                )
            }
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = card.name,
                // A landscape battlefield cropped into a portrait tile shows its middle
                // and hides its name, so letterbox it instead. The collection grid is
                // the one place that rotates them upright rather than fitting them.
                contentScale = if (card.isLandscape) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }

        // Rare, Epic and Showcase only exist as foils, so they always shimmer.
        val showFoil = isFoil || (quantity > 0 && card.isAlwaysFoil)
        if (showFoil) {
            Box(Modifier.matchParentSize().clip(shape).foilSheen())
        }

        if (showBannedBand) CardBannedBand(card)

        if (language != null && language != CardLanguage.EN) {
            Text(
                text = language.flag,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 2.dp)
                    .semantics { contentDescription = language.displayName },
            )
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

private val BannedRed = Color(0xFFD70015)

/**
 * The BANNED band. Public only for the deck browser's cell, which draws its own image
 * with a different dimming rule; every other surface gets it through [CardThumbnail].
 *
 * A sash across the top-right corner, so the art and the card's name stay visible. Its
 * ends run past the tile's clipped edges, which is what reads as a corner ribbon. Sized
 * from the tile's own width, so it reads the same on a 50dp list thumbnail and a 400dp
 * detail image.
 */
@Composable
fun BoxScope.CardBannedBand(card: Card) {
    if (!card.banned) return
    BoxWithConstraints(Modifier.matchParentSize()) {
        val bandHeight = (maxWidth * 0.12f).coerceAtLeast(9.dp)
        // Distance of the sash's centre from the top and right edges. The visible part is
        // about 2.8 × inset long, so small thumbnails push it further in to keep the word whole.
        val inset = maxOf(maxWidth * 0.17f, bandHeight * 1.6f)
        val bandLength = maxOf(maxWidth * 0.7f, inset * 3.2f)
        val fontSize = with(LocalDensity.current) { (bandHeight * 0.6f).toSp() }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = maxWidth - inset - bandLength / 2, y = inset - bandHeight / 2)
                .requiredWidth(bandLength)
                .height(bandHeight)
                .graphicsLayer { rotationZ = 45f }
                .background(BannedRed.copy(alpha = 0.92f))
                .semantics { contentDescription = "Banned" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "BANNED",
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                letterSpacing = fontSize * 0.08f,
                maxLines = 1,
            )
        }
    }
}

/**
 * Stand-in for a judge promo's art. Upstream only has the regular print, without the
 * judge seal, and showing that would misidentify the card, so judge promos are drawn as
 * text instead. Fills whatever box it is given; the BANNED band, foil sheen and badges
 * still draw over it.
 */
@Composable
fun JudgePromoPlaceholder(card: Card, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .semantics { contentDescription = "Judge promo card, ${card.name}" },
        contentAlignment = Alignment.Center,
    ) {
        // Grid tiles get the label alone; anything roomier also names the card.
        val roomy = maxWidth >= 120.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Verified,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(if (roomy) 36.dp else 20.dp),
            )
            Text(
                text = "Judge promo card",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = if (roomy) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (roomy) {
                Text(
                    text = card.name,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
