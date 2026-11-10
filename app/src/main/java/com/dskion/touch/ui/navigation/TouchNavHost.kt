package com.dskion.touch.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dskion.touch.R
import com.dskion.touch.ui.AppViewModelFactory
import com.dskion.touch.ui.commands.CommandsScreen
import com.dskion.touch.ui.console.ConsoleScreen
import com.dskion.touch.ui.home.HomeScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_COMMANDS = "commands"
private const val ROUTE_CONSOLE = "console"

@Composable
fun TouchNavHost(application: Application) {
    val factory = AppViewModelFactory(application)
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == ROUTE_HOME,
                    onClick = { navController.navigate(ROUTE_HOME) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_home)) },
                )
                NavigationBarItem(
                    selected = currentRoute == ROUTE_COMMANDS,
                    onClick = { navController.navigate(ROUTE_COMMANDS) },
                    icon = { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_commands)) },
                )
                NavigationBarItem(
                    selected = currentRoute == ROUTE_CONSOLE,
                    onClick = { navController.navigate(ROUTE_CONSOLE) },
                    icon = { Icon(Icons.Filled.Terminal, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_console)) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(ROUTE_HOME) { HomeScreen(factory = factory) }
            composable(ROUTE_COMMANDS) { CommandsScreen(factory = factory) }
            composable(ROUTE_CONSOLE) { ConsoleScreen() }
        }
    }
}
