package com.dskion.touch.ui.commands

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dskion.touch.R
import com.dskion.touch.ui.AppViewModelFactory

/** Which voice phrases are wired up for each app, what they do, and - for 배민커넥트's commands -
 * an inline editor to add/remove your own synonym phrases on top of the defaults. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandsScreen(factory: AppViewModelFactory, viewModel: CommandsViewModel = viewModel(factory = factory)) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_commands)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            voiceCommandSections.forEach { section -> AppCommandCard(section, viewModel) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommandEntry(command: VoiceCommandInfo, viewModel: CommandsViewModel) {
    var newPhrase by remember(command.editableCommand) { mutableStateOf("") }
    val extraPhrasesFlow = remember(command.editableCommand) { command.editableCommand?.let(viewModel::extraPhrases) }
    val extraPhrases by (extraPhrasesFlow?.collectAsState(initial = emptySet()) ?: remember { mutableStateOf(emptySet<String>()) })

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                IconButton(
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
