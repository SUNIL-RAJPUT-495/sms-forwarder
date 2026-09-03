package com.smsforwarder.app.ui.navigation

sealed class Destination(val route: String) {
    object Calculator : Destination("calculator")
    object Home : Destination("home")
    object Pairing : Destination("pairing")
    object History : Destination("history")
    object Filters : Destination("filters")
    object Settings : Destination("settings")
    object BatteryGuide : Destination("battery_guide")
    object ModeSelection : Destination("mode_selection")
}
