package com.smsforwarder.oppo.ui.navigation

/**
 * All navigation destinations in the OPPO app.
 */
sealed class OppoDestination(val route: String) {
    object Home : OppoDestination("home")
    object Settings : OppoDestination("settings")
    object FilterRules : OppoDestination("filter_rules")
    object Pairing : OppoDestination("pairing")
    object BackgroundSetup : OppoDestination("background_setup")
}
