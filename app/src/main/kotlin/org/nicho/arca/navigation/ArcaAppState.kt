package org.nicho.arca.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.nicho.arca.feature.capture.CaptureRequest

/**
 * Shell state (SDD-ARC-002 §9.1): navigation, the open capture sheet and the snackbar host.
 * The capture sheet isn't a destination, so the FAB and in-app links simply set a request.
 */
@Stable
class ArcaAppState(
    val navController: NavHostController,
    val snackbarHostState: SnackbarHostState,
) {
    var captureRequest by mutableStateOf<CaptureRequest?>(null)
        private set

    val currentDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    /** The tab bar and its add button belong to the tab roots only (DD-A20). */
    @Composable
    fun currentTab(): TopLevelDestination? {
        val destination = currentDestination ?: return null
        return TopLevelDestination.entries.firstOrNull { destination.hasRoute(it.route::class) }
    }

    /** Per-tab back stacks are saved and restored, so each tab keeps its scroll and depth. */
    fun navigateToTab(tab: TopLevelDestination) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigate(route: Any) = navController.navigate(route) { launchSingleTop = true }

    fun back() {
        navController.popBackStack()
    }

    fun openCapture(request: CaptureRequest = CaptureRequest()) {
        captureRequest = request
    }

    fun closeCapture() {
        captureRequest = null
    }
}

@Composable
fun rememberArcaAppState(
    navController: NavHostController = rememberNavController(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
): ArcaAppState = remember(navController, snackbarHostState) { ArcaAppState(navController, snackbarHostState) }
