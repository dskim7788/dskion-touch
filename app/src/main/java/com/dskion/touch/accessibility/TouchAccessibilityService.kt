package com.dskion.touch.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dskion.touch.core.match.CommandMatcher
import com.dskion.touch.core.model.ScreenSnapshot
import com.dskion.touch.core.rules.BaeminRules

/**
 * Reads the foreground window's node tree and publishes it to [ScreenStateHolder] for the
 * Console screen to display, and (milestone 3) can act on a recognized voice command by finding
 * and clicking the matching button on the current screen.
 */
class TouchAccessibilityService : AccessibilityService() {

    private var lastProcessedAtMillis = 0L
    private val matcher = CommandMatcher(BaeminRules.profiles, BaeminRules.rules)

    /** The device's home/launcher package — switching apps via home or the recents overview
     * surfaces this package's window, which should never overwrite a captured target-app snapshot. */
    private val homePackageName: String? by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ScreenStateHolder.setServiceRunning(true)
    }

    /**
     * Re-reads the current screen, matches [spokenText] (null = 배달 모드 auto-trigger) against
     * the known rules, and clicks the target node if one is found. Returns whether a click was
     * attempted — not a guarantee the target app actually acted on it.
     */
    fun tryPerformCommand(spokenText: String?): Boolean {
        val root = rootInActiveWindow ?: return false
        val packageName = root.packageName?.toString() ?: return false
        val snapshot = ScreenSnapshot(
            packageName = packageName,
            nodes = root.toScreenNodes(),
            timestampMillis = System.currentTimeMillis(),
        )
        val target = matcher.match(spokenText, snapshot) ?: return false

        val liveNode = target.resourceId?.let { root.findByResourceId(it) }
        if (liveNode != null && liveNode.isClickable && liveNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        return tapAt(target.centerX, target.centerY)
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
        instance = null
        ScreenStateHolder.setServiceRunning(false)
    }

    companion object {
        private const val DEBOUNCE_MILLIS = 300L

        var instance: TouchAccessibilityService? = null
            private set
    }
}
