package com.dskion.touch.ui.console

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dskion.touch.R
import com.dskion.touch.accessibility.TouchAccessibilityService
import com.dskion.touch.core.format.ScreenSnapshotFormatter
import com.dskion.touch.core.model.ScreenNode
import com.dskion.touch.ui.theme.DarkNavy
import com.dskion.touch.ui.theme.PointPurple
import com.dskion.touch.ui.theme.StatusGranted
import com.dskion.touch.ui.theme.StatusWarning

private const val NODE_PREVIEW_COUNT = 5

/**
 * Shows the accessibility service's live state and the last screen it read, with a
 * copy-to-clipboard dump (for capturing new 배민/쿠팡이츠 node data) and a one-shot voice-command
 * test button that matches recognized speech against the known rules and taps the target button.
 * The actual listening runs inside [TouchAccessibilityService] (a foreground service), not here,
 * so it keeps working after the user switches away from this screen to the target app.
 */
@Composable
fun ConsoleScreen(viewModel: ConsoleViewModel = viewModel()) {
    val serviceRunning by viewModel.serviceRunning.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val voiceStatus by viewModel.voiceStatus.collectAsState()
    val context = LocalContext.current
    var justCopied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VoiceHeroCard(
            running = serviceRunning,
            voiceStatus = voiceStatus,
            onTestClick = { TouchAccessibilityService.instance?.startVoiceCommand() },
        )

        val currentSnapshot = snapshot
        if (currentSnapshot == null) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Text(
                    stringResource(R.string.console_empty_state),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            InfoCard(
                label = stringResource(R.string.console_package_label),
                value = currentSnapshot.packageName,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard(
                    label = stringResource(R.string.console_node_count_label),
                    value = stringResource(R.string.console_node_count, currentSnapshot.nodes.size),
                    modifier = Modifier.weight(1f),
                )
                InfoCard(
                    label = stringResource(R.string.console_screen_state_label),
                    value = stringResource(
                        if (serviceRunning) R.string.console_screen_state_active else R.string.console_screen_state_idle,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val dump = ScreenSnapshotFormatter.format(currentSnapshot)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("screen_dump", dump))
                    justCopied = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (justCopied) R.string.console_copy_done else R.string.console_copy_dump))
            }

            NodeListSection(currentSnapshot.nodes)
        }
    }
}

@Composable
private fun VoiceHeroCard(running: Boolean, voiceStatus: String?, onTestClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(DarkNavy, PointPurple)), RoundedCornerShape(28.dp))
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (running) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (running) StatusGranted else StatusWarning,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (running) R.string.console_service_running else R.string.console_service_stopped),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }

            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.14f)) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(20.dp)
                        .size(36.dp),
                )
            }

            Waveform(color = Color.White.copy(alpha = 0.8f))

            Text(
                voiceStatus ?: stringResource(R.string.console_voice_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = onTestClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(stringResource(R.string.console_voice_test))
            }
        }
    }
}

@Composable
private fun Waveform(color: Color) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val baseHeights = listOf(16, 26, 13, 22, 18)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        baseHeights.forEachIndexed { index, base ->
            val scale by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500 + index * 120, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$index",
            )
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height((base * scale).dp)
                    .background(color, RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun InfoCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NodeListSection(nodes: List<ScreenNode>) {
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) nodes else nodes.take(NODE_PREVIEW_COUNT)

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.console_node_list_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (nodes.size > NODE_PREVIEW_COUNT) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) R.string.console_node_list_collapse else R.string.console_node_list_more))
                    }
                }
            }
            visible.forEach { node -> NodeRow(node) }
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
