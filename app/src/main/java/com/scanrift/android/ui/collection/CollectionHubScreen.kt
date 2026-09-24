package com.scanrift.android.ui.collection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanrift.android.domain.model.CardList
import com.scanrift.android.ui.adaptive.ProvideContentWidth
import com.scanrift.android.ui.components.CreateListSheet
import com.scanrift.android.ui.theme.Dimens
import com.scanrift.android.ui.theme.parseHexColor

/**
 * Tiles for "All Collection" plus every list.
 *
 * The grid uses an adaptive cell, so more columns simply fall out on a wider pane —
 * no branch needed, which is why this screen has no adaptive code of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionHubScreen(
    onOpenAllCollection: () -> Unit,
    onOpenList: (String) -> Unit,
    viewModel: CollectionHubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var search by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CardList?>(null) }
    var deleting by remember { mutableStateOf<CardList?>(null) }
    val visibleLists = state.lists.filter { search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Collection") },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New list")
            }
        },
    ) { padding ->
        ProvideContentWidth(Modifier.padding(padding).fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = Dimens.HubGridItemMin),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search lists") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (search.isNotEmpty()) {
                                IconButton(onClick = { search = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "all") {
                    HubTile(
                        title = "All Collection",
                        subtitle = "${state.totalCards} cards (${state.uniqueCards} unique)",
                        tint = MaterialTheme.colorScheme.primary,
                        icon = Icons.Filled.GridView,
                        onClick = onOpenAllCollection,
                    )
                }
                items(visibleLists, key = { it.id }) { list ->
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        HubTile(
                            title = list.name,
                            subtitle = "${list.cards.size} cards",
                            tint = parseHexColor(list.colorHex),
                            icon = if (list.isWishlist) Icons.Filled.Star else Icons.Filled.Folder,
                            onClick = { onOpenList(list.id) },
                            onLongClick = if (list.isSystem) null else ({ menuOpen = true }),
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = { menuOpen = false; editing = list },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = { menuOpen = false; deleting = list },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        CreateListSheet(
            editing = editing,
            onSave = viewModel::saveList,
            onDismiss = { creating = false; editing = null },
        )
    }

    deleting?.let { list ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete List") },
            text = {
                Text("Are you sure you want to delete \"${list.name}\"? Cards will not be removed from your collection.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteList(list.id); deleting = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HubTile(
    title: String,
    subtitle: String,
    tint: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).padding(horizontal = 4.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
