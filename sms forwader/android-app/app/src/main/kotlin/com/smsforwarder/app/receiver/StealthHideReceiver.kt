package com.smsforwarder.app.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Background Alarm Receiver for Stealth Icon Disappear Mode.
 * Executes 3 seconds after the user has exited the app to ensure the OS Launcher
 * removes the icon cleanly from the App Drawer WITHOUT triggering "App Info" redirect.
 */
class StealthHideReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        runCatching {
            val pm = appContext.packageManager
            val aliasComponent = ComponentName(appContext.packageName, "${appContext.packageName}.LauncherAlias")
            pm.setComponentEnabledSetting(
                aliasComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d("StealthHideReceiver", "Successfully disabled launcher alias in background")
        }.onFailure { e ->
            Log.e("StealthHideReceiver", "Failed to disable launcher alias: ${e.message}")
        }
    }
}
