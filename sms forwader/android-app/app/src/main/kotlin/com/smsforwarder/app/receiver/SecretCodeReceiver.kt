package com.smsforwarder.app.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import com.smsforwarder.app.MainActivity

/**
 * Secret Code Receiver (*#*#767#*#*)
 * Allows un-hiding the app launcher icon from phone dialer.
 */
class SecretCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            val pm = context.packageManager
            val aliasComponent = ComponentName(context.packageName, "${context.packageName}.LauncherAlias")
            val invComponent = ComponentName(context.packageName, "${context.packageName}.InvisibleAlias")
            val calcComponent = ComponentName(context.packageName, "${context.packageName}.CalculatorAlias")

            // Re-enable default LauncherAlias
            pm.setComponentEnabledSetting(
                aliasComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // Disable InvisibleAlias & CalculatorAlias
            pm.setComponentEnabledSetting(
                invComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                calcComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launchIntent)

            Toast.makeText(context, "SMS Forwarder App Icon Restored!", Toast.LENGTH_LONG).show()
        }
    }
}
