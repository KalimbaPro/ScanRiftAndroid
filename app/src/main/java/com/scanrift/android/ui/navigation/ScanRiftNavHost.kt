package com.scanrift.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.navigation.navigation
import com.scanrift.android.ui.collection.CollectionBrowsePane
import com.scanrift.android.ui.collection.CollectionHubScreen

/**
 * Five nested graphs, one per tab, so each keeps its own back stack.
 */
@Composable
fun ScanRiftNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = CollectionGraph,
        modifier = modifier,
    ) {
        navigation<CollectionGraph>(startDestination = CollectionHubRoute) {
            composable<CollectionHubRoute> {
                CollectionHubScreen(
                    onOpenAllCollection = { navController.navigate(CollectionBrowseRoute()) },
                    onOpenList = { listId -> navController.navigate(CollectionBrowseRoute(listId)) },
                )
            }
            composable<CollectionBrowseRoute> { entry ->
                val route = entry.toRoute<CollectionBrowseRoute>()
                CollectionBrowsePane(
                    listId = route.listId,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        navigation<DecksGraph>(startDestination = DeckListRoute) {
            composable<DeckListRoute> { Placeholder("Decks") }
            composable<DeckBuilderRoute> { Placeholder("Deck builder") }
        }

        navigation<ScannerGraph>(startDestination = ScannerRoute) {
            composable<ScannerRoute> { Placeholder("Scanner") }
        }

        navigation<GameGraph>(startDestination = GameRoute) {
            composable<GameRoute> { Placeholder("Game") }
            composable<GameHistoryRoute> { Placeholder("Game history") }
        }

        navigation<SettingsGraph>(startDestination = SettingsRoute) {
            composable<SettingsRoute> { Placeholder("Settings") }
        }
    }
}

/** Temporary, until each screen lands. */
@Composable
private fun Placeholder(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label) }
}
