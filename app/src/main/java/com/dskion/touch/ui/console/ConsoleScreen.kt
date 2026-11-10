package com.dskion.touch.ui.console

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dskion.touch.R
import com.dskion.touch.accessibility.TouchAccessibilityService
import com.dskion.touch.core.format.ScreenSnapshotFormatter
import com.dskion.touch.core.model.ScreenNode
import com.dskion.touch.ui.theme.StatusGranted
import com.dskion.touch.ui.theme.StatusMissing

/**
 * Shows the accessibility service's live state and the last screen it read, with a
 * copy-to-clipboard dump (for capturing new 배민/쿠팡이츠 node data) and a one-shot voice-command
 * test button that matches recognized speech against the known rules and taps the target button.
 * The actual listening runs inside [TouchAccessibilityService] (a foreground service), not here,
 * so it keeps working after the user switches away from this screen to the target app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(viewModel: ConsoleViewModel = viewModel()) {
    val serviceRunning by viewModel.serviceRunning.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val voiceStatus by viewModel.voiceStatus.collectAsState()
    val context = LocalContext.current
    var justCopied by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_console)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ServiceStatusRow(running = serviceRunning)

            Button(onClick = { TouchAccessibilityService.instance?.startVoiceCommand() }) {
                Text(stringResource(R.string.console_voice_test))
            }
            voiceStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val currentSnapshot = snapshot
            if (currentSnapshot == null) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.console_empty_state),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(currentSnapshot.packageName, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.console_node_count, currentSnapshot.nodes.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val dump = ScreenSnapshotFormatter.format(currentSnapshot)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("screen_dump", dump))
                    justCopied = true
                }) {
                    Text(stringResource(if (justCopied) R.string.console_copy_done else R.string.console_copy_dump))
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(currentSnapshot.nodes) { node -> NodeRow(node) }
                }
            }
        }
    }
}

@Composable
private fun ServiceStatusRow(running: Boolean) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (running) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (running) StatusGranted else StatusMissing,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.service_status_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(if (running) R.string.service_status_on else R.string.service_status_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NodeRow(node: ScreenNode) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                node.text?.let { "\"$it\"" } ?: node.resourceId ?: node.className ?: "(이름 없음)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "resId=${node.resourceId} clickable=${node.isClickable}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
