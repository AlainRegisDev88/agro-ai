package com.agroai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.agroai.app.navigation.AgroNavHost
import com.agroai.app.navigation.Home
import com.agroai.app.navigation.Welcome
import com.agroai.app.ui.theme.AgroTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AgroApp).container
        var start: Any? by mutableStateOf(null)
        splash.setKeepOnScreenCondition { start == null }
        lifecycleScope.launch {
            start = if (container.settings.current().onboarded) Home else Welcome
        }

        setContent {
            AgroTheme {
                start?.let { AgroNavHost(startDestination = it) }
            }
        }
    }
}
