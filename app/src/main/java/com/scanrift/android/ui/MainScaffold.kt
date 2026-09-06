package com.scanrift.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.scanrift.android.ui.navigation.ScanRiftNavHost
import com.scanrift.android.ui.navigation.TopLevelDestination

/**
 * Lets the Point Tracker's fullscreen mode hide the whole navigation container.
 *
 * Hoisted rather than passed down because the toggle lives several layers inside the
 * Game tab and the container is at the root.
 */
val LocalImmersiveMode = staticCompositionLocalOf { mutableStateOf(false) }

/**
 * The app shell.
 *
 * `NavigationSuiteScaffold` swaps a bottom bar for a navigation rail as the window
 * widens. The `layoutType` is pinned explicitly rather than left to the default,
 * which promotes to a permanent drawer at Expanded width — that would steal ~240dp
 * from the collection grid on an unfolded device in landscape.
 */
@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val immersive = remember { mutableStateOf(false) }

    // An unfolded Fold in portrait is ~700dp, which clears the Medium breakpoint —
    // this is the check that decides whether the inner display gets the rail.
    val isAtLeastMedium = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val layoutType = when {
        immersive.value -> NavigationSuiteType.None
        isAtLeastMedium -> NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.NavigationBar
    }

    CompositionLocalProvider(LocalImmersiveMode provides immersive) {
        NavigationSuiteScaffold(
            layoutType = layoutType,
            navigationSuiteItems = {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = backStackEntry?.destination?.hierarchyContains(destination) == true
                    item(
                        selected = selected,
                        onClick = { navController.navigateToTopLevel(destination) },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            },
        ) {
            ScanRiftNavHost(navController = navController, modifier = Modifier.fillMaxSize())
        }
    }
}

private fun androidx.navigation.NavDestination.hierarchyContains(destination: TopLevelDestination): Boolean =
    hierarchy.any { it.hasRoute(destination.graph::class) }

private fun androidx.navigation.NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.graph) {
        // Keep each tab's own back stack, the way a TabView does.
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
