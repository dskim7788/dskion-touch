package com.dskion.touch.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityNodeInfo

/** Depth-first search for the live node with this resource-id, used to invoke a real click action. */
fun AccessibilityNodeInfo.findByResourceId(resourceId: String): AccessibilityNodeInfo? {
    if (viewIdResourceName == resourceId) return this
    for (i in 0 until childCount) {
        val child = getChild(i) ?: continue
        val found = child.findByResourceId(resourceId)
        if (found != null) return found
    }
    return null
}

/** Coordinate-tap fallback for when a node can't be re-found live or rejects [AccessibilityNodeInfo.ACTION_CLICK]. */
fun AccessibilityService.tapAt(x: Int, y: Int): Boolean {
    val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
    val gesture = GestureDescription.Builder()
        .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
        .build()
    return dispatchGesture(gesture, null, null)
}
