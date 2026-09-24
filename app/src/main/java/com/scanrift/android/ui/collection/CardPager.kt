package com.scanrift.android.ui.collection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanrift.android.domain.model.Card
import com.scanrift.android.domain.model.CardCondition
import com.scanrift.android.domain.model.CollectionEntry
import com.scanrift.android.ui.adaptive.AdaptiveRules
import com.scanrift.android.ui.theme.RarityShowcase
import com.scanrift.android.ui.util.lightImpact

class CardPagerActions(
    val onBack: (() -> Unit)?,
    val onCurrentCardChange: (Card) -> Unit,
    val onAdjustQuantity: (Card, CollectionEntry?, Int) -> Unit,
    val onSetQuantity: (Card, CollectionEntry?, Int) -> Unit,
    val onConditionChange: (CollectionEntry, CardCondition) -> Unit,
    val onToggleWishlist: (Card) -> Boolean,
    val onAddToList: (Card) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardPager(
    pages: List<Card>,
    startIndex: Int,
    state: CollectionBrowseState,
    actions: CardPagerActions,
) {
    val pagerState = rememberPagerState(initialPage = startIndex) { pages.size }
    val current = pages[pagerState.currentPage.coerceIn(pages.indices)]
    val currentEntry = CollectionQuery.preferredEntry(state.entriesByCard[current.id])
    val inWishlist = state.wishlist?.cards?.any { it.id == current.id } == true
    val feedback = LocalHapticFeedback.current

    LaunchedEffect(current.id) { actions.onCurrentCardChange(current) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showsNameInContent = AdaptiveRules.useTwoColumnCardDetail(maxWidth)
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
            windowInsets = WindowInsets(0),
                title = {
                    if (!showsNameInContent) Text(current.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    actions.onBack?.let {
                        IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (actions.onToggleWishlist(current)) feedback.lightImpact()
                    }) {
                        Icon(
                            if (inWishlist) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (inWishlist) "Remove from wishlist" else "Add to wishlist",
                            tint = if (inWishlist) RarityShowcase else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { actions.onAddToList(current) }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "Add to list")
                    }
                },
            )
            Box(Modifier.weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    val card = pages[index]
                    CardDetailPage(
                        card = card,
                        entry = CollectionQuery.preferredEntry(state.entriesByCard[card.id]),
                        onConditionChange = actions.onConditionChange,
                        bottomInset = FabClearance,
                    )
                }
                CollectionFab(
                    quantity = currentEntry?.quantity ?: 0,
                    onIncrement = { actions.onAdjustQuantity(current, currentEntry, 1) },
                    onDecrement = { actions.onAdjustQuantity(current, currentEntry, -1) },
                    onSetQuantity = { actions.onSetQuantity(current, currentEntry, it) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp),
                )
            }
        }
    }
}

private val FabClearance = 88.dp
