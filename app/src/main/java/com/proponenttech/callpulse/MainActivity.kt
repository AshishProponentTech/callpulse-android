package com.proponenttech.callpulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.proponenttech.callpulse.core.ui.theme.CallPulseTheme
import com.proponenttech.callpulse.feature.auth.LoginScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity + Navigation Compose (§10).
 *
 * The graph grows one destination per stage: consent and the permission stepper in Stage H,
 * the review sheet in Stage J, the day view in Stage L.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CallPulseTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = Routes.LOGIN) {
                    composable(Routes.LOGIN) {
                        LoginScreen()
                    }
                }
            }
        }
    }
}

object Routes {
    const val LOGIN = "login"
}
