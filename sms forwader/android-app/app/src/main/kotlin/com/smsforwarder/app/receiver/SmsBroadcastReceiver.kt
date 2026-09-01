package com.smsforwarder.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.smsforwarder.app.domain.model.SmsMessageData
import com.smsforwarder.app.filter.SmsForwardingPipeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var forwardingPipeline: SmsForwardingPipeline

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages: Array<SmsMessage> = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?: return

        if (messages.isEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: "UNKNOWN"
        val timestamp = messages[0].timestampMillis

        // Reconstruct multipart SMS
        val bodyBuilder = StringBuilder()
        for (msg in messages) {
            msg.displayMessageBody?.let { bodyBuilder.append(it) }
        }
        val fullBody = bodyBuilder.toString()

        val smsData = SmsMessageData(
            sender = sender,
            body = fullBody,
            timestampMs = timestamp
        )

        // Async execution to avoid ANR on main thread
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                forwardingPipeline.processAndForward(smsData)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
