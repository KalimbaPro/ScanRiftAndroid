package com.scanrift.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import com.scanrift.android.R
import kotlinx.serialization.Serializable

/**
 * Type-safe routes.
 *
 * Five nested graphs, one per tab, each with its own back stack — matching the iOS
 * TabView, in the same order.
 */
@Serializable data object CollectionGraph

@Serializable data object CollectionHubRoute

@Serializable data class CollectionBrowseRoute(val listId: String? = null)

@Serializable data object DecksGraph

@Serializable data object DeckListRoute

@Serializable data class DeckBuilderRoute(val deckId: String)

@Serializable data object ScannerGraph

@Serializable data object ScannerRoute

@Serializable data object GameGraph

@Serializable data object GameRoute

@Serializable data object GameHistoryRoute

@Serializable data object SettingsGraph

@Serializable data object SettingsRoute

@Serializable data object InitialSyncRoute

enum class TopLevelDestination(
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val graph: Any,
) {
    COLLECTION(R.string.tab_collection, Icons.Filled.GridView, Icons.Outlined.GridView, CollectionGraph),
    DECKS(R.string.tab_decks, Icons.Filled.Layers, Icons.Outlined.Layers, DecksGraph),
    SCANNER(R.string.tab_scanner, Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt, ScannerGraph),
    GAME(R.string.tab_game, Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports, GameGraph),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings, SettingsGraph),
}
