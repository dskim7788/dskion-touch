package com.dskion.touch.core.rules

import com.dskion.touch.core.match.CommandRule
import com.dskion.touch.core.match.NodeSignature
import com.dskion.touch.core.match.ScreenProfile

/**
 * 배민커넥트 라이더 앱(com.woowahan.bros) 화면/명령어 규칙. 실제 기기에서 뽑은 노드 덤프를
 * 기반으로 하나씩 채워나간다 — 화면이 바뀌면 여기만 고치면 된다.
 */
object BaeminRules {
    const val PACKAGE_NAME = "com.woowahan.bros"

    /** 운행 시작 전 대기 화면 — "운행시작" 버튼(resId="start-work-button")으로 식별. */
    val readyToStartScreen = ScreenProfile(
        id = "baemin_ready_to_start",
        packageName = PACKAGE_NAME,
        requiredSignatures = listOf(NodeSignature(resourceId = "start-work-button")),
    )

    val startWorkRule = CommandRule(
        screenProfileId = readyToStartScreen.id,
        spokenPhrases = setOf("운행 시작", "운행시작", "시작"),
        targetSignature = NodeSignature(resourceId = "start-work-button"),
    )

    /** 운행 시작 후 배차 대기 중 화면 — "신규배차 켜기" 토글(resId="신규배차_켜기버튼")로 식별. */
    val waitingForDispatchScreen = ScreenProfile(
        id = "baemin_waiting_for_dispatch",
        packageName = PACKAGE_NAME,
        requiredSignatures = listOf(NodeSignature(resourceId = "신규배차_켜기버튼")),
    )

    val toggleNewDispatchRule = CommandRule(
        screenProfileId = waitingForDispatchScreen.id,
        spokenPhrases = setOf("신규배차 켜", "신규배차 켜줘", "배차 켜", "배차 켜줘"),
        targetSignature = NodeSignature(resourceId = "신규배차_켜기버튼"),
    )

    val profiles = listOf(readyToStartScreen, waitingForDispatchScreen)
    val rules = listOf(startWorkRule, toggleNewDispatchRule)
}
