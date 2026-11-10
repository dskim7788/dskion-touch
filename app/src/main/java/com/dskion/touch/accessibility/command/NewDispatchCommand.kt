package com.dskion.touch.accessibility.command

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.dskion.touch.core.rules.BaeminRules

private const val LOG_TAG = "NewDispatch"
private const val LAUNCH_RETRY_DELAY_MILLIS = 1500L
private const val POLL_INTERVAL_MILLIS = 250L
private const val MAX_POLL_ATTEMPTS = 6

/**
 * 배민커넥트의 "신규배차 켜기/끄기" 토글 전용 명령. [com.dskion.touch.core.match.CommandMatcher]의
 * 단순 화면-규칙 매칭으로는 표현 안 되는 걸 직접 처리한다: 상태 판별(React Native라 Switch가
 * 아니라 resId 이름에 on/off가 들어 있음), 배민이 백그라운드일 때 앞으로 불러오기, 클릭 후
 * 상태가 실제로 바뀌었는지 재확인.
 */
class NewDispatchCommand(private val service: AccessibilityService) {

    /** "배차"가 없는 말은 이 명령과 무관 (null). 있으면 끄기 단어를 먼저 보고, 그다음 켜기 단어. */
    fun parseIntent(spokenText: String): Boolean? {
        val normalized = spokenText.replace(" ", "")
        if (!normalized.contains("배차")) return null

        val offWords = listOf("꺼", "끄", "끈", "중지", "멈춰", "정지")
        if (offWords.any(normalized::contains)) return false

        val onWords = listOf("켜", "킨", "키", "시작")
        if (onWords.any(normalized::contains)) return true

        return null
    }

    fun execute(desiredOn: Boolean, onResult: (String) -> Unit) {
        findBaeminRoot { root ->
            if (root == null) {
                Log.d(LOG_TAG, "배민 화면을 찾지 못함")
                onResult("배민 화면을 찾지 못했어요")
                return@findBaeminRoot
            }

            val toggleNode = findToggleNode(root)
            if (toggleNode == null) {
                Log.d(LOG_TAG, "신규배차 토글 노드를 찾지 못함")
                onResult("신규배차 버튼을 찾지 못했어요")
                return@findBaeminRoot
            }

            val currentlyOn = isCurrentlyOn(root, toggleNode)
            Log.d(LOG_TAG, "클릭 전 resId=${toggleNode.viewIdResourceName} currentlyOn=$currentlyOn desiredOn=$desiredOn")

            if (currentlyOn == desiredOn) {
                onResult(if (desiredOn) "신규배차 이미 켜져 있어요" else "신규배차 이미 꺼져 있어요")
                return@findBaeminRoot
            }

            if (!toggleNode.isClickable || !toggleNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(LOG_TAG, "클릭 실패")
                onResult("신규배차 버튼을 누르지 못했어요")
                return@findBaeminRoot
            }

            pollForStateChange(desiredOn, attempt = 1, onResult = onResult)
        }
    }

    private fun pollForStateChange(desiredOn: Boolean, attempt: Int, onResult: (String) -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            val root = service.rootInActiveWindow?.takeIf { it.packageName == BaeminRules.PACKAGE_NAME }
            val toggleNode = root?.let(::findToggleNode)
            val nowOn = root != null && toggleNode != null && isCurrentlyOn(root, toggleNode)
            Log.d(LOG_TAG, "클릭 후 attempt=$attempt resId=${toggleNode?.viewIdResourceName} nowOn=$nowOn")

            when {
                toggleNode != null && nowOn == desiredOn ->
                    onResult(if (desiredOn) "신규배차 켰어요" else "신규배차 껐어요")
                attempt >= MAX_POLL_ATTEMPTS ->
                    onResult("신규배차 변경을 확인하지 못했어요")
                else -> pollForStateChange(desiredOn, attempt + 1, onResult)
            }
        }, POLL_INTERVAL_MILLIS)
    }

    /** 활성 창 → service.windows → 배민 앱을 직접 띄워서 1.5초 뒤 1회 재시도, 순서로 찾는다. */
    private fun findBaeminRoot(onFound: (AccessibilityNodeInfo?) -> Unit) {
        val active = service.rootInActiveWindow
        if (active?.packageName == BaeminRules.PACKAGE_NAME) {
            onFound(active)
            return
        }

        val fromWindows = service.windows.firstNotNullOfOrNull { window ->
            window.root?.takeIf { it.packageName == BaeminRules.PACKAGE_NAME }
        }
        if (fromWindows != null) {
            onFound(fromWindows)
            return
        }

        val launchIntent = service.packageManager.getLaunchIntentForPackage(BaeminRules.PACKAGE_NAME)
        if (launchIntent == null) {
            onFound(null)
            return
        }
        service.startActivity(launchIntent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        Handler(Looper.getMainLooper()).postDelayed({
            val retried = service.rootInActiveWindow?.takeIf { it.packageName == BaeminRules.PACKAGE_NAME }
                ?: service.windows.firstNotNullOfOrNull { window ->
                    window.root?.takeIf { it.packageName == BaeminRules.PACKAGE_NAME }
                }
            onFound(retried)
        }, LAUNCH_RETRY_DELAY_MILLIS)
    }

    /** 1순위: 텍스트가 정확히 "신규배차"인 라벨 노드에서 부모로 올라가며 첫 clickable.
     *  2순위: resId가 "신규배차_"로 시작하고 "버튼"으로 끝나는 clickable 노드. */
    private fun findToggleNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        findNodeMatching(root) { it.text?.toString() == "신규배차" }?.let { label ->
            var current: AccessibilityNodeInfo? = label
            while (current != null) {
                if (current.isClickable) return current
                current = current.parent
            }
        }

        return findNodeMatching(root) { node ->
            node.isClickable &&
                node.viewIdResourceName?.let { it.startsWith("신규배차_") && it.endsWith("버튼") } == true
        }
    }

    private fun isCurrentlyOn(root: AccessibilityNodeInfo, toggleNode: AccessibilityNodeInfo): Boolean {
        val resId = toggleNode.viewIdResourceName
        if (resId != null) {
            if (resId.contains("켜기") || resId.contains("꺼져")) return false
            if (resId.contains("끄기") || resId.contains("켜져")) return true
        }
        val turnedOffHint = findNodeMatching(root) { it.text?.toString()?.contains("신규배차를 켜고") == true }
        return turnedOffHint == null
    }

    private fun findNodeMatching(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodeMatching(child, predicate)?.let { return it }
        }
        return null
    }
}
