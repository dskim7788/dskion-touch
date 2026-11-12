package com.dskion.touch

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catches any uncaught crash - on the main thread or the accessibility service's background
 * coroutines - and writes the stack trace to a local file before letting it proceed exactly as
 * before (the OS's own "앱이 종료됐습니다" dialog, if any). This dev environment has no logcat/
 * device access, so a crash otherwise leaves nothing to diagnose beyond a screenshot of that
 * dialog - [com.dskion.touch.ui.home.HomeScreen] shows this file's contents on the next launch so
 * the user can screenshot/copy the real stack trace back.
 */
class TouchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val writer = StringWriter()
                throwable.printStackTrace(PrintWriter(writer))
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())
                File(filesDir, CRASH_LOG_FILE_NAME).writeText("[$timestamp] thread=${thread.name}\n$writer")
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CRASH_LOG_FILE_NAME = "last_crash.txt"
    }
}
