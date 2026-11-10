package com.dskion.touch.ui.commands

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dskion.touch.R
import com.dskion.touch.ui.AppViewModelFactory
import com.dskion.touch.ui.theme.InfoBlue
import com.dskion.touch.ui.theme.PointPurple
import com.dskion.touch.ui.theme.StatusGranted
import com.dskion.touch.ui.theme.StatusMissing

/** Which voice phrases are wired up for each app, what they do, and - for 배민커넥트's commands -
 * an inline editor to add/remove your own synonym phrases on top of the defaults. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandsScreen(factory: AppViewModelFactory, viewModel: CommandsViewModel = viewModel(factory = factory)) {
    var selectedIndex by remember { mutableStateOf(0) }
    val section = voiceCommandSections[selectedIndex]

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_commands)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlatformTabRow(
                sections = voiceCommandSections,
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppCommandCard(section, viewModel)
            }
        }
    }
}

@Composable
private fun PlatformTabRow(
    sections: List<AppCommandSection>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            sections.forEachIndexed { index, section ->
                val selected = index == selectedIndex
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) },
                ) {
                    Text(
                        section.appName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppCommandCard(section: AppCommandSection, viewModel: CommandsViewModel) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(section.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!section.ready) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            stringResource(R.string.commands_not_ready),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (section.commands.isEmpty()) {
                Text(
                    stringResource(R.string.commands_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                section.commands.forEachIndexed { index, command ->
                    CommandEntry(command, viewModel)
                    if (index != section.commands.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

private fun CommandKind.icon(): ImageVector = when (this) {
    CommandKind.START -> Icons.Filled.PlayArrow
    CommandKind.DISPATCH_ON -> Icons.Filled.ToggleOn
    CommandKind.DISPATCH_OFF -> Icons.Filled.ToggleOff
    CommandKind.CHECK -> Icons.Filled.CheckCircle
    CommandKind.ACCEPT -> Icons.Filled.Check
    CommandKind.REJECT -> Icons.Filled.Close
}

@Composable
private fun CommandKind.tint(): Color = when (this) {
    CommandKind.START -> InfoBlue
    CommandKind.DISPATCH_ON -> PointPurple
    CommandKind.DISPATCH_OFF -> PointPurple
    CommandKind.CHECK -> StatusGranted
    CommandKind.ACCEPT -> StatusGranted
    CommandKind.REJECT -> StatusMissing
}

@Composable
private fun CommandKindBadge(kind: CommandKind) {
    val tint = kind.tint()
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.14f)) {
        Icon(
            kind.icon(),
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(8.dp)
                .size(18.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommandEntry(command: VoiceCommandInfo, viewModel: CommandsViewModel) {
    var newPhrase by remember(command.editableCommand) { mutableStateOf("") }
    val extraPhrasesFlow = remember(command.editableCommand) { command.editableCommand?.let(viewModel::extraPhrases) }
    val extraPhrases by (extraPhrasesFlow?.collectAsState(initial = emptySet()) ?: remember { mutableStateOf(emptySet<String>()) })

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CommandKindBadge(command.kind)

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                command.phrases.forEach { phrase -> PhraseChip(phrase) }
                extraPhrases.forEach { phrase ->
                    PhraseChip(phrase, removable = true) { command.editableCommand?.let { viewModel.removePhrase(it, phrase) } }
                }
            }
            Text(
                command.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            command.editableCommand?.let { editable ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPhrase,
                        onValueChange = { newPhrase = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.commands_add_phrase_hint)) },
                        singleLine = true,
                    )
                    FilledIconButton(
                        onClick = {
                            if (newPhrase.isNotBlank()) {
                                viewModel.addPhrase(editable, newPhrase)
                                newPhrase = ""
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.commands_add_phrase_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhraseChip(phrase: String, removable: Boolean = false, onRemove: () -> Unit = {}) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "\"$phrase\"",
                modifier = Modifier.padding(start = 12.dp, end = if (removable) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (removable) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.commands_remove_phrase_action),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp)
                        .clickable(onClick = onRemove),
                )
            }
        }
    }
}
