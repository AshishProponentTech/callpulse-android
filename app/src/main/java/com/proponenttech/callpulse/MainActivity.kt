package com.proponenttech.callpulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.proponenttech.callpulse.core.data.session.SessionRepository
import com.proponenttech.callpulse.core.ui.theme.CallPulseTheme
import com.proponenttech.callpulse.feature.auth.HomeRoute
import com.proponenttech.callpulse.feature.auth.LoginRoute
import com.proponenttech.callpulse.feature.auth.OnboardingRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single Activity + Navigation Compose (§10).
 *
 * The review sheet (Stage J) and the day view (Stage L) join this graph as they land.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var session: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A stored token means the app reopens into the app, not the login screen.
        val start = if (session.isLoggedIn) Routes.HOME else Routes.LOGIN

        setContent {
            CallPulseTheme {
                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    session.state.signedIn.collect { signedIn ->
                        if (!signedIn && navController.currentDestination?.route != Routes.LOGIN) {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(navController.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }

                NavHost(navController = navController, startDestination = start) {
                    composable(Routes.LOGIN) {
                        LoginRoute(
                            onSignedIn = {
                                // Straight into consent + permissions; capture cannot run
                                // until both are done (decision #14, §12).
                                navController.navigate(Routes.ONBOARDING) {
                                    popUpTo(Routes.LOGIN) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(Routes.ONBOARDING) {
                        OnboardingRoute(
                            onReady = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(Routes.HOME) {
                        HomeRoute(
                            onSignOut = {
                                navController.navigate(Routes.LOGIN) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            onOpenOnboarding = { navController.navigate(Routes.ONBOARDING) },
                        )
                    }
                }
            }
        }
    }
}

object Routes {
    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
}
