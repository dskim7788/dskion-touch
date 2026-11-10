package com.dskion.touch.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.dskion.touch.core.model.ScreenSnapshot

/**
 * Milestone 2: reads the foreground window's node tree and publishes it to [ScreenStateHolder]
 * for the Console screen to display. No matching or tapping yet — that lands once real
 * 배민/쿠팡이츠 dumps are captured through this build (milestone 3+).
 */
class TouchAccessibilityService : AccessibilityService() {

    private var lastProcessedAtMillis = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScreenStateHolder.setServiceRunning(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastProcessedAtMillis < DEBOUNCE_MILLIS) return
        lastProcessedAtMillis = now

        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString() ?: return
        // Skip our own app's windows so switching to the Console tab to read a captured
        // snapshot doesn't immediately overwrite it with the Console screen itself.
        if (packageName == applicationContext.packageName) return
        val snapshot = ScreenSnapshot(
            packageName = packageName,
            nodes = root.toScreenNodes(),
            timestampMillis = System.currentTimeMillis(),
        )
        ScreenStateHolder.updateSnapshot(snapshot)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        ScreenStateHolder.setServiceRunning(false)
    }

    companion object {
        private const val DEBOUNCE_MILLIS = 300L
    }
}
