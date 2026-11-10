package com.dskion.touch.ui.navigation

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dskion.touch.R

private val HeaderHeight = 64.dp
private val HeaderIconSize = 24.dp

/** Common header for all three tabs - same height/background/alignment everywhere. Home shows
 * the brand wordmark + notification/settings actions; Commands/Console show a plain screen
 * title + a single screen-appropriate action icon, per the redesign spec. */
@Composable
fun TouchTopBar(route: String?) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(HeaderHeight)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (route) {
                ROUTE_COMMANDS -> ScreenTitleHeader(
                    title = stringResource(R.string.nav_commands),
                    actionIcon = Icons.Filled.Search,
                    actionDescription = stringResource(R.string.header_search_action),
                    onAction = {},
                )
                ROUTE_CONSOLE -> ScreenTitleHeader(
                    title = stringResource(R.string.nav_console),
                    actionIcon = Icons.Filled.HelpOutline,
                    actionDescription = stringResource(R.string.header_help_action),
                    onAction = {},
                )
                else -> HomeHeader()
            }
        }
    }
}

@Composable
private fun RowScope.ScreenTitleHeader(
    title: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionDescription: String,
    onAction: () -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f),
    )
    IconButton(onClick = onAction, modifier = Modifier.size(48.dp)) {
        Icon(actionIcon, contentDescription = actionDescription, modifier = Modifier.size(HeaderIconSize))
    }
}

@Composable
private fun RowScope.HomeHeader() {
    val context = LocalContext.current
    Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            "dskion",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            " touch",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
        Icon(
            Icons.Filled.Notifications,
            contentDescription = stringResource(R.string.header_notifications_action),
            modifier = Modifier.size(HeaderIconSize),
        )
    }
    IconButton(
        onClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        },
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            Icons.Filled.Settings,
            contentDescription = stringResource(R.string.header_settings_action),
            modifier = Modifier.size(HeaderIconSize),
        )
    }
}
