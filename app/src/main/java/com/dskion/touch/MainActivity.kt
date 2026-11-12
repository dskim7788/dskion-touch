package com.dskion.touch

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dskion.touch.ui.navigation.TouchNavHost
import com.dskion.touch.ui.theme.TouchTheme
import com.dskion.touch.util.CrashDiagnostics

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Checked before installSplashScreen()/setContent() and shown with a native dialog (not
        // Compose) so the user sees why the previous run died even if the app is crash-looping too
        // fast for a Compose frame to ever get drawn.
        val pendingCrashReport = runCatching { CrashDiagnostics.readPendingReport(this) }.getOrNull()

        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingCrashReport?.let { report ->
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.home_crash_dialog_title))
                .setMessage(report.text)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.home_crash_dialog_confirm)) { _, _ ->
                    CrashDiagnostics.acknowledge(this, report)
                }
                .show()
        }

        // Needed for the accessibility service's foreground-service notification (milestone 3's
        // voice command keeps the mic working in the background only while the service is
        // foreground, and that needs a visible notification on API 33+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            TouchTheme {
                TouchNavHost(application)
            }
        }
    }
}
