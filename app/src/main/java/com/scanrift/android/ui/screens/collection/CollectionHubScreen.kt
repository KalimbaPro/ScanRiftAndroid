package com.scanrift.android.ui.screens.collection

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.entity.CardListEntity
import com.scanrift.android.data.local.entity.CardListWithCards
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class CollectionHubViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ScanRiftDatabase.getInstance(application)
    private val cardListDao = database.cardListDao()
    private val collectionEntryDao = database.collectionEntryDao()

    val listsWithCards: StateFlow<List<CardListWithCards>> = cardListDao.getAllListsWithCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCollectionCards: StateFlow<Int?> = collectionEntryDao.getTotalCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val uniqueCollectionCards: StateFlow<Int> = collectionEntryDao.getUniqueCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        ensureWishlistExists()
    }

    private fun ensureWishlistExists() {
        viewModelScope.launch {
            val existing = cardListDao.getSystemList("wishlist")
            if (existing == null) {
                cardListDao.insert(
                    CardListEntity(
                        id = UUID.randomUUID().toString(),
                        name = "Wishlist",
                        colorHex = "#FFD60A",
                        isSystem = true,
                        systemType = "wishlist"
                    )
                )
            }
        }
    }

    fun createList(name: String, colorHex: String) {
        viewModelScope.launch {
            cardListDao.insert(
                CardListEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    colorHex = colorHex
                )
            )
        }
    }

    fun deleteList(list: CardListEntity) {
        viewModelScope.launch {
            cardListDao.delete(list)
        }
    }

    fun updateList(list: CardListEntity) {
        viewModelScope.launch {
            cardListDao.update(list)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionHubScreen(
    viewModel: CollectionHubViewModel = viewModel(),
    onNavigateToCollection: () -> Unit = {},
    onNavigateToList: (String) -> Unit = {}
) {
    val listsWithCards by viewModel.listsWithCards.collectAsState()
    val totalCards by viewModel.totalCollectionCards.collectAsState()
    val uniqueCards by viewModel.uniqueCollectionCards.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var listToDelete by remember { mutableStateOf<CardListEntity?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Collection") },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create list")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            // All Collection card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCollection() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "All Collection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${totalCards ?: 0} cards (${uniqueCards} unique)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            // Lists section header
            item {
                Text(
                    "My Lists",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Card lists
            items(listsWithCards, key = { it.list.id }) { listWithCards ->
                val list = listWithCards.list
                val isSystem = list.isSystem

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToList(list.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Color dot
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    parseHexColor(list.colorHex),
                                    CircleShape
                                )
                        )

                        Spacer(Modifier.width(12.dp))

                        if (isSystem && list.systemType == "wishlist") {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = parseHexColor(list.colorHex),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                list.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${listWithCards.cards.size} cards",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!isSystem) {
                            IconButton(onClick = { listToDelete = list }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }

    // Create list dialog
    if (showCreateDialog) {
        CreateListDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, color ->
                viewModel.createList(name, color)
                showCreateDialog = false
            }
        )
    }

    // Delete confirmation
    listToDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            title = { Text("Delete List") },
            text = { Text("Delete \"${list.name}\"? Cards will not be removed from your collection.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteList(list)
                    listToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { listToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun parseHexColor(hex: String): Color {
    val cleanHex = hex.removePrefix("#")
    return try {
        Color(android.graphics.Color.parseColor("#$cleanHex"))
    } catch (e: Exception) {
        Color(0xFFFF9500)
    }
}
