package com.dskion.touch.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.dskion.touch.TouchApplication
import java.io.File

private const val PREFS_NAME = "crash_diagnostics"
private const val KEY_LAST_SEEN_EXIT_TIMESTAMP = "last_seen_exit_timestamp"

/**
 * Finds out why the app's previous run ended, if that's worth telling the user about. Read
 * synchronously, as early as possible in [com.dskion.touch.MainActivity.onCreate] - before any
 * Compose/splash-screen work - because when the app is crash-looping on every single launch,
 * there is no guarantee a Compose frame ever gets drawn (the earlier Home-screen-based version of
 * this check could lose that race entirely). Two sources, combined:
 * 1) [TouchApplication]'s own Thread.UncaughtExceptionHandler - only covers exceptions our code
 *    actually threw and caught.
 * 2) [ActivityManager.getHistoricalProcessExitReasons] - the OS's own memory of why the process
 *    died, which also catches native crashes, ANRs, and the system killing the process outright.
 *    The OS keeps this around across launches regardless of whether it was already shown, so a
 *    SharedPreferences-stored timestamp is used to avoid showing the same old entry forever.
 */
object CrashDiagnostics {

    data class Report(val text: String, val exitTimestampMillis: Long?)

    fun readPendingReport(context: Context): Report? {
        val ownCrash = File(context.filesDir, TouchApplication.CRASH_LOG_FILE_NAME)
            .takeIf { it.exists() }
            ?.let { runCatching { it.readText() }.getOrNull() }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSeenTimestamp = prefs.getLong(KEY_LAST_SEEN_EXIT_TIMESTAMP, 0L)
        val exitInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            readLastExitReason(context)?.takeIf { it.timestampMillis > lastSeenTimestamp }
        } else {
            null
        }

        val text = listOfNotNull(exitInfo?.text, ownCrash?.let { "[앱이 직접 기록한 예외]\n$it" })
            .joinToString("\n\n")
            .ifBlank { null }
            ?: return null

        return Report(text, exitInfo?.timestampMillis)
    }

    fun acknowledge(context: Context, report: Report) {
        runCatching { File(context.filesDir, TouchApplication.CRASH_LOG_FILE_NAME).delete() }
        report.exitTimestampMillis?.let { ts ->
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_SEEN_EXIT_TIMESTAMP, ts).apply()
        }
    }

    private data class ExitReasonInfo(val text: String, val timestampMillis: Long)

    /** Reasons worth bothering the user about - excludes ordinary closes (swiped away from
     * recents, user/system stopped the process, "OTHER") which [ApplicationExitInfo] reports
     * just as faithfully as an actual crash, and which were being shown as if something had gone
     * wrong even though nothing had. */
    private val REPORTABLE_REASONS = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_SIGNALED,
    )

    private fun readLastExitReason(context: Context): ExitReasonInfo? {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return null
        val info = runCatching {
            activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
        }.getOrNull() ?: return null
        if (info.reason !in REPORTABLE_REASONS) return null

        val reasonText = when (info.reason) {
            ApplicationExitInfo.REASON_CRASH -> "CRASH (처리되지 않은 예외)"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE (네이티브 크래시)"
            ApplicationExitInfo.REASON_ANR -> "ANR (응답 없음)"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE (자원 과다 사용)"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY (메모리 부족)"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            else -> "code=${info.reason}"
        }
        val trace = runCatching {
            info.traceInputStream?.bufferedReader()?.use { it.readText() }
        }.getOrNull()

        val text = buildString {
            append("[마지막 종료 원인: $reasonText]")
            info.description?.takeIf { it.isNotBlank() }?.let { append("\n설명: $it") }
            trace?.takeIf { it.isNotBlank() }?.let { append("\n--- trace ---\n${it.take(4000)}") }
        }
        return ExitReasonInfo(text, info.timestamp)
    }
}
