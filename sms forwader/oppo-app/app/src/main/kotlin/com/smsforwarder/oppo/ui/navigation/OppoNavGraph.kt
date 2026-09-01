package com.smsforwarder.oppo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smsforwarder.oppo.ui.home.HomeScreen
import com.smsforwarder.oppo.ui.pairing.PairingScreen
import com.smsforwarder.oppo.ui.settings.BackgroundSetupScreen
import com.smsforwarder.oppo.ui.settings.FilterRulesScreen
import com.smsforwarder.oppo.ui.settings.SettingsScreen

/**
 * Root navigation graph for the OPPO (Source) app.
 */
@Composable
fun OppoNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = OppoDestination.Home.route
    ) {
        composable(OppoDestination.Home.route) {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(OppoDestination.Settings.route) },
                onNavigateToPairing = { navController.navigate(OppoDestination.Pairing.route) },
                onNavigateToBackgroundSetup = { navController.navigate(OppoDestination.BackgroundSetup.route) }
            )
        }

        composable(OppoDestination.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFilterRules = { navController.navigate(OppoDestination.FilterRules.route) }
            )
        }

        composable(OppoDestination.FilterRules.route) {
            FilterRulesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(OppoDestination.Pairing.route) {
            PairingScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(OppoDestination.BackgroundSetup.route) {
            BackgroundSetupScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
