package com.dskion.touch.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dskion.touch.ui.AppViewModelFactory
import com.dskion.touch.ui.commands.CommandsScreen
import com.dskion.touch.ui.console.ConsoleScreen
import com.dskion.touch.ui.home.HomeScreen
import com.dskion.touch.ui.settings.SettingsScreen

/** Shared app chrome - one [TouchTopBar] and one [TouchBottomBar] wrapping the nav-hosted screen
 * content, so every tab gets the exact same header/footer style, height and insets handling
 * instead of each screen building its own Scaffold. */
@Composable
fun TouchNavHost(application: Application) {
    val factory = AppViewModelFactory(application)
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TouchTopBar(
                route = currentRoute,
                onSettingsClick = { navController.navigate(ROUTE_SETTINGS) },
                onBack = { navController.popBackStack() },
            )

            Box(modifier = Modifier.weight(1f)) {
                NavHost(navController = navController, startDestination = ROUTE_HOME) {
                    composable(ROUTE_HOME) { HomeScreen(factory = factory) }
                    composable(ROUTE_COMMANDS) { CommandsScreen(factory = factory) }
                    composable(ROUTE_CONSOLE) { ConsoleScreen() }
                    composable(ROUTE_SETTINGS) { SettingsScreen(factory = factory) }
                }
            }

            TouchBottomBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}
