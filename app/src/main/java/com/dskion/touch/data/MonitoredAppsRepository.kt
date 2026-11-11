package com.dskion.touch.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.monitoredAppsDataStore by preferencesDataStore(name = "monitored_apps")
private val KEY_MONITORED_PACKAGES = stringSetPreferencesKey("monitored_packages")

/**
 * Which apps [com.dskion.touch.accessibility.TouchAccessibilityService] is allowed to read screen
 * content from at all. This is enforced at the OS level via
 * [android.accessibilityservice.AccessibilityServiceInfo.packageNames] - apps not on this list
 * never generate an accessibility event for this service in the first place, so their on-screen
 * text (passwords, balances, OTPs, ...) never reaches this app's process, not even transiently.
 * Defaults to just the two rider apps this app has any commands for.
 */
class MonitoredAppsRepository(private val context: Context) {

    val monitoredPackages: Flow<Set<String>> = context.monitoredAppsDataStore.data.map { prefs ->
        prefs[KEY_MONITORED_PACKAGES] ?: DEFAULT_PACKAGES
    }

    suspend fun addPackage(packageName: String) {
        context.monitoredAppsDataStore.edit { prefs ->
            prefs[KEY_MONITORED_PACKAGES] = (prefs[KEY_MONITORED_PACKAGES] ?: DEFAULT_PACKAGES) + packageName
        }
    }

    suspend fun removePackage(packageName: String) {
        context.monitoredAppsDataStore.edit { prefs ->
            prefs[KEY_MONITORED_PACKAGES] = (prefs[KEY_MONITORED_PACKAGES] ?: DEFAULT_PACKAGES) - packageName
        }
    }

    companion object {
        val DEFAULT_PACKAGES = setOf("com.woowahan.bros", "com.coupang.mobile.eats.courier")
    }
}
