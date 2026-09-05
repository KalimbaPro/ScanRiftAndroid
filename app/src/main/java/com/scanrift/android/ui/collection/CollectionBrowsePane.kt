package com.scanrift.android.ui.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.ui.adaptive.AdaptiveRules
import com.scanrift.android.ui.adaptive.ProvideContentWidth
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * The collection browser: a grid beside a card detail.
 *
 * **Why not the default directive.** `calculatePaneScaffoldDirective` only yields two
 * panes at Expanded width (840dp+). An unfolded Fold in portrait is around 700dp, so
 * with the default the inner display would stay single-pane and the whole two-pane
 * feature would silently never appear on the device it was built for. The
 * ...WithTwoPanesOnMediumWidth variant splits at Medium instead — and it still reads
 * `windowPosture.hingeList`, so in book posture the boundary lands on the hinge.
 *
 * **Why the navigator is keyed on an id, not an index.** iOS's pager takes a start
 * index, and so did the old Android route. Change a filter while a detail is open and
 * the index now points at a different card, silently. Keying on `DisplayCard.id` makes
 * that impossible; a card filtered out of the list resolves to nothing instead.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun CollectionBrowsePane(
    listId: String?,
    onBack: () -> Unit,
    viewModel: CollectionBrowseViewModel = hiltViewModel(),
) {
    LaunchedEffect(listId) { viewModel.setListContext(listId) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showFilters by rememberSaveable { mutableStateOf(false) }

    val navigator = rememberListDetailPaneScaffoldNavigator<String>(
        scaffoldDirective = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(currentWindowAdaptiveInfo()),
    )

    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    // Hoisted above the scaffold so the scroll position survives a fold, which
    // recomposes the pane structure around it.
    val gridState = rememberLazyGridState()

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                ProvideContentWidth {
                    Column(Modifier.fillMaxSize()) {
                        CollectionToolbar(
                            state = state,
                            onSearchChange = viewModel::setSearchQuery,
                            onOpenFilters = { showFilters = true },
                            onOwnershipChange = viewModel::setOwnership,
                            onSortChange = viewModel::setSortOption,
                            onRemoveFilter = { updated -> viewModel.updateFilters(updated) },
                            onClearFilters = viewModel::clearFilters,
                        )
                        CollectionGrid(
                            state = state,
                            gridState = gridState,
                            selectedId = navigator.currentDestination?.contentKey,
                            onCardClick = { displayCard ->
                                viewModel.selectCard(displayCard.id)
                                scope.launch {
                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, displayCard.id)
                                }
                            },
                        )
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                ProvideContentWidth {
                    val key = navigator.currentDestination?.contentKey
                    val selected = state.cards.firstOrNull { it.id == key }
                    when {
                        key == null -> EmptyDetail("Select a card")
                        selected == null -> EmptyDetail("That card is no longer in this view")
                        else -> CardDetailPane(displayCard = selected)
                    }
                }
            }
        },
    )

    if (showFilters) {
        CollectionFilterSheet(
            state = state,
            onFiltersChange = viewModel::updateFilters,
            onClearAll = { viewModel.clearFilters(); showFilters = false },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun EmptyDetail(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Column count comes from the **pane's** width, not the window's — at Medium window
 * width this grid is only about half the screen.
 */
@Composable
private fun CollectionGrid(
    state: CollectionBrowseState,
    gridState: LazyGridState,
    selectedId: String?,
    onCardClick: (DisplayCard) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val paneWidth = maxWidth
        val columns = AdaptiveRules.gridColumnCount(paneWidth)
        val spacing = if (AdaptiveRules.useFixedGridColumns(paneWidth)) {
            Dimens.GridSpacingRegular
        } else {
            Dimens.GridSpacingCompact
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "${state.ownedCount} owned / ${state.totalCount} total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(state.cards, key = { it.id }) { displayCard ->
                CardThumbnail(
                    card = displayCard.card,
                    quantity = displayCard.quantity,
                    isFoil = displayCard.entry?.isFoil == true,
                    showUnownedInColor = state.showUnownedInColor,
                    // Landscape battlefields are rotated upright in the grid only.
                    rotateLandscape = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(Dimens.CARD_ASPECT_RATIO)
                        .then(
                            if (displayCard.id == selectedId) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onCardClick(displayCard) },
                )
            }
        }
    }
}
