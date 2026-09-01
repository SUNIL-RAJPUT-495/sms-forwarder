package com.smsforwarder.samsung.ui.navigation

sealed class SamsungDestination(val route: String) {
    object Home : SamsungDestination("home")
    object History : SamsungDestination("history")
    object Pairing : SamsungDestination("pairing")
    object Settings : SamsungDestination("settings")
}
