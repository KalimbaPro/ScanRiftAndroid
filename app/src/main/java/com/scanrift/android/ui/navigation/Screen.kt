package com.scanrift.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Collection : Screen(
        route = "collection",
        label = "Collection",
        selectedIcon = Icons.Filled.GridView,
        unselectedIcon = Icons.Outlined.GridView
    )

    data object Scanner : Screen(
        route = "scanner",
        label = "Scan",
        selectedIcon = Icons.Filled.CameraAlt,
        unselectedIcon = Icons.Outlined.CameraAlt
    )

    data object Decks : Screen(
        route = "decks",
        label = "Decks",
        selectedIcon = Icons.Filled.Layers,
        unselectedIcon = Icons.Outlined.Layers
    )

    data object Settings : Screen(
        route = "settings",
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        val bottomBarScreens = listOf(Collection, Scanner, Decks, Settings)

        // Collection sub-routes
        const val COLLECTION_HUB = "collection_hub"
        const val COLLECTION_GRID = "collection_grid"
        const val COLLECTION_GRID_WITH_LIST = "collection_grid/{listId}"
        const val CARD_DETAIL = "card_detail/{cardId}"
        const val CARD_PAGER = "card_pager/{startIndex}"

        fun collectionGridRoute(listId: String? = null): String {
            return if (listId != null) "collection_grid/$listId" else COLLECTION_GRID
        }

        fun cardDetailRoute(cardId: String): String = "card_detail/$cardId"
        fun cardPagerRoute(startIndex: Int): String = "card_pager/$startIndex"
    }
}
