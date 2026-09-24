package com.scanrift.android.ui.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.core.log.Log
import com.scanrift.android.domain.model.Card
import com.scanrift.android.ui.adaptive.AdaptiveRules
import com.scanrift.android.ui.adaptive.ProvideContentWidth
import com.scanrift.android.ui.components.AddToListSheet
import com.scanrift.android.ui.components.CardThumbnail
import com.scanrift.android.ui.components.EmptyState
import com.scanrift.android.ui.theme.Dimens
import com.scanrift.android.ui.util.shareFile
import kotlinx.coroutines.launch

private class PagerSnapshot(val cards: List<Card>, val startIndex: Int)

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
    val controls = state.controls
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var addToListCards by remember { mutableStateOf<List<Card>?>(null) }
    var pagerSnapshot by remember { mutableStateOf<PagerSnapshot?>(null) }
    var pagerCardId by remember { mutableStateOf<String?>(null) }

    val navigator = rememberListDetailPaneScaffoldNavigator<String>(
        scaffoldDirective = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(currentWindowAdaptiveInfo()),
    )
    val detailKey = navigator.currentDestination?.contentKey
    val singlePane = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden ||
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Hidden

    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }
    BackHandler(enabled = controls.isSelecting) { viewModel.exitSelection() }

    // Hoisted above the scaffold so the scroll position survives a fold, which
    // recomposes the pane structure around it.
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    fun openCard(index: Int) {
        val row = state.cards[index]
        pagerSnapshot = PagerSnapshot(state.cards.map { it.card }, index)
        pagerCardId = row.card.id
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, row.card.id) }
    }

    val rowActions = RowActions(
        onClick = { index, row ->
            if (controls.isSelecting) viewModel.toggleSelection(row.id) else openCard(index)
        },
        onLongClick = { row -> viewModel.startSelection(row.id) },
    )

    fun export(format: SelectionExportFormat) {
        scope.launch {
            runCatching { viewModel.writeSelectionExport(context.cacheDir, format) }
                .onSuccess { context.shareFile(it, format.mimeType) }
                .onFailure { Log.general.e(it, "Export error") }
        }
    }

    // Driven by `scaffoldValue`, not by the navigator's `scaffoldState`.
    //
    // `NavigableListDetailPaneScaffold` renders from `navigator.scaffoldState`, which
    // the navigator builds once and only re-syncs on navigation. Folding the device
    // updates `scaffoldDirective` and therefore `scaffoldValue`, but nothing pushes
    // that into `scaffoldState` — so refolding left both panes on screen until the
    // next navigation, which in practice meant switching tabs. `scaffoldValue` is
    // derived state and always current, and this overload animates to it on every
    // change.
    //
    // The cost is the predictive-back preview that the Navigable wrapper installs.
    // Back itself is unaffected; the `BackHandler` above already drives the navigator.
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        // This screen has no Scaffold of its own, so nothing else consumes the status
        // bar, navigation bar or display cutout — without this the toolbar sits under
        // the clock and the grid runs behind the gesture bar.
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        listPane = {
            AnimatedPane {
                ProvideContentWidth {
                    Column(Modifier.fillMaxSize()) {
                        CollectionTopBar(
                            state = state,
                            onBack = onBack,
                            onOpenFilters = { showFilters = true },
                            onOwnershipChange = viewModel::setOwnership,
                            onSortChange = viewModel::setSortOption,
                            onToggleDirection = viewModel::toggleSortDirection,
                            onViewModeChange = viewModel::setViewMode,
                            onShowUnownedInColorChange = viewModel::setShowUnownedInColor,
                            selection = SelectionActions(
                                onExit = viewModel::exitSelection,
                                onExportRiftboundGg = { export(SelectionExportFormat.RIFTBOUND_GG) },
                                onExportCsv = { export(SelectionExportFormat.CSV) },
                                onExportJson = { export(SelectionExportFormat.JSON) },
                                onAddToCollection = viewModel::addSelectionToCollection,
                                onAddToList = { addToListCards = state.selectedRows.map { it.card }.distinctBy { it.id } },
                                onToggleSelectAll = viewModel::toggleSelectAll,
                            ),
                        )
                        CollectionSearchField(controls.searchQuery, viewModel::setSearchQuery)
                        if (!controls.filters.isEmpty) {
                            ActiveFiltersBar(controls.filters, viewModel::updateFilters, viewModel::clearFilters)
                        }
                        if (controls.searchQuery.isNotEmpty()) {
                            SearchOptionsBar(
                                controls = controls,
                                onSortChange = viewModel::setSortOption,
                                onToggleDirection = viewModel::toggleSortDirection,
                                onOwnershipChange = viewModel::setOwnership,
                                onOpenFilters = { showFilters = true },
                            )
                        }
                        val highlighted = if (detailKey == null || controls.isSelecting) null else pagerCardId ?: detailKey
                        when {
                            state.isListScopeEmpty -> EmptyListContent(state.listContext?.isWishlist == true)
                            state.isCollectionEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                EmptyState(
                                    icon = Icons.Filled.Style,
                                    title = "No Cards Yet",
                                    message = "Go to the Scan tab to start scanning your Riftbound cards.",
                                )
                            }
                            controls.viewMode == CollectionViewMode.GRID ->
                                CollectionGrid(state, gridState, highlighted, rowActions)
                            else -> CollectionList(state, listState, rowActions)
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                ProvideContentWidth {
                    val fallback = remember(detailKey, state.isLoading) {
                        state.cards.indexOfFirst { it.card.id == detailKey }
                            .takeIf { it >= 0 }
                            ?.let { PagerSnapshot(state.cards.map { row -> row.card }, it) }
                    }
                    val snapshot = pagerSnapshot?.takeIf { it.cards.getOrNull(it.startIndex)?.id == detailKey } ?: fallback
                    when {
                        detailKey == null -> EmptyDetail("Select a card")
                        snapshot == null -> EmptyDetail("That card is no longer in this view")
                        else -> key(snapshot) {
                            CardPager(
                                pages = snapshot.cards,
                                startIndex = snapshot.startIndex,
                                state = state,
                                actions = CardPagerActions(
                                    onBack = if (singlePane) ({ scope.launch { navigator.navigateBack() } }) else null,
                                    onCurrentCardChange = { pagerCardId = it.id },
                                    onAdjustQuantity = viewModel::adjustQuantity,
                                    onSetQuantity = viewModel::setQuantity,
                                    onConditionChange = viewModel::setCondition,
                                    onToggleWishlist = viewModel::toggleWishlist,
                                    onAddToList = { addToListCards = listOf(it) },
                                ),
                            )
                        }
                    }
                }
            }
        },
    )

    if (showFilters) {
        CollectionFilterSheet(
            state = state,
            onFiltersChange = viewModel::updateFilters,
            onClearAll = viewModel::clearFilters,
            onDismiss = { showFilters = false },
        )
    }

    addToListCards?.let { cards ->
        AddToListSheet(
            cards = cards,
            lists = state.customLists,
            onToggle = viewModel::toggleListMembership,
            onSaveList = viewModel::saveList,
            onDismiss = { addToListCards = null },
        )
    }
}

