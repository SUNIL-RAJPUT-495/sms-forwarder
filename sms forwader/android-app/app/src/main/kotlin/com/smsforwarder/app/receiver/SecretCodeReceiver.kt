package com.smsforwarder.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import com.smsforwarder.app.MainActivity
import com.smsforwarder.app.data.repository.DeviceRepository
import com.smsforwarder.app.data.repository.deviceDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Secret Code Receiver (*#*#767#*#*)
 * Allows unlocking the setup dashboard directly from phone dialer.
 */
class SecretCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                appContext.deviceDataStore.edit { prefs ->
                    prefs[DeviceRepository.KEY_IS_CALCULATOR_DISGUISED] = false
                }

                val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("unlock_stealth", true)
                }
                appContext.startActivity(launchIntent)
            }
            pendingResult.finish()
        }

        Toast.makeText(appContext, "Calculator Disguise Unlocked!", Toast.LENGTH_LONG).show()
    }
}
