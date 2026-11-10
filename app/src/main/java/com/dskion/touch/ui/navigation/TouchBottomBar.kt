package com.dskion.touch.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dskion.touch.R
import com.dskion.touch.ui.theme.NeutralGray

private data class BottomNavItem(val route: String, val labelRes: Int, val icon: ImageVector)

private val bottomNavItems = listOf(
    BottomNavItem(ROUTE_HOME, R.string.nav_home, Icons.Filled.Home),
    BottomNavItem(ROUTE_COMMANDS, R.string.nav_commands, Icons.Filled.RecordVoiceOver),
    BottomNavItem(ROUTE_CONSOLE, R.string.nav_console, Icons.Filled.Terminal),
)

/** Fully custom bottom nav (not Material3's NavigationBar) so the selected-tab pill indicator and
 * spacing match the brand reference exactly, and stay identical across all three screens. */
@Composable
fun TouchBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(76.dp),
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentRoute == item.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onNavigate(item.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary else NeutralGray,
                            modifier = Modifier
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                                .size(24.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(item.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else NeutralGray,
                    )
                }
            }
        }
    }
}
