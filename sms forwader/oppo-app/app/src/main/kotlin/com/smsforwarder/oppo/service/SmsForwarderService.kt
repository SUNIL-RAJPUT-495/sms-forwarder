package com.smsforwarder.oppo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.smsforwarder.oppo.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Foreground service that keeps the SMS forwarding pipeline alive.
 *
 * On OPPO/ColorOS, processes without a visible foreground notification are
 * aggressively killed. This service maintains a persistent notification
 * ensuring the process survives when the screen is off.
 *
 * The BroadcastReceiver handles SMS arrivals independently (it is
 * manifest-registered and will wake the process if killed). This service
 * provides additional reliability and keeps WorkManager scheduling active.
 *
 * Phase 3 will wire in the actual SMS queue monitoring here.
 */
@AndroidEntryPoint
class SmsForwarderService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        // Phase 3: start observing OutboundQueue and drain pending messages
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Forwarder Service",
                NotificationManager.IMPORTANCE_LOW // Low: no sound, persistent
            ).apply {
                description = "Keeps SMS forwarding active in the background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Forwarder Active")
            .setContentText("Monitoring for bank SMS to forward securely")
            .setSmallIcon(android.R.drawable.ic_dialog_email) // Replace with proper icon in Phase 8
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    companion object {
        const val CHANNEL_ID = "sms_forwarder_service"
        const val NOTIFICATION_ID = 1001
    }
}
