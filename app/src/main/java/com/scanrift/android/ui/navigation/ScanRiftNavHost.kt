package com.scanrift.android.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scanrift.android.data.local.ScanRiftDatabase
import com.scanrift.android.data.local.entity.CollectionEntryEntity
import com.scanrift.android.ui.screens.collection.CardDetailScreen
import com.scanrift.android.ui.screens.collection.CardPagerScreen
import com.scanrift.android.ui.screens.collection.CollectionHubScreen
import com.scanrift.android.ui.screens.collection.CollectionScreen
import com.scanrift.android.ui.screens.decks.DeckBuilderScreen
import com.scanrift.android.ui.screens.decks.DecksScreen
import com.scanrift.android.ui.screens.onboarding.InitialSyncScreen
import com.scanrift.android.ui.screens.scanner.ScannerScreen
import com.scanrift.android.ui.screens.settings.SettingsScreen

private const val INITIAL_SYNC_ROUTE = "initial_sync"

@Composable
fun ScanRiftNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Check if DB is empty on first launch to redirect to onboarding
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val needsSync by produceState(initialValue = false) {
        val database = ScanRiftDatabase.getInstance(application)
        val cardCount = database.cardDao().getCardCountValue()
        value = cardCount == 0
    }

    LaunchedEffect(needsSync) {
        if (needsSync) {
            navController.navigate(INITIAL_SYNC_ROUTE) {
                popUpTo(Screen.COLLECTION_HUB) { inclusive = true }
            }
        }
    }

    // Show bottom bar only on main screens
    val showBottomBar = currentDestination?.route in listOf(
        Screen.COLLECTION_HUB,
        Screen.COLLECTION_GRID,
        Screen.COLLECTION_GRID_WITH_LIST,
        Screen.Scanner.route,
        Screen.Decks.route,
        Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val screens = listOf(Screen.Collection, Screen.Scanner, Screen.Decks, Screen.Settings)
                    screens.forEach { screen ->
                        val selected = when {
                            screen == Screen.Collection -> currentDestination?.route in listOf(
                                Screen.COLLECTION_HUB,
                                Screen.COLLECTION_GRID,
                                Screen.COLLECTION_GRID_WITH_LIST
                            )
                            else -> currentDestination?.route == screen.route
                        }

                        val targetRoute = if (screen == Screen.Collection) {
                            Screen.COLLECTION_HUB
                        } else {
                            screen.route
                        }

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(targetRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.label
                                )
                            },
                            label = { Text(screen.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.COLLECTION_HUB,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Initial Sync / Onboarding
            composable(INITIAL_SYNC_ROUTE) {
                InitialSyncScreen(
                    onComplete = {
                        navController.navigate(Screen.COLLECTION_HUB) {
                            popUpTo(INITIAL_SYNC_ROUTE) { inclusive = true }
                        }
                    },
                    onSkip = {
                        navController.navigate(Screen.COLLECTION_HUB) {
                            popUpTo(INITIAL_SYNC_ROUTE) { inclusive = true }
                        }
                    }
                )
            }

            // Collection Hub
            composable(Screen.COLLECTION_HUB) {
                CollectionHubScreen(
                    onNavigateToCollection = {
                        navController.navigate(Screen.COLLECTION_GRID)
                    },
                    onNavigateToList = { listId ->
                        navController.navigate(Screen.collectionGridRoute(listId))
                    }
                )
            }

            // Collection Grid (all cards)
            composable(Screen.COLLECTION_GRID) {
                CollectionScreen(
                    onCardClick = { card ->
                        navController.navigate(Screen.cardDetailRoute(card.id))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // Collection Grid (filtered by list)
            composable(
                route = Screen.COLLECTION_GRID_WITH_LIST,
                arguments = listOf(navArgument("listId") { type = NavType.StringType })
            ) { backStackEntry ->
                val listId = backStackEntry.arguments?.getString("listId")
                CollectionScreen(
                    listId = listId,
                    onCardClick = { card ->
                        navController.navigate(Screen.cardDetailRoute(card.id))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // Card Detail
            composable(
                route = Screen.CARD_DETAIL,
                arguments = listOf(navArgument("cardId") { type = NavType.StringType })
            ) { backStackEntry ->
                val cardId = backStackEntry.arguments?.getString("cardId") ?: return@composable
                CardDetailRoute(
                    cardId = cardId,
                    onBack = { navController.popBackStack() }
                )
            }

            // Card Pager
            composable(
                route = Screen.CARD_PAGER,
                arguments = listOf(navArgument("startIndex") { type = NavType.IntType })
            ) { backStackEntry ->
                val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0
                CardPagerScreen(
                    startIndex = startIndex,
                    onBack = { navController.popBackStack() }
                )
            }

            // Scanner
            composable(Screen.Scanner.route) {
                ScannerScreen()
            }

            // Decks
            composable(Screen.Decks.route) {
                DecksScreen(
                    onDeckClick = { deckId ->
                        navController.navigate("deck_builder/$deckId")
                    }
                )
            }

            // Deck Builder
            composable(
                route = "deck_builder/{deckId}",
                arguments = listOf(navArgument("deckId") { type = NavType.StringType })
            ) { backStackEntry ->
                val deckId = backStackEntry.arguments?.getString("deckId") ?: return@composable
                DeckBuilderScreen(
                    deckId = deckId,
                    onBack = { navController.popBackStack() }
                )
            }

            // Settings
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun CardDetailRoute(
    cardId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val database = ScanRiftDatabase.getInstance(application)
    val cardDao = database.cardDao()
    val collectionEntryDao = database.collectionEntryDao()

    val scope = rememberCoroutineScope()

    val card by cardDao.getCardByIdFlow(cardId).collectAsState(initial = null)
    var entries by remember { mutableStateOf(emptyList<CollectionEntryEntity>()) }

    LaunchedEffect(cardId) {
        entries = collectionEntryDao.getEntriesForCard(cardId)
    }

    val quantity = entries.sumOf { it.quantity }
    val primaryEntry = entries.firstOrNull()

    card?.let { cardEntity ->
        CardDetailScreen(
            card = cardEntity,
            quantity = quantity,
            condition = primaryEntry?.condition,
            dateAdded = primaryEntry?.dateAdded,
            onBack = onBack,
            onAddToCollection = {
                scope.launch {
                    val newEntry = CollectionEntryEntity(
                        cardId = cardId,
                        quantity = 1,
                        isFoil = cardEntity.isAlwaysFoil
                    )
                    collectionEntryDao.insert(newEntry)
                    entries = collectionEntryDao.getEntriesForCard(cardId)
                }
            },
            onQuantityChange = { newQty ->
                scope.launch {
                    val entry = primaryEntry ?: return@launch
                    if (newQty <= 0) {
                        collectionEntryDao.delete(entry)
                    } else {
                        collectionEntryDao.update(entry.copy(quantity = newQty))
                    }
                    entries = collectionEntryDao.getEntriesForCard(cardId)
                }
            },
            onConditionChange = { newCondition ->
                scope.launch {
                    val entry = primaryEntry ?: return@launch
                    collectionEntryDao.update(entry.copy(condition = newCondition))
                    entries = collectionEntryDao.getEntriesForCard(cardId)
                }
            }
        )
    }
}
