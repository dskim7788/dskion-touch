package com.dskion.touch.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dskion.touch.R
import com.dskion.touch.data.ManagerPersona
import com.dskion.touch.ui.AppViewModelFactory

/** 매니저 페르소나 선택 + 음성 채팅(칭찬/위로/정보 안내) on/off + AI 대화용 API 키. 홈 탭의
 * 설정 아이콘에서 들어온다. */
@Composable
fun SettingsScreen(factory: AppViewModelFactory, viewModel: SettingsViewModel = viewModel(factory = factory)) {
    val persona by viewModel.persona.collectAsState()
    val voiceChatEnabled by viewModel.voiceChatEnabled.collectAsState()
    val savedApiKey by viewModel.apiKey.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_manager_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.settings_manager_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ManagerCard(
                        selected = persona == ManagerPersona.MALE,
                        imageRes = R.drawable.manager_male,
                        name = ManagerPersona.MALE.displayName,
                        onClick = { viewModel.setPersona(ManagerPersona.MALE) },
                        modifier = Modifier.weight(1f),
                    )
                    ManagerCard(
                        selected = persona == ManagerPersona.FEMALE,
                        imageRes = R.drawable.manager_female,
                        name = ManagerPersona.FEMALE.displayName,
                        onClick = { viewModel.setPersona(ManagerPersona.FEMALE) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_voice_chat_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_voice_chat_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = voiceChatEnabled, onCheckedChange = viewModel::setVoiceChatEnabled)
            }
        }

        Text(
            stringResource(R.string.settings_voice_chat_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_ai_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.settings_ai_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ApiKeyField(savedApiKey = savedApiKey, onSave = viewModel::setApiKey)
                Text(
                    stringResource(R.string.settings_ai_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ApiKeyField(savedApiKey: String, onSave: (String) -> Unit) {
    var input by remember { mutableStateOf(savedApiKey) }
    var visible by remember { mutableStateOf(false) }
    // savedApiKey only has a real value once DataStore's first emission lands, so sync the
    // field to it exactly once - after that, the field is the user's to edit.
    LaunchedEffect(Unit) { input = savedApiKey }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.settings_ai_key_label)) },
            placeholder = { Text(stringResource(R.string.settings_ai_key_placeholder)) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null,
                    )
                }
            },
        )
        TextButton(onClick = { onSave(input) }) {
            Text(stringResource(R.string.settings_ai_key_save))
        }
    }
}

@Composable
private fun ManagerCard(
    selected: Boolean,
    imageRes: Int,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
            Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}
