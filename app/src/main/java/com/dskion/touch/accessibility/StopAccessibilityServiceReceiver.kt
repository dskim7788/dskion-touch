package com.dskion.touch.accessibility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Backs the "서비스 끄기" action button on the persistent foreground-service notification -
 * lets the user turn the whole service off (same as toggling accessibility off in system
 * Settings, via [android.accessibilityservice.AccessibilityService.disableSelf]) straight from
 * the notification shade, without opening the app. */
class StopAccessibilityServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TouchAccessibilityService.instance?.disableSelf()
    }
}
