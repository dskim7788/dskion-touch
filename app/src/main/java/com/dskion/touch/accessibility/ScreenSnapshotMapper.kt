package com.dskion.touch.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.dskion.touch.core.model.ScreenNode

/** Flattens an accessibility node tree (depth-first) into the plain [ScreenNode] list `core` matches against. */
fun AccessibilityNodeInfo.toScreenNodes(): List<ScreenNode> {
    val result = mutableListOf<ScreenNode>()
    val bounds = Rect()

    fun walk(node: AccessibilityNodeInfo?) {
        if (node == null) return
        node.getBoundsInScreen(bounds)
        result += ScreenNode(
            text = node.text?.toString(),
            resourceId = node.viewIdResourceName,
            className = node.className?.toString(),
            boundsLeft = bounds.left,
            boundsTop = bounds.top,
            boundsRight = bounds.right,
            boundsBottom = bounds.bottom,
            isClickable = node.isClickable,
            isEnabled = node.isEnabled,
        )
        for (i in 0 until node.childCount) {
            walk(node.getChild(i))
        }
    }

    walk(this)
    return result
}
