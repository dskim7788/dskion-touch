package com.dskion.touch.accessibility.command

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

private const val LOG_TAG = "RespondDispatch"
private const val ACCEPT_RESOURCE_ID = "신규배차_수락버튼"
private const val REJECT_RESOURCE_ID = "신규배차_거절버튼"

/** "수락"/"배차수락"과 "거절" 음성 명령으로 신규배차 카드의 두 버튼 중 하나를 누른다.
 * 두 버튼 모두 clickable=true라 [AcceptDispatchCommand]의 배너와 달리 좌표 탭 없이
 * 바로 ACTION_CLICK으로 처리된다. */
class RespondDispatchCommand(private val service: AccessibilityService) {

    /** true=수락, false=거절, null=이 명령과 무관. "거절"을 먼저 검사해 "수락거절" 같은
     * 애매한 입력에서 더 안전한 쪽(거절 안 함)으로 치우치지 않게 한다. */
    fun parseIntent(spokenText: String): Boolean? {
        val normalized = spokenText.replace(" ", "")
        if (normalized.contains("거절")) return false
        if (normalized.contains("수락")) return true
        return null
    }

    fun execute(accept: Boolean, onResult: (String) -> Unit) {
        findBaeminRoot(service) { root ->
            if (root == null) {
                Log.d(LOG_TAG, "배민 화면을 찾지 못함")
                onResult("배민 화면을 찾지 못했어요")
                return@findBaeminRoot
            }

            val resourceId = if (accept) ACCEPT_RESOURCE_ID else REJECT_RESOURCE_ID
            val button = findNodeMatching(root) { it.viewIdResourceName == resourceId }
            if (button == null) {
                Log.d(LOG_TAG, "버튼을 찾지 못함 resourceId=$resourceId")
                onResult(if (accept) "수락 버튼을 찾지 못했어요" else "거절 버튼을 찾지 못했어요")
                return@findBaeminRoot
            }

            if (!button.isClickable || !button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(LOG_TAG, "클릭 실패 resourceId=$resourceId")
                onResult(if (accept) "수락 버튼을 누르지 못했어요" else "거절 버튼을 누르지 못했어요")
                return@findBaeminRoot
            }

            onResult(if (accept) "배차를 수락했어요" else "배차를 거절했어요")
        }
    }
}
