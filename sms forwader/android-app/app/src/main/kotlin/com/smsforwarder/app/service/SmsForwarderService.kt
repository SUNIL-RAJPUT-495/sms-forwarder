package com.smsforwarder.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.smsforwarder.app.notification.UniversalNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmsForwarderService : Service() {

    @Inject
    lateinit var notificationManager: UniversalNotificationManager

    override fun onCreate() {
        super.onCreate()
        val notification = notificationManager.buildServiceNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                UniversalNotificationManager.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(UniversalNotificationManager.SERVICE_NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Keep running in background
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
