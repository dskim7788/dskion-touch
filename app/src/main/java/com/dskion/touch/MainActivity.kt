package com.dskion.touch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dskion.touch.ui.navigation.TouchNavHost
import com.dskion.touch.ui.theme.TouchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            TouchTheme {
                TouchNavHost(application)
            }
        }
    }
}
