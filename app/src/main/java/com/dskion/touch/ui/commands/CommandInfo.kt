package com.dskion.touch.ui.commands

import com.dskion.touch.data.EditableCommand

/** One voice command: its default (hardcoded) phrases, what it does, and - if editable - which
 * [EditableCommand] slot holds the user's own added synonym phrases. */
data class VoiceCommandInfo(
    val phrases: List<String>,
    val description: String,
    val editableCommand: EditableCommand? = null,
)

data class AppCommandSection(
    val appName: String,
    val commands: List<VoiceCommandInfo>,
    val ready: Boolean = true,
)

val voiceCommandSections = listOf(
    AppCommandSection(
        appName = "배민커넥트",
        commands = listOf(
            VoiceCommandInfo(
                phrases = EditableCommand.START_WORK.defaultPhrases,
                description = "운행 시작 대기 화면에서 운행을 시작해요",
                editableCommand = EditableCommand.START_WORK,
            ),
            VoiceCommandInfo(
                phrases = EditableCommand.NEW_DISPATCH_ON.defaultPhrases,
                description = "신규배차 수신을 켜요 (이미 켜져 있으면 그대로 둬요)",
                editableCommand = EditableCommand.NEW_DISPATCH_ON,
            ),
            VoiceCommandInfo(
                phrases = EditableCommand.NEW_DISPATCH_OFF.defaultPhrases,
                description = "신규배차 수신을 꺼요 (이미 꺼져 있으면 그대로 둬요)",
                editableCommand = EditableCommand.NEW_DISPATCH_OFF,
            ),
            VoiceCommandInfo(
                phrases = EditableCommand.ACCEPT_DISPATCH.defaultPhrases,
                description = "새 배차 알림을 확인하고, 픽업지·배달료를 음성으로 읽어줘요",
                editableCommand = EditableCommand.ACCEPT_DISPATCH,
            ),
            VoiceCommandInfo(
                phrases = EditableCommand.RESPOND_ACCEPT.defaultPhrases,
                description = "방금 확인한 배차를 수락해요",
                editableCommand = EditableCommand.RESPOND_ACCEPT,
            ),
            VoiceCommandInfo(
                phrases = EditableCommand.RESPOND_REJECT.defaultPhrases,
                description = "방금 확인한 배차를 거절해요",
                editableCommand = EditableCommand.RESPOND_REJECT,
            ),
        ),
    ),
    AppCommandSection(
        appName = "쿠팡이츠",
        commands = emptyList(),
        ready = false,
    ),
)
