package com.scanrift.android.ui.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.CardLanguage
import com.scanrift.android.ui.adaptive.AdaptiveRules
import com.scanrift.android.ui.theme.Dimens

private val MatchedGreen = Color(0xFF34C759)
private val UnmatchedOrange = Color(0xFFFF9500)

/**
 * Review of one binder page: the frozen photo with a box per card found — green when
 * the code matched a card, orange when it did not (tap → the correction sheet) — and the
 * same cards as a list. "Add N" puts the matched cards into the scan session.
 *
 * Side by side when the scanner has the width ([AdaptiveRules.usePageReviewSplit]),
 * photo above list otherwise. Keyed off the measured width, never the device type, so
 * folding and unfolding swaps the layout live.
 */
@Composable
fun PageReviewScreen(state: ScannerUiState, viewModel: ScannerViewModel, onAdded: () -> Unit) {
    val page = state.page ?: return
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val split = AdaptiveRules.usePageReviewSplit(maxWidth)
        Column(Modifier.fillMaxSize()) {
            PageReviewTopBar(page, onClose = viewModel::dismissPage)
            HorizontalDivider()
            if (split) {
                Row(Modifier.weight(1f)) {
                    PagePhoto(page, viewModel, Modifier.weight(1f).fillMaxHeight())
                    VerticalDivider()
                    PageItemList(page, viewModel, Modifier.width(Dimens.ScannerSidebarWidth).fillMaxHeight())
                }
            } else {
                PagePhoto(page, viewModel, Modifier.weight(1f).fillMaxWidth())
                HorizontalDivider()
                PageItemList(page, viewModel, Modifier.weight(1f).fillMaxWidth())
            }
            HorizontalDivider()
            val count = page.matched.size
            Button(
                onClick = { if (viewModel.addPageToSession() > 0) onAdded() },
                enabled = count > 0 && !page.isProcessing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(if (count == 1) "Add 1 card" else "Add $count cards", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PageReviewTopBar(page: PageReview, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Discard page") }
        Column(Modifier.weight(1f)) {
            Text("Binder Page", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val unmatched = page.items.size - page.matched.size
            Text(
                when {
                    page.isProcessing -> "Reading the page…"
                    page.error != null -> page.error
                    page.items.isEmpty() -> "No collector numbers found"
                    unmatched > 0 -> "${page.matched.size} matched, $unmatched to check"
                    else -> "${page.matched.size} matched"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (page.isProcessing) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 16.dp).size(20.dp))
    }
}

/** The photo, letterboxed, with each card's estimated outline drawn over it. */
@Composable
private fun PagePhoto(page: PageReview, viewModel: ScannerViewModel, modifier: Modifier) {
    val image = remember(page.image) { page.image.asImageBitmap() }
    val items by rememberUpdatedState(page.items)
    BoxWithConstraints(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        Image(
            bitmap = image,
            contentDescription = "Photographed page",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(image) {
                    detectTapGestures { tap ->
                        val frame = fittedFrame(Size(size.width.toFloat(), size.height.toFloat()), image.width, image.height)
                        val x = (tap.x - frame.first.x) / frame.second.width
                        val y = (tap.y - frame.first.y) / frame.second.height
                        items.firstOrNull { x in it.bounds.left..it.bounds.right && y in it.bounds.top..it.bounds.bottom }
                            ?.let { viewModel.startPageCorrection(it.id) }
                    }
                },
        ) {
            val (origin, fitted) = fittedFrame(size, image.width, image.height)
            items.forEach { item ->
                val box = item.bounds.clamped()
                drawRoundRect(
                    color = if (item.card != null) MatchedGreen else UnmatchedOrange,
                    topLeft = Offset(origin.x + box.left * fitted.width, origin.y + box.top * fitted.height),
                    size = Size(box.width * fitted.width, box.height * fitted.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
    }
}

/** Where a ContentScale.Fit image lands inside [container]: (top-left, size). */
private fun fittedFrame(container: Size, imageWidth: Int, imageHeight: Int): Pair<Offset, Size> {
    if (imageWidth <= 0 || imageHeight <= 0) return Offset.Zero to container
    val scale = minOf(container.width / imageWidth, container.height / imageHeight)
    val fitted = Size(imageWidth * scale, imageHeight * scale)
    return Offset((container.width - fitted.width) / 2f, (container.height - fitted.height) / 2f) to fitted
}

@Composable
private fun PageItemList(page: PageReview, viewModel: ScannerViewModel, modifier: Modifier) {
    if (!page.isProcessing && page.items.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Hold the page flat and fill the frame, or try a photo with less glare.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }
    LazyColumn(modifier) {
        items(page.items, key = { it.id }) { item ->
            PageItemRow(item, viewModel)
            HorizontalDivider(Modifier.padding(start = 72.dp))
        }
    }
}

@Composable
private fun PageItemRow(item: PageItem, viewModel: ScannerViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.startPageCorrection(item.id) }
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val card = item.card
        if (card != null) {
            ScanThumbnail(card, item.isFoil, item.language)
        } else {
            Box(
                Modifier.size(width = 40.dp, height = 56.dp).background(UnmatchedOrange.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = UnmatchedOrange)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                card?.name ?: "No match",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (card == null) UnmatchedOrange else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (card == null) "${item.code} · tap to pick the card" else "${card.setLabel} · ${card.publicCode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        PageLanguageChip(item.language) { viewModel.setPageItemLanguage(item.id, it) }
        IconButton(onClick = { viewModel.togglePageItemFoil(item.id) }, enabled = card != null && !card.isAlwaysFoil) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = "Toggle foil",
                tint = if (item.isFoil) Color(0xFFFFD60A) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { viewModel.removePageItem(item.id) }) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove from page", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PageLanguageChip(language: CardLanguage, onChange: (CardLanguage) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = "Language: ${language.displayName}" },
        ) { Text("${language.flag} ${language.code}", style = MaterialTheme.typography.labelMedium) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CardLanguage.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.flag}  ${option.displayName}") },
                    onClick = { open = false; onChange(option) },
                )
            }
        }
    }
}
