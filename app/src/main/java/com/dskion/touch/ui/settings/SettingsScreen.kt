package com.dskion.touch.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
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
import com.dskion.touch.ai.GeminiVoiceOption
import com.dskion.touch.ai.GeminiVoices
import com.dskion.touch.data.ManagerPersona
import com.dskion.touch.data.ManagerVoiceEngine
import com.dskion.touch.ui.AppViewModelFactory
import kotlinx.coroutines.delay

/** 매니저 페르소나 선택 + 음성 채팅(칭찬/위로/정보 안내) on/off + AI 대화용 API 키. 홈 탭의
 * 설정 아이콘에서 들어온다. */
@Composable
fun SettingsScreen(factory: AppViewModelFactory, viewModel: SettingsViewModel = viewModel(factory = factory)) {
    val persona by viewModel.persona.collectAsState()
    val voiceChatEnabled by viewModel.voiceChatEnabled.collectAsState()
    val savedApiKey by viewModel.apiKey.collectAsState()
    val maleVoiceName by viewModel.maleVoiceName.collectAsState()
    val femaleVoiceName by viewModel.femaleVoiceName.collectAsState()
    val previewState by viewModel.previewState.collectAsState()
    val lastAiError by viewModel.lastAiError.collectAsState()
    val voiceEngine by viewModel.voiceEngine.collectAsState()

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
                lastAiError?.let { error ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.settings_ai_last_error, error),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::clearAiError) {
                            Text(stringResource(R.string.settings_ai_last_error_clear))
                        }
                    }
                }
            }
        }

        if (savedApiKey.isNotBlank()) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(R.string.settings_voice_picker_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.settings_voice_picker_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VoiceEngineRow(engine = voiceEngine, onSelect = viewModel::setVoiceEngine)
                    VoicePickerRow(
                        label = ManagerPersona.MALE.displayName,
                        selectedVoice = maleVoiceName,
                        previewState = previewState,
                        onSelect = viewModel::setMaleVoiceName,
                        onPreview = { voiceName -> viewModel.previewVoice(voiceName, VOICE_PREVIEW_TEXT) },
                        onFailureShown = viewModel::clearPreviewFailure,
                    )
                    VoicePickerRow(
                        label = ManagerPersona.FEMALE.displayName,
                        selectedVoice = femaleVoiceName,
                        previewState = previewState,
                        onSelect = viewModel::setFemaleVoiceName,
                        onPreview = { voiceName -> viewModel.previewVoice(voiceName, VOICE_PREVIEW_TEXT) },
                        onFailureShown = viewModel::clearPreviewFailure,
                    )
                }
            }
        }
    }
}

private const val VOICE_PREVIEW_TEXT = "안녕하세요, 라이더님! 오늘도 안전 운행 하세요."

/** AI 음성(Gemini, 네트워크+무료 한도 필요)과 기기 기본 음성(완전히 로컬, 한도 없음)
 * 중 매니저가 실제로 어떤 걸로 말할지 직접 고르는 스위치 - 자동으로 알아서 줄여주는
 * 대신, 한도를 아끼고 싶으면 사용자가 명시적으로 끌 수 있게 한다. */
@Composable
private fun VoiceEngineRow(engine: ManagerVoiceEngine, onSelect: (ManagerVoiceEngine) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_voice_engine_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                if (engine == ManagerVoiceEngine.AI) {
                    stringResource(R.string.settings_voice_engine_ai_desc)
                } else {
                    stringResource(R.string.settings_voice_engine_device_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = engine == ManagerVoiceEngine.AI,
            onCheckedChange = { useAi -> onSelect(if (useAi) ManagerVoiceEngine.AI else ManagerVoiceEngine.DEVICE) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePickerRow(
    label: String,
    selectedVoice: String,
    previewState: VoicePreviewState,
    onSelect: (String) -> Unit,
    onPreview: (String) -> Unit,
    onFailureShown: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = GeminiVoices.options.find { it.voiceName == selectedVoice }
    val isLoading = previewState is VoicePreviewState.Loading && previewState.voiceName == selectedVoice
    val isFailed = previewState is VoicePreviewState.Failed && previewState.voiceName == selectedVoice

    // 실패 표시를 잠깐 보여주고 자동으로 지운다 - 버튼을 눌러도 반응이 없어 보여서 "테스트가
    // 안 된다"는 혼란이 있었다. 지금은 최소한 뭐가 잘못됐는지는 보인다.
    LaunchedEffect(isFailed) {
        if (isFailed) {
            delay(2500)
            onFailureShown()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = voiceOptionLabel(selectedOption, selectedVoice),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    GeminiVoices.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(voiceOptionLabel(option, option.voiceName)) },
                            onClick = {
                                onSelect(option.voiceName)
                                expanded = false
                            },
                        )
                    }
                }
            }
            IconButton(onClick = { onPreview(selectedVoice) }, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.settings_voice_preview_action))
                }
            }
        }
        if (isFailed) {
            Text(
                stringResource(R.string.settings_voice_preview_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun voiceOptionLabel(option: GeminiVoiceOption?, fallbackName: String): String =
    option?.let { "${it.voiceName} · ${it.tone}" } ?: fallbackName

@Composable
private fun ApiKeyField(savedApiKey: String, onSave: (String) -> Unit) {
    var input by remember { mutableStateOf(savedApiKey) }
    var visible by remember { mutableStateOf(false) }
    // 저장 버튼을 눌러도 아무 반응이 없어 보여서 "저장이 안 되는 것 같다"는 혼란이 있었다 -
    // 실제로는 저장이 됐어도 눈에 보이는 확인이 전혀 없었기 때문. 누를 때마다 잠깐 체크
    // 표시를 보여준다.
    var justSaved by remember { mutableStateOf(false) }
    // savedApiKey starts as "" (stateIn's seed value) and only becomes the real stored key once
    // DataStore's first emission lands a moment later - keying on Unit (run once) could lose that
    // race and leave the field permanently empty after navigating back to this screen. Keying on
    // savedApiKey instead re-syncs whenever it changes, but stops once the user starts typing so
    // it never clobbers an in-progress edit.
    var hasUserEdited by remember { mutableStateOf(false) }
    LaunchedEffect(savedApiKey) {
        if (!hasUserEdited) input = savedApiKey
    }
    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(1500)
            justSaved = false
        }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; hasUserEdited = true },
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
            TextButton(onClick = {
                onSave(input)
                justSaved = true
            }) {
                Text(stringResource(R.string.settings_ai_key_save))
            }
        }
        AnimatedVisibility(visible = justSaved) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.settings_ai_key_saved),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
