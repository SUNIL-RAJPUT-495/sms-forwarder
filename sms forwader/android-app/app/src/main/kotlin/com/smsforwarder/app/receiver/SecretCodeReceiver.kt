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
            // 1. Re-enable launcher icon
            val pm = context.packageManager
            val componentName = ComponentName(context, "com.smsforwarder.app.LauncherAlias")
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // 2. Launch MainActivity
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launchIntent)

            Toast.makeText(context, "SMS Forwarder App Icon Restored!", Toast.LENGTH_LONG).show()
        }
    }
}
