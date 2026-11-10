package com.dskion.touch.accessibility.command

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.dskion.touch.core.rules.BaeminRules

private const val LAUNCH_RETRY_DELAY_MILLIS = 1500L

/**
 * 배민 창을 찾는 공통 순서: 활성 창 -> service.windows -> 배민 앱을 직접 띄워서 1.5초 뒤
 * 1회 재시도. [NewDispatchCommand]와 [AcceptDispatchCommand]가 공유한다.
 */
internal fun findBaeminRoot(service: AccessibilityService, onFound: (AccessibilityNodeInfo?) -> Unit) {
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

/** Depth-first search; no findAccessibilityNodeInfosByViewId since RN's testID-derived resource
 * ids carry no package prefix for that API to match against. */
internal fun findNodeMatching(
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

/**
 * Label/value rows in the dispatch card have no distinguishing resource-id (both are plain
 * "bros-textview"), so the value is read off as "whichever sibling's text comes right after the
 * label, under the label's own parent".
 */
internal fun findSiblingTextAfterLabel(root: AccessibilityNodeInfo, isLabel: (String) -> Boolean): String? {
    val labelNode = findNodeMatching(root) { it.text?.toString()?.let(isLabel) == true } ?: return null
    val parent = labelNode.parent ?: return null
    var passedLabel = false
    for (i in 0 until parent.childCount) {
        val child = parent.getChild(i) ?: continue
        val text = child.text?.toString()
        if (passedLabel && !text.isNullOrBlank()) return text
        if (!passedLabel && text?.let(isLabel) == true) passedLabel = true
    }
    return null
}
