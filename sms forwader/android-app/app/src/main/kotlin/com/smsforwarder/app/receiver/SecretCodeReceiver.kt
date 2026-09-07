package com.smsforwarder.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.smsforwarder.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Secret Code Receiver (*#*#767#*#*)
 * Allows opening the setup dashboard directly from phone dialer.
 */
class SecretCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("unlock_stealth", true)
                }
                appContext.startActivity(launchIntent)
            }
            pendingResult.finish()
        }

        Toast.makeText(appContext, "App Dashboard Opened!", Toast.LENGTH_LONG).show()
    }
}
