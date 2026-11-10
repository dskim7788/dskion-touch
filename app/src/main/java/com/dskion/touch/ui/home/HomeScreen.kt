package com.dskion.touch.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dskion.touch.R
import com.dskion.touch.core.model.ScreenSnapshot
import com.dskion.touch.data.OperatingMode
import com.dskion.touch.ui.AppViewModelFactory
import com.dskion.touch.ui.theme.StatusGranted
import com.dskion.touch.ui.theme.StatusMissing
import com.dskion.touch.util.accessibilitySettingsIntent
import com.dskion.touch.util.batteryOptimizationIntent
import com.dskion.touch.util.overlaySettingsIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(factory: AppViewModelFactory, viewModel: HomeViewModel = viewModel(factory = factory)) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mode by viewModel.mode.collectAsState()
    val serviceRunning by viewModel.serviceRunning.collectAsState()
    val latestSnapshot by viewModel.latestSnapshot.collectAsState()
    val voiceStatus by viewModel.voiceStatus.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermissions(context)
    }

    DisposableEffect(lifecycleOwner) {
        viewModel.refreshPermissions(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_title)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.home_hero_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.home_hero_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ServiceStatusCard(
                running = serviceRunning,
                mode = mode,
                snapshot = latestSnapshot,
                voiceStatus = voiceStatus,
            )

            val state = viewModel.permissionState
            val allPermissionsGranted = state.micGranted && state.accessibilityGranted &&
                state.overlayGranted && state.batteryIgnored
            PermissionChecklist(allGranted = allPermissionsGranted) {
                PermissionRow(
                    title = stringResource(R.string.permission_mic_title),
                    description = stringResource(R.string.permission_mic_desc),
                    granted = state.micGranted,
                    onAction = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                )
                PermissionRow(
                    title = stringResource(R.string.permission_accessibility_title),
                    description = stringResource(R.string.permission_accessibility_desc),
                    granted = state.accessibilityGranted,
                    onAction = { context.startActivity(accessibilitySettingsIntent()) },
                )
                PermissionRow(
                    title = stringResource(R.string.permission_overlay_title),
                    description = stringResource(R.string.permission_overlay_desc),
                    granted = state.overlayGranted,
                    onAction = { context.startActivity(overlaySettingsIntent(context)) },
                )
                PermissionRow(
                    title = stringResource(R.string.permission_battery_title),
                    description = stringResource(R.string.permission_battery_desc),
                    granted = state.batteryIgnored,
                    onAction = { context.startActivity(batteryOptimizationIntent(context)) },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.mode_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeCard(
                    selected = mode == OperatingMode.NORMAL,
                    icon = Icons.Filled.Mic,
                    title = stringResource(R.string.mode_normal),
                    description = stringResource(R.string.mode_normal_desc),
                    onClick = { viewModel.setMode(OperatingMode.NORMAL) },
                    modifier = Modifier.weight(1f),
                )
                ModeCard(
                    selected = mode == OperatingMode.DELIVERY,
                    icon = Icons.Filled.TwoWheeler,
                    title = stringResource(R.string.mode_delivery),
                    description = stringResource(R.string.mode_delivery_desc),
                    onClick = { viewModel.setMode(OperatingMode.DELIVERY) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceStatusCard(
    running: Boolean,
    mode: OperatingMode,
    snapshot: ScreenSnapshot?,
    voiceStatus: String?,
) {
    val containerColor = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(shape = RoundedCornerShape(24.dp), color = containerColor) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(32.dp),
                )
                Column {
                    Text(
                        stringResource(if (running) R.string.service_status_on else R.string.service_status_off),
                        style = MaterialTheme.typography.titleLarge,
                        color = contentColor,
                    )
                    Text(
                        stringResource(if (running) R.string.home_status_running_desc else R.string.home_status_off_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(
                    text = stringResource(
                        if (running) R.string.home_status_chip_accessibility_on else R.string.home_status_chip_accessibility_off,
                    ),
                    contentColor = contentColor,
                )
                StatusChip(
                    text = stringResource(if (mode == OperatingMode.NORMAL) R.string.mode_normal else R.string.mode_delivery),
                    contentColor = contentColor,
                )
                StatusChip(
                    text = snapshot?.let { stringResource(R.string.home_status_chip_screen, it.packageName) }
                        ?: stringResource(R.string.home_status_chip_screen_none),
                    contentColor = contentColor,
                )
            }

            Text(
                voiceStatus?.let { stringResource(R.string.home_status_last_command, it) }
                    ?: stringResource(R.string.home_status_no_command),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun StatusChip(text: String, contentColor: Color) {
    Surface(shape = RoundedCornerShape(50), color = contentColor.copy(alpha = 0.16f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onAction: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (granted) StatusGranted else StatusMissing,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.permission_status_granted)) })
            } else {
                Button(onClick = onAction) { Text(stringResource(R.string.permission_action_open_settings)) }
            }
        }
    }
}

@Composable
private fun PermissionChecklist(allGranted: Boolean, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember(allGranted) { mutableStateOf(!allGranted) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (allGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (allGranted) StatusGranted else StatusMissing,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.home_permissions_title) +
                        (if (allGranted) " · ${stringResource(R.string.home_permissions_all_granted)}" else ""),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier.animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun ModeCard(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = if (selected) 0.dp else 1.dp,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(28.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)
            Text(description, style = MaterialTheme.typography.bodySmall, color = contentColor)
        }
    }
}
