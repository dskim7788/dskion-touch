package com.dskion.touch.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Fully-qualified id the accessibility service will register under once it exists
 * (milestone 2). Defined now so this check needs no changes when that service lands.
 */
const val ACCESSIBILITY_SERVICE_ID = "com.dskion.touch/com.dskion.touch.accessibility.TouchAccessibilityService"

fun isMicGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    return enabled.split(':').any { it.equals(ACCESSIBILITY_SERVICE_ID, ignoreCase = true) }
}

fun isOverlayGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

fun overlaySettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

fun batteryOptimizationIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
