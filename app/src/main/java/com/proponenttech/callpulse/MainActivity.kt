package com.proponenttech.callpulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.proponenttech.callpulse.core.data.session.SessionRepository
import com.proponenttech.callpulse.core.ui.theme.CallPulseTheme
import com.proponenttech.callpulse.feature.auth.HomeRoute
import com.proponenttech.callpulse.feature.auth.LoginRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single Activity + Navigation Compose (§10).
 *
 * The graph grows one destination per stage: the consent and permission stepper in Stage H,
 * the review sheet in Stage J, the day view in Stage L.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var session: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A stored token means the app reopens straight into the app, not the login screen.
        val start = if (session.isLoggedIn) Routes.HOME else Routes.LOGIN

        setContent {
            CallPulseTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = start) {
                    composable(Routes.LOGIN) {
                        LoginRoute(
                            onSignedIn = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.LOGIN) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(Routes.HOME) {
                        HomeRoute(
                            onSignOut = {
                                navController.navigate(Routes.LOGIN) {
                                    popUpTo(Routes.HOME) { inclusive = true }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
}
