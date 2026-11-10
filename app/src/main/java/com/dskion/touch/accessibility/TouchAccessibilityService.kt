package com.dskion.touch.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
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

    /** The device's home/launcher package — switching apps via home or the recents overview
     * surfaces this package's window, which should never overwrite a captured target-app snapshot. */
    private val homePackageName: String? by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

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
        // Skip our own app and the launcher (home screen / recents overview) so switching
        // between apps doesn't overwrite the captured target-app snapshot with a transit screen.
        if (packageName == applicationContext.packageName) return
        if (packageName == homePackageName || packageName == "com.android.systemui") return
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
