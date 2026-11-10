package com.dskion.touch.ui.commands

/** One voice command: the phrases that trigger it and what it does. Mirrors the rules actually
 * wired up in BaeminRules/NewDispatchCommand/AcceptDispatchCommand/RespondDispatchCommand -
 * update this alongside those when a command changes. */
data class VoiceCommandInfo(
    val phrases: List<String>,
    val description: String,
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
                phrases = listOf("운행 시작", "운행시작", "시작"),
                description = "운행 시작 대기 화면에서 운행을 시작해요",
            ),
            VoiceCommandInfo(
                phrases = listOf("신규배차 켜줘", "배차 켜"),
                description = "신규배차 수신을 켜요 (이미 켜져 있으면 그대로 둬요)",
            ),
            VoiceCommandInfo(
                phrases = listOf("신규배차 꺼줘", "배차 꺼"),
                description = "신규배차 수신을 꺼요 (이미 꺼져 있으면 그대로 둬요)",
            ),
            VoiceCommandInfo(
                phrases = listOf("배달확인"),
                description = "새 배차 알림을 확인하고, 픽업지·배달료를 음성으로 읽어줘요",
            ),
            VoiceCommandInfo(
                phrases = listOf("수락", "배차수락"),
                description = "방금 확인한 배차를 수락해요",
            ),
            VoiceCommandInfo(
                phrases = listOf("거절"),
                description = "방금 확인한 배차를 거절해요",
            ),
        ),
    ),
    AppCommandSection(
        appName = "쿠팡이츠",
        commands = emptyList(),
        ready = false,
    ),
)
