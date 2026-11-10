package com.dskion.touch.accessibility.command

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.dskion.touch.accessibility.tapAt
import com.dskion.touch.core.rules.BaeminRules

private const val LOG_TAG = "AcceptDispatch"
private const val BANNER_RESOURCE_ID = "신규배차_주황색_토스트바"
private const val CARD_RESOURCE_ID = "신규배차_카드"
private const val POLL_INTERVAL_MILLIS = 250L
private const val MAX_POLL_ATTEMPTS = 6

/**
 * "배달확인" 음성 명령: 신규배차 알림 배너를 눌러서 배달 경로/수락·거절 카드를 띄우고, 픽업지와
 * 배달료를 읽어준다. 라이더가 그 내용을 듣고 "수락"/"거절"([RespondDispatchCommand])로 결정한다.
 *
 * 배너를 이루는 노드가 전부 clickable=false라서(React Native에서 흔함) ACTION_CLICK이 아니라
 * 배너 영역 좌표를 직접 탭한다 - [com.dskion.touch.accessibility.tapAt] 폴백과 같은 방식.
 */
class AcceptDispatchCommand(private val service: AccessibilityService) {

    fun parseIntent(spokenText: String): Boolean = spokenText.replace(" ", "").contains("배달확인")

    fun execute(onResult: (String) -> Unit) {
        findBaeminRoot(service) { root ->
            if (root == null) {
                Log.d(LOG_TAG, "배민 화면을 찾지 못함")
                onResult("배민 화면을 찾지 못했어요")
                return@findBaeminRoot
            }

            if (findNodeMatching(root) { it.viewIdResourceName == CARD_RESOURCE_ID } != null) {
                describeCard(root, onResult)
                return@findBaeminRoot
            }

            val bannerNode = findNodeMatching(root) { it.viewIdResourceName == BANNER_RESOURCE_ID }
            if (bannerNode == null) {
                Log.d(LOG_TAG, "배차 알림 배너 노드를 찾지 못함")
                onResult("배차 알림 배너를 찾지 못했어요")
                return@findBaeminRoot
            }

            val hasPendingOffer = findNodeMatching(root) { it.text?.toString()?.contains("신규배달") == true } != null
            if (!hasPendingOffer) {
                Log.d(LOG_TAG, "배너는 있지만 대기 중인 배차 없음")
                onResult("현재 대기 중인 배차가 없어요")
                return@findBaeminRoot
            }

            val bounds = Rect()
            bannerNode.getBoundsInScreen(bounds)
            Log.d(LOG_TAG, "배너 탭 시도 center=(${bounds.centerX()},${bounds.centerY()})")

            if (!service.tapAt(bounds.centerX(), bounds.centerY())) {
                Log.d(LOG_TAG, "탭 실패")
                onResult("배차 알림 배너를 누르지 못했어요")
                return@findBaeminRoot
            }

            pollForCard(attempt = 1, onResult = onResult)
        }
    }

    private fun pollForCard(attempt: Int, onResult: (String) -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            val root = service.rootInActiveWindow?.takeIf { it.packageName == BaeminRules.PACKAGE_NAME }
            val card = root?.let { r -> findNodeMatching(r) { it.viewIdResourceName == CARD_RESOURCE_ID } }
            when {
                root != null && card != null -> describeCard(root, onResult)
                attempt >= MAX_POLL_ATTEMPTS -> onResult("배달 경로 확인 화면을 불러오지 못했어요")
                else -> pollForCard(attempt + 1, onResult)
            }
        }, POLL_INTERVAL_MILLIS)
    }

    private fun describeCard(root: AccessibilityNodeInfo, onResult: (String) -> Unit) {
        val pickup = findSiblingTextAfterLabel(root) { it == "픽업지" }
        val fee = findSiblingTextAfterLabel(root) { it.startsWith("배달료") }
        Log.d(LOG_TAG, "카드 내용 pickup=$pickup fee=$fee")
        onResult("${pickup ?: "픽업지 확인 필요"} 픽업, 배달료 ${fee ?: "확인 필요"}")
    }
}