private class RowActions(
    val onClick: (Int, DisplayCard) -> Unit,
    val onLongClick: (DisplayCard) -> Unit,
)

@Composable
private fun EmptyDetail(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyListContent(isWishlist: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = if (isWishlist) Icons.Filled.Star else Icons.Filled.Folder,
            title = "No Cards",
            message = if (isWishlist) {
                "Tap the star on any card to add it to your wishlist."
            } else {
                "Add cards to this list from the card detail view."
            },
        )
    }
}

@Composable
private fun SelectionLongPress(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val configuration = remember(base) {
        object : ViewConfiguration by base {
            override val longPressTimeoutMillis: Long = Dimens.LONG_PRESS_MULTI_SELECT_MS
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides configuration, content = content)
}

/**
 * Column count comes from the **pane's** width, not the window's — at Medium window
 * width this grid is only about half the screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionGrid(
    state: CollectionBrowseState,
    gridState: LazyGridState,
    highlightedCardId: String?,
    actions: RowActions,
) {
    val selecting = state.controls.isSelecting
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val paneWidth = maxWidth
        val columns = AdaptiveRules.gridColumnCount(paneWidth)
        val spacing = if (AdaptiveRules.useFixedGridColumns(paneWidth)) Dimens.GridSpacingRegular else Dimens.GridSpacingCompact
        SelectionLongPress {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "info", span = { GridItemSpan(maxLineSpan) }) { InfoBar(state, horizontalPadding = 0.dp) }
                itemsIndexed(state.cards, key = { _, row -> row.id }) { index, row ->
                    val selected = row.id in state.controls.selectedIds
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(Dimens.CARD_ASPECT_RATIO)
                            .clip(shape)
                            .combinedClickable(
                                onClick = { actions.onClick(index, row) },
                                onLongClick = if (selecting) null else ({ actions.onLongClick(row) }),
                            ),
                    ) {
                        CardThumbnail(
                            card = row.card,
                            quantity = row.quantity,
                            isFoil = row.entry?.isFoil == true || row.card.isAlwaysFoil,
                            showUnownedInColor = state.prefs.showUnownedInColor,
                            // Landscape battlefields are rotated upright in the grid only.
                            rotateLandscape = true,
                            modifier = Modifier.matchParentSize(),
                        )
                        when {
                            selecting -> SelectionOverlay(selected)
                            row.card.id == highlightedCardId ->
                                Box(Modifier.matchParentSize().border(3.dp, MaterialTheme.colorScheme.primary, shape))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.SelectionOverlay(selected: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = if (selected) 0.15f else 0.35f), shape))
    if (selected) Box(Modifier.matchParentSize().border(2.dp, accent, shape))
    Box(
        Modifier
            .align(Alignment.Center)
            .size(28.dp)
            .dropShadow(CircleShape) {
                color = Color.Black
                alpha = 0.4f
                radius = 2f * density
                offset = Offset(0f, density)
            }
            .background(if (selected) accent else Color.White.copy(alpha = 0.4f), CircleShape)
            .border(2.dp, Color.White, CircleShape),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionList(state: CollectionBrowseState, listState: LazyListState, actions: RowActions) {
    val selecting = state.controls.isSelecting
    val accent = MaterialTheme.colorScheme.primary
    SelectionLongPress {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            stickyHeader(key = "info") { InfoBar(state) }
            itemsIndexed(state.cards, key = { _, row -> row.id }) { index, row ->
                val selected = row.id in state.controls.selectedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { actions.onClick(index, row) },
                            onLongClick = if (selecting) null else ({ actions.onLongClick(row) }),
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selecting) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .background(if (selected) accent else Color.Transparent, CircleShape)
                                .border(1.5.dp, if (selected) accent else MaterialTheme.colorScheme.outline, CircleShape),
                        )
                    }
                    Box(Modifier.size(width = 50.dp, height = 70.dp)) {
                        CardThumbnail(
                            card = row.card,
                            quantity = row.quantity,
                            isFoil = row.entry?.isFoil == true || row.card.isAlwaysFoil,
                            showUnownedInColor = state.prefs.showUnownedInColor,
                            cornerRadius = 4.dp,
                            showQuantityBadge = false,
                            modifier = Modifier.matchParentSize(),
                        )
                        if (row.quantity > 1) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 4.dp, y = 4.dp)
                                    .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                                    .background(accent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${row.quantity}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 3.dp),
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            row.card.setLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                row.card.rarity,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                            if (!row.isOwned) {
                                Text("Not owned", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start = if (selecting) 112.dp else 78.dp))
            }
        }
    }
}
