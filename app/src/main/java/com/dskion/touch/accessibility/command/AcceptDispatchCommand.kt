package com.dskion.touch.accessibility.command

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import com.dskion.touch.accessibility.tapAt

private const val LOG_TAG = "AcceptDispatch"
private const val BANNER_RESOURCE_ID = "신규배차_주황색_토스트바"

/**
 * "배달확인" 음성 명령: 신규배차 알림 배너를 눌러서 배달 경로 확인 화면으로 넘어간다.
 * 배너를 이루는 노드가 전부 clickable=false라서(React Native에서 흔함) ACTION_CLICK이 아니라
 * 배너 영역 좌표를 직접 탭한다 - [com.dskion.touch.accessibility.tapAt] 폴백과 같은 방식.
 *
 * 1단계(이 명령): 배너 클릭까지. 2단계(경로 확인 화면의 "확인하기"까지)는 그 화면 덤프를 받은
 * 뒤 이어서 구현한다.
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

            onResult("배달 경로 확인 화면으로 이동했어요")
        }
    }
}
