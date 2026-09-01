package com.smsforwarder.samsung.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smsforwarder.samsung.ui.history.HistoryScreen
import com.smsforwarder.samsung.ui.home.HomeScreen
import com.smsforwarder.samsung.ui.pairing.PairingScreen
import com.smsforwarder.samsung.ui.settings.SettingsScreen

@Composable
fun SamsungNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SamsungDestination.Home.route) {
        composable(SamsungDestination.Home.route) {
            HomeScreen(
                onNavigateToHistory = { navController.navigate(SamsungDestination.History.route) },
                onNavigateToPairing = { navController.navigate(SamsungDestination.Pairing.route) },
                onNavigateToSettings = { navController.navigate(SamsungDestination.Settings.route) }
            )
        }
        composable(SamsungDestination.History.route) {
            HistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(SamsungDestination.Pairing.route) {
            PairingScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(SamsungDestination.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
